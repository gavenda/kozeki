package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.db.MetadataCacheDao
import dev.gavenda.kozeki.data.db.MetadataCacheEntity
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.singleLines
import dev.gavenda.kozeki.data.settings.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The only way the app talks to metadata sources. Every lookup goes through a persistent cache
 * first, because the sources are rate-limited (Google Books counts requests per day) and because
 * repeating a search while offline should still work.
 */
class MetadataRepository(
    private val providers: Map<MetadataSource, MetadataProvider>,
    private val cache: MetadataCacheDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(BookMetadata.serializer())

    // One request at a time per source, spaced out, so a burst of imports cannot burn the quota.
    private val gates = MetadataSource.entries.associateWith { Mutex() }
    private val lastRequestAt = ConcurrentHashMap<MetadataSource, Long>()

    suspend fun selectedSource(): MetadataSource = settings.metadataSource.first()

    suspend fun unavailableReason(source: MetadataSource): MetadataProvider.Unavailable? =
        (providers[source] ?: return MetadataProvider.Unavailable.NOT_CONFIGURED).unavailableReason()

    suspend fun search(query: String, source: MetadataSource): List<BookMetadata> {
        val normalized = BookMatcher.normalize(query)
        if (normalized.length < MIN_QUERY_LENGTH) return emptyList()
        return cached(source, "search|$normalized", SEARCH_TTL) { provider(source).search(query.trim()) }
    }

    suspend fun findByIsbn(isbn13: String, source: MetadataSource): List<BookMetadata> =
        cached(source, "isbn|$isbn13", LOOKUP_TTL) { provider(source).findByIsbn(isbn13) }

    suspend fun searchByTitleAndAuthor(
        title: String,
        author: String?,
        source: MetadataSource,
    ): List<BookMetadata> {
        val key = "title|${BookMatcher.normalize(title)}|${author?.let(BookMatcher::normalize).orEmpty()}"
        return cached(source, key, LOOKUP_TTL) { provider(source).searchByTitleAndAuthor(title, author) }
    }

    /** How many lookups are held in the cache. */
    fun observeCacheSize(): Flow<Int> = cache.observeCount()

    suspend fun clearCache() = cache.clear()

    private suspend fun cached(
        source: MetadataSource,
        key: String,
        ttl: Duration,
        fetch: suspend () -> List<BookMetadata>,
    ): List<BookMetadata> {
        val fullKey = "${source.name}|$key"
        read(fullKey)?.let { return it }

        return gates.getValue(source).withLock {
            // Another caller may have filled the entry while this one waited for the gate.
            read(fullKey)?.let { return@withLock it }

            val wait = (lastRequestAt[source] ?: 0L) + MIN_REQUEST_GAP_MS - clock.millis()
            if (wait > 0) delay(wait)

            val result = try {
                fetch()
            } catch (e: MetadataException) {
                // Offline or throttled: an expired answer is better than none.
                read(fullKey, allowExpired = true)?.let { return@withLock it }
                throw e
            } finally {
                lastRequestAt[source] = clock.millis()
            }

            val now = clock.millis()
            // An empty answer is cached too, but briefly: the book may be added to the source later.
            val lifetime = if (result.isEmpty()) EMPTY_TTL else ttl
            cache.upsert(
                MetadataCacheEntity(
                    key = fullKey,
                    source = source,
                    payload = json.encodeToString(serializer, result),
                    fetchedAt = now,
                    expiresAt = now + lifetime.toMillis(),
                ),
            )
            cache.deleteExpired(now - STALE_GRACE.toMillis())
            result
        }
    }

    private suspend fun read(fullKey: String, allowExpired: Boolean = false): List<BookMetadata>? {
        val entry = cache.get(fullKey) ?: return null
        if (!allowExpired && entry.expiresAt < clock.millis()) return null
        return runCatching { json.decodeFromString(serializer, entry.payload) }.getOrNull()
            // Entries cached before names were tidied on the way in.
            ?.map { it.copy(authors = it.authors.singleLines()) }
    }

    private fun provider(source: MetadataSource): MetadataProvider =
        providers[source] ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.NOT_CONFIGURED)

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val MIN_REQUEST_GAP_MS = 400L
        val SEARCH_TTL: Duration = Duration.ofDays(7)
        val LOOKUP_TTL: Duration = Duration.ofDays(30)
        val EMPTY_TTL: Duration = Duration.ofDays(1)

        /** How long expired entries are kept around as an offline fallback before being purged. */
        val STALE_GRACE: Duration = Duration.ofDays(90)
    }
}
