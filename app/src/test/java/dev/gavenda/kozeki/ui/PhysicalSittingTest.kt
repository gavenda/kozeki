package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.ui.book.PhysicalSitting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalSittingTest {

    private var now = 1_000_000L
    private val sitting = PhysicalSitting { now }
    private val minute = 60_000L

    @Test
    fun `a sitting is kept when it closes, less the time it was paused`() {
        sitting.start()
        now += 10 * minute
        sitting.pause()
        now += 5 * minute
        sitting.resume()
        now += 2 * minute

        assertEquals(PhysicalSitting.Part(1_000_000L, now, 12 * minute), sitting.close())
    }

    @Test
    fun `going out of sight keeps what was read, and coming back carries on in a new part`() {
        sitting.start()
        now += 20 * minute
        val first = sitting.hide()
        now += 60 * minute
        sitting.show()
        now += 5 * minute

        assertEquals(20 * minute, first!!.durationMs)
        assertEquals(PhysicalSitting.Clock(20 * minute, now - 5 * minute), sitting.clock)
        assertEquals(PhysicalSitting.Part(now - 5 * minute, now, 5 * minute), sitting.close())
    }

    @Test
    fun `a paused timer stays paused when the app comes back`() {
        sitting.start()
        now += minute
        sitting.pause()
        sitting.hide()
        sitting.show()

        assertNull(sitting.clock.runningSince)
    }

    @Test
    fun `a moment too short for a session is dropped on closing`() {
        sitting.start()
        now += 5_000L

        assertNull(sitting.close())
    }
}
