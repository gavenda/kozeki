package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.ui.statistics.yearLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class YearLabelsTest {

    @Test
    fun `a handful of years are all named`() {
        assertEquals(listOf("2026"), yearLabels(listOf(2026)))
        assertEquals(
            listOf("2021", "2022", "2023", "2024", "2025", "2026"),
            yearLabels((2021..2026).toList()),
        )
    }

    @Test
    fun `a long run names every few years, the last one always`() {
        assertEquals(
            listOf("", "2020", "", "2022", "", "2024", "", "2026"),
            yearLabels((2019..2026).toList()),
        )
        assertEquals(
            listOf("2012", "", "", "2015", "", "", "2018", "", "", "2021", "", "", "2024", "", "", "2027"),
            yearLabels((2012..2027).toList()),
        )
    }

    @Test
    fun `no years need no labels`() {
        assertEquals(emptyList<String>(), yearLabels(emptyList()))
    }
}
