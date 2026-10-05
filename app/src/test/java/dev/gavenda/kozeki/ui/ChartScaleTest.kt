package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.ui.components.niceCeiling
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartScaleTest {

    @Test
    fun `axis tops land on clean numbers at or above the data`() {
        assertEquals(50f, niceCeiling(45f))
        assertEquals(50f, niceCeiling(50f))
        assertEquals(100f, niceCeiling(51f))
        assertEquals(20f, niceCeiling(12f))
        assertEquals(5f, niceCeiling(3f))
        assertEquals(5000f, niceCeiling(3497f))
    }

    @Test
    fun `an empty chart still has a scale`() {
        assertEquals(1f, niceCeiling(0f))
        assertEquals(1f, niceCeiling(0.3f))
    }
}
