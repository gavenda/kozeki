package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.model.PhysicalReading
import dev.gavenda.kozeki.data.model.pagesUnread
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalReadingTest {

    // Three sittings that took the copy to page 200.
    private val readings = listOf(PhysicalReading(0, 50), PhysicalReading(50, 120), PhysicalReading(120, 200))

    @Test
    fun `moving on or staying unreads nothing`() {
        assertEquals(0, pagesUnread(readings, currentPage = 200, newPage = 240))
        assertEquals(0, pagesUnread(readings, currentPage = 200, newPage = 200))
    }

    @Test
    fun `going back unreads what is on record past the new page`() {
        // All of the last sitting.
        assertEquals(80, pagesUnread(readings, currentPage = 200, newPage = 120))
        // The last sitting and the end of the one before, which the new page falls inside of.
        assertEquals(100, pagesUnread(readings, currentPage = 200, newPage = 100))
        assertEquals(200, pagesUnread(readings, currentPage = 200, newPage = 0))
    }

    @Test
    fun `stopping unreads everything on record`() {
        assertEquals(200, pagesUnread(readings, currentPage = 200, newPage = null))
    }

    // A page set before readings were kept, or one the Completed state put at the end.
    @Test
    fun `going back over pages that were never recorded unreads nothing`() {
        assertEquals(0, pagesUnread(emptyList(), currentPage = 200, newPage = 80))
        assertEquals(0, pagesUnread(readings, currentPage = 300, newPage = 250))
        assertEquals(40, pagesUnread(readings, currentPage = 300, newPage = 160))
    }

    // After Read again the copy is back at the start, with the earlier read-through still on record.
    @Test
    fun `a first page is not going back, whatever is on record`() {
        assertEquals(0, pagesUnread(readings, currentPage = null, newPage = 40))
        assertEquals(0, pagesUnread(readings, currentPage = 0, newPage = 40))
        assertEquals(0, pagesUnread(readings, currentPage = 0, newPage = null))
    }
}
