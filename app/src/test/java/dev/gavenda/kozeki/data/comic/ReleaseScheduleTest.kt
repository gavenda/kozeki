package dev.gavenda.kozeki.data.comic

import dev.gavenda.kozeki.data.comic.ReleaseSchedule.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseScheduleTest {

    private val start = 20_000L * DAY_MS

    /** A chapter at noon of each of [days] after [start]. */
    private fun on(vararg days: Int) = days.map { start + it * DAY_MS + DAY_MS / 2 }

    private fun at(day: Double) = start + (day * DAY_MS).toLong()

    @Test
    fun `a weekly comic is weekly`() {
        assertEquals(7 * DAY_MS, ReleaseSchedule.interval(on(0, 7, 14, 21, 28)))
    }

    @Test
    fun `one long break does not set the pace`() {
        assertEquals(7 * DAY_MS, ReleaseSchedule.interval(on(0, 7, 14, 60, 67, 74)))
    }

    @Test
    fun `chapters put up together are one release`() {
        assertNull(ReleaseSchedule.interval(on(0, 0, 0, 0, 7, 7)))
        assertEquals(7 * DAY_MS, ReleaseSchedule.interval(on(0, 0, 0, 7, 7, 14)))
    }

    @Test
    fun `too few dated chapters say nothing`() {
        assertNull(ReleaseSchedule.interval(listOf(0L, 0L, 0L)))
        assertNull(ReleaseSchedule.interval(on(0, 7)))
    }

    @Test
    fun `the pace is kept between a day and four weeks`() {
        assertEquals(28 * DAY_MS, ReleaseSchedule.interval(on(0, 90, 180, 270)))
    }

    @Test
    fun `a weekly comic is not due mid week`() {
        val uploads = on(0, 7, 14, 21)
        assertFalse(ReleaseSchedule.isDue(uploads, checkedAt = at(22.0), now = at(24.0)))
    }

    @Test
    fun `a weekly comic is due as its next chapter nears`() {
        val uploads = on(0, 7, 14, 21)
        assertTrue(ReleaseSchedule.isDue(uploads, checkedAt = at(22.0), now = at(27.6)))
        assertTrue(ReleaseSchedule.isDue(uploads, checkedAt = at(22.0), now = at(29.0)))
    }

    @Test
    fun `a late chapter is looked for once a cycle`() {
        val uploads = on(0, 7, 14, 21)
        // Asked the day it was due and nothing came: left until the week after.
        assertFalse(ReleaseSchedule.isDue(uploads, checkedAt = at(28.6), now = at(31.0)))
        assertTrue(ReleaseSchedule.isDue(uploads, checkedAt = at(28.6), now = at(34.6)))
    }

    @Test
    fun `a comic never asked or without a pace is always due`() {
        assertTrue(ReleaseSchedule.isDue(on(0, 7, 14, 21), checkedAt = 0, now = at(22.0)))
        assertTrue(ReleaseSchedule.isDue(on(21), checkedAt = at(22.0), now = at(22.1)))
    }
}
