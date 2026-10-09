package dev.gavenda.kozeki.data.model

import java.time.LocalDate

/** Reading totals for one calendar day. */
data class DayReading(
    val date: LocalDate,
    val durationMs: Long = 0L,
    val pages: Int = 0,
)

/** What was read of one book over some period. */
data class BookReading(
    val book: Book,
    val durationMs: Long,
    val pages: Int,
    val startPosition: Int? = null,
    val endPosition: Int? = null,
    /** Share of the book covered, 0.0 to 1.0. */
    val progressGained: Double = 0.0,
    /**
     * Read in a physical copy, which is how a book without an EPUB is told. Nobody timed it, so
     * there is no duration, and the pages and positions are pages of that copy.
     */
    val physical: Boolean = false,
) {
    /** Pages per hour, or null when too little was read to say. */
    val pagesPerHour: Double?
        get() = if (durationMs >= 60_000 && pages > 0) pages / (durationMs / 3_600_000.0) else null
}

/** One line of a day's timeline. */
sealed interface TimelineEntry {
    val book: Book

    /** When it happened, which is what the timeline is ordered by. */
    val at: Long

    /** A stretch of reading in the built-in reader, placed at when it started. */
    data class Session(
        override val book: Book,
        override val at: Long,
        val durationMs: Long,
        val chapter: String?,
    ) : TimelineEntry

    /** Pages read in a physical copy, placed at when the page reached was entered. */
    data class Pages(
        override val book: Book,
        override val at: Long,
        val startPage: Int,
        val endPage: Int,
    ) : TimelineEntry
}

/** A read-through that ended with the book finished. */
data class CompletedBook(
    val book: Book,
    val finishedOn: LocalDate,
    /** Sets this apart from the other times the book was finished, which its number may not. */
    val readThroughId: String,
)

data class DailyStats(
    val date: LocalDate,
    val goalMinutes: Int,
    val durationMs: Long,
    val pages: Int,
    val books: List<BookReading>,
    val timeline: List<TimelineEntry>,
    val completed: List<CompletedBook>,
    /** The week around [date], for the day strip. */
    val week: List<DayReading>,
) {
    val goalProgress: Float
        get() = if (goalMinutes <= 0) 0f else (durationMs / 60_000f) / goalMinutes
}

/** A week or a month. */
data class PeriodStats(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val goalMinutes: Int,
    val days: List<DayReading>,
    val durationMs: Long,
    val pages: Int,
    val sessionCount: Int,
    val books: List<BookReading>,
    val completed: List<CompletedBook>,
) {
    /** A day read on paper took no time on record, so its pages are what tell it was read on. */
    val daysRead: Int get() = days.count { it.durationMs > 0 || it.pages > 0 }
    val goalDaysMet: Int get() = days.count { goalMinutes > 0 && it.durationMs >= goalMinutes * 60_000L }
}

data class YearStats(
    val year: Int,
    val goalBooks: Int?,
    val completed: List<CompletedBook>,
    /** Twelve entries, January first. */
    val completedPerMonth: List<Int>,
    val durationPerMonth: List<Long>,
    val pagesPerMonth: List<Int>,
    val durationMs: Long,
    val pages: Int,
    val ratingAverage: Float?,
    /** Number of rated books per whole star, index 0 being one star. */
    val ratingCounts: List<Int>,
    /** Money spent on books bought this year, per currency code. */
    val spending: Map<String, Long>,
    /** Monthly spending in [spendingCurrency], the currency most purchases used. */
    val spendingPerMonth: List<Long>,
    val spendingCurrency: String?,
) {
    val goalProgress: Float?
        get() = goalBooks?.takeIf { it > 0 }?.let { completed.size.toFloat() / it }
}

/** Reading totals for one calendar year. */
data class YearReading(
    val year: Int,
    /** Read-throughs finished that year. */
    val completed: Int = 0,
    val durationMs: Long = 0L,
)

/** Everything on record, with no period to step through. */
data class AllTimeStats(
    /** The first day something was read or finished, or null while nothing has been. */
    val since: LocalDate?,
    val completed: List<CompletedBook>,
    /** Books finished a month, over the months from [since] to the present. */
    val booksPerMonth: Float,
    /** One entry per year, from the year of [since] to the current one. */
    val years: List<YearReading>,
    val durationMs: Long,
    val pages: Int,
    val daysRead: Int,
    val ratingAverage: Float?,
    /** Number of rated books per whole star, index 0 being one star. */
    val ratingCounts: List<Int>,
    /** Money spent on books, per currency code. */
    val spending: Map<String, Long>,
    /** Yearly spending in [spendingCurrency], from the year of its first purchase to the current one. */
    val spendingPerYear: Map<Int, Long>,
    val spendingCurrency: String?,
) {
    /** Nothing was read, finished or bought yet. */
    val isEmpty: Boolean get() = since == null && spending.isEmpty()
}

/** One cell of the book calendar. */
data class CalendarDay(
    val date: LocalDate,
    /** Books read that day, most-read first. */
    val books: List<CalendarBook>,
)

data class CalendarBook(
    val book: Book,
    val durationMs: Long,
    /** The book was finished on this day. */
    val completed: Boolean,
    /** The book was read in a physical copy on this day, which took no time on record. */
    val onPaper: Boolean = false,
)
