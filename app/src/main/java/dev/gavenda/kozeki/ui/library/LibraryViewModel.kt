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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    PAUSED(R.string.state_paused, setOf(ReadingState.PAUSED)),
    DROPPED(R.string.state_dropped, setOf(ReadingState.DROPPED)),
    COMPLETED(R.string.state_completed, setOf(ReadingState.COMPLETED)),
    WISHLIST(R.string.wishlist_tab, null),
    PURCHASED(R.string.purchased_tab, null),
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

data class LibraryUiState(
    val loading: Boolean = true,
    val filter: LibraryFilter = LibraryFilter.READING,
    val favorites: List<Book> = emptyList(),
    val others: List<Book> = emptyList(),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    val totalBooks: Int = 0,
    val importing: Boolean = false,
    val lastCurrency: String? = null,
    val purchaseLocations: List<String> = emptyList(),
) {
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

    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = eventChannel.receiveAsFlow()

    // Books with an EPUB come first, most recently read at the top, then the ones still without a file.
    private val books = combine(library.observeLibrary(), library.observeWithoutFile()) { withFile, without ->
        withFile + without
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            books,
            chosenFilter,
            importing,
            settings.lastCurrency,
            library.observePurchaseLocations(),
        ) { books, chosen, busy, currency, locations ->
            val counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) }
            val filter = chosen ?: when {
                counts.getValue(LibraryFilter.READING) > 0 -> LibraryFilter.READING
                else -> LibraryFilter.ALL
            }
            val (favorites, others) = books.filter(filter::matches).partition { it.isFavorite }
            LibraryUiState(
                loading = false,
                filter = filter,
                favorites = favorites,
                others = others,
                counts = counts,
                totalBooks = books.size,
                importing = busy,
                lastCurrency = currency,
                purchaseLocations = locations,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectFilter(filter: LibraryFilter) {
        chosenFilter.value = filter
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

    fun toggleFavorite(book: Book) {
        viewModelScope.launch { library.setFavorite(book.id, !book.isFavorite) }
    }

    fun setState(book: Book, state: ReadingState) {
        viewModelScope.launch { library.setState(book.id, state) }
    }

    fun markPurchased(book: Book, priceMinor: Long?, currency: String?, purchasedOn: LocalDate, location: String?) {
        viewModelScope.launch {
            library.setPurchase(book.id, Acquisition.PURCHASED, priceMinor, currency, purchasedOn, location)
            if (priceMinor != null && currency != null) settings.setLastCurrency(currency)
        }
    }

    /** Back to the wishlist, or to a plain download when the book has its EPUB. */
    fun unmarkPurchased(book: Book) {
        val unbought = if (book.inLibrary) Acquisition.DOWNLOADED else Acquisition.WISHLIST
        viewModelScope.launch { library.setPurchase(book.id, unbought, null, null, null, null) }
    }

    fun delete(book: Book) {
        viewModelScope.launch { library.deleteBook(book.id) }
    }
}
