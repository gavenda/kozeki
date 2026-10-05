package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ReadingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookProgressTest {

    private fun book(
        physicalPage: Int? = null,
        physicalPageCount: Int? = null,
        progression: Double = 0.0,
        inLibrary: Boolean = false,
        state: ReadingState = ReadingState.READING,
    ) = Book(
        id = "book",
        title = "Children of Time",
        state = state,
        acquisition = Acquisition.PURCHASED,
        inLibrary = inLibrary,
        progression = progression,
        physicalPage = physicalPage,
        physicalPageCount = physicalPageCount,
    )

    @Test
    fun `physical progress is the page over the page count`() {
        assertEquals(0.25, book(physicalPage = 150, physicalPageCount = 600).physicalProgression!!, 1e-9)
    }

    @Test
    fun `physical progress is unknown without a page or a page count`() {
        assertNull(book(physicalPageCount = 600).physicalProgression)
        assertNull(book(physicalPage = 150).physicalProgression)
    }

    @Test
    fun `physical progress does not move the EPUB's`() {
        val both = book(physicalPage = 300, physicalPageCount = 600, progression = 0.1, inLibrary = true)
        assertEquals(0.1, both.progression, 1e-9)
        assertEquals(0.5, both.physicalProgression!!, 1e-9)
    }

    @Test
    fun `the overall figure follows whichever copy is further along`() {
        assertEquals(0.5, book(300, 600, progression = 0.1, inLibrary = true).overallProgression, 1e-9)
        assertEquals(0.8, book(300, 600, progression = 0.8, inLibrary = true).overallProgression, 1e-9)
    }

    // A book without an EPUB may carry a stale EPUB figure from being completed and reopened.
    @Test
    fun `a book without an EPUB only counts its physical copy`() {
        assertEquals(0.5, book(300, 600, progression = 1.0).overallProgression, 1e-9)
        assertEquals(0.0, book(progression = 1.0).overallProgression, 1e-9)
    }

    @Test
    fun `a completed book is fully read`() {
        assertEquals(1.0, book(300, 600, state = ReadingState.COMPLETED).overallProgression, 1e-9)
    }
}
