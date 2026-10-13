package dev.gavenda.kozeki.ui.book

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.manga.MangaChapterRepository
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.BookSearch
import dev.gavenda.kozeki.data.metadata.BookReviews
import dev.gavenda.kozeki.data.metadata.MatchService
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ImportResult
import dev.gavenda.kozeki.data.model.MangaChapter
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.Note
import dev.gavenda.kozeki.data.model.PhysicalReading
import dev.gavenda.kozeki.data.model.ReadThrough
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.inReadingOrder
import dev.gavenda.kozeki.data.model.resumeChapter
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.ui.LookupError
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sheet where the user picks which record in the metadata source a book corresponds to. */
data class MatchUiState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val query: String = "",
    /** The catalogue the sheet looks in. */
    val source: MetadataSource = MetadataSource.HARDCOVER,
    val results: List<BookMetadata> = emptyList(),
    /** Whether the source may have results beyond the ones in [results]. */
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: LookupError? = null,
)

/** What other readers wrote about the book, for a book that is linked to a source with reviews. */
data class ReviewsUiState(
    val loading: Boolean = false,
    /** Null until the source has answered. */
    val reviews: BookReviews? = null,
    val error: LookupError? = null,
)

/** The chapters of a manga, for the page of a book that is one. */
data class MangaChaptersUiState(
    /** In reading order, the first chapter first. */
    val chapters: List<MangaChapter> = emptyList(),
    /** Whether the source is being asked what chapters it has right now. */
    val loading: Boolean = false,
    val error: LookupError? = null,
) {
    /** Where reading goes on from, which is the last chapter once every one is read. */
    val resume: MangaChapter? get() = chapters.resumeChapter() ?: chapters.lastOrNull()

    /** Every chapter is read. */
    val finished: Boolean get() = chapters.isNotEmpty() && chapters.all { it.read }

    /** Something in it has been read, so reading would continue and not start. */
    val started: Boolean get() = chapters.any { it.read || it.lastPageRead > 0 }
}

data class BookDetailUiState(
    /** Null for a book that is not a manga. */
    val chapters: MangaChaptersUiState? = null,
    val loading: Boolean = true,
    val book: Book? = null,
    val notes: List<Note> = emptyList(),
    val readThroughs: List<ReadThrough> = emptyList(),
    val readingTimeMs: Long = 0L,
    /** What is on record of reading the physical copy, which going back to an earlier page takes from. */
    val physicalReadings: List<PhysicalReading> = emptyList(),
    val match: MatchUiState = MatchUiState(),
    /** Whether the book's details are being looked up right now, by the lookup that runs by itself. */
    val matching: Boolean = false,
    /** Null while the book is not linked to a source that has reviews, which leaves nothing to ask for. */
    val reviews: ReviewsUiState? = null,
    val importing: Boolean = false,
    /** Currency code last used for a price, to pre-fill the next one. */
    val lastCurrency: String? = null,
    /** Places entered for earlier purchases, offered again when typing the next one. */
    val purchaseLocations: List<String> = emptyList(),
)

sealed interface BookDetailEvent {
    data object Deleted : BookDetailEvent
    data class ImportFinished(val result: ImportResult) : BookDetailEvent

    /** The picture picked as the cover could not be read. */
    data object CoverFailed : BookDetailEvent
}

class BookDetailViewModel(
    private val bookId: String,
    private val library: LibraryRepository,
    private val metadata: MetadataRepository,
    private val matchService: MatchService,
    private val settings: SettingsRepository,
    private val mangaChapters: MangaChapterRepository,
    private val scheduleMatching: () -> Unit,
) : ViewModel() {

    private data class Activity(
        val importing: Boolean,
        val matching: Boolean,
        val chapters: List<MangaChapter>,
        val chaptersStatus: MangaChaptersUiState,
    )

    private data class BookData(
        val book: Book?,
        val notes: List<Note>,
        val readThroughs: List<ReadThrough>,
        val readingTimeMs: Long,
        val physicalReadings: List<PhysicalReading>,
    )

    private val match = MutableStateFlow(MatchUiState())
    private val reviews = MutableStateFlow<ReviewsUiState?>(null)
    private val importing = MutableStateFlow(false)

    /** Whether the source is being asked for chapters, and how that went. Its list is not in it. */
    private val chaptersStatus = MutableStateFlow(MangaChaptersUiState())
    private var chaptersJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    /** The search the match sheet's results are the start of, when there may be more of it. */
    private var matchPager: BookSearch? = null

    /** Whether the match sheet shows the candidates it opened with, and not a search the user ran. */
    private var showingCandidates = false
    private var reviewsJob: Job? = null

    /** The source and the ID on it of the book the reviews on screen belong to. */
    private var reviewed: Pair<MetadataSource, String>? = null

    private val eventChannel = Channel<BookDetailEvent>(Channel.BUFFERED)
    val events: Flow<BookDetailEvent> = eventChannel.receiveAsFlow()

    private val bookData = combine(
        library.observeBook(bookId),
        library.observeNotes(bookId),
        library.observeReadThroughs(bookId),
        library.observeReadingTime(bookId),
        library.observePhysicalReadings(bookId),
        ::BookData,
    )

    val uiState: StateFlow<BookDetailUiState> =
        combine(
            bookData,
            combine(match, reviews, ::Pair),
            combine(
                importing,
                matchService.matching.map { bookId in it },
                mangaChapters.observe(bookId),
                chaptersStatus,
                ::Activity,
            ),
            settings.lastCurrency,
            library.observePurchaseLocations(),
        ) { data, (matchState, reviewsState), activity, currency, locations ->
            val (busy, matching) = activity.importing to activity.matching
            BookDetailUiState(
                loading = false,
                chapters = if (data.book?.isManga == true) {
                    activity.chaptersStatus.copy(chapters = activity.chapters.inReadingOrder())
                } else {
                    null
                },
                book = data.book,
                notes = data.notes,
                readThroughs = data.readThroughs,
                readingTimeMs = data.readingTimeMs,
                physicalReadings = data.physicalReadings,
                match = matchState,
                matching = matching,
                reviews = reviewsState,
                importing = busy,
                lastCurrency = currency,
                purchaseLocations = locations,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())

    init {
        // A manga's chapters are asked for again whenever its page is opened: new ones appear there.
        viewModelScope.launch {
            val book = library.observeBook(bookId).filterNotNull().first()
            if (book.isManga) refreshChapters()
        }
        // Reviews follow the link to the source: matching, re-matching or unlinking the book
        // changes which reviews, if any, belong on the screen.
        viewModelScope.launch {
            library.observeBook(bookId).map { it?.reviewsLink() }.distinctUntilChanged().collectLatest { link ->
                reviewed = link
                loadReviews()
            }
        }
    }

    fun setState(state: ReadingState) = launch { library.setState(bookId, state) }

    fun readAgain() = launch {
        library.readAgain(bookId)
        // A manga is read again from its first chapter, so none counts as read.
        uiState.value.chapters?.chapters?.let { mangaChapters.markRead(bookId, it.map(MangaChapter::url), read = false) }
    }

    /** Marks a chapter of a manga read, or puts it back to unread. */
    fun setChapterRead(url: String, read: Boolean) = launch { mangaChapters.markRead(bookId, listOf(url), read) }

    /** Asks the source for its chapters again, after it failed or to see whether new ones came out. */
    fun retryChapters() = refreshChapters()

    private fun refreshChapters() {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            val book = library.observeBook(bookId).first() ?: return@launch
            val sourceId = book.sourceId ?: return@launch
            chaptersStatus.value = MangaChaptersUiState(loading = true)
            chaptersStatus.value = try {
                mangaChapters.sync(bookId, mangaChapters.fetch(sourceId, book.title, book.sourceMemo).chapters.orEmpty())
                MangaChaptersUiState()
            } catch (e: MetadataException) {
                MangaChaptersUiState(error = LookupError.from(e))
            }
        }
    }

    fun setFavorite(favorite: Boolean) = launch { library.setFavorite(bookId, favorite) }

    fun setRating(rating: Float?) = launch { library.setRating(bookId, rating) }

    fun setPurchase(
        acquisition: Acquisition,
        priceMinor: Long?,
        currency: String?,
        purchasedOn: LocalDate?,
        location: String?,
        memo: String?,
    ) = launch {
        library.setPurchase(bookId, acquisition, priceMinor, currency, purchasedOn, location, memo)
        if (priceMinor != null && currency != null) settings.setLastCurrency(currency)
    }

    fun setPhysicalProgress(page: Int?, pageCount: Int?) =
        launch { library.setPhysicalProgress(bookId, page, pageCount) }

    fun saveNote(noteId: String?, text: String) {
        if (text.isBlank()) return
        launch { library.saveNote(bookId, noteId, text) }
    }

    fun deleteNote(noteId: String) = launch { library.deleteNote(noteId) }

    fun delete() = launch {
        library.deleteBook(bookId)
        eventChannel.send(BookDetailEvent.Deleted)
    }

    /** Makes the picked picture the cover of this book. */
    fun setCover(uri: Uri) = launch {
        if (!library.setCustomCover(bookId, uri)) eventChannel.send(BookDetailEvent.CoverFailed)
    }

    fun removeCover() = launch { library.removeCustomCover(bookId) }

    /** Imports the picked EPUB as the file of this book. */
    fun attachEpub(uri: Uri) = launch {
        importing.value = true
        val result = try {
            library.importEpub(uri, attachTo = bookId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ImportResult.Failed(ImportResult.Failed.Reason.UNREADABLE, null)
        } finally {
            importing.value = false
        }
        eventChannel.send(BookDetailEvent.ImportFinished(result))
    }

    // ---- Reviews ---------------------------------------------------------------------------

    /** Asks again after a failure, for instance once the user has signed in or is back online. */
    fun retryReviews() = loadReviews()

    /** Where the reviews of this book are, or null when it is linked to nothing that has any. */
    private fun Book.reviewsLink(): Pair<MetadataSource, String>? {
        val source = source?.takeIf { it.hasReviews } ?: return null
        return source to (sourceId ?: return null)
    }

    private fun loadReviews() {
        reviewsJob?.cancel()
        val (source, sourceId) = reviewed ?: run {
            reviews.value = null
            return
        }
        reviewsJob = viewModelScope.launch {
            reviews.value = ReviewsUiState(loading = true)
            reviews.value = try {
                ReviewsUiState(reviews = metadata.reviews(source, sourceId))
            } catch (e: MetadataException) {
                ReviewsUiState(error = LookupError.from(e))
            }
        }
    }

    // ---- Matching --------------------------------------------------------------------------

    /**
     * Opens the match sheet pre-filled with the candidates for this book's title and author, from
     * the source the user searched last.
     */
    fun openMatch() {
        val book = uiState.value.book ?: return
        val query = listOfNotNull(book.title, book.authors.firstOrNull()).joinToString(" ")
        match.value = MatchUiState(
            open = true,
            loading = true,
            query = query,
            // Manga are never what a file is matched to.
            source = settings.searchSource.takeIf { it in MetadataSource.matchable } ?: MetadataSource.HARDCOVER,
        )
        loadCandidates(book)
    }

    private fun loadCandidates(book: Book) {
        cancelMatchSearch()
        showingCandidates = true
        val source = match.value.source
        searchJob = viewModelScope.launch {
            match.update { it.copy(loading = true, error = null) }
            lookup { matchService.candidates(book, source).map { it.metadata } }
        }
    }

    fun onMatchQueryChange(query: String) = match.update { it.copy(query = query) }

    /** Points the match sheet at [source], repeats there what it was showing, and remembers the choice. */
    fun setMatchSource(source: MetadataSource) {
        if (source == match.value.source) return
        settings.searchSource = source
        val candidates = showingCandidates
        cancelMatchSearch()
        // What is in the sheet came from the other source, its failures included.
        match.update {
            it.copy(source = source, loading = false, results = emptyList(), canLoadMore = false, error = null)
        }
        val book = uiState.value.book
        if (candidates && book != null) loadCandidates(book) else searchMatch()
    }

    fun searchMatch() {
        val query = match.value.query.trim()
        if (query.isEmpty()) return
        cancelMatchSearch()
        searchJob = viewModelScope.launch {
            val source = match.value.source
            match.update { it.copy(loading = true, error = null) }
            val search = BookSearch(metadata, source, query)
            lookup { search.next() }
            matchPager = search
            match.update { it.copy(canLoadMore = it.error == null && search.hasMore) }
        }
    }

    /** Fetches the results after the ones in the sheet, for when the list is scrolled to its end. */
    fun loadMoreMatches() {
        val search = matchPager ?: return
        if (!search.hasMore || loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            match.update { it.copy(loadingMore = true) }
            try {
                val more = search.next()
                match.update { it.copy(results = it.results + more, canLoadMore = search.hasMore) }
            } catch (_: MetadataException) {
                // What is in the sheet stays; scrolling back to the end asks again.
            } finally {
                match.update { it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun lookup(fetch: suspend () -> List<BookMetadata>) {
        try {
            val results = fetch()
            match.update { it.copy(loading = false, results = results, canLoadMore = false, error = null) }
        } catch (e: MetadataException) {
            match.update {
                it.copy(loading = false, results = emptyList(), canLoadMore = false, error = LookupError.from(e))
            }
        }
    }

    private fun cancelMatchSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        matchPager = null
        showingCandidates = false
    }

    fun applyMatch(candidate: BookMetadata) = launch {
        library.applyMetadata(bookId, candidate)
        closeMatch()
    }

    fun closeMatch() {
        cancelMatchSearch()
        match.update { it.copy(open = false, loading = false) }
    }

    fun unlink() = launch { library.unlink(bookId) }

    /** Queues the automatic lookup again, for a book whose earlier lookup found nothing. */
    fun retryAutomaticMatch() = launch {
        library.setMatchStatus(bookId, MatchStatus.PENDING)
        scheduleMatching()
    }

    private fun launch(block: suspend () -> Unit): Job = viewModelScope.launch { block() }
}
