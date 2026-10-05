package dev.gavenda.kozeki.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.epub.Readium
import dev.gavenda.kozeki.data.files.BookStorage
import dev.gavenda.kozeki.data.model.ReadingProgress
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.SessionDraft
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.settings.ReaderPreferences
import dev.gavenda.kozeki.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.navigator.web.fixedlayout.FixedWebConfiguration
import org.readium.navigator.web.fixedlayout.FixedWebGoLocation
import org.readium.navigator.web.fixedlayout.FixedWebRenditionFactory
import org.readium.navigator.web.fixedlayout.preferences.FixedWebPreferences
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.navigator.web.reflowable.ReflowableWebRenditionFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.publication.services.positions

enum class ReaderFailure { NOT_FOUND, FILE_MISSING, UNSUPPORTED, UNREADABLE }

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Failed(val reason: ReaderFailure) : ReaderUiState

    data class Ready(
        val title: String,
        val rendition: Rendition,
        val toc: List<TocEntry>,
        /** Readium positions in the book, which stand in for page numbers. */
        val positionCount: Int,
    ) : ReaderUiState
}

sealed interface ReaderEvent {
    /** The end was reached and the book was marked as completed. */
    data object Completed : ReaderEvent
}

class ReaderViewModel(
    private val bookId: String,
    private val application: Application,
    private val library: LibraryRepository,
    private val readium: Readium,
    private val storage: BookStorage,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(ReaderProgress())
    val progress: StateFlow<ReaderProgress> = _progress.asStateFlow()

    private val _preferences = MutableStateFlow(ReaderPreferences())
    val preferences: StateFlow<ReaderPreferences> = _preferences.asStateFlow()

    private val eventChannel = Channel<ReaderEvent>(Channel.BUFFERED)
    val events: Flow<ReaderEvent> = eventChannel.receiveAsFlow()

    private var opening = false
    private var publication: Publication? = null
    private var toc: List<TocEntry> = emptyList()
    private var positions: List<Locator> = emptyList()
    private var readThroughId: String? = null

    private val tracker = SessionTracker(System::currentTimeMillis)
    private var resumed = false

    /** The last position the navigator reported. Null until the first page has been laid out. */
    private var lastSnapshot: SessionTracker.Snapshot? = null
    private var lastLocator: Locator? = null

    private var systemColors: ReaderColors? = null
    private var recovering = false
    private val recoveries = ArrayDeque<Long>()
    private var completionHandled = false

    /** The latest position not yet written to the database. */
    private val unsaved = MutableStateFlow<ReadingProgress?>(null)

    init {
        // Page turns come in bursts; writing once they settle is enough, and pausing flushes the rest.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            unsaved.filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect { library.saveProgress(bookId, it) }
        }
    }

    /**
     * Opens the book. The app theme's colours are needed up front so the first page is already
     * painted in them instead of flashing white.
     */
    fun open(colors: ReaderColors) {
        if (opening) return
        opening = true
        systemColors = colors
        viewModelScope.launch {
            val book = library.getBook(bookId) ?: return@launch fail(ReaderFailure.NOT_FOUND)
            if (!book.hasFile) return@launch fail(ReaderFailure.FILE_MISSING)

            val opened = try {
                readium.open(storage.epubFile(bookId))
            } catch (e: Readium.OpenException) {
                return@launch fail(ReaderFailure.UNREADABLE)
            }
            publication = opened

            val readerPreferences = settings.readerPreferences.first()
            _preferences.value = readerPreferences
            val initialLocator = book.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

            val rendition = createRendition(opened, initialLocator, readerPreferences, colors)
                ?: return@launch fail(ReaderFailure.UNSUPPORTED)

            toc = opened.flattenedToc()
            // Reading the files to place the anchors takes a moment on big books; do not hold the first page for it.
            viewModelScope.launch {
                toc = withContext(Dispatchers.IO) { opened.locateTocEntries(toc) }
                lastLocator?.let { onLocationChanged(it, atEnd = false) }
            }
            positions = withContext(Dispatchers.IO) { runCatching { opened.positions() }.getOrDefault(emptyList()) }
            readThroughId = library.beginReading(bookId)
            completionHandled = book.state == ReadingState.COMPLETED

            _progress.value = ReaderProgress(
                progression = book.progression,
                position = book.position,
                chapterTitle = book.chapterTitle,
                tocIndex = book.chapterIndex?.minus(1),
            )
            _uiState.value = ReaderUiState.Ready(
                title = book.title,
                rendition = rendition,
                toc = toc,
                positionCount = positions.size,
            )
        }
    }

    private suspend fun createRendition(
        publication: Publication,
        initialLocator: Locator?,
        preferences: ReaderPreferences,
        colors: ReaderColors,
    ): Rendition? {
        ReflowableWebRenditionFactory(application, publication)?.let { factory ->
            val state = factory.createRenditionState(
                initialPreferences = preferences.toReflowable(colors),
                initialLocation = initialLocator?.let(::ReflowableWebGoLocation),
            ).getOrNull()
            if (state != null) return Rendition.Reflowable(state)
        }
        FixedWebRenditionFactory(application, publication, FixedWebConfiguration())?.let { factory ->
            val state = factory.createRenditionState(
                initialPreferences = FixedWebPreferences(),
                initialLocation = initialLocator?.let(::FixedWebGoLocation),
            ).getOrNull()
            if (state != null) return Rendition.Fixed(state)
        }
        return null
    }

    private fun fail(reason: ReaderFailure) {
        _uiState.value = ReaderUiState.Failed(reason)
    }

    /**
     * Called by the screen whenever the navigator reports a new location.
     *
     * @param atEnd the last page of the book is showing.
     */
    fun onLocationChanged(locator: Locator, atEnd: Boolean) {
        val book = publication ?: return
        val spineIndex = book.readingOrder.indexOfFirstWithHref(locator.href)
        val tocIndex = toc.chapterIndexAt(spineIndex, locator.locations.progression ?: 0.0)
        val progression = if (atEnd) 1.0 else locator.locations.totalProgression ?: _progress.value.progression
        val chapterTitle = tocIndex?.let { toc[it].title } ?: locator.title

        _progress.value = ReaderProgress(
            progression = progression,
            position = locator.locations.position,
            chapterTitle = chapterTitle,
            tocIndex = tocIndex,
        )
        unsaved.value = ReadingProgress(
            locatorJson = locator.toJSON().toString(),
            progression = progression,
            position = locator.locations.position,
            positionCount = positions.size.takeIf { it > 0 },
            chapterTitle = chapterTitle,
            // Without a table of contents, fall back to counting the book's resources.
            chapterIndex = (tocIndex ?: spineIndex)?.plus(1),
            chapterCount = if (toc.isNotEmpty()) toc.size else book.readingOrder.size,
        )

        val snapshot = SessionTracker.Snapshot(progression, locator.locations.position, chapterTitle)
        lastSnapshot = snapshot
        lastLocator = locator
        if (resumed && !tracker.isRunning) tracker.begin(snapshot) else tracker.activity(snapshot)

        if (atEnd && !completionHandled) {
            completionHandled = true
            viewModelScope.launch {
                if (library.getBook(bookId)?.state == ReadingState.READING) {
                    library.setState(bookId, ReadingState.COMPLETED)
                    eventChannel.send(ReaderEvent.Completed)
                }
            }
        }
    }

    /**
     * The WebView process that draws the pages died, by crashing or by being reclaimed for memory.
     * Its pages cannot be revived, so the navigator is rebuilt at the last known position. A book
     * that keeps killing its renderer is given up on rather than reloaded forever.
     */
    fun onRendererGone() {
        val ready = _uiState.value as? ReaderUiState.Ready ?: return
        val opened = publication ?: return
        val colors = systemColors ?: return
        if (recovering) return

        val now = System.currentTimeMillis()
        while (recoveries.isNotEmpty() && now - recoveries.first() > RECOVERY_WINDOW_MS) recoveries.removeFirst()
        if (recoveries.size >= MAX_RECOVERIES) return fail(ReaderFailure.UNREADABLE)
        recoveries.addLast(now)

        recovering = true
        viewModelScope.launch {
            val rendition = createRendition(opened, lastLocator, _preferences.value, colors)
            recovering = false
            _uiState.value = if (rendition != null) ready.copy(rendition = rendition) else ReaderUiState.Failed(ReaderFailure.UNREADABLE)
        }
    }

    /** A tap that did not turn the page still means somebody is reading. */
    fun onInteraction() = tracker.activity()

    fun onResumed() {
        resumed = true
        // Before the first page is up there is nothing to start from; onLocationChanged starts it then.
        lastSnapshot?.let { if (!tracker.isRunning) tracker.begin(it) }
    }

    fun onPaused() {
        resumed = false
        flush()
    }

    fun undoCompleted() {
        viewModelScope.launch { library.setState(bookId, ReadingState.READING) }
    }

    fun setPreferences(preferences: ReaderPreferences) {
        _preferences.value = preferences
        appScope.launch { settings.setReaderPreferences(preferences) }
    }

    /** The locator for a page number picked on the progress slider. */
    fun locatorForPosition(position: Int): Locator? = positions.getOrNull(position - 1)

    fun saveNote(text: String) {
        if (text.isBlank()) return
        val chapter = _progress.value.chapterTitle
        appScope.launch { library.saveNote(bookId, null, text, chapter) }
    }

    /** Writes the pending progress and the finished session. Runs outside this ViewModel's scope so it survives closing the reader. */
    private fun flush() {
        val progress = unsaved.value
        unsaved.value = null
        val session = tracker.end()
        val readThrough = readThroughId
        if (progress == null && session == null) return
        appScope.launch {
            progress?.let { library.saveProgress(bookId, it) }
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
                        pages = it.pages,
                        startChapter = it.start.chapterTitle,
                        endChapter = it.end.chapterTitle,
                    ),
                )
            }
        }
    }

    override fun onCleared() {
        flush()
        publication?.close()
        publication = null
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 1_500L
        const val MAX_RECOVERIES = 3
        const val RECOVERY_WINDOW_MS = 60_000L
    }
}
