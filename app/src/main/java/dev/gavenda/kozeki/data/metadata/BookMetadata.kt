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
    /** Who among [authors] the source keeps a page for. */
    val authorRefs: List<AuthorRef> = emptyList(),
    val description: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val categories: List<String> = emptyList(),
    val coverUrl: String? = null,
    /** The book's page on the source. */
    val infoUrl: String? = null,
) {
    /** The author called [name], when the source can show more of them. */
    fun authorRef(name: String): AuthorRef? = authorRefs.firstOrNull { it.name == name }
}

/** An author by the ID the source knows them under. */
@Serializable
data class AuthorRef(val id: String, val name: String)

/** An author as described by a metadata source. */
@Serializable
data class Author(
    val id: String,
    val name: String,
    val bio: String? = null,
    val bornYear: Int? = null,
    val deathYear: Int? = null,
    val location: String? = null,
    /** How many books the source credits them with, which can be more than it lists. */
    val booksCount: Int = 0,
    val imageUrl: String? = null,
    /** The author's page on the source. */
    val infoUrl: String? = null,
)

/** An author and one page of their books. */
@Serializable
data class AuthorPage(
    /** Null when the source has no such author. */
    val author: Author? = null,
    val books: BookPage = BookPage(),
)

/** One page of a search, and whether the source has more to give after it. */
@Serializable
data class BookPage(
    val books: List<BookMetadata> = emptyList(),
    val hasMore: Boolean = false,
)

/** What a source's readers make of a book: the overall figures and some of the written reviews. */
@Serializable
data class BookReviews(
    /** Mean rating out of 5, or null when nobody has rated the book. */
    val averageRating: Float? = null,
    val ratingsCount: Int = 0,
    /** How many written reviews the source holds, which can be more than [reviews] carries. */
    val reviewsCount: Int = 0,
    val reviews: List<BookReview> = emptyList(),
)

/** One reader's written review. */
@Serializable
data class BookReview(
    val id: String,
    /** Null when the source would not say who wrote it. */
    val reviewer: String? = null,
    /** 0.5 to 5.0, or null when the review came without a rating. */
    val rating: Float? = null,
    val text: String,
    val hasSpoilers: Boolean = false,
    /** Epoch day the review was written on. */
    val reviewedOn: Long? = null,
    val likes: Int = 0,
)

/** A catalogue that can be searched for books. Implementations do no caching of their own. */
interface MetadataProvider {

    val source: MetadataSource

    /** Why the provider cannot be used right now, or null when it is ready. */
    suspend fun unavailableReason(): Unavailable?

    /** The results on [page] of a search for [query], the first page being 1. */
    suspend fun search(query: String, page: Int = 1): BookPage

    suspend fun findByIsbn(isbn13: String): List<BookMetadata>

    suspend fun searchByTitleAndAuthor(title: String, author: String?): List<BookMetadata> =
        search(listOfNotNull(title, author).joinToString(" ")).books

    /** The author with [authorId] on this source and [page] of their books, the first page being 1. */
    suspend fun author(authorId: String, page: Int = 1): AuthorPage

    /** Ratings and written reviews for the book with [sourceId] on this source. */
    suspend fun reviews(sourceId: String): BookReviews

    enum class Unavailable {
        /** The source needs an account and nobody is signed in. */
        SIGNED_OUT,
    }
}

sealed class MetadataException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Unavailable(val reason: MetadataProvider.Unavailable) :
        MetadataException("Metadata source unavailable: $reason")

    /** The token was rejected. */
    class Unauthorized(message: String) : MetadataException(message)

    /**
     * The token is fine but was not granted what the request needs, typically because the user
     * signed in before the app started asking for it.
     *
     * @param scope The scopes the source says are missing, as it names them.
     */
    class MissingScope(val scope: String?) : MetadataException("Missing scope: ${scope ?: "unknown"}")

    /** The quota is used up; worth retrying later, not now. */
    class RateLimited(val retryAfterSeconds: Long? = null) : MetadataException("Rate limit reached")

    class Network(cause: Throwable) : MetadataException("Network error: ${cause.message}", cause)

    class Server(val code: Int, message: String) : MetadataException("HTTP $code: $message")
}
