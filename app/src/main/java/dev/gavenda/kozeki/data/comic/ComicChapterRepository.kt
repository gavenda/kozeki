package dev.gavenda.kozeki.data.comic

import androidx.room.withTransaction
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.db.ComicChapterEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.comic.ComicProvider.Companion.toMetadata
import dev.gavenda.kozeki.data.comic.ComicProvider.Companion.withUrl
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataProvider
import dev.gavenda.kozeki.data.model.ComicChapter
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.inReadingOrder
import dev.gavenda.kozeki.data.model.recognizeChapterNumber
import dev.gavenda.kozeki.data.model.resumeChapter
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The chapters of comics, as their sources list them and as the user has read them. */
class ComicChapterRepository(
    private val db: KozekiDatabase,
    private val clock: Clock,
    private val sources: ComicSources,
) {

    private val chapters = db.comicChapterDao()
    private val books = db.bookDao()

    private val _chapterReads = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** The ID of a book each time chapters of it were marked read or unread, or finished by reading. */
    val chapterReads: SharedFlow<String> = _chapterReads.asSharedFlow()

    /** The chapters of [bookId] in the order the source listed them. */
    fun observe(bookId: String): Flow<List<ComicChapter>> =
        chapters.observeForBook(bookId).map { rows -> rows.map { it.toChapter() } }

    /** A source may not be asked about one comic twice at once, and says so by throwing. */
    private val updateLocks = ConcurrentHashMap<String, Mutex>()

    /** What a source answers about a comic: what it says of it, when asked, and the chapters, when asked. */
    class Update(val details: BookMetadata?, val chapters: List<SChapter>?)

    /**
     * Asks the source of the comic with [sourceId] about it, once, for whatever of its details and
     * its chapters is wanted. Nothing is stored. Throws when the source is not installed or fails.
     */
    suspend fun fetch(
        sourceId: String,
        title: String,
        memo: String?,
        details: Boolean = false,
        chapters: Boolean = true,
    ): Update = guardSource(site = { ComicProvider.mihonSourceOf(sourceId)?.let { sources.siteOf(it) } }) {
        val (id, url) = ComicProvider.parseSourceId(sourceId)
            ?: throw MetadataException.Server(0, "Not a comic: $sourceId")
        // A comic whose extension was removed can be browsed but not asked for chapters.
        val source = sources.get(id)
            ?: throw MetadataException.Unavailable(MetadataProvider.Unavailable.NO_EXTENSIONS)
        val stub = SManga.create().apply {
            this.url = url
            this.title = title
            this.memo = ComicMemo.decode(memo)
        }
        updateLocks.getOrPut(sourceId) { Mutex() }.withLock {
            val update = source.getMangaUpdate(stub, emptyList(), fetchDetails = details, fetchChapters = chapters)
            Update(
                details = if (details) update.manga.withUrl(url).toMetadata(source) else null,
                chapters = if (chapters) update.chapters else null,
            )
        }
    }

    /** The chapters of [bookId] as the source listed them, in the order it did. */
    suspend fun get(bookId: String): List<ComicChapter> = chapters.getForBook(bookId).map { it.toChapter() }

    /** What a source lists, as chapters nobody has read. For showing a comic before it is the user's. */
    fun preview(remote: List<SChapter>): List<ComicChapter> =
        remote.distinctBy { it.url }.mapIndexed { index, chapter ->
            ComicChapter(
                url = chapter.url,
                name = chapter.name,
                number = chapter.chapter_number.takeIf { it >= 0f } ?: recognizeChapterNumber(chapter.name),
                scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
                uploadedAt = chapter.date_upload.takeIf { it > 0 },
                sourceOrder = index,
                read = false,
                lastPageRead = 0,
                pageCount = null,
                memo = ComicMemo.encode(chapter.memo),
            )
        }

    /**
     * Takes over the chapters [remote] the source lists now. What the user did in the ones it
     * already had is kept, and chapters the source no longer lists are put away rather than lost,
     * in case they come back.
     *
     * With [countNew] the chapters that were not there before are added to the book's count of new
     * ones, for a sync nobody is watching. One the user is looking at has nothing to announce.
     */
    suspend fun sync(bookId: String, remote: List<SChapter>, countNew: Boolean = false) {
        db.withTransaction {
            val existing = chapters.getAllForBook(bookId)
            val changed = mergeChapters(bookId, existing, remote, clock.millis())
            if (changed.isNotEmpty()) chapters.upsertAll(changed)
            val arrived = newChapterCount(existing, changed)
            if (countNew && arrived > 0) books.addNewChapters(bookId, arrived)
        }
        updateBookProgress(bookId)
    }

    /**
     * Asks the source of each comic being followed for its chapters, counting what is new. A source
     * that cannot be reached is left for the next run. [pauseMs] is the wait between comics.
     * [onProgress] hears of each comic before its source is asked, and once more when all are done.
     * Returns whether every comic was answered.
     */
    suspend fun checkForUpdates(
        pauseMs: Long = CHECK_PAUSE_MS,
        onProgress: (UpdateCheckProgress) -> Unit = {},
    ): Boolean {
        var complete = true
        val followed = books.getForUpdateCheck().filter { it.sourceId != null }
        for ((index, book) in followed.withIndex()) {
            val sourceId = book.sourceId ?: continue
            onProgress(UpdateCheckProgress(checked = index, total = followed.size, current = book.title))
            try {
                val update = fetch(sourceId, book.title, book.sourceMemo)
                sync(book.id, update.chapters.orEmpty(), countNew = true)
                books.markChaptersChecked(book.id, clock.millis())
            } catch (e: MetadataException) {
                complete = false
            }
            // A run over a long library is not a burst on any one source.
            delay(pauseMs)
        }
        onProgress(UpdateCheckProgress(checked = followed.size, total = followed.size, current = null))
        return complete
    }

    /** The user has seen what came out: the book's count of new chapters goes back to none. */
    suspend fun clearNew(bookId: String) = books.addNewChapters(bookId, 0)

    /** Records how far the user got in a chapter. [read] is that it was finished. */
    suspend fun saveProgress(bookId: String, url: String, read: Boolean, lastPageRead: Int, pageCount: Int?) {
        chapters.updateProgress(bookId, url, read, lastPageRead, pageCount, clock.millis())
        // The part of the chapter in hand that has been turned through counts towards the book too.
        val partial = if (!read && pageCount != null && pageCount > 0) (lastPageRead + 1.0) / pageCount else 0.0
        updateBookProgress(bookId, reading = true, currentUrl = url, partial = partial)
        if (read) _chapterReads.tryEmit(bookId)
    }

    suspend fun markRead(bookId: String, urls: List<String>, read: Boolean) {
        if (urls.isEmpty()) return
        chapters.markRead(bookId, urls, read, clock.millis())
        updateBookProgress(bookId)
        _chapterReads.tryEmit(bookId)
    }

    /**
     * Puts the book's own progress where its chapters say: the part read, and the chapter reading
     * goes on from. Left alone when nothing changed, so a screen opened does not make the book
     * look edited.
     */
    private suspend fun updateBookProgress(
        bookId: String,
        reading: Boolean = false,
        currentUrl: String? = null,
        partial: Double = 0.0,
    ) {
        val ordered = chapters.getForBook(bookId).map { it.toChapter() }.inReadingOrder()
        val book = books.get(bookId) ?: return
        if (ordered.isEmpty()) return
        val current = ordered.firstOrNull { it.url == currentUrl } ?: ordered.resumeChapter() ?: ordered.last()
        val progression = (ordered.count { it.read } + partial) / ordered.size
        val index = ordered.indexOf(current) + 1
        if (reading) {
            // Reading puts the book at the top of the library, unlike marking chapters read.
            books.updateProgress(
                id = bookId,
                locator = null,
                progression = progression,
                position = null,
                positionCount = null,
                chapterTitle = current.name,
                chapterIndex = index,
                chapterCount = ordered.size,
                now = clock.millis(),
            )
            return
        }
        val effective = if (book.state == ReadingState.COMPLETED) 1.0 else progression
        if (book.progression == effective && book.chapterTitle == current.name &&
            book.chapterIndex == index && book.chapterCount == ordered.size
        ) {
            return
        }
        books.updateChapterProgress(bookId, progression, current.name, index, ordered.size, clock.millis())
    }

    private fun ComicChapterEntity.toChapter() = ComicChapter(
        url = url,
        name = name,
        number = chapterNumber.takeIf { it >= 0f } ?: recognizeChapterNumber(name),
        scanlator = scanlator,
        uploadedAt = uploadedAt.takeIf { it > 0 },
        sourceOrder = sourceOrder,
        read = read,
        lastPageRead = lastPageRead,
        pageCount = pageCount,
        memo = memo,
    )
}

private const val CHECK_PAUSE_MS = 2_000L

/**
 * How many of [changed], the rows [mergeChapters] made of [existing], are chapters the book did not
 * have before. A book with no chapters yet is being added, and none of its chapters is news.
 */
internal fun newChapterCount(existing: List<ComicChapterEntity>, changed: List<ComicChapterEntity>): Int {
    if (existing.isEmpty()) return 0
    val known = existing.mapTo(HashSet()) { it.url }
    return changed.count { it.url !in known && it.sync.deletedAt == null && !it.read }
}

/**
 * The rows to write so that [existing] matches what the source lists in [remote], and nothing else:
 * a row that is already as it should be is not part of the result.
 */
internal fun mergeChapters(
    bookId: String,
    existing: List<ComicChapterEntity>,
    remote: List<SChapter>,
    now: Long,
): List<ComicChapterEntity> {
    val byUrl = existing.associateBy { it.url }
    val listed = HashSet<String>()
    val changed = ArrayList<ComicChapterEntity>()

    remote.forEachIndexed { index, chapter ->
        // A source that lists a chapter twice means one chapter.
        if (!listed.add(chapter.url)) return@forEachIndexed
        val old = byUrl[chapter.url]
        val row = ComicChapterEntity(
            bookId = bookId,
            url = chapter.url,
            name = chapter.name,
            chapterNumber = chapter.chapter_number,
            scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
            uploadedAt = chapter.date_upload,
            sourceOrder = index,
            // What the user did is not the source's to say.
            read = old?.read ?: false,
            lastPageRead = old?.lastPageRead ?: 0,
            pageCount = old?.pageCount,
            memo = ComicMemo.encode(chapter.memo),
            sync = old?.sync?.copy(deletedAt = null) ?: SyncStamp.created(now),
        )
        if (old == null || old != row) changed += row.copy(sync = row.sync.touched(now))
    }

    for (old in existing) {
        if (old.url !in listed && old.sync.deletedAt == null) changed += old.copy(sync = old.sync.deleted(now))
    }
    return changed
}

/** How far a check for new chapters has got: [checked] of [total] comics, with [current] being asked now. */
data class UpdateCheckProgress(val checked: Int, val total: Int, val current: String?)
