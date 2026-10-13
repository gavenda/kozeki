package dev.gavenda.kozeki.data.manga

import dev.gavenda.kozeki.data.metadata.AuthorPage
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.BookPage
import dev.gavenda.kozeki.data.metadata.BookReviews
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.settings.SettingsRepository
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * The manga sources of the installed Mihon extensions as a catalogue. A search goes to the one
 * source the user has picked; a result says which source it came from, so it can be opened and
 * read later whatever is picked then.
 */
class MangaProvider(
    private val sources: MangaSources,
    private val settings: SettingsRepository,
) : MetadataProvider {

    override val source: MetadataSource = MetadataSource.MANGA

    override suspend fun unavailableReason(): MetadataProvider.Unavailable? {
        sources.ensureLoaded()
        return if (searchable().isEmpty()) MetadataProvider.Unavailable.NO_EXTENSIONS else null
    }

    /** A blank [query] lists what the source finds most popular. */
    override suspend fun search(query: String, page: Int): BookPage = guardSource {
        val source = pickedSource()
        val result = if (query.isBlank()) {
            source.getPopularManga(page)
        } else {
            source.getSearchManga(page, query.trim(), source.getFilterList())
        }
        BookPage(books = result.mangas.map { it.toMetadata(source) }, hasMore = result.hasNextPage)
    }

    override suspend fun findByIsbn(isbn13: String): List<BookMetadata> = emptyList()

    override suspend fun book(sourceId: String): BookMetadata? = guardSource {
        val (id, url) = parseSourceId(sourceId) ?: return@guardSource null
        val source = sources.get(id) ?: return@guardSource null
        val stub = SManga.create().apply { this.url = url }
        val update = source.getMangaUpdate(stub, emptyList(), fetchDetails = true, fetchChapters = false)
        // Some sources hand back details without the fields they were asked about.
        update.manga.also { if (it.url.isEmpty()) it.url = url }.toMetadata(source)
    }

    override suspend fun author(authorId: String, page: Int): AuthorPage = AuthorPage()

    override suspend fun reviews(sourceId: String): BookReviews = BookReviews()

    private suspend fun searchable(): List<CatalogueSource> =
        sources.extensions.value.orEmpty().filterIsInstance<LoadedExtension>().flatMap { it.sources }

    /** The source the user picked, or the first one while that is not installed or nothing was picked. */
    private suspend fun pickedSource(): CatalogueSource {
        sources.ensureLoaded()
        val all = searchable()
        val wanted = settings.mangaSourceId
        return all.firstOrNull { it.id == wanted }
            ?: all.firstOrNull()
            ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.NO_EXTENSIONS)
    }

    companion object {
        /** A result's [BookMetadata.sourceId]: the Mihon source and the manga's URL on it. */
        fun sourceId(mihonSourceId: Long, mangaUrl: String): String = "$mihonSourceId:$mangaUrl"

        /** The Mihon source a [BookMetadata.sourceId] names, or null when it is not a manga's. */
        fun mihonSourceOf(sourceId: String): Long? = parseSourceId(sourceId)?.first

        fun parseSourceId(sourceId: String): Pair<Long, String>? {
            val id = sourceId.substringBefore(':').toLongOrNull() ?: return null
            val url = sourceId.substringAfter(':', "").takeIf { it.isNotEmpty() } ?: return null
            return id to url
        }

        /** The authors a manga lists: its author and artist, which often are the same people. */
        fun creators(manga: SManga): List<String> =
            listOfNotNull(manga.author, manga.artist)
                .flatMap { it.split(',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        fun SManga.toMetadata(source: CatalogueSource): BookMetadata = BookMetadata(
            source = MetadataSource.MANGA,
            sourceId = sourceId(source.id, url),
            title = title,
            authors = creators(this),
            description = description?.takeIf { it.isNotBlank() },
            language = source.lang.takeUnless { it == "all" || it == "other" },
            categories = genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            coverUrl = thumbnail_url?.takeIf { it.isNotBlank() }?.let { MangaCover.url(source.id, it) },
            infoUrl = runCatching { (source as? HttpSource)?.getMangaUrl(this) }.getOrNull(),
            sourceMemo = MangaMemo.encode(memo),
        )
    }
}

private const val TAG = "MangaSource"

/** Extensions fail in their own ways. Whatever one throws is put in terms the lookup screens know. */
internal suspend fun <T> guardSource(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: MetadataException) {
    throw e
} catch (e: HttpException) {
    Log.w(TAG, "A manga source answered HTTP ${e.code}", e)
    throw if (e.code == 429) MetadataException.RateLimited() else MetadataException.Server(e.code, e.message.orEmpty())
} catch (e: IOException) {
    Log.w(TAG, "A manga source could not be reached", e)
    throw MetadataException.Network(e)
} catch (e: Exception) {
    Log.w(TAG, "A manga source failed", e)
    throw MetadataException.Server(0, "${e::class.simpleName}: ${e.message}")
} catch (e: LinkageError) {
    // An extension built against an API this app does not have.
    Log.w(TAG, "A manga source does not fit this app", e)
    throw MetadataException.Server(0, "${e::class.simpleName}: ${e.message}")
}
