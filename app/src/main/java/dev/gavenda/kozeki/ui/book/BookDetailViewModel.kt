package dev.gavenda.kozeki.ui.book

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MatchService
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ImportResult
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.Note
import dev.gavenda.kozeki.data.model.ReadThrough
import dev.gavenda.kozeki.data.model.ReadingState
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sheet where the user picks which record in the metadata source a book corresponds to. */
data class MatchUiState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val query: String = "",
    val results: List<BookMetadata> = emptyList(),
    val error: LookupError? = null,
    val source: MetadataSource = MetadataSource.GOOGLE_BOOKS,
)

data class BookDetailUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val notes: List<Note> = emptyList(),
    val readThroughs: List<ReadThrough> = emptyList(),
    val readingTimeMs: Long = 0L,
    val match: MatchUiState = MatchUiState(),
    val importing: Boolean = false,
    /** Currency code last used for a price, to pre-fill the next one. */
    val lastCurrency: String? = null,
    /** Places entered for earlier purchases, offered again when typing the next one. */
    val purchaseLocations: List<String> = emptyList(),
)

sealed interface BookDetailEvent {
    data object Deleted : BookDetailEvent
    data class ImportFinished(val result: ImportResult) : BookDetailEvent
}

class BookDetailViewModel(
    private val bookId: String,
    private val library: LibraryRepository,
    private val metadata: MetadataRepository,
    private val matchService: MatchService,
    private val settings: SettingsRepository,
    private val scheduleMatching: () -> Unit,
) : ViewModel() {

    private data class BookData(
        val book: Book?,
        val notes: List<Note>,
        val readThroughs: List<ReadThrough>,
        val readingTimeMs: Long,
    )

    private val match = MutableStateFlow(MatchUiState())
    private val importing = MutableStateFlow(false)
    private var searchJob: Job? = null

    private val eventChannel = Channel<BookDetailEvent>(Channel.BUFFERED)
    val events: Flow<BookDetailEvent> = eventChannel.receiveAsFlow()

    private val bookData = combine(
        library.observeBook(bookId),
        library.observeNotes(bookId),
        library.observeReadThroughs(bookId),
        library.observeReadingTime(bookId),
        ::BookData,
    )

    val uiState: StateFlow<BookDetailUiState> =
        combine(
            bookData,
            match,
            importing,
            settings.lastCurrency,
            library.observePurchaseLocations(),
        ) { data, matchState, busy, currency, locations ->
            BookDetailUiState(
                loading = false,
                book = data.book,
                notes = data.notes,
                readThroughs = data.readThroughs,
                readingTimeMs = data.readingTimeMs,
                match = matchState,
                importing = busy,
                lastCurrency = currency,
                purchaseLocations = locations,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())

    fun setState(state: ReadingState) = launch { library.setState(bookId, state) }

    fun readAgain() = launch { library.readAgain(bookId) }

    fun setFavorite(favorite: Boolean) = launch { library.setFavorite(bookId, favorite) }

    fun setRating(rating: Float?) = launch { library.setRating(bookId, rating) }

    fun setPurchase(
        acquisition: Acquisition,
        priceMinor: Long?,
        currency: String?,
        purchasedOn: LocalDate?,
        location: String?,
    ) = launch {
        library.setPurchase(bookId, acquisition, priceMinor, currency, purchasedOn, location)
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

    /** Imports the picked EPUB as the file of this book. */
    fun attachEpub(uri: Uri) = launch {
        importing.value = true
        val result = try {
            library.importEpub(uri, attachTo = bookId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportResult.Failed(ImportResult.Failed.Reason.UNREADABLE, null)
        } finally {
            importing.value = false
        }
        eventChannel.send(BookDetailEvent.ImportFinished(result))
    }

    // ---- Matching --------------------------------------------------------------------------

    /** Opens the match sheet pre-filled with the candidates for this book's title and author. */
    fun openMatch() {
        val book = uiState.value.book ?: return
        val query = listOfNotNull(book.title, book.authors.firstOrNull()).joinToString(" ")
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val source = metadata.selectedSource()
            match.value = MatchUiState(open = true, loading = true, query = query, source = source)
            lookup { matchService.candidates(book).map { it.metadata } }
        }
    }

    fun onMatchQueryChange(query: String) = match.update { it.copy(query = query) }

    fun searchMatch() {
        val query = match.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            match.update { it.copy(loading = true, error = null) }
            lookup { metadata.search(query, match.value.source) }
        }
    }

    private suspend fun lookup(fetch: suspend () -> List<BookMetadata>) {
        try {
            val results = fetch()
            match.update { it.copy(loading = false, results = results, error = null) }
        } catch (e: MetadataException) {
            match.update { it.copy(loading = false, results = emptyList(), error = LookupError.from(e)) }
        }
    }

    fun applyMatch(candidate: BookMetadata) = launch {
        library.applyMetadata(bookId, candidate)
        closeMatch()
    }

    fun closeMatch() {
        searchJob?.cancel()
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
