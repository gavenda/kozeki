package dev.gavenda.kozeki.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import dev.gavenda.kozeki.ui.library.ScrollTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollTurnTest {

    private val turn = ScrollTurn(threshold = 50f)

    /** Moves the list by [pixels]: above zero is the finger going down, which is back toward the top. */
    private fun move(vararg pixels: Float) = pixels.forEach {
        assertEquals(Offset.Zero, turn.onPreScroll(Offset(0f, it), NestedScrollSource.UserInput))
    }

    @Test
    fun `a list that was not moved is not on its way up`() {
        assertFalse(turn.towardTop)
    }

    @Test
    fun `going down is not the way up, however far`() {
        move(-30f, -30f, -400f)

        assertFalse(turn.towardTop)
    }

    @Test
    fun `turning back counts once it has gone further than a nudge`() {
        move(-400f, 30f)
        assertFalse(turn.towardTop)

        move(30f)
        assertTrue(turn.towardTop)
    }

    @Test
    fun `a waver as the finger lifts does not turn it around`() {
        move(-400f, 80f)
        move(-3f, -2f)
        assertTrue(turn.towardTop)

        move(-400f, 80f, 3f, -4f)
        assertTrue(turn.towardTop)
    }

    @Test
    fun `wavers the other way do not add up to a turn`() {
        move(-400f)
        move(30f, -1f, 30f, -1f, 30f)

        assertFalse(turn.towardTop)
    }

    @Test
    fun `going down again far enough ends the way up`() {
        move(-400f, 80f)
        move(-30f, -30f)

        assertFalse(turn.towardTop)
    }
}
