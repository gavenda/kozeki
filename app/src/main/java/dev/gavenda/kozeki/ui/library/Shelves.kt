package dev.gavenda.kozeki.ui.library

import androidx.compose.ui.state.ToggleableState
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.Shelf

/** A shelf and its books, laid out in the library's own groups. [sections] is empty for a shelf with nothing on it. */
data class ShelfGroup(val shelf: Shelf, val sections: List<LibrarySection>) {
    val books: List<Book> get() = sections.flatMap { it.books }
}

/**
 * What [books] cost, one sum for each currency, the one most of them were bought in first. A book
 * without a currency counts in the default one, as its price is shown in it. Empty when none has a price.
 */
fun priceTotals(books: List<Book>): List<Pair<String?, Long>> = books
    .filter { it.purchasePriceMinor != null }
    .groupBy { it.purchaseCurrency }
    .entries
    .sortedByDescending { it.value.size }
    .map { (currency, priced) -> currency to priced.sumOf { it.purchasePriceMinor ?: 0L } }

/** The average of the ratings given to [books], or null when none is rated. */
fun averageRating(books: List<Book>): Float? =
    books.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

/** The ids of every book that is on some shelf, given what [memberships] says of each shelf. */
fun shelvedIds(memberships: Map<String, Set<String>>): Set<String> = memberships.values.flatMapTo(HashSet()) { it }

/**
 * The shelves, one after another, each with its books from [books] (already filtered and sorted) set
 * out as the library sets out its own. A book on two shelves is under both. A shelf the [filter] leaves
 * nothing of is left out, except that every shelf shows under [LibraryFilter.ALL], empty or not.
 */
fun shelfGroups(
    shelves: List<Shelf>,
    memberships: Map<String, Set<String>>,
    books: List<Book>,
    filter: LibraryFilter,
): List<ShelfGroup> = shelves.mapNotNull { shelf ->
    val onShelf = memberships[shelf.id].orEmpty()
    val sections = librarySections(filter, books.filter { it.id in onShelf })
    if (sections.isEmpty() && filter != LibraryFilter.ALL) null else ShelfGroup(shelf, sections)
}

/** The books of [books] that are not on the shelf, for picking more of them to put on it. */
fun booksOffShelf(books: List<Book>, onShelf: Set<String>): List<Book> = books.filter { it.id !in onShelf }

/**
 * Whether a shelf holds all, some or none of [picked] books, given [onShelf] of them that are on it.
 * A shelf with some of them is the one state a tap does not leave alone.
 */
fun shelfState(onShelf: Int, picked: Int): ToggleableState = when {
    onShelf <= 0 -> ToggleableState.Off
    onShelf >= picked -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

/** What a tap on a shelf's checkbox makes of it: a shelf with some of the books gets all of them. */
fun ToggleableState.tapped(): ToggleableState =
    if (this == ToggleableState.On) ToggleableState.Off else ToggleableState.On

/** The shelves to put the books on, and the ones to take them off. */
data class ShelfChange(
    val add: Set<String>,
    val remove: Set<String>,
    /** Names of shelves to make, with the books put on each. */
    val create: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = add.isEmpty() && remove.isEmpty() && create.isEmpty()
}

/**
 * What changing the shelves from [initial] to [chosen] comes to. A shelf still with some of the
 * books is left as it was, and one missing from [chosen] was not touched.
 */
fun shelfChange(initial: Map<String, ToggleableState>, chosen: Map<String, ToggleableState>): ShelfChange {
    val add = HashSet<String>()
    val remove = HashSet<String>()
    chosen.forEach { (id, now) ->
        val before = initial[id] ?: ToggleableState.Off
        when {
            now == before -> Unit
            now == ToggleableState.On -> add += id
            now == ToggleableState.Off -> remove += id
        }
    }
    return ShelfChange(add, remove)
}
