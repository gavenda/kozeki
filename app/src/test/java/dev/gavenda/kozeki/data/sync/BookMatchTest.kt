package dev.gavenda.kozeki.data.sync

import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.ReadingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When two entries are one book, and what the one left keeps. */
class BookMatchTest {

    private fun book(
        id: String,
        title: String = "Title",
        fileHash: String? = null,
        isbn13: String? = null,
        sourceId: String? = null,
        createdAt: Long = 1,
        updatedAt: Long = 1,
        syncedAt: Long? = null,
    ) = BookEntity(
        id = id,
        title = title,
        fileHash = fileHash,
        isbn13 = isbn13,
        source = sourceId?.let { MetadataSource.HARDCOVER },
        sourceId = sourceId,
        sync = SyncStamp(createdAt = createdAt, updatedAt = updatedAt, syncedAt = syncedAt),
    )

    @Test
    fun theSameFileIsTheSameBookWhateverItsDetails() {
        assertTrue(sameBook(book("a", title = "Vol. 1", fileHash = "h"), book("b", title = "Volume One", fileHash = "h")))
    }

    @Test
    fun twoFilesAreTwoBooksEvenWithTheSameIsbn() {
        assertFalse(sameBook(book("a", fileHash = "h1", isbn13 = "978"), book("b", fileHash = "h2", isbn13 = "978")))
    }

    @Test
    fun withoutAFileOnBothTheIsbnDecides() {
        assertTrue(sameBook(book("a", fileHash = "h", isbn13 = "978"), book("b", isbn13 = "978")))
        assertFalse(sameBook(book("a", isbn13 = "978"), book("b", isbn13 = "979", sourceId = "1")))
    }

    @Test
    fun aCatalogueEntryNeedsTheSameTitleToo() {
        assertTrue(sameBook(book("a", title = "Bones", sourceId = "1"), book("b", title = "bones ", sourceId = "1")))
        // A wrong match can give two volumes the same entry.
        assertFalse(sameBook(book("a", title = "Vol. 2", sourceId = "1"), book("b", title = "Vol. 3", sourceId = "1")))
        assertFalse(sameBook(book("a", title = "Bones"), book("b", title = "Bones")))
    }

    @Test
    fun onlyTheFileCountsWhenAskedTo() {
        assertFalse(sameBook(book("a", isbn13 = "978"), book("b", isbn13 = "978"), fileOnly = true))
    }

    @Test
    fun theEarliestEntryIsKept() {
        val first = book("z", fileHash = "h", createdAt = 1)
        val second = book("a", fileHash = "h", createdAt = 2)
        val third = book("m", fileHash = "h", createdAt = 3)
        val pairs = duplicatePairs(listOf(third, second, first), fileOnly = true)
        assertEquals(listOf("a" to "z", "m" to "z"), pairs.map { it.first.id to it.second.id })
    }

    @Test
    fun eachAccountBookTakesOneOfThisDevicesBooks() {
        val account = listOf(book("acc", fileHash = "h", syncedAt = 1))
        val device = listOf(book("d1", fileHash = "h", createdAt = 1), book("d2", fileHash = "h", createdAt = 2))
        assertEquals(listOf("d1" to "acc"), accountPairs(device, account).map { it.first.id to it.second.id })
    }

    @Test
    fun theCombinedBookTakesTheLaterEditAndFillsInTheRest() {
        val kept = book("kept", updatedAt = 1000, syncedAt = 1000).copy(
            state = ReadingState.PLANNED,
            acquisition = Acquisition.WISHLIST,
            rating = 4f,
            lastReadAt = 50,
            progression = 0.1,
        )
        val dropped = book("dropped", fileHash = "h", updatedAt = 2000).copy(
            title = "Edited later",
            state = ReadingState.READING,
            acquisition = Acquisition.PURCHASED,
            rating = null,
            lastReadAt = 900,
            progression = 0.5,
            isFavorite = true,
        )
        val combined = combineBooks(kept, dropped, now = 5000)
        assertEquals("kept", combined.id)
        assertEquals("Edited later", combined.title)
        assertEquals(ReadingState.READING, combined.state)
        assertEquals(Acquisition.PURCHASED, combined.acquisition)
        assertEquals(4f, combined.rating)
        assertEquals("h", combined.fileHash)
        assertEquals(0.5, combined.progression, 0.0)
        assertEquals(900L, combined.lastReadAt)
        assertTrue(combined.isFavorite)
        assertEquals(5000L, combined.sync.updatedAt)
        assertNull(combined.sync.syncedAt)
    }
}
