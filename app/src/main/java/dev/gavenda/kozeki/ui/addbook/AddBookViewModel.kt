package dev.gavenda.kozeki.ui.addbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.comic.ComicSources
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.BookSearch
import dev.gavenda.kozeki.data.metadata.BrowseOrder
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.metadata.OwnedBooks
import dev.gavenda.kozeki.data.metadata.sortedBySeries
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.components.BookDisplay
import dev.gavenda.kozeki.ui.components.BulkAdded
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A comic source the search can be pointed at, as a chip. */
data class ComicSourceOption(val id: Long, val label: String)

data class AddBookUiState(
    val query: String = "",
    /** The catalogue being searched. */
    val source: MetadataSource = MetadataSource.HARDCOVER,
    val loading: Boolean = false,
    /** Null until a search has been run, to tell "nothing yet" from "nothing found". */
    val results: List<BookMetadata>? = null,
    /** Whether the source may have results beyond the ones in [results]. */
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: LookupError? = null,
    /** Where the captcha the source asks for is solved, when that is why [error] is set. */
    val captchaUrl: String? = null,
    /** The results already among the user's own books, by source ID, so their rows can show it. */
    val owned: Map<String, Book> = emptyMap(),
    /** The comic sources the installed extensions provide. Only searched when [source] is comics. */
    val comicSources: List<ComicSourceOption> = emptyList(),
    /** The one of [comicSources] searched, or 0 when there is none. */
    val comicSourceId: Long = 0L,
    /** How the results are laid out: rows, as they always were, until the user picks covers. */
    val display: BookDisplay = BookDisplay.LIST,
    /** The lists the source can show with nothing typed; empty when it has none, as Google Books. */
    val browseOrders: List<BrowseOrder> = emptyList(),
    /** The one of [browseOrders] shown while nothing is typed. */
    val browseOrder: BrowseOrder? = null,
) {
    /** How much of a query is searched. Titles of comics can be short. */
    val minQueryLength: Int get() = if (source == MetadataSource.COMICS) 1 else 3
}

class AddBookViewModel(
    private val metadata: MetadataRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    comicSources: ComicSources,
) : ViewModel() {

    // Opens on the source searched last time.
    private val _uiState = MutableStateFlow(AddBookUiState(source = settings.searchSource))

    private val pickedComic = MutableStateFlow(settings.comicSourceId)

    val uiState: StateFlow<AddBookUiState> =
        combine(
            _uiState,
            library.observeAll().map(::OwnedBooks),
            comicSources.sources,
            pickedComic,
            settings.searchDisplay,
        ) { state, owned, sources, picked, display ->
            val labels = comicSourceLabels(sources.map { it.name to it.lang })
            val options = sources.mapIndexed { i, source -> ComicSourceOption(source.id, labels[i]) }
            state.copy(
                owned = owned.among(state.results.orEmpty()),
                comicSources = options,
                // The same fallback the search itself makes while the picked source is not installed.
                comicSourceId = (options.firstOrNull { it.id == picked } ?: options.firstOrNull())?.id ?: 0L,
                display = BookDisplay.named(display, BookDisplay.LIST),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var availabilityJob: Job? = null

    /** The search the results on screen are the start of, when there may be more of it. */
    private var pager: BookSearch? = null

    /** The query the last search was started for, empty for a list shown with nothing typed. */
    private var searched: String? = null

    init {
        // Nothing is typed yet, so it opens on what the source finds popular.
        search()
    }

    private val addedChannel = Channel<BulkAdded>(Channel.BUFFERED)

    /** Picked results that were added, for the snackbar. */
    val added: Flow<BulkAdded> = addedChannel.receiveAsFlow()

    /** Adds the picked [results] to the library together, as [acquisition]. */
    fun addAll(results: List<BookMetadata>, acquisition: Acquisition) {
        if (results.isEmpty()) return
        viewModelScope.launch {
            val count = library.addAllFromMetadata(results, acquisition)
            addedChannel.send(BulkAdded(count, acquisition))
        }
    }

    /** Points the search at [source], repeats it there, and remembers the choice for next time. */
    fun setSource(source: MetadataSource) {
        if (source == _uiState.value.source) return
        cancelSearch()
        // What is on screen came from the other source, its failures included.
        _uiState.update {
            it.copy(
                source = source,
                loading = false,
                results = null,
                canLoadMore = false,
                error = null,
                browseOrders = emptyList(),
                browseOrder = null,
            )
        }
        settings.searchSource = source
        if (source != MetadataSource.COMICS) settings.bookSearchSource = source
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || query.length >= _uiState.value.minQueryLength) search() else checkAvailability()
    }

    /** Shows the list [order] while nothing is typed, and keeps it for the next time books or comics are browsed. */
    fun setBrowseOrder(order: BrowseOrder) {
        val state = _uiState.value
        if (order !in state.browseOrders) return
        if (state.source == MetadataSource.COMICS) settings.comicBrowseOrder = order else settings.bookBrowseOrder = order
        if (order == state.browseOrder && state.results != null) return
        _uiState.update { it.copy(browseOrder = order) }
        if (state.query.isBlank()) browse(debounceMs = 0)
    }

    /** Lays the results out as [display], and keeps it for the next search. */
    fun setDisplay(display: BookDisplay) {
        viewModelScope.launch { settings.setSearchDisplay(display.name) }
    }

    /** Switches between books and comics, books going back to the catalogue they were last searched in. */
    fun setComics(comics: Boolean) {
        setSource(if (comics) MetadataSource.COMICS else settings.bookSearchSource)
    }

    /** Points comic searches at the source with [id], and repeats the search there. */
    fun setComicSource(id: Long) {
        if (id == _uiState.value.comicSourceId) return
        settings.comicSourceId = id
        pickedComic.value = id
        cancelSearch()
        _uiState.update { it.copy(loading = false, results = null, canLoadMore = false, error = null) }
        search()
    }

    /** Says at once when the source cannot be searched, instead of after the first attempt. */
    private fun checkAvailability() {
        availabilityJob?.cancel()
        availabilityJob = viewModelScope.launch {
            val unavailable = metadata.unavailableReason(_uiState.value.source)
            _uiState.update { it.copy(error = unavailable?.let(LookupError::from)) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        search(SEARCH_DEBOUNCE_MS)
    }

    /** Searches at once, for when the user submits instead of waiting for the search to follow. */
    fun search() = search(debounceMs = 0)

    /**
     * Searches for the query once it has stood still for [debounceMs]. Every request counts
     * against the source's quota, so a word being typed is not searched letter by letter.
     */
    private fun search(debounceMs: Long) {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) {
            // Typing that clears the field puts the list back, unless it is already on screen.
            if (debounceMs > 0 && searched == "") return
            browse(debounceMs)
            return
        }
        if (query.length < _uiState.value.minQueryLength) {
            // Too short to search. A list shown with nothing typed stays while the first letters
            // go in; results for something longer go.
            if (debounceMs > 0 && searched != "") {
                cancelSearch()
                _uiState.update { it.copy(loading = false, results = null, canLoadMore = false) }
            }
            return
        }
        // Typing that leaves the query as it was, a trailing space for one, is not a new search.
        if (debounceMs > 0 && query == searched) return
        cancelSearch()
        searchJob = viewModelScope.launch {
            delay(debounceMs.milliseconds)
            searched = query
            browsed = false
            val source = _uiState.value.source
            _uiState.update { it.copy(loading = true, error = null, captchaUrl = null) }
            try {
                // A bare ISBN gets an exact lookup instead of a text search.
                val isbn = if (source == MetadataSource.COMICS) null else Isbn.toIsbn13(query)
                val search = if (isbn == null) BookSearch(metadata, source, query) else null
                val results = (search?.next() ?: metadata.findByIsbn(source, isbn!!)).sortedBySeries()
                pager = search
                _uiState.update { it.copy(loading = false, results = results, canLoadMore = search?.hasMore == true) }
            } catch (e: MetadataException) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        results = null,
                        canLoadMore = false,
                        error = LookupError.from(e),
                        captchaUrl = (e as? MetadataException.Captcha)?.url,
                    )
                }
            }
        }
    }

    /** Whether the results on screen are a list shown with nothing typed, ranked by the source. */
    private var browsed = false

    /**
     * Shows the list the user picked last time for this kind, or the source's first, while nothing
     * is typed. A source with no lists says why it cannot be searched, or waits for a query.
     */
    private fun browse(debounceMs: Long) {
        cancelSearch()
        searchJob = viewModelScope.launch {
            delay(debounceMs.milliseconds)
            searched = ""
            val source = _uiState.value.source
            _uiState.update { it.copy(loading = true, error = null, captchaUrl = null) }
            try {
                val orders = metadata.browseOrders(source)
                val saved = if (source == MetadataSource.COMICS) settings.comicBrowseOrder else settings.bookBrowseOrder
                val order = _uiState.value.browseOrder?.takeIf { it in orders }
                    ?: saved?.takeIf { it in orders }
                    ?: orders.firstOrNull()
                _uiState.update { it.copy(browseOrders = orders, browseOrder = order) }
                if (order == null) {
                    _uiState.update { it.copy(loading = false, results = null, canLoadMore = false) }
                    checkAvailability()
                    return@launch
                }
                val search = BookSearch(metadata, source, order)
                // In the source's order: the point of the list is what comes first.
                val results = search.next()
                pager = search
                browsed = true
                _uiState.update { it.copy(loading = false, results = results, canLoadMore = search.hasMore) }
            } catch (e: MetadataException) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        results = null,
                        canLoadMore = false,
                        error = LookupError.from(e),
                        captchaUrl = (e as? MetadataException.Captcha)?.url,
                    )
                }
            }
        }
    }

    /** Fetches the results after the ones on screen, for when the list is scrolled to its end. */
    fun loadMore() {
        val search = pager ?: return
        if (!search.hasMore || loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            try {
                val more = search.next()
                // Sorted as a whole, so a volume on a later page joins the rest of its series. A
                // ranked list keeps its ranks.
                _uiState.update {
                    val all = it.results.orEmpty() + more
                    it.copy(results = if (browsed) all else all.sortedBySeries(), canLoadMore = search.hasMore)
                }
            } catch (_: MetadataException) {
                // What is on screen stays; scrolling back to the end asks again.
            } finally {
                _uiState.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        availabilityJob?.cancel()
        searched = null
        pager = null
    }

    internal companion object {
        /**
         * How each of the comic sources, given as (name, language), is shown: by name, and with its
         * language only where another has the same name, as sources of one site often differ only by it.
         */
        fun comicSourceLabels(sources: List<Pair<String, String>>): List<String> {
            val clashing = sources.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
            return sources.map { (name, lang) ->
                if (name in clashing && lang != "all" && lang != "other" && lang.isNotBlank()) {
                    "$name · ${lang.uppercase()}"
                } else {
                    name
                }
            }
        }

        private const val SEARCH_DEBOUNCE_MS = 500L
    }
}
