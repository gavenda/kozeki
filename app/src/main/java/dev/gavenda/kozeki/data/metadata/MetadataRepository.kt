package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.db.MetadataCacheDao
import dev.gavenda.kozeki.data.db.MetadataCacheEntity
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.singleLines
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The only way the app talks to the metadata source. Every lookup goes through a persistent cache
 * first, because the source is rate-limited and because repeating a lookup while offline should
 * still work.
 */
class MetadataRepository(
    private val provider: MetadataProvider,
    private val cache: MetadataCacheDao,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val bookList = ListSerializer(BookMetadata.serializer())

    // One request at a time, spaced out, so a burst of imports cannot burn the quota.
    private val gate = Mutex()
    private var lastRequestAt = 0L

    val source: MetadataSource get() = provider.source

    suspend fun unavailableReason(): MetadataProvider.Unavailable? = provider.unavailableReason()

    /** One page of a search, the first being 1. [BookSearch] reads them in turn. */
    suspend fun search(query: String, page: Int = 1): BookPage {
        val normalized = BookMatcher.normalize(query)
        if (normalized.length < MIN_QUERY_LENGTH) return BookPage()
        return cached(
            key = "search|$SEARCH_VERSION|$normalized|$page",
            serializer = BookPage.serializer(),
            lifetime = { if (it.books.isEmpty() && !it.hasMore) EMPTY_TTL else SEARCH_TTL },
        ) { provider.search(query.trim(), page) }
    }

    suspend fun findByIsbn(isbn13: String): List<BookMetadata> =
        books("isbn|$isbn13", LOOKUP_TTL) { provider.findByIsbn(isbn13) }

    suspend fun searchByTitleAndAuthor(title: String, author: String?): List<BookMetadata> {
        val key = "title|$SEARCH_VERSION|${BookMatcher.normalize(title)}|${author?.let(BookMatcher::normalize).orEmpty()}"
        return books(key, LOOKUP_TTL) { provider.searchByTitleAndAuthor(title, author) }
    }

    /** The author with [authorId] and one page of their books, the first being 1. */
    suspend fun author(authorId: String, page: Int = 1): AuthorPage =
        cached(
            key = "author|$authorId|$page",
            serializer = AuthorPage.serializer(),
            lifetime = { if (it.author == null) EMPTY_TTL else AUTHOR_TTL },
        ) { provider.author(authorId, page) }

    /** What the source's readers think of the book with [sourceId]. */
    suspend fun reviews(sourceId: String): BookReviews =
        cached("reviews|$sourceId", BookReviews.serializer(), { REVIEWS_TTL }) { provider.reviews(sourceId) }

    /** How many lookups are held in the cache. */
    fun observeCacheSize(): Flow<Int> = cache.observeCount()

    suspend fun clearCache() = cache.clear()

    private suspend fun books(
        key: String,
        ttl: Duration,
        fetch: suspend () -> List<BookMetadata>,
    ): List<BookMetadata> =
        // An empty answer is cached too, but briefly: the book may be added to the source later.
        cached(key, bookList, { if (it.isEmpty()) EMPTY_TTL else ttl }, fetch)
            // Entries cached before names were tidied on the way in.
            .map { it.copy(authors = it.authors.singleLines()) }

    private suspend fun <T> cached(
        key: String,
        serializer: KSerializer<T>,
        lifetime: (T) -> Duration,
        fetch: suspend () -> T,
    ): T {
        val fullKey = "${source.name}|$key"
        read(fullKey, serializer)?.let { return it }

        return gate.withLock {
            // Another caller may have filled the entry while this one waited for the gate.
            read(fullKey, serializer)?.let { return@withLock it }

            val wait = lastRequestAt + MIN_REQUEST_GAP_MS - clock.millis()
            if (wait > 0) delay(wait)

            val result = try {
                fetch()
            } catch (e: MetadataException) {
                // Offline or throttled: an expired answer is better than none.
                read(fullKey, serializer, allowExpired = true)?.let { return@withLock it }
                throw e
            } finally {
                lastRequestAt = clock.millis()
            }

            val now = clock.millis()
            cache.upsert(
                MetadataCacheEntity(
                    key = fullKey,
                    source = source,
                    payload = json.encodeToString(serializer, result),
                    fetchedAt = now,
                    expiresAt = now + lifetime(result).toMillis(),
                ),
            )
            cache.deleteExpired(now - STALE_GRACE.toMillis())
            result
        }
    }

    private suspend fun <T> read(fullKey: String, serializer: KSerializer<T>, allowExpired: Boolean = false): T? {
        val entry = cache.get(fullKey) ?: return null
        if (!allowExpired && entry.expiresAt < clock.millis()) return null
        return runCatching { json.decodeFromString(serializer, entry.payload) }.getOrNull()
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val MIN_REQUEST_GAP_MS = 400L

        /** Bumped when what a search returns changes, so answers cached under the old rules are not served. */
        const val SEARCH_VERSION = 3
        val SEARCH_TTL: Duration = Duration.ofDays(7)
        val LOOKUP_TTL: Duration = Duration.ofDays(30)
        val EMPTY_TTL: Duration = Duration.ofDays(1)
        val AUTHOR_TTL: Duration = Duration.ofDays(7)

        /** Reviews keep arriving, so they are refreshed far sooner than a book's own details. */
        val REVIEWS_TTL: Duration = Duration.ofDays(1)

        /** How long expired entries are kept around as an offline fallback before being purged. */
        val STALE_GRACE: Duration = Duration.ofDays(90)
    }
}
