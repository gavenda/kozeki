package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.ui.reader.SessionTracker
import dev.gavenda.kozeki.ui.reader.SessionTracker.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTrackerTest {

    private var now = 1_000_000L
    private val tracker = SessionTracker { now }

    private fun advance(ms: Long) {
        now += ms
    }

    @Test
    fun `time between page turns adds up`() {
        tracker.begin(Snapshot(0.10, 10, "One"))
        advance(60_000)
        tracker.activity(Snapshot(0.11, 11, "One"))
        advance(90_000)
        tracker.activity(Snapshot(0.12, 12, "Two"))
        advance(30_000)

        val result = tracker.end()
        assertNotNull(result)
        assertEquals(180_000L, result!!.activeMs)
        assertEquals(10, result.start.position)
        assertEquals(12, result.end.position)
        assertEquals("Two", result.end.chapterTitle)
        assertFalse(tracker.isRunning)
    }

    @Test
    fun `pages turned are counted, jumps and going back are not`() {
        tracker.begin(Snapshot(0.01, 1, null))
        advance(30_000)
        tracker.activity(Snapshot(0.02, 2, null))
        advance(30_000)
        tracker.activity(Snapshot(0.05, 5, null))
        // Picking a chapter from the contents is not twenty pages of reading.
        advance(5_000)
        tracker.activity(Snapshot(0.30, 28, "Five"))
        advance(30_000)
        tracker.activity(Snapshot(0.31, 29, "Five"))
        advance(10_000)
        tracker.activity(Snapshot(0.30, 28, "Five"))

        val result = tracker.end()!!
        assertEquals(5, result.pages)
        assertEquals(1, result.start.position)
        assertEquals(28, result.end.position)
    }

    @Test
    fun `an idle gap counts only up to the cap`() {
        tracker.begin(Snapshot(0.0, 1, null))
        advance(2 * 60 * 60_000L)
        tracker.activity(Snapshot(0.01, 2, null))

        assertEquals(SessionTracker.IDLE_CAP_MS, tracker.end()!!.activeMs)
    }

    @Test
    fun `a glance at the book is not a session`() {
        tracker.begin(Snapshot(0.0, 1, null))
        advance(SessionTracker.MIN_SESSION_MS - 1)
        assertNull(tracker.end())
    }

    @Test
    fun `ending without a start yields nothing and a new session starts clean`() {
        assertNull(tracker.end())

        tracker.begin(Snapshot(0.5, 50, null))
        advance(20_000)
        assertEquals(20_000L, tracker.end()!!.activeMs)

        tracker.begin(Snapshot(0.6, 60, null))
        advance(30_000)
        val second = tracker.end()!!
        assertEquals(30_000L, second.activeMs)
        assertEquals(60, second.start.position)
    }
}
