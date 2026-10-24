package dev.gavenda.kozeki.ui.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.comic.ComicChapterRepository
import dev.gavenda.kozeki.data.comic.ComicChapterPages
import dev.gavenda.kozeki.data.comic.ComicDownloads
import dev.gavenda.kozeki.data.comic.ComicPages
import dev.gavenda.kozeki.data.comic.ComicProvider
import dev.gavenda.kozeki.data.comic.ComicReaderPreferences
import dev.gavenda.kozeki.data.comic.ComicReadingMode
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ComicChapter
import dev.gavenda.kozeki.data.model.SessionDraft
import dev.gavenda.kozeki.data.model.inReadingOrder
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.reader.SessionTracker
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A chapter laid out in the reader, with its pages. */
class ChapterSegment(val chapter: ComicChapter, val pages: ComicChapterPages)

sealed interface ComicReaderUiState {
    data object Loading : ComicReaderUiState

    /** [error] is null when the book or the chapter is no longer there. */
    data class Failed(val error: LookupError?, val captchaUrl: String? = null) : ComicReaderUiState

    data class Ready(
        val bookTitle: String,
        val chapter: ComicChapter,
        /** Loads as the reader says which page is in view. */
        val pages: ComicChapterPages,
        /** The page the chapter opens on: where it was left, or the last one when coming back from the next. */
        val startPage: Int,
        val previous: ComicChapter?,
        val next: ComicChapter?,
        /** 1-based place of the chapter in the order of reading, out of [chapterCount]. */
        val chapterNumber: Int,
        val chapterCount: Int,
        /**
         * The chapters laid out one after another, [chapter] among them. Only the strip shows more
         * than one: it scrolls on into the next chapter, and back into the one before.
         */
        val segments: List<ChapterSegment>,
        /** The chapter before the first of [segments], and the one after the last. */
        val before: ComicChapter?,
        val after: ComicChapter?,
        /** Changes when the chapters on screen are replaced, which starts the reading over. */
        val generation: Int,
    ) : ComicReaderUiState
}

/** The page reached in a chapter, not yet written down. */
private data class Unsaved(val chapterUrl: String, val page: Int, val pageCount: Int, val read: Boolean)

class ComicReaderViewModel(
    private val bookId: String,
    initialChapterUrl: String,
    private val library: LibraryRepository,
    private val chapters: ComicChapterRepository,
    private val pages: ComicPages,
    private val downloads: ComicDownloads,
    private val preferences: ComicReaderPreferences,
    private val appScope: CoroutineScope,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComicReaderUiState>(ComicReaderUiState.Loading)
    val uiState: StateFlow<ComicReaderUiState> = _uiState.asStateFlow()

    // Until the book is known, which says whether it is a webtoon, what the user last picked.
    private val _mode = MutableStateFlow(preferences.mode(bookId, categories = emptyList()))
    val mode: StateFlow<ComicReadingMode> = _mode.asStateFlow()

    /** The page on screen, which a change of reading mode keeps hold of. */
    var currentPage: Int = 0
        private set

    private var book: Book? = null
    private var ordered: List<ComicChapter> = emptyList()
    private var current: ComicChapter? = null
    private var segments: List<ChapterSegment> = emptyList()
    private var sourceId: Long = 0L
    private var generation = 0

    /** The chapters being opened to be laid out next to the one on screen. */
    private val extending = HashSet<String>()
    private var readThroughId: String? = null
    private var chapterUrl = initialChapterUrl
    private var loadJob: Job? = null

    private val tracker = SessionTracker(System::currentTimeMillis)
    private var resumed = false
    private var lastSnapshot: SessionTracker.Snapshot? = null

    /** Chapters read to their last page since the session began, which is what the statistics count for a comic. */
    private val finishedInSession = mutableSetOf<String>()

    /** The pages of each chapter on screen this session, so that a chapter skipped through is not counted as read. */
    private val seenInSession = mutableMapOf<String, MutableSet<Int>>()
    private val unsaved = MutableStateFlow<Unsaved?>(null)

    init {
        // Page turns come in bursts; writing once they settle is enough, and pausing flushes the rest.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            unsaved.filterNotNull().debounce(SAVE_DEBOUNCE_MS.milliseconds).collect { save(it) }
        }
        load(initialChapterUrl, fromStart = false)
    }

    /** Opens the chapter that follows the one on screen, at its first page. */
    fun openNext() {
        val next = (uiState.value as? ComicReaderUiState.Ready)?.next ?: return
        load(next.url, fromStart = true)
    }

    /** Opens the chapter before the one on screen, at its first page. */
    fun openPrevious() {
        val previous = (uiState.value as? ComicReaderUiState.Ready)?.previous ?: return
        load(previous.url, fromStart = true)
    }

    fun retry() = load(chapterUrl, fromStart = false)

    fun setMode(mode: ComicReadingMode) {
        _mode.value = mode
        preferences.setMode(bookId, mode, categories = book?.categories.orEmpty())
    }

    /**
     * Called with the page the reader has come to, which is also what a tap or a swipe is. In the
     * strip it may be a page of a chapter other than the one on screen: the one scrolled into.
     */
    fun onPageChanged(chapterUrl: String, index: Int) {
        var ready = uiState.value as? ComicReaderUiState.Ready ?: return
        if (chapterUrl != ready.chapter.url) {
            ready = switchTo(ready, chapterUrl) ?: return
        }
        val page = index.coerceIn(0, ready.pages.size - 1)
        currentPage = page
        val read = page >= ready.pages.size - 1
        val record = Unsaved(ready.chapter.url, page, ready.pages.size, read)
        // What was reached in the chapter left behind is written down now, not when this one settles.
        unsaved.value?.takeIf { it.chapterUrl != record.chapterUrl }?.let { viewModelScope.launch { save(it) } }
        unsaved.value = record
        if (read) markReadHere(ready.chapter.url)
        val snapshot = snapshot(ready, page)
        val seen = seenInSession.getOrPut(ready.chapter.url) { mutableSetOf() }.apply { add(page) }
        if (read && turnedThrough(seen, page)) finishedInSession += ready.chapter.url
        lastSnapshot = snapshot
        if (resumed && !tracker.isRunning) tracker.begin(snapshot) else tracker.activity(snapshot)
        ready.pages.request(page)
        // Finishing a chapter is worth knowing at once, not after the next page that never comes.
        if (read) viewModelScope.launch { save(record) }
        if (_mode.value == ComicReadingMode.VERTICAL) extendAround(ready, page)
    }

    /** The chapter scrolled into becomes the one on screen, without anything being loaded again. */
    private fun switchTo(ready: ComicReaderUiState.Ready, url: String): ComicReaderUiState.Ready? {
        val segment = segments.firstOrNull { it.chapter.url == url } ?: return null
        // What was being fetched for the chapter left is no longer wanted until it is scrolled back to.
        ready.pages.idle()
        current = segment.chapter
        chapterUrl = url
        currentPage = 0
        viewModelScope.launch { downloadIfWanted(segment.chapter) }
        return emit(ready.bookTitle, segment, startPage = 0)
    }

    /**
     * Lays the chapter after the one on screen below it, and the one before above it, once the
     * reader gets close to either end, so that scrolling never waits at a chapter's edge.
     */
    private fun extendAround(ready: ComicReaderUiState.Ready, page: Int) {
        val at = ordered.indexOfFirst { it.url == ready.chapter.url }
        if (at < 0) return
        if (page >= ready.pages.size - 1 - EXTEND_MARGIN && segments.last().chapter.url == ready.chapter.url) {
            ordered.getOrNull(at + 1)?.let { extend(it, after = true) }
        }
        if (page <= EXTEND_MARGIN && segments.first().chapter.url == ready.chapter.url) {
            ordered.getOrNull(at - 1)?.let { extend(it, after = false) }
        }
    }

    private fun extend(target: ComicChapter, after: Boolean) {
        if (!extending.add(target.url)) return
        val generationAtStart = generation
        viewModelScope.launch {
            try {
                val opened = open(target)
                // The reader moved on to other chapters meanwhile.
                if (generationAtStart != generation) {
                    opened.close()
                    return@launch
                }
                val segment = ChapterSegment(target, opened)
                segments = if (after) segments + segment else listOf(segment) + segments
                val ready = uiState.value as? ComicReaderUiState.Ready ?: return@launch
                val currentSegment = segments.firstOrNull { it.chapter.url == ready.chapter.url } ?: return@launch
                _uiState.value = emit(ready.bookTitle, currentSegment, startPage = ready.startPage)
            } catch (e: CancellationException) {
                throw e
            } catch (_: MetadataException) {
                // The end of the strip says what comes next, with a button to go there, which asks again.
            } finally {
                extending.remove(target.url)
            }
        }
    }

    /** Keeps what the user did in a chapter in the list the snapshots and the neighbours go by. */
    private fun markReadHere(url: String) {
        ordered = ordered.map { if (it.url == url && !it.read) it.copy(read = true) else it }
    }

    /**
     * Builds the state for [segment] being the chapter on screen with the segments there are,
     * keeping only the ones next to it, since a chapter further away is no use in the strip.
     */
    private fun emit(title: String, segment: ChapterSegment, startPage: Int): ComicReaderUiState.Ready {
        val at = segments.indexOfFirst { it.chapter.url == segment.chapter.url }
        val keep = segments.filterIndexed { i, _ -> i in (at - 1)..(at + 1) }
        segments.filter { it !in keep }.forEach { it.pages.close() }
        segments = keep
        val index = ordered.indexOfFirst { it.url == segment.chapter.url }
        val first = ordered.indexOfFirst { it.url == keep.first().chapter.url }
        val last = ordered.indexOfFirst { it.url == keep.last().chapter.url }
        return ComicReaderUiState.Ready(
            bookTitle = title,
            chapter = segment.chapter,
            pages = segment.pages,
            startPage = startPage,
            previous = ordered.getOrNull(index - 1),
            next = ordered.getOrNull(index + 1),
            chapterNumber = index + 1,
            chapterCount = ordered.size,
            segments = keep,
            before = ordered.getOrNull(first - 1),
            after = ordered.getOrNull(last + 1),
            generation = generation,
        ).also { _uiState.value = it }
    }

    /**
     * The first page in view of each chapter that has any in view, in the strip, where the end of
     * one chapter and the start of the next can be on screen together. Each is loaded from there: the
     * chapter on screen is not the only one whose pages are being looked at.
     */
    fun onPagesInView(firstByChapter: Map<String, Int>) {
        for ((url, page) in firstByChapter) {
            segments.firstOrNull { it.chapter.url == url }?.pages?.request(page)
        }
    }

    /** A tap that did not turn the page still means somebody is reading. */
    fun onInteraction() = tracker.activity()

    fun onResumed() {
        resumed = true
        lastSnapshot?.let { if (!tracker.isRunning) tracker.begin(it) }
    }

    fun onPaused() {
        resumed = false
        flush()
    }

    /**
     * Opens [url] and lays it out alone. At its first page when [fromStart], which is what going to
     * a chapter on purpose means; else where it was left, or the first page of one that was finished.
     */
    private fun load(url: String, fromStart: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // What was reached in the chapter being left has to be down before the list is read again.
            unsaved.value?.let {
                unsaved.value = null
                save(it)
            }
            _uiState.value = ComicReaderUiState.Loading
            chapterUrl = url
            val book = book ?: library.getBook(bookId)?.also { book = it }
            val sourceId = book?.sourceId?.let { ComicProvider.mihonSourceOf(it) }
            if (book == null || sourceId == null) {
                _uiState.value = ComicReaderUiState.Failed(error = null)
                return@launch
            }
            if (readThroughId == null) {
                readThroughId = library.beginReading(bookId)
                _mode.value = preferences.mode(bookId, book.categories)
            }

            ordered = chapters.get(bookId).inReadingOrder()
            val chapter = ordered.firstOrNull { it.url == url }
            if (chapter == null) {
                _uiState.value = ComicReaderUiState.Failed(error = null)
                return@launch
            }
            val opened = try {
                open(chapter, sourceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MetadataException) {
                _uiState.value = ComicReaderUiState.Failed(LookupError.from(e), (e as? MetadataException.Captcha)?.url)
                return@launch
            }

            // Everything laid out before is replaced.
            segments.forEach { it.pages.close() }
            extending.clear()
            this@ComicReaderViewModel.sourceId = sourceId
            generation++
            val segment = ChapterSegment(chapter, opened)
            segments = listOf(segment)
            current = chapter
            val start = when {
                fromStart -> 0
                // A chapter that was finished is read again from its beginning.
                chapter.read -> 0
                else -> chapter.lastPageRead.coerceIn(0, opened.size - 1)
            }
            currentPage = start
            downloadIfWanted(chapter)
            val index = ordered.indexOf(chapter)
            emit(book.title, segment, startPage = start)
            opened.request(start)
            // The chapter after this one is listed ahead of time, so that turning to it does not wait.
            ordered.getOrNull(index + 1)?.let { next ->
                launch { if (downloads.files(bookId, next.url) == null) runCatching { pages.list(sourceId, next) } }
            }
        }
    }

    /**
     * The pages of [chapter]: from this device when it is downloaded, from its source otherwise.
     * Pages from its source go into its download's folder when it is downloaded as it is read, or
     * is in the queue already, so that the download does not fetch them again.
     */
    private suspend fun open(chapter: ComicChapter, source: Long = sourceId): ComicChapterPages {
        downloads.files(bookId, chapter.url)?.let { return pages.openLocal(source, chapter.url, it) }
        val keep = settings.downloadWhileReading.first() || downloads.isQueued(bookId, chapter.url)
        if (!keep) return pages.open(source, chapter)
        return pages.open(
            source,
            chapter,
            into = downloads.folder(bookId, chapter.url),
            // A chapter laid out beside the one read, but never scrolled into, is not queued, and its pages go.
            onClose = { downloads.release(bookId, chapter.url) },
        )
    }

    /** Puts [chapter], now the one on screen, next in the download queue, if chapters are downloaded as they are read. */
    private suspend fun downloadIfWanted(chapter: ComicChapter) {
        if (settings.downloadWhileReading.first()) downloads.enqueue(bookId, listOf(chapter), first = true)
    }

    private fun snapshot(ready: ComicReaderUiState.Ready, page: Int): SessionTracker.Snapshot {
        // Chapters finished before this one, and the part of this one that has been turned through.
        val finished = ordered.count { it.read && it.url != ready.chapter.url }
        val partial = (page + 1.0) / ready.pages.size
        val progression = ((finished + partial) / ordered.size.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return SessionTracker.Snapshot(progression, position = page, chapterTitle = ready.chapter.name)
    }

    private suspend fun save(record: Unsaved) {
        chapters.saveProgress(bookId, record.chapterUrl, record.read, record.page, record.pageCount)
    }

    /** Writes the pending progress and the finished session, outside this ViewModel's scope so closing the reader keeps them. */
    private fun flush() {
        val progress = unsaved.value
        unsaved.value = null
        val session = tracker.end()
        val chaptersRead = finishedInSession.size
        finishedInSession.clear()
        seenInSession.clear()
        val readThrough = readThroughId
        if (progress == null && session == null) return
        appScope.launch {
            progress?.let { save(it) }
            session?.let {
                library.recordSession(
                    SessionDraft(
                        bookId = bookId,
                        readThroughId = readThrough,
                        startedAt = it.startedAt,
                        endedAt = it.endedAt,
                        durationMs = it.activeMs,
                        startProgression = it.start.progression,
                        endProgression = it.end.progression,
                        startPosition = it.start.position,
                        endPosition = it.end.position,
                        // Its pages start over with every chapter, so it is counted in chapters.
                        pages = 0,
                        startChapter = it.start.chapterTitle,
                        endChapter = it.end.chapterTitle,
                        chapters = chaptersRead,
                    ),
                )
            }
        }
    }

    override fun onCleared() {
        flush()
        segments.forEach { it.pages.close() }
    }

    /**
     * Whether a chapter whose [last] page was reached was read to it, not skipped: most of its pages,
     * from the first one on screen this session, were on screen too.
     */
    private fun turnedThrough(seen: Set<Int>, last: Int): Boolean {
        val from = seen.min()
        return from < last && seen.size >= (last - from + 1) * TURNED_THROUGH
    }

    private companion object {
        /** The share of a chapter's pages that has to be on screen for it to count as read. */
        const val TURNED_THROUGH = 0.8

        const val SAVE_DEBOUNCE_MS = 1_500L

        /** How close to the end of a chapter, in pages, the next one starts being laid out. */
        const val EXTEND_MARGIN = 3
    }
}
