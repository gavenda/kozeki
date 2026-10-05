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
}
