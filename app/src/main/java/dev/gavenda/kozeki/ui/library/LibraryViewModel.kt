package dev.gavenda.kozeki.ui.library

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ImportResult
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The reading states sort the books by where the user is with them. A book that is only wanted is
 * always Planned, so it is listed there as well as under its own filter. Purchased is every book
 * with a purchase on record, with or without an EPUB; an EPUB alone does not put a book there.
 */
enum class LibraryFilter(@param:StringRes val label: Int, val states: Set<ReadingState>?) {
    ALL(R.string.filter_all, null),
    READING(R.string.state_reading, setOf(ReadingState.READING)),
    PLANNED(R.string.filter_planning, setOf(ReadingState.PLANNED)),
    COMPLETED(R.string.state_completed, setOf(ReadingState.COMPLETED)),
    WISHLIST(R.string.wishlist_tab, null),
    PURCHASED(R.string.purchased_tab, null),
    PAUSED(R.string.state_paused, setOf(ReadingState.PAUSED)),
    DROPPED(R.string.state_dropped, setOf(ReadingState.DROPPED)),
    ;

    fun matches(book: Book): Boolean {
        val wished = book.acquisition == Acquisition.WISHLIST
        return when (this) {
            ALL -> true
            WISHLIST -> wished
            PURCHASED -> book.acquisition == Acquisition.PURCHASED
            PLANNED -> wished || book.state == ReadingState.PLANNED
            else -> !wished && states != null && book.state in states
        }
    }
}

/** The order the books of a filter are shown in. Books that tie keep the order of [RECENT]. */
enum class LibrarySort(@param:StringRes val label: Int) {
    /** Books with an EPUB first, most recently read at the top, then the ones still without a file. */
    RECENT(R.string.sort_recent),
    TITLE(R.string.sort_title),

    /** Newest first. */
    DATE_ADDED(R.string.sort_date_added),

    /** The user's own rating, highest first, with the books not rated yet at the end. */
    RATING(R.string.sort_rating),
    ;

    /** [books], given in the order of [RECENT], in this order. */
    fun sorted(books: List<Book>): List<Book> = when (this) {
        RECENT -> books
        TITLE -> books.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        DATE_ADDED -> books.sortedByDescending { it.addedAt }
        RATING -> books.sortedByDescending { it.rating ?: -1f }
    }
}

/** The books picked in the grid, and which of them each action applies to. */
data class LibrarySelection(val books: List<Book> = emptyList()) {
    val ids: Set<String> = books.mapTo(HashSet()) { it.id }
    val size: Int get() = books.size
    val isEmpty: Boolean get() = books.isEmpty()

    /** A book that is only wanted cannot be a favorite, so it sits that change out. */
    val favoritable: List<Book> = books.filter { it.canFavorite }
    val allFavorite: Boolean get() = favoritable.isNotEmpty() && favoritable.all { it.isFavorite }

    /** Likewise for the reading state: a wanted book stays Planned. */
    val stateChangeable: List<Book> = books.filter { it.canChangeState }

    /** The state every one of those is in, when they agree. */
    val sharedState: ReadingState? get() = stateChangeable.map { it.state }.distinct().singleOrNull()

    val purchased: List<Book> = books.filter { it.acquisition == Acquisition.PURCHASED }
    val notPurchased: List<Book> = books.filter { it.acquisition != Acquisition.PURCHASED }
}

data class LibraryUiState(
    val loading: Boolean = true,
    val filter: LibraryFilter = LibraryFilter.READING,
    val sort: LibrarySort = LibrarySort.RECENT,
    val favorites: List<Book> = emptyList(),
    val others: List<Book> = emptyList(),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    val totalBooks: Int = 0,
    val importing: Boolean = false,
    val lastCurrency: String? = null,
    val purchaseLocations: List<String> = emptyList(),
    val selection: LibrarySelection = LibrarySelection(),
) {
    val selecting: Boolean get() = !selection.isEmpty
    val allSelected: Boolean get() = selection.size == favorites.size + others.size

    val isLibraryEmpty: Boolean get() = !loading && totalBooks == 0
    val isFilterEmpty: Boolean get() = !loading && favorites.isEmpty() && others.isEmpty()
}

sealed interface LibraryEvent {
    data class ImportFinished(val results: List<ImportResult>) : LibraryEvent
}

class LibraryViewModel(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val scheduleMatching: () -> Unit,
) : ViewModel() {

    // Null until the user picks one, so the first view can avoid opening on an empty filter.
    private val chosenFilter = MutableStateFlow<LibraryFilter?>(null)
    private val importing = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = eventChannel.receiveAsFlow()

    // Books with an EPUB come first, most recently read at the top, then the ones still without a file.
    private val books = combine(library.observeLibrary(), library.observeWithoutFile()) { withFile, without ->
        withFile + without
    }

    private val sort = settings.librarySort.map { name ->
        LibrarySort.entries.firstOrNull { it.name == name } ?: LibrarySort.RECENT
    }

    private val view = combine(chosenFilter, sort, ::Pair)

    private val purchaseDefaults = combine(settings.lastCurrency, library.observePurchaseLocations(), ::Pair)

    val uiState: StateFlow<LibraryUiState> =
        combine(books, view, importing, selectedIds, purchaseDefaults) { books, view, busy, selected, defaults ->
            val (chosen, sort) = view
            val (currency, locations) = defaults
            val counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) }
            val filter = chosen ?: when {
                counts.getValue(LibraryFilter.READING) > 0 -> LibraryFilter.READING
                else -> LibraryFilter.ALL
            }
            val (favorites, others) = sort.sorted(books.filter(filter::matches)).partition { it.isFavorite }
            LibraryUiState(
                loading = false,
                filter = filter,
                sort = sort,
                favorites = favorites,
                others = others,
                counts = counts,
                totalBooks = books.size,
                importing = busy,
                lastCurrency = currency,
                purchaseLocations = locations,
                // Only what is on screen: a book that left the filter is no longer picked.
                selection = LibrarySelection((favorites + others).filter { it.id in selected }),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectFilter(filter: LibraryFilter) {
        chosenFilter.value = filter
        clearSelection()
    }

    fun selectSort(sort: LibrarySort) {
        viewModelScope.launch { settings.setLibrarySort(sort.name) }
    }

    fun toggleSelection(book: Book) {
        selectedIds.update { if (book.id in it) it - book.id else it + book.id }
    }

    fun selectAll() {
        val state = uiState.value
        selectedIds.value = (state.favorites + state.others).mapTo(HashSet()) { it.id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            val results = uris.map { uri ->
                try {
                    library.importEpub(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ImportResult.Failed(ImportResult.Failed.Reason.UNREADABLE, null)
                }
            }
            importing.value = false

            val added = results.filter { it is ImportResult.Imported || it is ImportResult.Attached }
            if (added.isNotEmpty()) {
                scheduleMatching()
                // A fresh import is Planned; make sure it is visible rather than filtered away.
                chosenFilter.value = LibraryFilter.ALL
            }
            eventChannel.send(LibraryEvent.ImportFinished(results))
        }
    }

    /** Runs [action] on the picked books and ends the selection, as the action is what it was for. */
    private fun withSelection(action: suspend (LibrarySelection) -> Unit) {
        val selection = uiState.value.selection
        clearSelection()
        viewModelScope.launch { action(selection) }
    }

    fun setFavorite(favorite: Boolean) = withSelection { selection ->
        selection.favoritable.forEach { library.setFavorite(it.id, favorite) }
    }

    fun setState(state: ReadingState) = withSelection { selection ->
        selection.stateChangeable.forEach { library.setState(it.id, state) }
    }

    /**
     * A single book takes the details as given, which also edits a purchase already on record.
     * With several picked, only those without a purchase get one, so no recorded price is lost.
     */
    fun markPurchased(priceMinor: Long?, currency: String?, purchasedOn: LocalDate, location: String?) =
        withSelection { selection ->
            val books = selection.books.singleOrNull()?.let(::listOf) ?: selection.notPurchased
            books.forEach {
                library.setPurchase(it.id, Acquisition.PURCHASED, priceMinor, currency, purchasedOn, location)
            }
            if (priceMinor != null && currency != null) settings.setLastCurrency(currency)
        }

    /** Back to the wishlist, or to a plain download when the book has its EPUB. */
    fun unmarkPurchased() = withSelection { selection ->
        selection.purchased.forEach { book ->
            val unbought = if (book.inLibrary) Acquisition.DOWNLOADED else Acquisition.WISHLIST
            library.setPurchase(book.id, unbought, null, null, null, null)
        }
    }

    fun delete() = withSelection { selection ->
        selection.books.forEach { library.deleteBook(it.id) }
    }
}
