package dev.gavenda.kozeki.data.tracking.anilist

import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.await
import dev.gavenda.kozeki.data.model.PublicationStatus
import dev.gavenda.kozeki.data.model.TrackStatus
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The account a token belongs to. */
data class AniListViewer(val name: String, val avatarUrl: String?, val scoreFormat: ScoreFormat)

/** A manga on AniList, with the signed-in user's entry for it when they have one. */
data class AniListMedia(
    val id: Long,
    val title: String,
    val siteUrl: String?,
    val chapters: Int?,
    val publication: PublicationStatus?,
    val coverUrl: String?,
    val year: Int?,
    val format: String?,
    val entry: AniListEntry?,
)

/** A manga on the user's list. [score] is 0 to 100 whatever the account shows. */
data class AniListEntry(
    val id: Long,
    val status: TrackStatus,
    val progress: Int,
    val score: Int,
    val startedOn: LocalDate?,
    val completedOn: LocalDate?,
)

/** What is written to an entry. Dates are left alone while null. */
data class AniListEntryUpdate(
    val status: TrackStatus? = null,
    val progress: Int? = null,
    val score: Int? = null,
    val startedOn: LocalDate? = null,
    val completedOn: LocalDate? = null,
)

/**
 * AniList's GraphQL API. Every call takes the token it is made with, so that signing in, which
 * has to ask who the token belongs to, does not depend on the account it is about to store.
 */
class AniListApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun viewer(token: String): AniListViewer {
        val data = query(token, VIEWER, JsonObject(emptyMap()))
        val viewer = data["Viewer"]?.jsonObject ?: throw MetadataException.Server(200, "No viewer")
        return AniListViewer(
            name = viewer["name"].text().orEmpty(),
            avatarUrl = (viewer["avatar"] as? JsonObject)?.get("medium").text(),
            scoreFormat = ScoreFormat.parse(
                ((viewer["mediaListOptions"] as? JsonObject)?.get("scoreFormat")).text(),
            ),
        )
    }

    suspend fun search(token: String, text: String): List<AniListMedia> {
        val data = query(token, SEARCH, buildJsonObject { put("search", text) })
        val media = (data["Page"] as? JsonObject)?.get("media") as? JsonArray ?: return emptyList()
        return media.mapNotNull { (it as? JsonObject)?.let(::parseMedia) }
    }

    suspend fun media(token: String, id: Long): AniListMedia? {
        val data = query(token, MEDIA, buildJsonObject { put("id", id) })
        return (data["Media"] as? JsonObject)?.let(::parseMedia)
    }

    /** Creates the entry for [mediaId], or changes the one there is. */
    suspend fun save(token: String, mediaId: Long, update: AniListEntryUpdate): AniListEntry {
        val variables = buildJsonObject {
            put("mediaId", mediaId)
            update.status?.let { put("status", it.name) }
            update.progress?.let { put("progress", it) }
            update.score?.let { put("score", it) }
            update.startedOn?.let { put("startedAt", date(it)) }
            update.completedOn?.let { put("completedAt", date(it)) }
        }
        val data = query(token, SAVE, variables)
        val entry = data["SaveMediaListEntry"] as? JsonObject ?: throw MetadataException.Server(200, "No entry")
        return parseEntry(entry) ?: throw MetadataException.Server(200, "No entry")
    }

    suspend fun delete(token: String, entryId: Long) {
        query(token, DELETE, buildJsonObject { put("id", entryId) })
    }

    private suspend fun query(token: String, text: String, variables: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("query", text)
            put("variables", variables)
        }.toString().toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val (code, text2) = client.newCall(request).await().use { it.code to it.body.string() }
        val root = withContext(Dispatchers.Default) {
            runCatching { json.parseToJsonElement(text2).jsonObject }.getOrNull()
        }
        val errors = root?.get("errors") as? JsonArray
        if (code == 401 || errors?.any { error -> error.errorMessage()?.contains("Invalid token", ignoreCase = true) == true } == true) {
            throw MetadataException.Unauthorized("AniList refused the token")
        }
        if (code == 429) throw MetadataException.RateLimited()
        if (errors != null && errors.isNotEmpty()) {
            throw MetadataException.Server(code, errors.first().errorMessage() ?: "AniList refused the request")
        }
        if (code !in 200..299 || root == null) throw MetadataException.Server(code, "AniList did not answer")
        return root["data"] as? JsonObject ?: throw MetadataException.Server(code, "AniList sent no data")
    }

    private fun JsonElement.errorMessage(): String? = (this as? JsonObject)?.get("message").text()

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonElement?.number(): Long? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull

    private fun parseMedia(media: JsonObject): AniListMedia? {
        val id = media["id"].number() ?: return null
        val title = (media["title"] as? JsonObject)?.get("userPreferred").text() ?: return null
        return AniListMedia(
            id = id,
            title = title,
            siteUrl = media["siteUrl"].text(),
            chapters = media["chapters"].number()?.toInt(),
            publication = media["status"].text()?.let { name -> PublicationStatus.entries.firstOrNull { it.name == name } },
            coverUrl = (media["coverImage"] as? JsonObject)?.get("large").text(),
            year = ((media["startDate"] as? JsonObject)?.get("year")).number()?.toInt(),
            format = media["format"].text(),
            entry = (media["mediaListEntry"] as? JsonObject)?.let(::parseEntry),
        )
    }

    private fun parseEntry(entry: JsonObject): AniListEntry? {
        val id = entry["id"].number() ?: return null
        val status = entry["status"].text()?.let { name -> TrackStatus.entries.firstOrNull { it.name == name } }
            ?: return null
        return AniListEntry(
            id = id,
            status = status,
            progress = entry["progress"].number()?.toInt() ?: 0,
            score = entry["score"].number()?.toInt() ?: 0,
            startedOn = parseDate(entry["startedAt"]),
            completedOn = parseDate(entry["completedAt"]),
        )
    }

    private fun parseDate(element: JsonElement?): LocalDate? {
        val date = element as? JsonObject ?: return null
        val year = date["year"].number()?.toInt() ?: return null
        val month = date["month"].number()?.toInt() ?: 1
        val day = date["day"].number()?.toInt() ?: 1
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun date(date: LocalDate): JsonObject = buildJsonObject {
        put("year", date.year)
        put("month", date.monthValue)
        put("day", date.dayOfMonth)
    }

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        // GraphQL variables begin with a dollar sign, which a Kotlin string would read as its own.
        const val V = "$"

        const val ENTRY = "id status progress score(format: POINT_100) startedAt { year month day } " +
            "completedAt { year month day }"
        const val MEDIA_FIELDS = "id title { userPreferred } siteUrl chapters status format startDate { year } " +
            "coverImage { large } mediaListEntry { $ENTRY }"

        const val VIEWER = "query { Viewer { name avatar { medium } mediaListOptions { scoreFormat } } }"
        const val SEARCH = "query(${V}search: String) { Page(perPage: 25) { " +
            "media(search: ${V}search, type: MANGA) { $MEDIA_FIELDS } } }"
        const val MEDIA = "query(${V}id: Int) { Media(id: ${V}id, type: MANGA) { $MEDIA_FIELDS } }"
        const val SAVE = "mutation(${V}mediaId: Int, ${V}status: MediaListStatus, ${V}progress: Int, " +
            "${V}score: Int, ${V}startedAt: FuzzyDateInput, ${V}completedAt: FuzzyDateInput) { " +
            "SaveMediaListEntry(mediaId: ${V}mediaId, status: ${V}status, progress: ${V}progress, " +
            "scoreRaw: ${V}score, startedAt: ${V}startedAt, completedAt: ${V}completedAt) { $ENTRY } }"
        const val DELETE = "mutation(${V}id: Int) { DeleteMediaListEntry(id: ${V}id) { deleted } }"
    }
}
