package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.library.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {

    // In the order the library hands them over: most recently read first.
    private val books = listOf(
        Book(id = "dune", title = "dune", rating = 4f, addedAt = 20),
        Book(id = "ubik", title = "Ubik", addedAt = 40),
        Book(id = "emma", title = "Emma", rating = 4.5f, addedAt = 10),
        Book(id = "solaris", title = "Solaris", rating = 4f, addedAt = 30),
    )

    private fun ids(sort: LibrarySort): List<String> = sort.sorted(books).map { it.id }

    @Test
    fun `recent keeps the order of the library`() {
        assertEquals(listOf("dune", "ubik", "emma", "solaris"), ids(LibrarySort.RECENT))
    }

    @Test
    fun `title ignores case`() {
        assertEquals(listOf("dune", "emma", "solaris", "ubik"), ids(LibrarySort.TITLE))
    }

    @Test
    fun `date added puts the newest first`() {
        assertEquals(listOf("ubik", "solaris", "dune", "emma"), ids(LibrarySort.DATE_ADDED))
    }

    @Test
    fun `rating puts the highest first, ties as they were and the unrated last`() {
        assertEquals(listOf("emma", "dune", "solaris", "ubik"), ids(LibrarySort.RATING))
    }

    @Test
    fun `price puts the dearest first, keeps each currency together and the unpriced last`() {
        val shelf = listOf(
            Book(id = "free", title = "Free"),
            Book(id = "yen", title = "Yen", purchasePriceMinor = 1500, purchaseCurrency = "JPY"),
            Book(id = "cheap", title = "Cheap", purchasePriceMinor = 499, purchaseCurrency = "USD"),
            Book(id = "dear", title = "Dear", purchasePriceMinor = 2999, purchaseCurrency = "USD"),
            Book(id = "mid", title = "Mid", purchasePriceMinor = 1299, purchaseCurrency = "USD"),
        )
        assertEquals(
            listOf("dear", "mid", "cheap", "yen", "free"),
            LibrarySort.PRICE.sorted(shelf).map { it.id },
        )
        assertEquals(
            listOf("cheap", "mid", "dear", "yen", "free"),
            LibrarySort.PRICE.sorted(shelf, reversed = true).map { it.id },
        )
    }

    @Test
    fun `series groups by name, runs by number, puts the unnumbered after and the unseried last`() {
        val shelf = listOf(
            Book(id = "loose", title = "Loose"),
            Book(id = "e3", title = "Abaddon's Gate", series = "The Expanse", seriesNumber = 3.0),
            Book(id = "d1", title = "Dune", series = "dune", seriesNumber = 1.0),
            Book(id = "e1", title = "Leviathan Wakes", series = "The Expanse", seriesNumber = 1.0),
            Book(id = "eNovella", title = "Gods of Risk", series = "The Expanse"),
            Book(id = "e2_5", title = "The Churn", series = "The Expanse", seriesNumber = 2.5),
        )
        assertEquals(
            listOf("d1", "e1", "e2_5", "e3", "eNovella", "loose"),
            LibrarySort.SERIES.sorted(shelf).map { it.id },
        )
        assertEquals(
            listOf("e3", "e2_5", "e1", "eNovella", "d1", "loose"),
            LibrarySort.SERIES.sorted(shelf, reversed = true).map { it.id },
        )
    }

    @Test
    fun `reversed turns the order round and keeps the books without a value at the end`() {
        assertEquals(listOf("ubik", "solaris", "emma", "dune"), LibrarySort.TITLE.sorted(books, reversed = true).map { it.id })
        assertEquals(listOf("emma", "dune", "solaris", "ubik"), LibrarySort.DATE_ADDED.sorted(books, reversed = true).map { it.id })
        assertEquals(listOf("dune", "solaris", "emma", "ubik"), LibrarySort.RATING.sorted(books, reversed = true).map { it.id })
    }

    @Test
    fun `the arrow follows the usual direction and the reversal`() {
        assertEquals(true, LibrarySort.DATE_ADDED.isDescending(reversed = false))
        assertEquals(false, LibrarySort.DATE_ADDED.isDescending(reversed = true))
        assertEquals(false, LibrarySort.TITLE.isDescending(reversed = false))
        assertEquals(true, LibrarySort.TITLE.isDescending(reversed = true))
    }
}
