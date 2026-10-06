package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.library.LibraryFilter
import dev.gavenda.kozeki.ui.library.LibrarySection
import dev.gavenda.kozeki.ui.library.librarySections
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

    @Test
    fun `a favorite is listed under favorites whatever its state`() {
        ReadingState.entries.forEach { state ->
            val favorite = book(state, Acquisition.PURCHASED).copy(isFavorite = true)
            assertEquals(true, LibraryFilter.FAVORITES.matches(favorite))
            assertEquals(false, LibraryFilter.FAVORITES.matches(favorite.copy(isFavorite = false)))
        }
    }

    @Test
    fun `every book at once is grouped by state with reading first`() {
        val books = listOf(
            book(ReadingState.DROPPED, Acquisition.PURCHASED).copy(id = "dropped"),
            book(ReadingState.PLANNED, Acquisition.PURCHASED).copy(id = "planned", isFavorite = true),
            book(ReadingState.READING, Acquisition.PURCHASED).copy(id = "reading"),
            book(ReadingState.PLANNED, Acquisition.WISHLIST).copy(id = "wished"),
        )
        assertEquals(
            listOf(
                LibraryFilter.READING to listOf("reading"),
                LibraryFilter.PLANNED to listOf("planned", "wished"),
                LibraryFilter.DROPPED to listOf("dropped"),
            ),
            librarySections(LibraryFilter.ALL, books).map { section -> section.heading to section.books.map { it.id } },
        )
    }

    @Test
    fun `a narrower filter is one run without a heading`() {
        val books = listOf(book(ReadingState.PLANNED, Acquisition.PURCHASED), book(ReadingState.READING, Acquisition.PURCHASED))
        assertEquals(listOf(LibrarySection(null, books)), librarySections(LibraryFilter.FAVORITES, books))
        assertEquals(emptyList<LibrarySection>(), librarySections(LibraryFilter.ALL, emptyList()))
    }
}
