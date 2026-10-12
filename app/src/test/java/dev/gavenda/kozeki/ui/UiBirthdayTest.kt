package dev.gavenda.kozeki.ui

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiBirthdayTest {

    @Test
    fun `23 April is her birthday in every year, leap or not`() {
        listOf(2023, 2024, 2026, 1999).forEach { year ->
            assertTrue(isUiBirthday(LocalDate.of(year, 4, 23)))
        }
    }

    @Test
    fun `the days around it and the same day in other months are not`() {
        assertFalse(isUiBirthday(LocalDate.of(2026, 4, 22)))
        assertFalse(isUiBirthday(LocalDate.of(2026, 4, 24)))
        assertFalse(isUiBirthday(LocalDate.of(2026, 3, 23)))
        assertFalse(isUiBirthday(LocalDate.of(2026, 5, 23)))
        assertFalse(isUiBirthday(LocalDate.of(2026, 10, 4)))
    }
}
