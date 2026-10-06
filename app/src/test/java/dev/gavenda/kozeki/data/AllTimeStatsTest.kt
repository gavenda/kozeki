package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.CompletedBook
import dev.gavenda.kozeki.data.model.YearReading
import dev.gavenda.kozeki.data.repository.allTimeStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllTimeStatsTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val book = Book(id = "book", title = "The Dispossessed")
    private var sessions = 0
    private var readThroughs = 0

    private fun session(day: LocalDate, minutes: Long, pages: Int = 0) = ReadingSessionEntity(
        id = "session-${sessions++}",
        bookId = book.id,
        readThroughId = null,
        startedAt = 0L,
        endedAt = 0L,
        durationMs = minutes * 60_000,
        day = day.toEpochDay(),
        startProgression = 0.0,
        endProgression = 0.0,
        pages = pages,
        sync = SyncStamp.created(0L),
    )

    private fun finished(on: LocalDate, book: Book = this.book) =
        CompletedBook(book, on, "read-through-${readThroughs++}")

    private fun bought(on: LocalDate, price: Long, currency: String? = "USD") = Book(
        id = "bought-$on",
        title = "Piranesi",
        purchasedOn = on,
        purchasePriceMinor = price,
        purchaseCurrency = currency,
    )

    @Test
    fun `nothing on record is empty`() {
        val stats = allTimeStats(emptyList(), emptyList(), listOf(book), today)

        assertTrue(stats.isEmpty)
        assertNull(stats.since)
        assertEquals(emptyList<YearReading>(), stats.years)
        assertEquals(0f, stats.booksPerMonth, 0f)
        assertEquals(0L, stats.durationMs)
        assertEquals(0, stats.daysRead)
        assertNull(stats.ratingAverage)
        assertEquals(emptyMap<Int, Long>(), stats.spendingPerYear)
        assertNull(stats.spendingCurrency)
    }

    @Test
    fun `time, pages and days read add up across the years`() {
        val stats = allTimeStats(
            sessions = listOf(
                session(LocalDate.of(2024, 5, 1), minutes = 30, pages = 20),
                session(LocalDate.of(2024, 5, 1), minutes = 15, pages = 10),
                session(LocalDate.of(2025, 12, 31), minutes = 60, pages = 41),
                session(LocalDate.of(2026, 1, 1), minutes = 5, pages = 3),
            ),
            completed = emptyList(),
            books = listOf(book),
            today = today,
        )

        assertEquals(110 * 60_000L, stats.durationMs)
        assertEquals(74, stats.pages)
        // Two sessions on the same day are one day read.
        assertEquals(3, stats.daysRead)
    }

    @Test
    fun `a day whose sessions took no time is not a day read`() {
        val stats = allTimeStats(listOf(session(today, minutes = 0)), emptyList(), listOf(book), today)

        assertEquals(0, stats.daysRead)
    }

    @Test
    fun `it starts on the first day something was read or finished`() {
        val read = session(LocalDate.of(2025, 3, 9), minutes = 20)

        // A physical copy is finished without ever opening the reader.
        val finishedFirst = allTimeStats(listOf(read), listOf(finished(LocalDate.of(2024, 11, 2))), listOf(book), today)
        assertEquals(LocalDate.of(2024, 11, 2), finishedFirst.since)

        val readFirst = allTimeStats(listOf(read), listOf(finished(LocalDate.of(2025, 4, 1))), listOf(book), today)
        assertEquals(LocalDate.of(2025, 3, 9), readFirst.since)
    }

    @Test
    fun `every year from the first to the current one is listed, with nothing in the quiet ones`() {
        val stats = allTimeStats(
            sessions = listOf(
                session(LocalDate.of(2023, 6, 1), minutes = 90),
                session(LocalDate.of(2025, 2, 1), minutes = 30),
                session(LocalDate.of(2025, 8, 1), minutes = 45),
            ),
            completed = listOf(finished(LocalDate.of(2023, 7, 1)), finished(LocalDate.of(2025, 9, 1))),
            books = listOf(book),
            today = today,
        )

        assertEquals(
            listOf(
                YearReading(2023, completed = 1, durationMs = 90 * 60_000L),
                YearReading(2024),
                YearReading(2025, completed = 1, durationMs = 75 * 60_000L),
                YearReading(2026),
            ),
            stats.years,
        )
    }

    // The device's clock may have been ahead when something was recorded.
    @Test
    fun `a year past the current one is not lost`() {
        val ahead = session(LocalDate.of(2027, 1, 3), minutes = 10)
        val stats = allTimeStats(listOf(ahead), emptyList(), listOf(book), today)

        assertEquals(listOf(YearReading(2027, durationMs = 10 * 60_000L)), stats.years)
        assertEquals(0f, stats.booksPerMonth, 0f)
    }

    @Test
    fun `the monthly average counts from the month of the first reading`() {
        val completed = (1..6).map { finished(LocalDate.of(2026, 9, it)) }

        // August, September and October.
        val first = session(LocalDate.of(2026, 8, 31), minutes = 10)
        val stats = allTimeStats(listOf(first), completed, listOf(book), today)
        assertEquals(2f, stats.booksPerMonth, 1e-6f)

        // A first month still counts as a whole one.
        val firstMonth = allTimeStats(emptyList(), listOf(finished(LocalDate.of(2026, 10, 2))), listOf(book), today)
        assertEquals(1f, firstMonth.booksPerMonth, 1e-6f)
    }

    @Test
    fun `a re-read is another book finished but one rating`() {
        val rated = book.copy(rating = 4.5f)
        val other = Book(id = "other", title = "Piranesi", rating = 2f)
        val unrated = Book(id = "unrated", title = "Blindsight")
        val stats = allTimeStats(
            sessions = emptyList(),
            completed = listOf(
                finished(LocalDate.of(2024, 1, 5), rated),
                finished(LocalDate.of(2025, 1, 5), rated),
                finished(LocalDate.of(2025, 2, 5), other),
                finished(LocalDate.of(2025, 3, 5), unrated),
            ),
            books = listOf(rated, other, unrated),
            today = today,
        )

        assertEquals(4, stats.completed.size)
        assertEquals(3.25f, stats.ratingAverage!!, 1e-6f)
        // A half star counts towards the whole star above it.
        assertEquals(listOf(0, 1, 0, 0, 1), stats.ratingCounts)
    }

    @Test
    fun `spending is totalled per currency and charted by year in the main one`() {
        val stats = allTimeStats(
            sessions = emptyList(),
            completed = emptyList(),
            books = listOf(
                bought(LocalDate.of(2024, 3, 1), 1299),
                bought(LocalDate.of(2024, 9, 1), 500),
                bought(LocalDate.of(2025, 6, 1), 1500, currency = "EUR"),
                bought(LocalDate.of(2026, 2, 1), 899),
                // Bought, but for a price nobody wrote down.
                Book(id = "gift", title = "Blindsight", purchasedOn = LocalDate.of(2020, 1, 1)),
            ),
            today = today,
        )

        assertEquals(mapOf("USD" to 2698L, "EUR" to 1500L), stats.spending)
        assertEquals("USD", stats.spendingCurrency)
        assertEquals(listOf(2024 to 1799L, 2025 to 0L, 2026 to 899L), stats.spendingPerYear.toList())
    }

    @Test
    fun `a book bought long before any reading does not stretch the reading years`() {
        val stats = allTimeStats(
            sessions = listOf(session(LocalDate.of(2026, 10, 2), minutes = 25)),
            completed = emptyList(),
            books = listOf(book, bought(LocalDate.of(2019, 4, 1), 1099)),
            today = today,
        )

        assertEquals(listOf(2026), stats.years.map { it.year })
        assertEquals((2019..2026).toList(), stats.spendingPerYear.keys.toList())
        assertEquals(LocalDate.of(2026, 10, 2), stats.since)
    }

    @Test
    fun `purchases alone are something to show`() {
        val stats = allTimeStats(emptyList(), emptyList(), listOf(bought(LocalDate.of(2026, 10, 1), 1299)), today)

        assertFalse(stats.isEmpty)
        assertNull(stats.since)
        assertEquals(emptyList<YearReading>(), stats.years)
    }
}
