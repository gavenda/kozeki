package dev.gavenda.kozeki.data.metadata.googlebooks

import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.metadata.await
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.singleLine
import dev.gavenda.kozeki.data.model.singleLineOrNull
import dev.gavenda.kozeki.data.model.singleLines
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Google Books, used for public volume metadata only, which needs an API key and no OAuth.
 *
 * @param androidCertSha1 SHA-1 of the signing certificate. Sent with the package name so the key
 *   can be restricted to this app in the Google Cloud console.
 * @param onRequest Called once per request actually sent, to keep the daily quota visible.
 */
class GoogleBooksProvider(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val androidPackage: String,
    private val androidCertSha1: String?,
    private val onRequest: suspend () -> Unit = {},
) : MetadataProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override val source: MetadataSource = MetadataSource.GOOGLE_BOOKS

    override suspend fun unavailableReason(): MetadataProvider.Unavailable? =
        if (apiKey.isBlank()) MetadataProvider.Unavailable.NOT_CONFIGURED else null

    override suspend fun search(query: String): List<BookMetadata> = volumes(query)

    override suspend fun findByIsbn(isbn13: String): List<BookMetadata> = volumes("isbn:$isbn13")

    override suspend fun searchByTitleAndAuthor(title: String, author: String?): List<BookMetadata> {
        val precise = buildString {
            append("intitle:").append(phrase(title))
            if (!author.isNullOrBlank()) append(" inauthor:").append(phrase(author))
        }
        // The field operators are strict about wording, so fall back to a plain search.
        return volumes(precise).ifEmpty { volumes(listOfNotNull(title, author).joinToString(" ")) }
    }

    private suspend fun volumes(query: String): List<BookMetadata> {
        if (apiKey.isBlank()) throw MetadataException.Unavailable(MetadataProvider.Unavailable.NOT_CONFIGURED)

        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("maxResults", "12")
            .addQueryParameter("printType", "books")
            // Partial response: only the fields that are mapped, which keeps payloads small.
            .addQueryParameter("fields", FIELDS)
            .apply {
                // Without it Google sometimes cannot place the caller and refuses the request.
                Locale.getDefault().country.takeIf { it.length == 2 }?.let { addQueryParameter("country", it) }
            }
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("X-Android-Package", androidPackage)
            .apply { androidCertSha1?.let { header("X-Android-Cert", it) } }
            .build()

        onRequest()
        client.newCall(request).await().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw failure(response.code, response.header("Retry-After"), body)
            val parsed = runCatching { json.decodeFromString<VolumesResponse>(body) }
                .getOrElse { throw MetadataException.Server(response.code, "Unreadable response") }
            return parsed.items.mapNotNull(::toMetadata)
        }
    }

    private fun failure(code: Int, retryAfter: String?, body: String): MetadataException {
        val error = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()
        val reasons = error?.errors.orEmpty().map { it.reason }
        val message = error?.message ?: "Request failed"
        return when {
            code == 429 || error?.status == "RESOURCE_EXHAUSTED" || reasons.any { it in QUOTA_REASONS } ->
                MetadataException.RateLimited(retryAfter?.toLongOrNull())
            code == 400 || code == 401 || code == 403 -> MetadataException.Unauthorized(message)
            else -> MetadataException.Server(code, message)
        }
    }

    private fun toMetadata(volume: Volume): BookMetadata? {
        val info = volume.volumeInfo
        val title = info.title?.singleLineOrNull() ?: return null
        val identifiers = info.industryIdentifiers.associate { it.type to it.identifier }
        val isbn13 = identifiers["ISBN_13"]?.let(Isbn::toIsbn13)
            ?: identifiers["ISBN_10"]?.let(Isbn::toIsbn13)
        return BookMetadata(
            source = MetadataSource.GOOGLE_BOOKS,
            sourceId = volume.id,
            title = title,
            subtitle = info.subtitle?.singleLineOrNull(),
            authors = info.authors.singleLines(),
            description = info.description,
            publisher = info.publisher?.singleLineOrNull(),
            publishedDate = info.publishedDate,
            language = info.language,
            pageCount = info.pageCount?.takeIf { it > 0 },
            isbn10 = identifiers["ISBN_10"] ?: isbn13?.let(Isbn::toIsbn10),
            isbn13 = isbn13,
            categories = info.categories.singleLines(),
            coverUrl = (info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail)?.let(::cleanCoverUrl),
            infoUrl = info.canonicalVolumeLink ?: info.infoLink,
        )
    }

    /** Thumbnails come as plain http with a page-curl overlay; ask for neither. */
    private fun cleanCoverUrl(url: String): String =
        url.replaceFirst("http://", "https://").replace("&edge=curl", "")

    private fun phrase(text: String): String = "\"" + text.replace("\"", " ").singleLine() + "\""

    @Serializable
    private data class VolumesResponse(val items: List<Volume> = emptyList())

    @Serializable
    private data class Volume(val id: String, val volumeInfo: VolumeInfo = VolumeInfo())

    @Serializable
    private data class VolumeInfo(
        val title: String? = null,
        val subtitle: String? = null,
        val authors: List<String> = emptyList(),
        val publisher: String? = null,
        val publishedDate: String? = null,
        val description: String? = null,
        val industryIdentifiers: List<IndustryIdentifier> = emptyList(),
        val pageCount: Int? = null,
        val categories: List<String> = emptyList(),
        val imageLinks: ImageLinks? = null,
        val language: String? = null,
        val infoLink: String? = null,
        val canonicalVolumeLink: String? = null,
    )

    @Serializable
    private data class IndustryIdentifier(val type: String = "", val identifier: String = "")

    @Serializable
    private data class ImageLinks(val smallThumbnail: String? = null, val thumbnail: String? = null)

    @Serializable
    private data class ErrorResponse(val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(
        val message: String? = null,
        val status: String? = null,
        val errors: List<ErrorDetail> = emptyList(),
    )

    @Serializable
    private data class ErrorDetail(val reason: String = "")

    private companion object {
        const val ENDPOINT = "https://www.googleapis.com/books/v1/volumes"
        const val FIELDS = "items(id,volumeInfo(title,subtitle,authors,publisher,publishedDate,description," +
            "industryIdentifiers,pageCount,categories,imageLinks,language,infoLink,canonicalVolumeLink))"
        val QUOTA_REASONS = setOf("rateLimitExceeded", "dailyLimitExceeded", "userRateLimitExceeded", "quotaExceeded")
    }
}
