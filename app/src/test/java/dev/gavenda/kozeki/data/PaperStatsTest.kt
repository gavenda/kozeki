package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.db.PhysicalReadingEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.BookReading
import dev.gavenda.kozeki.data.model.DayReading
import dev.gavenda.kozeki.data.model.PeriodStats
import dev.gavenda.kozeki.data.repository.paperBooks
import dev.gavenda.kozeki.data.repository.paperOnly
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaperStatsTest {

    private val paperback = Book(id = "paperback", title = "Children of Time")
    private val hardback = Book(id = "hardback", title = "Blindsight")
    private val epub = Book(id = "epub", title = "Piranesi", inLibrary = true)
    private val books = listOf(paperback, hardback, epub).associateBy { it.id }
    private var ids = 0

    private fun pages(from: Int, to: Int, book: Book = paperback) = PhysicalReadingEntity(
        id = "reading-${ids++}",
        bookId = book.id,
        readThroughId = null,
        recordedAt = 0L,
        day = 0L,
        startPage = from,
        endPage = to,
        sync = SyncStamp.created(0L),
    )

    @Test
    fun `a book without an EPUB is told by what was read of it on paper`() {
        val onPaper = pages(0, 30)

        assertEquals(listOf(onPaper), paperOnly(listOf(onPaper), books))
    }

    // Its sessions in the reader already tell what was read of it.
    @Test
    fun `a book with an EPUB is not told by its paper copy as well`() {
        assertEquals(emptyList<PhysicalReadingEntity>(), paperOnly(listOf(pages(0, 30, epub)), books))
    }

    @Test
    fun `what was read on paper of a book that is gone is left out`() {
        val gone = Book(id = "gone", title = "Embassytown")

        assertEquals(emptyList<PhysicalReadingEntity>(), paperOnly(listOf(pages(0, 30, gone)), books))
    }

    @Test
    fun `the sittings of a book add up to one card, from its first page to its last`() {
        val cards = paperBooks(listOf(pages(120, 150), pages(150, 200)), books)

        assertEquals(listOf(BookReading(paperback, 0L, 80, 120, 200, physical = true)), cards)
    }

    @Test
    fun `nobody timed a physical copy, so it has no speed`() {
        assertNull(paperBooks(listOf(pages(0, 300)), books).single().pagesPerHour)
    }

    @Test
    fun `the book read furthest comes first`() {
        val cards = paperBooks(listOf(pages(0, 12), pages(40, 95, hardback), pages(12, 20)), books)

        assertEquals(listOf(hardback, paperback), cards.map { it.book })
        assertEquals(listOf(55, 20), cards.map { it.pages })
    }

    @Test
    fun `a day read only on paper is a day read`() {
        val monday = LocalDate.of(2026, 10, 5)
        val week = PeriodStats(
            start = monday,
            endInclusive = monday.plusDays(2),
            goalMinutes = 20,
            days = listOf(
                DayReading(monday, durationMs = 25 * 60_000L, pages = 14),
                DayReading(monday.plusDays(1), pages = 30),
                DayReading(monday.plusDays(2)),
            ),
            durationMs = 25 * 60_000L,
            pages = 44,
            sessionCount = 1,
            books = emptyList(),
            completed = emptyList(),
        )

        assertEquals(2, week.daysRead)
        // The goal is one of time, which paper does not add to.
        assertEquals(1, week.goalDaysMet)
    }
}
