package dev.gavenda.kozeki.ui.library

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.files.IncomingEpubs
import dev.gavenda.kozeki.data.metadata.MatchService
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ImportResult
import dev.gavenda.kozeki.data.model.LEGACY_COMICS
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.comic.ComicChapterRepository
import dev.gavenda.kozeki.data.comic.ComicDownloads
import dev.gavenda.kozeki.data.comic.UpdateCheckProgress
import dev.gavenda.kozeki.data.model.Shelf
import dev.gavenda.kozeki.data.repository.LibraryRepository
import dev.gavenda.kozeki.data.repository.ShelfRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.ui.components.BookDisplay
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The reading states sort the books the user has by where they are with them. A book that is only
 * wanted is kept apart under its own filter, so Planning is what is already there to be read.
 * Purchased is every book with a purchase on record, with or without an EPUB; an EPUB alone does
 * not put a book there. Favorites cut across all of that, and so do the comics with chapters
 * downloaded to this device.
 */
enum class LibraryFilter(@param:StringRes val label: Int, val states: Set<ReadingState>?) {
    ALL(R.string.filter_all, null),
    FAVORITES(R.string.library_favorites, null),
    READING(R.string.state_reading, setOf(ReadingState.READING)),
    PLANNED(R.string.filter_planning, setOf(ReadingState.PLANNED)),
    COMPLETED(R.string.state_completed, setOf(ReadingState.COMPLETED)),
    WISHLIST(R.string.wishlist_tab, null),
    PURCHASED(R.string.purchased_tab, null),
    PAUSED(R.string.state_paused, setOf(ReadingState.PAUSED)),
    DROPPED(R.string.state_dropped, setOf(ReadingState.DROPPED)),
    DOWNLOADED(R.string.filter_downloaded, null),
    ;

    /** Whether the filter has a chip among [kind]'s: only comics are downloaded. */
    fun shownFor(kind: LibraryKind): Boolean = this != DOWNLOADED || kind != LibraryKind.BOOKS

    fun matches(book: Book): Boolean {
        val wished = book.acquisition == Acquisition.WISHLIST
        return when (this) {
            ALL -> true
            FAVORITES -> book.isFavorite
            WISHLIST -> wished
            PURCHASED -> book.acquisition == Acquisition.PURCHASED
            DOWNLOADED -> book.downloadedChapters > 0
            else -> !wished && states != null && book.state in states
        }
    }
}

/** What the library shows: the books, the comics or the shelves. */
enum class LibraryKind(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    /** Books with an EPUB, books on paper, and the ones only wanted. */
    BOOKS(R.string.library_kind_books, R.drawable.library_books_fill_48px),

    /** Comics, read online through a Mihon source. */
    COMICS(R.string.library_kind_comics, R.drawable.auto_stories_48px),

    /** Every shelf in turn, with the books and comics on it. */
    SHELVES(R.string.library_kind_shelves, R.drawable.shelves_fill_48px),
    ;

    fun matches(book: Book): Boolean = this == SHELVES || book.isComic == (this == COMICS)
}

/**
 * The order the books of a filter are shown in. Books that tie keep the order of [RECENT]. Each order
 * has a usual direction, [descending] or not, and can be turned the other way round; the books it has
 * nothing to go by for stay at the end either way.
 */
enum class LibrarySort(@param:StringRes val label: Int, val descending: Boolean) {
    /** Books with an EPUB first, most recently read at the top, then the ones still without a file. */
    RECENT(R.string.sort_recent, descending = true),
    TITLE(R.string.sort_title, descending = false),

    /** By the first author as their name is written, with the books that name nobody at the end. */
    AUTHOR(R.string.sort_author, descending = false),

    /** Newest first. */
    DATE_ADDED(R.string.sort_date_added, descending = true),

    /** The user's own rating, highest first, with the books not rated yet at the end. */
    RATING(R.string.sort_rating, descending = true),

    /**
     * What the book cost, highest first, with the books without a price at the end. Prices in different
     * currencies cannot be weighed against each other, so each currency is kept together, the one most
     * books were bought in first, whichever way the prices run.
     */
    PRICE(R.string.sort_price, descending = true),

    /**
     * By series name, and within a series by the book's number in it, the ones without a number after
     * the numbered ones and by title. Books in no series go at the end.
     */
    SERIES(R.string.sort_series, descending = false),
    ;

    /** Whether the books run from highest to lowest, once [reversed] is taken into account. */
    fun isDescending(reversed: Boolean): Boolean = descending != reversed

    /** [books], given in the order of [RECENT], in this order, or the other way round when [reversed]. */
    fun sorted(books: List<Book>, reversed: Boolean = false): List<Book> {
        if (this == RECENT && !reversed) return books
        val (placed, unplaced) = books.partition(::hasValue)
        val ordered = when (this) {
            RECENT -> placed.asReversed()
            TITLE -> placed.sortedWith(compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.flip(reversed))
            AUTHOR -> placed.sortedWith(
                compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.authors.first() }.flip(reversed),
            )
            DATE_ADDED -> placed.sortedWith(compareByDescending<Book> { it.addedAt }.flip(reversed))
            RATING -> placed.sortedWith(compareByDescending<Book> { it.rating }.flip(reversed))
            PRICE -> {
                val currencyCounts = placed.groupingBy { it.purchaseCurrency.orEmpty() }.eachCount()
                placed.sortedWith(
                    compareByDescending<Book> { currencyCounts.getValue(it.purchaseCurrency.orEmpty()) }
                        .thenBy { it.purchaseCurrency.orEmpty() }
                        .then(compareByDescending<Book> { it.purchasePriceMinor }.flip(reversed)),
                )
            }
            SERIES -> placed.sortedWith(
                compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.series.orEmpty() }.flip(reversed)
                    .thenBy { it.seriesNumber == null }
                    .then(compareBy<Book> { it.seriesNumber }.flip(reversed))
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
            )
        }
        return ordered + unplaced
    }

    /** Whether this order has anything to go by for [book]; the ones it has not go at the end. */
    private fun hasValue(book: Book): Boolean = when (this) {
        // Read at some point, or there to be read: the wishlist and books still to come stay at the end.
        RECENT -> book.isComic || book.hasFile || book.lastReadAt != null
        TITLE, DATE_ADDED -> true
        AUTHOR -> book.authors.isNotEmpty()
        RATING -> book.rating != null
        PRICE -> book.purchasePriceMinor != null
        SERIES -> book.series != null
    }

    private fun <T> Comparator<T>.flip(reversed: Boolean): Comparator<T> = if (reversed) reversed() else this
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

/** A run of books in the grid. [heading] is the filter they would be found under; null for no heading. */
data class LibrarySection(val heading: LibraryFilter?, val books: List<Book>)

/**
 * How [books], already filtered and sorted, are laid out: every book at once is grouped by reading
 * state, the ones being read first and the ones only wanted in a group of their own, while any
 * narrower filter is a single run.
 */
fun librarySections(filter: LibraryFilter, books: List<Book>): List<LibrarySection> = when {
    books.isEmpty() -> emptyList()
    filter != LibraryFilter.ALL -> listOf(LibrarySection(null, books))
    else -> GROUP_FILTERS.mapNotNull { group ->
        books.filter(group::matches).takeIf { it.isNotEmpty() }?.let { LibrarySection(group, it) }
    }
}

/** The books of one series under its name, or the books in no series when [name] is null. */
data class SeriesGroup(val name: String?, val sections: List<LibrarySection>) {
    val books: List<Book> get() = sections.flatMap { it.books }
}

/**
 * [books], already filtered and in [LibrarySort.SERIES], one series after another as the shelves are
 * set out, each laid out in the library's own groups. The books in no series come last, together.
 */
fun seriesGroups(filter: LibraryFilter, books: List<Book>): List<SeriesGroup> =
    books.groupBy { it.series?.lowercase() }.values.map { run ->
        SeriesGroup(run.first().series, librarySections(filter, run))
    }

/** Between them these take in every book exactly once. The wishlist follows what is planned to be read. */
private val GROUP_FILTERS = listOf(
    LibraryFilter.READING,
    LibraryFilter.PLANNED,
    LibraryFilter.WISHLIST,
    LibraryFilter.COMPLETED,
    LibraryFilter.PAUSED,
    LibraryFilter.DROPPED,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    /** Whether [sort] runs the other way round from its usual direction. */
    val sortReversed: Boolean = false,
    val display: BookDisplay = BookDisplay.COVERS,
    val kind: LibraryKind = LibraryKind.BOOKS,
    val sections: List<LibrarySection> = emptyList(),
    val counts: Map<LibraryFilter, Int> = emptyMap(),
    /** How many books are shown, whatever the filter. */
    val totalBooks: Int = 0,
    /** How many there are in the library altogether, which [totalBooks] is less of when one kind is shown. */
    val libraryBooks: Int? = null,
    val importing: Boolean = false,
    /** How far the check in the sheet has got, while it runs: see [updateCheckKind]. */
    val updateCheck: UpdateCheckProgress? = null,
    /** The last check got through everything it had to, rather than being cancelled. */
    val updateCheckFinished: Boolean = false,
    /** What [updateCheck] is going through: the comics' chapters, the books' details, or new books. */
    val updateCheckKind: UpdateCheckKind = UpdateCheckKind.CHAPTERS,
    val lastCurrency: String? = null,
    val purchaseLocations: List<String> = emptyList(),
    val selection: LibrarySelection = LibrarySelection(),
    /** Every shelf, by name. */
    val shelves: List<Shelf> = emptyList(),
    /** Each series with its books, when the books or comics are in [LibrarySort.SERIES]. */
    val seriesGroups: List<SeriesGroup> = emptyList(),
    /** The shelves with their books, when [kind] is the shelves. */
    val shelfGroups: List<ShelfGroup> = emptyList(),
    /** The shelf books are being picked for, if any. */
    val addingTo: Shelf? = null,
    /** The keys of the series and shelves folded away under their headers: see [groupKey]. */
    val collapsedGroups: Set<String> = emptySet(),
    /** The books of the library that are not on [addingTo]. */
    val shelfCandidates: List<Book> = emptyList(),
) {
    val inShelves: Boolean get() = kind == LibraryKind.SHELVES
    /** The books are set out a series at a time. The shelves keep to their own groups. */
    val bySeries: Boolean get() = !inShelves && sort == LibrarySort.SERIES
    val selecting: Boolean get() = !selection.isEmpty
    /** A check is running in the sheet. */
    val refreshing: Boolean get() = updateCheck != null
    /** Every book the filter shows, in the order shown, folded away or not. One on several shelves counts once. */
    val books: List<Book> get() = when {
        inShelves -> shelfGroups.flatMap { it.books }.distinctBy { it.id }
        bySeries -> seriesGroups.flatMap { it.books }
        else -> sections.flatMap { it.books }
    }

    /** The [books] not folded away under a header, which are the ones picking all of them takes. */
    val unfoldedBooks: List<Book> get() = when {
        inShelves -> shelfGroups.filterNot { isCollapsed(groupKey(it)) }.flatMap { it.books }.distinctBy { it.id }
        bySeries -> seriesGroups.filterNot { isCollapsed(groupKey(it)) }.flatMap { it.books }
        else -> books
    }

    /** What a shelf is kept as among the folded groups. */
    fun groupKey(group: ShelfGroup): String = "shelf/${group.shelf.id}"

    /** What a series is kept as among the folded groups: the books' and the comics' apart. */
    fun groupKey(group: SeriesGroup): String = "series/${kind.name}/${group.name?.lowercase().orEmpty()}"

    fun isCollapsed(key: String): Boolean = key in collapsedGroups

    val allSelected: Boolean get() = unfoldedBooks.all { it.id in selection.ids }

    val isLibraryEmpty: Boolean get() = !loading && (libraryBooks ?: totalBooks) == 0
    /** The shelves are shown and there are none yet, which has no filter to blame. */
    val isShelvesEmpty: Boolean get() = !loading && inShelves && shelves.isEmpty()
    val isFilterEmpty: Boolean get() =
        !loading && !isShelvesEmpty && if (inShelves) shelfGroups.isEmpty() else books.isEmpty()
}

/** The shelves, what is on each, and the one books are being picked for. */
private class ShelfData(val shelves: List<Shelf>, val memberships: Map<String, Set<String>>, val adding: String?)

sealed interface LibraryEvent {
    data class ImportFinished(val results: List<ImportResult>) : LibraryEvent
}

/**
 * The book to open after importing the one file that was opened with the app, which [results] is
 * the outcome of. That is the book the file is in the library as, whether it was new, completed
 * an entry that was waiting for it, or was there already.
 */
internal fun bookToOpen(results: List<ImportResult>): String? = when (val result = results.singleOrNull()) {
    is ImportResult.Imported -> result.bookId
    is ImportResult.Attached -> result.bookId
    is ImportResult.AlreadyInLibrary -> result.bookId
    is ImportResult.Failed, null -> null
}

class LibraryViewModel(
    private val library: LibraryRepository,
    private val shelves: ShelfRepository,
    private val settings: SettingsRepository,
    private val matchService: MatchService,
    private val incoming: IncomingEpubs,
    private val chapters: ComicChapterRepository,
    private val downloads: ComicDownloads,
    private val scheduleMatching: () -> Unit,
    private val cancelMatching: () -> Unit = {},
) : ViewModel() {

    private val chosenFilter = MutableStateFlow(LibraryFilter.ALL)
    private val addingToShelf = MutableStateFlow<String?>(null)

    // A count, as an EPUB from another app can arrive while the files of an earlier import are still coming in.
    private val importsRunning = MutableStateFlow(0)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val updateCheck = MutableStateFlow<UpdateCheckProgress?>(null)
    private val updateCheckFinished = MutableStateFlow(false)
    private val updateCheckKind = MutableStateFlow(UpdateCheckKind.CHAPTERS)
    private var updateJob: Job? = null

    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = eventChannel.receiveAsFlow()

    // Books with an EPUB come first, most recently read at the top, then the ones still without a file.
    private val books = combine(
        library.observeLibrary(),
        library.observeWithoutFile(),
        downloads.counts,
    ) { withFile, without, downloaded ->
        (withFile + without).map { book ->
            downloaded[book.id]?.let { book.copy(downloadedChapters = it) } ?: book
        }
    }

    private val sort = settings.librarySort.map { name ->
        LibrarySort.entries.firstOrNull { it.name == name } ?: LibrarySort.RECENT
    }.combine(settings.librarySortReversed, ::Pair)

    private val display = settings.libraryDisplay.map { name ->
        BookDisplay.named(name, BookDisplay.COVERS)
    }

    private val kind = settings.libraryKind.map { name ->
        LibraryKind.entries.firstOrNull { it.name == name }
            ?: LibraryKind.COMICS.takeIf { name == LEGACY_COMICS }
            ?: LibraryKind.BOOKS
    }

    private data class ViewOptions(
        val filter: LibraryFilter,
        val sort: Pair<LibrarySort, Boolean>,
        val display: BookDisplay,
        val kind: LibraryKind,
    )

    private val view = combine(chosenFilter, sort, display, kind, ::ViewOptions)
        .combine(settings.collapsedLibraryGroups, ::Pair)

    init {
        // A lookup of imported books shows in the same sheet, unless a refresh started here has it.
        viewModelScope.launch {
            matchService.run.collect { run ->
                if (updateJob?.isActive == true) return@collect
                if (run.progress != null) updateCheckKind.value = UpdateCheckKind.LOOKUP
                if (updateCheckKind.value != UpdateCheckKind.LOOKUP) return@collect
                updateCheck.value = run.progress
                updateCheckFinished.value = run.finished
            }
        }
    }

    private val busy = combine(
        importsRunning,
        updateCheck,
        updateCheckFinished,
        updateCheckKind,
    ) { imports, check, finished, checkKind ->
        Busy(imports > 0, check, finished, checkKind)
    }

    private class Busy(
        val importing: Boolean,
        val checking: UpdateCheckProgress?,
        val checkFinished: Boolean,
        val checkKind: UpdateCheckKind,
    )

    private val purchaseDefaults = combine(settings.lastCurrency, library.observePurchaseLocations(), ::Pair)

    // A shelf that was deleted while books were being picked for it is simply not found.
    private val shelfData: Flow<ShelfData> =
        combine(shelves.observeShelves(), shelves.observeMemberships(), addingToShelf, ::ShelfData)

    private val extras = combine(purchaseDefaults, shelfData, ::Pair)

    val uiState: StateFlow<LibraryUiState> =
        combine(books, view, busy, selectedIds, extras) { allBooks, (view, collapsed), busy, selected, extras ->
            val (filter, display) = view.filter to view.display
            val (sort, sortReversed) = view.sort
            val (defaults, shelfData) = extras
            val inShelves = view.kind == LibraryKind.SHELVES
            val bySeries = !inShelves && sort == LibrarySort.SERIES
            val ofKind = allBooks.filter(view.kind::matches)
            // The shelves show the books that are on one, however many.
            val shelved = if (inShelves) shelvedIds(shelfData.memberships) else null
            val books = if (shelved == null) ofKind else ofKind.filter { it.id in shelved }
            val (currency, locations) = defaults
            val counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) }
            val shown = sort.sorted(books.filter(filter::matches), sortReversed)
            LibraryUiState(
                loading = false,
                filter = filter,
                sort = sort,
                sortReversed = sortReversed,
                display = display,
                kind = view.kind,
                sections = if (inShelves || bySeries) emptyList() else librarySections(filter, shown),
                seriesGroups = if (bySeries) seriesGroups(filter, shown) else emptyList(),
                counts = counts,
                totalBooks = books.size,
                libraryBooks = allBooks.size,
                importing = busy.importing,
                updateCheck = busy.checking,
                updateCheckFinished = busy.checkFinished,
                updateCheckKind = busy.checkKind,
                lastCurrency = currency,
                purchaseLocations = locations,
                // Only what is on screen: a book that left the filter is no longer picked.
                selection = LibrarySelection(shown.filter { it.id in selected }),
                shelves = shelfData.shelves,
                shelfGroups = if (inShelves) shelfGroups(shelfData.shelves, shelfData.memberships, shown, filter) else emptyList(),
                addingTo = shelfData.shelves.firstOrNull { it.id == shelfData.adding },
                collapsedGroups = collapsed,
                shelfCandidates = shelfData.adding?.let { id ->
                    val onShelf = shelfData.memberships[id].orEmpty()
                    allBooks.filter { it.id !in onShelf }.sortedBy { b -> b.title.lowercase() }
                } ?: emptyList(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Asks the sources of the comics being followed for new chapters, for a pull to refresh. */
    fun checkForUpdates() = runCheck(UpdateCheckKind.CHAPTERS) { onProgress ->
        chapters.checkForUpdates(pauseMs = 500, onProgress = onProgress)
    }

    /**
     * Asks the metadata source of every linked book for its details again, putting them in place of
     * what is kept. The library asks first, as it replaces whatever was changed by hand.
     */
    fun refreshMetadata() = runCheck(UpdateCheckKind.METADATA) { onProgress ->
        matchService.refreshAll(onProgress = onProgress)
    }

    /** Runs one check at a time, its progress shown in the sheet. A second while one runs is ignored. */
    private fun runCheck(kind: UpdateCheckKind, check: suspend (onProgress: (UpdateCheckProgress) -> Unit) -> Unit) {
        if (updateJob?.isActive == true) return
        updateCheckKind.value = kind
        updateCheck.value = UpdateCheckProgress(checked = 0, total = 0, current = null)
        updateCheckFinished.value = false
        updateJob = viewModelScope.launch {
            try {
                check { updateCheck.value = it }
                // Not reached when cancelled.
                updateCheckFinished.value = true
            } finally {
                updateCheck.value = null
            }
        }
    }

    /** Stops a check part way. What was found or refreshed up to then is kept. */
    fun cancelUpdateCheck() {
        if (uiState.value.updateCheckKind == UpdateCheckKind.LOOKUP) cancelMatching() else updateJob?.cancel()
    }

    fun selectFilter(filter: LibraryFilter) {
        chosenFilter.value = filter
        clearSelection()
    }

    /** Makes a shelf called [name]. */
    fun createShelf(name: String) {
        viewModelScope.launch { shelves.create(name) }
    }

    fun renameShelf(shelfId: String, name: String) {
        viewModelScope.launch { shelves.rename(shelfId, name) }
    }

    fun deleteShelf(shelfId: String) {
        viewModelScope.launch { shelves.delete(shelfId) }
    }

    /** For each shelf, how many of the picked books are on it. */
    suspend fun shelvesOfSelection(): Map<String, Int> =
        shelves.membershipCounts(uiState.value.selection.books.map { it.id })

    /** Puts the picked books on the shelves in [change] to add and takes them off the ones to remove. */
    fun changeShelves(change: ShelfChange) = withSelection { selection ->
        shelves.apply(selection.books.map { it.id }, change.add, change.remove, change.create)
    }

    /** Starts picking books to put on the shelf [shelfId], or stops with null. */
    fun pickBooksFor(shelfId: String?) {
        addingToShelf.value = shelfId
    }

    /** Puts the books [bookIds] on the shelf [shelfId]. */
    fun addToShelf(shelfId: String, bookIds: Collection<String>) {
        viewModelScope.launch { shelves.addBooks(shelfId, bookIds) }
    }

    /** Puts the library in [sort], or turns it the other way round when it is already in it. */
    fun selectSort(sort: LibrarySort) {
        val current = uiState.value
        val reversed = if (sort == current.sort) !current.sortReversed else false
        viewModelScope.launch { settings.setLibrarySort(sort.name, reversed) }
    }

    /** Shows the books, the comics or the shelves. */
    fun selectKind(kind: LibraryKind) {
        if (kind == uiState.value.kind) return
        viewModelScope.launch { settings.setLibraryKind(kind.name) }
        if (!chosenFilter.value.shownFor(kind)) chosenFilter.value = LibraryFilter.ALL
        // What was picked among the other kind's books is not on screen any more.
        clearSelection()
    }

    fun selectDisplay(display: BookDisplay) {
        viewModelScope.launch { settings.setLibraryDisplay(display.name) }
    }

    /** Folds a series or shelf away under its header, or opens it again, by its [LibraryUiState.groupKey]. */
    fun toggleGroup(key: String) {
        val collapsed = !uiState.value.isCollapsed(key)
        viewModelScope.launch { settings.setLibraryGroupCollapsed(key, collapsed) }
    }

    fun toggleSelection(book: Book) {
        selectedIds.update { if (book.id in it) it - book.id else it + book.id }
    }

    fun selectAll() {
        val state = uiState.value
        selectedIds.value = state.unfoldedBooks.mapTo(HashSet()) { it.id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    /**
     * Takes in the EPUBs other apps hand over, for as long as the caller keeps this running. That
     * is the library on screen, where an import shows. They are not simply watched for here,
     * since a library further down the back stack has a view model of its own.
     */
    suspend fun importIncoming() {
        incoming.waiting.collect {
            while (true) {
                val request = incoming.take() ?: break
                import(request.uris, open = request.open)
            }
        }
    }

    /** @param open Whether the book is to be opened for reading once its file is in. */
    fun import(uris: List<Uri>, open: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importsRunning.update { it + 1 }
            val results = uris.map { uri ->
                try {
                    library.importEpub(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ImportResult.Failed(ImportResult.Failed.Reason.UNREADABLE, null)
                }
            }
            importsRunning.update { it - 1 }

            val added = results.filter { it is ImportResult.Imported || it is ImportResult.Attached }
            if (added.isNotEmpty()) {
                scheduleMatching()
                // A fresh import is Planned; make sure it is visible rather than filtered away.
                chosenFilter.value = LibraryFilter.ALL
            }
            val toOpen = if (open) bookToOpen(results) else null
            // A book that opens needs no message that it is in. Anything else gets one, a failure with its reason.
            if (toOpen != null) incoming.open(toOpen) else eventChannel.send(LibraryEvent.ImportFinished(results))
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
    fun markPurchased(
        priceMinor: Long?,
        currency: String?,
        purchasedOn: LocalDate,
        location: String?,
        memo: String?,
    ) =
        withSelection { selection ->
            val books = selection.books.singleOrNull()?.let(::listOf) ?: selection.notPurchased
            books.forEach {
                library.setPurchase(it.id, Acquisition.PURCHASED, priceMinor, currency, purchasedOn, location, memo)
            }
            if (priceMinor != null && currency != null) settings.setLastCurrency(currency)
        }

    /** Back to the wishlist, or to a plain download when the book has its EPUB. */
    fun unmarkPurchased() = withSelection { selection ->
        selection.purchased.forEach { book ->
            val unbought = if (book.inLibrary) Acquisition.DOWNLOADED else Acquisition.WISHLIST
            library.setPurchase(book.id, unbought, null, null, null, null, null)
        }
    }

    fun delete() = withSelection { selection ->
        selection.books.forEach { library.deleteBook(it.id) }
    }
}
