package dev.gavenda.kozeki.data.metadata.hardcover

import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.metadata.await
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.singleLineOrNull
import dev.gavenda.kozeki.data.model.singleLines
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Hardcover's GraphQL API. Only its `search` action is used, for both free-text and ISBN lookups,
 * because that needs the narrowest scope and the search index already carries every ISBN of a book.
 */
class HardcoverProvider(
    private val client: OkHttpClient,
    private val auth: HardcoverAuth,
) : MetadataProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override val source: MetadataSource = MetadataSource.HARDCOVER

    override suspend fun unavailableReason(): MetadataProvider.Unavailable? =
        if (auth.isSignedIn()) null else MetadataProvider.Unavailable.SIGNED_OUT

    override suspend fun search(query: String): List<BookMetadata> = searchBooks(query, isbn13 = null)

    override suspend fun findByIsbn(isbn13: String): List<BookMetadata> =
        searchBooks(isbn13, isbn13).filter { it.isbn13 == isbn13 }

    private suspend fun searchBooks(query: String, isbn13: String?): List<BookMetadata> {
        val body = buildJsonObject {
            put("query", SEARCH_QUERY)
            putJsonObject("variables") { put("query", query) }
        }.toString()

        val data = execute(body)
        val hits = data["search"].asObject()?.get("results").asObject()?.get("hits") as? JsonArray ?: return emptyList()
        return hits.mapNotNull { hit -> hit.asObject()?.get("document").asObject()?.let { toMetadata(it, isbn13) } }
    }

    /** Sends a GraphQL request, refreshing the token once if the API says it has expired. */
    private suspend fun execute(body: String): JsonObject {
        val token = auth.accessToken()
            ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.SIGNED_OUT)

        var response = send(body, token)
        if (response.code == 401) {
            response.close()
            val fresh = auth.refreshAfterRejection(token)
                ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.SIGNED_OUT)
            response = send(body, fresh)
        }

        response.use {
            val text = it.body.string()
            when {
                it.code == 429 -> throw MetadataException.RateLimited(it.header("Retry-After")?.toLongOrNull())
                it.code == 401 || it.code == 403 -> throw MetadataException.Unauthorized(errorMessage(text))
                !it.isSuccessful -> throw MetadataException.Server(it.code, errorMessage(text))
            }
            val root = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw MetadataException.Server(response.code, "Unreadable response") }
            (root["errors"] as? JsonArray)?.firstOrNull()?.asObject()?.string("message")?.let { message ->
                throw MetadataException.Server(response.code, message)
            }
            return root["data"].asObject() ?: throw MetadataException.Server(response.code, "Empty response")
        }
    }

    private suspend fun send(body: String, token: String) = client.newCall(
        Request.Builder()
            .url(HardcoverAuth.GRAPHQL_ENDPOINT)
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(HardcoverAuth.JSON_MEDIA_TYPE))
            .build(),
    ).await()

    private fun errorMessage(body: String): String = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root.string("error_description") ?: root.string("message") ?: root.string("error")
    }.getOrNull() ?: "Request failed"

    private fun toMetadata(document: JsonObject, queriedIsbn: String?): BookMetadata? {
        val id = document.string("id") ?: return null
        val title = document.string("title")?.singleLineOrNull() ?: return null
        val isbns = (document["isbns"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .mapNotNull(Isbn::toIsbn13)
        // A book's search document lists the ISBNs of all its editions; keep the one that was asked for.
        val isbn13 = queriedIsbn?.takeIf { it in isbns } ?: isbns.firstOrNull()
        val slug = document.string("slug")
        return BookMetadata(
            source = MetadataSource.HARDCOVER,
            sourceId = id,
            title = title,
            subtitle = document.string("subtitle")?.singleLineOrNull(),
            authors = strings(document["author_names"]),
            description = document.string("description")?.takeIf { it.isNotBlank() },
            publishedDate = document.string("release_date") ?: document.int("release_year")?.toString(),
            pageCount = document.int("pages")?.takeIf { it > 0 },
            isbn10 = isbn13?.let(Isbn::toIsbn10),
            isbn13 = isbn13,
            categories = strings(document["genres"]),
            coverUrl = document["image"].asObject()?.string("url")?.takeIf { it.startsWith("https://") },
            infoUrl = slug?.let { "https://hardcover.app/books/$it" },
        )
    }

    private fun strings(element: JsonElement?): List<String> =
        (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.singleLines()

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private companion object {
        const val SEARCH_QUERY =
            "query Search(\$query: String!) { search(query: \$query, query_type: \"Book\", per_page: 12, page: 1) { results } }"
    }
}
