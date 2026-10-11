package dev.gavenda.kozeki.data.backup.drive

import dev.gavenda.kozeki.data.backup.BackupInfo
import dev.gavenda.kozeki.data.metadata.await
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** The Google account a token gives access to. */
data class DriveAccount(val email: String, val photoUrl: String?)

/** The backup as a file on Drive. */
data class DriveFile(val id: String, val info: BackupInfo)

/** Drive answered, and refused. [reason] is its own word for why, such as `accessNotConfigured`. */
class DriveException(val code: Int, val reason: String?) :
    Exception("Drive answered $code" + reason?.let { " ($it)" }.orEmpty())

/**
 * The one file the app keeps on Google Drive: the backup, in the folder Drive sets aside for an
 * app's own data. That folder is not among the user's files, and reaching it takes the narrowest
 * access Drive has, to nothing but what the app itself put there.
 *
 * A transport failure comes out of every call as the app's usual network exception.
 */
class DriveApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun account(token: String): DriveAccount {
        val url = "$API/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(emailAddress,photoLink)")
            .build()
        val user = send(token, Request.Builder().url(url)) { json.decodeFromString<About>(it.body.string()) }.user
        return DriveAccount(user.emailAddress, user.photoLink)
    }

    /** The backup that is there, or null when none was made yet. */
    suspend fun find(token: String): DriveFile? {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", APP_DATA_FOLDER)
            .addQueryParameter("q", "name = '$FILE_NAME'")
            // Two devices backing up for the first time at once would each make a file.
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "1")
            .addQueryParameter("fields", "files($FILE_FIELDS)")
            .build()
        return send(token, Request.Builder().url(url)) { json.decodeFromString<FileList>(it.body.string()) }
            .files.firstOrNull()?.toDriveFile()
    }

    suspend fun download(token: String, fileId: String): ByteArray {
        val url = fileUrl(API, fileId).addQueryParameter("alt", "media").build()
        return send(token, Request.Builder().url(url)) { it.body.bytes() }
    }

    /** Puts [content] in the place of the backup with the ID [replacing], or makes the first one. */
    suspend fun upload(token: String, content: ByteArray, info: BackupInfo, replacing: String?): DriveFile {
        if (replacing != null) {
            try {
                return upload(token, content, info, fileUrl(UPLOAD, replacing), create = false)
            } catch (e: DriveException) {
                // Deleted since it was found, as the user can do from Drive's settings.
                if (e.code != 404) throw e
            }
        }
        return upload(token, content, info, "$UPLOAD/files".toHttpUrl().newBuilder(), create = true)
    }

    private suspend fun upload(
        token: String,
        content: ByteArray,
        info: BackupInfo,
        url: HttpUrl.Builder,
        create: Boolean,
    ): DriveFile {
        val metadata = buildJsonObject {
            // Where a file is cannot be changed afterwards, and its name need not be.
            if (create) {
                put("name", FILE_NAME)
                putJsonArray("parents") { add(APP_DATA_FOLDER) }
            }
            putJsonObject("appProperties") {
                put(CREATED_AT, info.createdAt.toString())
                // Drive takes 124 bytes for a property, its name included.
                put(DEVICE, info.device.take(30))
                put(BOOKS, info.books.toString())
            }
        }
        val body = MultipartBody.Builder()
            .setType(MULTIPART_RELATED)
            .addPart(metadata.toString().toRequestBody(JSON_TYPE))
            .addPart(content.toRequestBody(GZIP_TYPE))
            .build()
        val target = url.addQueryParameter("uploadType", "multipart").addQueryParameter("fields", FILE_FIELDS).build()
        val request = Request.Builder().url(target).method(if (create) "POST" else "PATCH", body)
        return send(token, request) { json.decodeFromString<FileResource>(it.body.string()) }.toDriveFile()
    }

    private fun fileUrl(base: String, fileId: String): HttpUrl.Builder =
        "$base/files".toHttpUrl().newBuilder().addPathSegment(fileId)

    private suspend fun <T> send(token: String, request: Request.Builder, read: (Response) -> T): T =
        client.newCall(request.header("Authorization", "Bearer $token").build()).await().use { response ->
            if (!response.isSuccessful) throw DriveException(response.code, reasonIn(response.body.string()))
            read(response)
        }

    /** Why Drive refused, out of the error it answers with. */
    private fun reasonIn(body: String): String? = runCatching {
        val error = json.parseToJsonElement(body).jsonObject.getValue("error").jsonObject
        (error["errors"]?.jsonArray?.firstOrNull() as? JsonObject)?.get("reason")?.jsonPrimitive?.content
            ?: error["status"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun FileResource.toDriveFile(): DriveFile = DriveFile(
        id = id,
        info = BackupInfo(
            // A file with nothing said about it is as old as it was last written.
            createdAt = appProperties[CREATED_AT]?.toLongOrNull()
                ?: modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: 0L,
            device = appProperties[DEVICE].orEmpty(),
            books = appProperties[BOOKS]?.toIntOrNull() ?: 0,
        ),
    )

    @Serializable
    private data class About(val user: User) {
        @Serializable
        data class User(val emailAddress: String, val photoLink: String? = null)
    }

    @Serializable
    private data class FileList(val files: List<FileResource> = emptyList())

    @Serializable
    private data class FileResource(
        val id: String,
        val modifiedTime: String? = null,
        val appProperties: Map<String, String> = emptyMap(),
    )

    companion object {
        /** All the access asked for: the app's own folder on Drive, and nothing of the user's files. */
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

        const val FILE_NAME = "kozeki-backup.json.gz"

        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val FILE_FIELDS = "id,modifiedTime,appProperties"
        private const val CREATED_AT = "createdAt"
        private const val DEVICE = "device"
        private const val BOOKS = "books"

        private val MULTIPART_RELATED = "multipart/related".toMediaType()
        private val JSON_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val GZIP_TYPE = "application/gzip".toMediaType()
    }
}
