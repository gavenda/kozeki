package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.CalendarBook
import dev.gavenda.kozeki.data.model.CalendarDay
import dev.gavenda.kozeki.ui.calendar.CalendarUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSummaryTest {

    private val today = LocalDate.of(2026, 10, 9)
    private val book = Book(id = "book", title = "Blindsight")

    private fun month(vararg days: Pair<Int, CalendarBook>) = CalendarUiState(
        month = YearMonth.from(today),
        today = today,
        firstDayOfWeek = DayOfWeek.MONDAY,
        days = days.associate { (day, read) ->
            val date = today.withDayOfMonth(day)
            date to CalendarDay(date, listOf(read))
        },
    )

    @Test
    fun `a month with nothing on it has no days read`() {
        assertEquals(0, month().daysRead)
    }

    @Test
    fun `a day read in the reader, on paper or only finished is a day read`() {
        val state = month(
            1 to CalendarBook(book, durationMs = 20 * 60_000L, completed = false),
            2 to CalendarBook(book, durationMs = 0L, completed = false, onPaper = true),
            3 to CalendarBook(book, durationMs = 0L, completed = true),
        )

        assertEquals(3, state.daysRead)
        assertEquals(20 * 60_000L, state.durationMs)
    }

    // The header then counts the days without a time, rather than say nothing was read.
    @Test
    fun `a month of books finished on paper has days read and no time`() {
        val state = month(3 to CalendarBook(book, durationMs = 0L, completed = true))

        assertEquals(1, state.daysRead)
        assertEquals(0L, state.durationMs)
    }
}
