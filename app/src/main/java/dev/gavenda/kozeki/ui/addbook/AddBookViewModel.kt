package dev.gavenda.kozeki.ui.addbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.ui.LookupError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddBookUiState(
    val source: MetadataSource = MetadataSource.GOOGLE_BOOKS,
    val query: String = "",
    val loading: Boolean = false,
    /** Null until a search has been run, to tell "nothing yet" from "nothing found". */
    val results: List<BookMetadata>? = null,
    val error: LookupError? = null,
    /** The result whose details are open. */
    val selected: BookMetadata? = null,
    /** Source IDs added during this visit, so their rows can show it. */
    val added: Set<String> = emptySet(),
)

sealed interface AddBookEvent {
    data class Added(val bookId: String, val title: String, val acquisition: Acquisition) : AddBookEvent
}

class AddBookViewModel(
    private val metadata: MetadataRepository,
    private val library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBookUiState())
    val uiState: StateFlow<AddBookUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<AddBookEvent>(Channel.BUFFERED)
    val events: Flow<AddBookEvent> = eventChannel.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settings.metadataSource.collect { source ->
                val unavailable = metadata.unavailableReason(source)
                _uiState.update {
                    it.copy(source = source, error = unavailable?.let(LookupError::from), results = null)
                }
            }
        }
    }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    /**
     * Runs the search. It only fires when the user submits, never per keystroke: every request
     * counts against the source's quota.
     */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.length < 3) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val source = _uiState.value.source
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                // A bare ISBN gets an exact lookup instead of a text search.
                val isbn = Isbn.toIsbn13(query)
                val results = if (isbn != null) metadata.findByIsbn(isbn, source) else metadata.search(query, source)
                _uiState.update { it.copy(loading = false, results = results) }
            } catch (e: MetadataException) {
                _uiState.update { it.copy(loading = false, results = null, error = LookupError.from(e)) }
            }
        }
    }

    fun select(result: BookMetadata?) = _uiState.update { it.copy(selected = result) }

    fun add(result: BookMetadata, acquisition: Acquisition) {
        viewModelScope.launch {
            val bookId = library.addFromMetadata(result, acquisition)
            _uiState.update { it.copy(selected = null, added = it.added + result.sourceId) }
            eventChannel.send(AddBookEvent.Added(bookId, result.title, acquisition))
        }
    }
}
