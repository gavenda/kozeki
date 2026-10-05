package dev.gavenda.kozeki.ui.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.metadata.Author
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.BookSearch
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.addbook.AddBookEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthorUiState(
    /** The name the author was opened under, shown until the source answers with its own. */
    val name: String,
    val author: Author? = null,
    val loading: Boolean = true,
    val books: List<BookMetadata> = emptyList(),
    /** Whether the source may have books beyond the ones in [books]. */
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: LookupError? = null,
    /** The source answered, and has no such author. */
    val notFound: Boolean = false,
    /** The book whose details are open. */
    val selected: BookMetadata? = null,
    /** Source IDs of the books already among the user's own, so their rows can show it. */
    val owned: Set<String> = emptySet(),
)

class AuthorViewModel(
    private val authorId: String,
    name: String,
    private val metadata: MetadataRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorUiState(name = name))
    val uiState: StateFlow<AuthorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<AddBookEvent>(Channel.BUFFERED)
    val events: Flow<AddBookEvent> = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    /** The author's books, of which the ones on screen are the start. */
    private var pager: BookSearch? = null

    init {
        viewModelScope.launch {
            library.observeAll().collect { books ->
                val owned = books.filter { it.source == metadata.source }.mapNotNullTo(mutableSetOf()) { it.sourceId }
                _uiState.update { it.copy(owned = owned) }
            }
        }
        load()
    }

    /** Fetches the author and their first books, again after a failure. */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, notFound = false) }
            try {
                val author = metadata.author(authorId).author
                if (author == null) {
                    _uiState.update { it.copy(loading = false, notFound = true) }
                    return@launch
                }
                // The first page is asked for twice, and answered from the cache the second time.
                val books = BookSearch { page -> metadata.author(authorId, page).books }
                val first = books.next()
                pager = books
                _uiState.update {
                    it.copy(
                        name = author.name,
                        author = author,
                        loading = false,
                        books = first,
                        canLoadMore = books.hasMore,
                    )
                }
            } catch (e: MetadataException) {
                _uiState.update { it.copy(loading = false, error = LookupError.from(e)) }
            }
        }
    }

    /** Fetches the books after the ones on screen, for when the list is scrolled to its end. */
    fun loadMore() {
        val books = pager ?: return
        if (!books.hasMore || loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            try {
                val more = books.next()
                _uiState.update { it.copy(books = it.books + more, canLoadMore = books.hasMore) }
            } catch (_: MetadataException) {
                // What is on screen stays; scrolling back to the end asks again.
            } finally {
                _uiState.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun select(result: BookMetadata?) = _uiState.update { it.copy(selected = result) }

    fun add(result: BookMetadata, acquisition: Acquisition) {
        viewModelScope.launch {
            val bookId = library.addFromMetadata(result, acquisition)
            _uiState.update { it.copy(selected = null) }
            eventChannel.send(AddBookEvent.Added(bookId, result.title, acquisition))
        }
    }
}
