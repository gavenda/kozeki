package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.model.MetadataSource
import kotlinx.serialization.Serializable

/** A book as described by a metadata source, normalised so the rest of the app is source-agnostic. */
@Serializable
data class BookMetadata(
    val source: MetadataSource,
    val sourceId: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val categories: List<String> = emptyList(),
    val coverUrl: String? = null,
    /** The book's page on the source. Google's terms require linking each result back to it. */
    val infoUrl: String? = null,
)

/** A catalogue that can be searched for books. Implementations do no caching of their own. */
interface MetadataProvider {

    val source: MetadataSource

    /** Why the provider cannot be used right now, or null when it is ready. */
    suspend fun unavailableReason(): Unavailable?

    suspend fun search(query: String): List<BookMetadata>

    suspend fun findByIsbn(isbn13: String): List<BookMetadata>

    suspend fun searchByTitleAndAuthor(title: String, author: String?): List<BookMetadata> =
        search(listOfNotNull(title, author).joinToString(" "))

    enum class Unavailable {
        /** No API key was built into the app. */
        NOT_CONFIGURED,

        /** The source needs an account and nobody is signed in. */
        SIGNED_OUT,
    }
}

sealed class MetadataException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Unavailable(val reason: MetadataProvider.Unavailable) :
        MetadataException("Metadata source unavailable: $reason")

    /** The key or token was rejected. */
    class Unauthorized(message: String) : MetadataException(message)

    /** The quota is used up; worth retrying later, not now. */
    class RateLimited(val retryAfterSeconds: Long? = null) : MetadataException("Rate limit reached")

    class Network(cause: Throwable) : MetadataException("Network error: ${cause.message}", cause)

    class Server(val code: Int, message: String) : MetadataException("HTTP $code: $message")
}
