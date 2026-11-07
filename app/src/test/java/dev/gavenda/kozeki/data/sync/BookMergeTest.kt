package dev.gavenda.kozeki.data.sync

import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same rules as `mergeBook` in webapp/shared/tables.ts and the server's upsert. */
class BookMergeTest {

    private fun book(updatedAt: Long, lastReadAt: Long?, progression: Double = 0.0, rating: Float? = null, syncedAt: Long? = null) =
        BookEntity(
            id = "b1",
            title = "Title",
            progression = progression,
            rating = rating,
            lastReadAt = lastReadAt,
            sync = SyncStamp(createdAt = 1, updatedAt = updatedAt, syncedAt = syncedAt),
        )

    @Test
    fun aPageTurnedHereSurvivesAnEditFromElsewhere() {
        val local = book(updatedAt = 1000, lastReadAt = 5000, progression = 0.6, rating = 3f)
        val remote = book(updatedAt = 2000, lastReadAt = 900, progression = 0.1, rating = 5f)
        val merged = mergeBook(local, remote)!!

        assertEquals(5f, merged.rating)
        assertEquals(0.6, merged.progression, 0.0)
        assertEquals(5000L, merged.lastReadAt)
        assertEquals(2000L, merged.sync.updatedAt)
        // Part of it is this device's, so it waits to be sent.
        assertNull(merged.sync.syncedAt)
    }

    @Test
    fun anEditHereSurvivesAPageTurnedElsewhere() {
        val local = book(updatedAt = 3000, lastReadAt = 100, rating = 5f)
        val remote = book(updatedAt = 1000, lastReadAt = 4000, progression = 0.7, rating = 3f)
        val merged = mergeBook(local, remote)!!

        assertEquals(5f, merged.rating)
        assertEquals(0.7, merged.progression, 0.0)
        assertNull(merged.sync.syncedAt)
    }

    @Test
    fun theRemoteRowWholeCountsAsSent() {
        val merged = mergeBook(book(1000, 1000), book(2000, 3000))!!
        assertEquals(3000L, merged.sync.syncedAt)
        assertEquals(merged.version, merged.sync.syncedAt)
    }

    @Test
    fun nothingIsTakenWhenThisSideIsLaterOnBothCounts() {
        assertNull(mergeBook(book(3000, 3000), book(2000, 2000)))
    }
}
