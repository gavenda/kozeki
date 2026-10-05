package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.library.LibraryFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {

    private fun book(state: ReadingState, acquisition: Acquisition) =
        Book(id = "book", title = "The Dispossessed", state = state, acquisition = acquisition)

    private fun filtersFor(book: Book): Set<LibraryFilter> = LibraryFilter.entries.filter { it.matches(book) }.toSet()

    @Test
    fun `a wishlisted book is planned`() {
        assertEquals(
            setOf(LibraryFilter.PLANNED, LibraryFilter.WISHLIST, LibraryFilter.ALL),
            filtersFor(book(ReadingState.PLANNED, Acquisition.WISHLIST)),
        )
    }

    // The repository keeps wishlist entries Planned; a stale row must still not show up elsewhere.
    @Test
    fun `a wishlisted book is planned whatever state it carries`() {
        ReadingState.entries.forEach { state ->
            assertEquals(
                setOf(LibraryFilter.PLANNED, LibraryFilter.WISHLIST, LibraryFilter.ALL),
                filtersFor(book(state, Acquisition.WISHLIST)),
            )
        }
    }

    @Test
    fun `a downloaded EPUB is filed under its state but not Purchased`() {
        assertEquals(
            setOf(LibraryFilter.PLANNED, LibraryFilter.ALL),
            filtersFor(book(ReadingState.PLANNED, Acquisition.DOWNLOADED)),
        )
        assertEquals(
            setOf(LibraryFilter.READING, LibraryFilter.ALL),
            filtersFor(book(ReadingState.READING, Acquisition.DOWNLOADED)),
        )
        assertEquals(
            setOf(LibraryFilter.PAUSED, LibraryFilter.ALL),
            filtersFor(book(ReadingState.PAUSED, Acquisition.DOWNLOADED)),
        )
    }

    @Test
    fun `a purchased book is filed under its state and Purchased`() {
        assertEquals(
            setOf(LibraryFilter.PLANNED, LibraryFilter.PURCHASED, LibraryFilter.ALL),
            filtersFor(book(ReadingState.PLANNED, Acquisition.PURCHASED)),
        )
        assertEquals(
            setOf(LibraryFilter.COMPLETED, LibraryFilter.PURCHASED, LibraryFilter.ALL),
            filtersFor(book(ReadingState.COMPLETED, Acquisition.PURCHASED)),
        )
    }
}
