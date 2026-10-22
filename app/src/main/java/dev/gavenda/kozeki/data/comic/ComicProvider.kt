package dev.gavenda.kozeki.data.comic

import dev.gavenda.kozeki.data.metadata.AuthorPage
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.BookPage
import dev.gavenda.kozeki.data.metadata.BookReviews
import dev.gavenda.kozeki.data.metadata.BrowseOrder
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
 * The comic sources of the installed Mihon extensions as a catalogue. A search goes to the one
 * source the user has picked; a result says which source it came from, so it can be opened and
 * read later whatever is picked then.
 */
class ComicProvider(
    private val sources: ComicSources,
    private val settings: SettingsRepository,
) : MetadataProvider {

    override val source: MetadataSource = MetadataSource.COMICS

    override suspend fun unavailableReason(): MetadataProvider.Unavailable? {
        sources.ensureLoaded()
        return if (searchable().isEmpty()) MetadataProvider.Unavailable.NO_EXTENSIONS else null
    }

    /** A blank [query] lists what the source finds most popular. */
    override suspend fun search(query: String, page: Int): BookPage = guardSource(site = { pickedSource().id.let { sources.siteOf(it) } }) {
        val source = pickedSource()
        val result = if (query.isBlank()) {
            source.getPopularManga(page)
        } else {
            source.getSearchManga(page, query.trim(), source.getFilterList())
        }
        BookPage(books = result.mangas.filter { it.hasUrl() }.map { it.toMetadata(source) }, hasMore = result.hasNextPage)
    }

    /** Every source has a popular list; only some keep one of their latest updates. */
    override suspend fun browseOrders(): List<BrowseOrder> {
        val source = runCatching { pickedSource() }.getOrNull() ?: return listOf(BrowseOrder.POPULAR)
        val latest = runCatching { source.supportsLatest }.getOrDefault(false)
        return listOfNotNull(BrowseOrder.POPULAR, BrowseOrder.LATEST_UPDATES.takeIf { latest })
    }

    override suspend fun browse(order: BrowseOrder, page: Int): BookPage = guardSource(site = { pickedSource().id.let { sources.siteOf(it) } }) {
        val source = pickedSource()
        val result = if (order == BrowseOrder.LATEST_UPDATES && source.supportsLatest) {
            source.getLatestUpdates(page)
        } else {
            source.getPopularManga(page)
        }
        BookPage(books = result.mangas.filter { it.hasUrl() }.map { it.toMetadata(source) }, hasMore = result.hasNextPage)
    }

    override suspend fun findByIsbn(isbn13: String): List<BookMetadata> = emptyList()

    override suspend fun book(sourceId: String): BookMetadata? = guardSource(site = { parseSourceId(sourceId)?.let { sources.siteOf(it.first) } }) {
        val (id, url) = parseSourceId(sourceId) ?: return@guardSource null
        val source = sources.get(id) ?: return@guardSource null
        val stub = SManga.create().apply { this.url = url }
        val update = source.getMangaUpdate(stub, emptyList(), fetchDetails = true, fetchChapters = false)
        // Some sources hand back details without the fields they were asked about.
        update.manga.withUrl(url).toMetadata(source)
    }

    override suspend fun author(authorId: String, page: Int): AuthorPage = AuthorPage()

    override suspend fun reviews(sourceId: String): BookReviews = BookReviews()

    private suspend fun searchable(): List<CatalogueSource> = sources.searchable()

    /**
     * The source the user picked, or the first one while that is not installed, is in a language
     * switched off, or nothing was picked.
     */
    private suspend fun pickedSource(): CatalogueSource {
        sources.ensureLoaded()
        val all = searchable()
        val wanted = settings.comicSourceId
        return all.firstOrNull { it.id == wanted }
            ?: all.firstOrNull()
            ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.NO_EXTENSIONS)
    }

    companion object {
        /** A result's [BookMetadata.sourceId]: the Mihon source and the comic's URL on it. */
        fun sourceId(mihonSourceId: Long, comicUrl: String): String = "$mihonSourceId:$comicUrl"

        /** The Mihon source a [BookMetadata.sourceId] names, or null when it is not a comic's. */
        fun mihonSourceOf(sourceId: String): Long? = parseSourceId(sourceId)?.first

        fun parseSourceId(sourceId: String): Pair<Long, String>? {
            val id = sourceId.substringBefore(':').toLongOrNull() ?: return null
            val url = sourceId.substringAfter(':', "").takeIf { it.isNotEmpty() } ?: return null
            return id to url
        }

        /** The authors a comic lists: its author and artist, which often are the same people. */
        fun creators(comic: SManga): List<String> =
            listOfNotNull(comic.author, comic.artist)
                .flatMap { it.split(',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        /** Extensions that build a comic without a URL leave it unset, and reading it then throws. */
        fun SManga.hasUrl(): Boolean = runCatching { url.isNotEmpty() }.getOrDefault(false)

        /** This comic with [fallback] as its URL when the source left it unset or empty. */
        fun SManga.withUrl(fallback: String): SManga = also { if (!it.hasUrl()) it.url = fallback }

        fun SManga.toMetadata(source: CatalogueSource): BookMetadata = BookMetadata(
            source = MetadataSource.COMICS,
            sourceId = sourceId(source.id, url),
            title = title,
            authors = creators(this),
            description = description?.takeIf { it.isNotBlank() },
            language = source.lang.takeUnless { it == "all" || it == "other" },
            categories = genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            coverUrl = thumbnail_url?.takeIf { it.isNotBlank() }?.let { ComicCover.url(source.id, it) },
            infoUrl = runCatching { (source as? HttpSource)?.getMangaUrl(this) }.getOrNull(),
            sourceMemo = ComicMemo.encode(memo),
        )
    }
}

private const val TAG = "ComicSource"

/** The address a captcha of [sourceId] is solved at: its site, when it is a web one. */
internal suspend fun ComicSources.siteOf(sourceId: Long): String? {
    val source = get(sourceId)
    val url = (source as? HttpSource)?.let { runCatching { it.baseUrl }.getOrNull() }?.takeIf { it.isNotBlank() }
    if (url == null) Log.w(TAG, "No site address for source $sourceId (${source?.let { it::class.java.name }})")
    return url
}

/** Whether [e], or what caused it, says that the site wants a captcha solved. */
private fun isCaptcha(e: Throwable): Boolean = generateSequence(e) { it.cause }.take(6).any {
    (it is HttpException && (it.code == 403 || it.code == 503)) ||
        it.message.orEmpty().let { m ->
            m.contains("captcha", ignoreCase = true) ||
                m.contains("cloudflare", ignoreCase = true) ||
                m.contains("webview", ignoreCase = true)
        }
}

/**
 * Extensions fail in their own ways. Whatever one throws is put in terms the lookup screens know.
 * [site] says where a captcha is solved, and is only asked when one is wanted.
 */
internal suspend fun <T> guardSource(site: suspend () -> String? = { null }, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: MetadataException) {
    throw e
} catch (e: Exception) {
    if (isCaptcha(e)) {
        Log.w(TAG, "A comic source wants a captcha solved", e)
        val url = try {
            site()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "No site address for the captcha", t)
            null
        }
        throw MetadataException.Captcha(url)
    }
    when (e) {
        is HttpException -> {
            Log.w(TAG, "A comic source answered HTTP ${e.code}", e)
            throw if (e.code == 429) MetadataException.RateLimited() else MetadataException.Server(e.code, e.message.orEmpty())
        }
        is IOException -> {
            Log.w(TAG, "A comic source could not be reached", e)
            throw MetadataException.Network(e)
        }
        else -> {
            Log.w(TAG, "A comic source failed", e)
            throw MetadataException.Server(0, "${e::class.simpleName}: ${e.message}")
        }
    }
} catch (e: LinkageError) {
    // An extension built against an API this app does not have.
    Log.w(TAG, "A comic source does not fit this app", e)
    throw MetadataException.Server(0, "${e::class.simpleName}: ${e.message}")
}
