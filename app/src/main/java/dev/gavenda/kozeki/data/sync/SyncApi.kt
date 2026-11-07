package dev.gavenda.kozeki.data.sync

import dev.gavenda.kozeki.data.account.AccountAuth
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.NoteEntity
import dev.gavenda.kozeki.data.db.PhysicalReadingEntity
import dev.gavenda.kozeki.data.db.ReadThroughEntity
import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.ShelfBookEntity
import dev.gavenda.kozeki.data.db.ShelfEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.db.YearlyGoalEntity
import dev.gavenda.kozeki.data.metadata.await
import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** One of the settings both apps share, as the server keeps it. */
@Serializable
data class SettingRow(val key: String, val value: JsonElement? = null, val sync: SyncStamp)

/**
 * Rows by table, the same both ways. Each row is the Room entity as it serialises, which the
 * server reads field for field (see `webapp/shared/tables.ts`).
 */
@Serializable
data class SyncRows(
    val books: List<BookEntity> = emptyList(),
    val readThroughs: List<ReadThroughEntity> = emptyList(),
    val readingSessions: List<ReadingSessionEntity> = emptyList(),
    val physicalReadings: List<PhysicalReadingEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val yearlyGoals: List<YearlyGoalEntity> = emptyList(),
    val shelves: List<ShelfEntity> = emptyList(),
    val shelfBooks: List<ShelfBookEntity> = emptyList(),
    val settings: List<SettingRow> = emptyList(),
) {
    val size: Int
        get() = books.size + readThroughs.size + readingSessions.size + physicalReadings.size + notes.size +
            yearlyGoals.size + shelves.size + shelfBooks.size + settings.size
}

@Serializable
data class SyncRequest(val cursor: Long, val push: SyncRows)

@Serializable
data class SyncResponse(val rows: SyncRows = SyncRows(), val cursor: Long, val more: Boolean = false)

/** Why talking to the server failed. */
sealed class SyncException(message: String) : IOException(message) {
    /** No connection, or no fresh token could be had for want of one. */
    class Offline(message: String) : SyncException(message)

    /** The server refused the request, with its reason. */
    class Refused(val code: Int, message: String) : SyncException(message)
}

/** Kozeki's API on the Worker in `webapp/`. */
class SyncApi(
    private val client: OkHttpClient,
    private val auth: AccountAuth,
    private val baseUrl: String,
) {
    private val jsonType = "application/json".toMediaType()

    suspend fun sync(request: SyncRequest): SyncResponse {
        val body = SyncJson.encodeToString(request).toRequestBody(jsonType)
        return SyncJson.decodeFromString(call("sync") { post(body) }.orEmpty())
    }

    /** Which of these cover files the server does not have yet. */
    suspend fun missingCovers(names: List<String>): List<String> {
        val body = SyncJson.encodeToString(MissingCovers(names)).toRequestBody(jsonType)
        return SyncJson.decodeFromString<MissingCovers>(call("covers/missing") { post(body) }.orEmpty()).missing
    }

    suspend fun uploadCover(name: String, file: File) {
        call("covers/$name") { put(file.asRequestBody("image/jpeg".toMediaType())) }
    }

    /** @return false when the server has no such cover. */
    suspend fun downloadCover(name: String, target: File): Boolean {
        val request = authorized("covers/$name").get().build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw SyncException.Offline(e.message ?: "No connection")
        }
        response.use {
            if (it.code == 404) return false
            if (!it.isSuccessful) throw SyncException.Refused(it.code, "Cover download failed")
            val partial = File(target.parentFile, "${target.name}.part")
            target.parentFile?.mkdirs()
            partial.outputStream().use { sink -> it.body.byteStream().copyTo(sink) }
            partial.renameTo(target)
            return true
        }
    }

    private suspend fun authorized(path: String): Request.Builder {
        val token = auth.accessToken() ?: throw SyncException.Offline("No access token")
        return Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
    }

    private suspend fun call(path: String, method: Request.Builder.() -> Request.Builder): String? {
        val request = authorized(path).method().build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw SyncException.Offline(e.message ?: "No connection")
        }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                val reason = runCatching { SyncJson.decodeFromString<ErrorBody>(text).error }.getOrNull()
                throw SyncException.Refused(it.code, reason ?: "HTTP ${it.code}")
            }
            return text
        }
    }

    @Serializable
    private data class MissingCovers(val names: List<String> = emptyList(), val missing: List<String> = emptyList())

    @Serializable
    private data class ErrorBody(val error: String? = null)
}

/**
 * How rows go over the wire. Nulls are left out, and a field the server does not send (a comic's
 * columns, or one added to the app later) falls back to its default.
 */
val SyncJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    coerceInputValues = true
}
