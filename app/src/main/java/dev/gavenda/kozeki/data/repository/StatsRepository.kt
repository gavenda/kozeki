package dev.gavenda.kozeki.data.repository

import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.db.ReadThroughEntity
import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.db.YearlyGoalEntity
import dev.gavenda.kozeki.data.model.AllTimeStats
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.BookReading
import dev.gavenda.kozeki.data.model.CalendarBook
import dev.gavenda.kozeki.data.model.CalendarDay
import dev.gavenda.kozeki.data.model.CompletedBook
import dev.gavenda.kozeki.data.model.DailyStats
import dev.gavenda.kozeki.data.model.DayReading
import dev.gavenda.kozeki.data.model.PeriodStats
import dev.gavenda.kozeki.data.model.TimelineEntry
import dev.gavenda.kozeki.data.model.YearReading
import dev.gavenda.kozeki.data.model.YearStats
import dev.gavenda.kozeki.data.settings.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Turns raw sessions and read-throughs into what the calendar and statistics screens show. */
class StatsRepository(
    private val db: KozekiDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val sessions = db.readingSessionDao()
    private val readThroughs = db.readThroughDao()
    private val goals = db.yearlyGoalDao()

    fun observeDay(date: LocalDate, firstDayOfWeek: DayOfWeek): Flow<DailyStats> {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val weekEnd = weekStart.plusDays(7)
        return combine(
            sessions.observeBetween(weekStart.toEpochDay(), weekEnd.toEpochDay()),
            readThroughs.observeCompletedBetween(date.toEpochDay(), date.toEpochDay() + 1),
            library.observeAll(),
            settings.dailyGoalMinutes,
        ) { weekSessions, finished, books, goal ->
            val byId = books.associateBy { it.id }
            val today = weekSessions.filter { it.day == date.toEpochDay() }
            DailyStats(
                date = date,
                goalMinutes = goal,
                durationMs = today.sumOf { it.durationMs },
                pages = today.sumOf { it.pages },
                books = perBook(today, byId),
                timeline = today.mapNotNull { session ->
                    val book = byId[session.bookId] ?: return@mapNotNull null
                    TimelineEntry(book, session.startedAt, session.durationMs, session.endChapter ?: session.startChapter)
                },
                completed = completed(finished, byId),
                week = perDay(weekSessions, weekStart, weekEnd),
            )
        }.flowOn(Dispatchers.Default)
    }

    /** Stats for the days from [start] to [endInclusive], used for both the week and the month view. */
    fun observePeriod(start: LocalDate, endInclusive: LocalDate): Flow<PeriodStats> {
        val end = endInclusive.plusDays(1)
        return combine(
            sessions.observeBetween(start.toEpochDay(), end.toEpochDay()),
            readThroughs.observeCompletedBetween(start.toEpochDay(), end.toEpochDay()),
            library.observeAll(),
            settings.dailyGoalMinutes,
        ) { periodSessions, finished, books, goal ->
            val byId = books.associateBy { it.id }
            val known = periodSessions.filter { it.bookId in byId }
            PeriodStats(
                start = start,
                endInclusive = endInclusive,
                goalMinutes = goal,
                days = perDay(known, start, end),
                durationMs = known.sumOf { it.durationMs },
                pages = known.sumOf { it.pages },
                sessionCount = known.size,
                books = perBook(known, byId),
                completed = completed(finished, byId),
            )
        }.flowOn(Dispatchers.Default)
    }

    fun observeYear(year: Int): Flow<YearStats> {
        val start = LocalDate.of(year, 1, 1)
        val end = start.plusYears(1)
        return combine(
            sessions.observeBetween(start.toEpochDay(), end.toEpochDay()),
            readThroughs.observeCompletedBetween(start.toEpochDay(), end.toEpochDay()),
            library.observeAll(),
            goals.observe(year),
        ) { yearSessions, finished, books, goal ->
            val byId = books.associateBy { it.id }
            val known = yearSessions.filter { it.bookId in byId }
            val done = completed(finished, byId)
            val rated = ratings(done)

            val purchases = books.filter { it.purchasedOn?.year == year && it.purchasePriceMinor != null }
            val mainCurrency = mainCurrency(purchases)

            YearStats(
                year = year,
                goalBooks = goal?.books,
                completed = done,
                completedPerMonth = monthly { month -> done.count { it.finishedOn.monthValue == month } },
                durationPerMonth = monthly { month ->
                    known.filter { LocalDate.ofEpochDay(it.day).monthValue == month }.sumOf { it.durationMs }
                },
                pagesPerMonth = monthly { month ->
                    known.filter { LocalDate.ofEpochDay(it.day).monthValue == month }.sumOf { it.pages }
                },
                durationMs = known.sumOf { it.durationMs },
                pages = known.sumOf { it.pages },
                ratingAverage = rated.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                ratingCounts = starCounts(rated),
                spending = spendingByCurrency(purchases),
                spendingPerMonth = monthly { month ->
                    purchases
                        .filter { it.purchaseCurrency.orEmpty() == mainCurrency && it.purchasedOn?.monthValue == month }
                        .sumOf { it.purchasePriceMinor ?: 0L }
                },
                spendingCurrency = mainCurrency,
            )
        }.flowOn(Dispatchers.Default)
    }

    /** Everything on record. Its yearly figures and its monthly average run up to [today]. */
    fun observeAllTime(today: LocalDate): Flow<AllTimeStats> = combine(
        sessions.observeAll(),
        readThroughs.observeCompleted(),
        library.observeAll(),
    ) { allSessions, finished, books ->
        val byId = books.associateBy { it.id }
        allTimeStats(allSessions.filter { it.bookId in byId }, completed(finished, byId), books, today)
    }.flowOn(Dispatchers.Default)

    /** Every day of [month] on which something was read or finished. */
    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, CalendarDay>> {
        val start = month.atDay(1)
        val end = month.plusMonths(1).atDay(1)
        return combine(
            sessions.observeBetween(start.toEpochDay(), end.toEpochDay()),
            readThroughs.observeCompletedBetween(start.toEpochDay(), end.toEpochDay()),
            library.observeAll(),
        ) { monthSessions, finished, books ->
            val byId = books.associateBy { it.id }
            val finishedByDay = finished.groupBy { it.finishedOn }
            val sessionsByDay = monthSessions.groupBy { it.day }

            (sessionsByDay.keys + finishedByDay.keys.filterNotNull()).associate { day ->
                val durations = sessionsByDay[day].orEmpty()
                    .groupBy { it.bookId }
                    .mapValues { (_, list) -> list.sumOf { it.durationMs } }
                val finishedIds = finishedByDay[day].orEmpty().map { it.bookId }.toSet()
                val date = LocalDate.ofEpochDay(day)
                date to CalendarDay(
                    date = date,
                    books = (durations.keys + finishedIds)
                        .mapNotNull { id -> byId[id]?.let { CalendarBook(it, durations[id] ?: 0L, id in finishedIds) } }
                        // Finished books lead, since the day's rating belongs to them.
                        .sortedWith(compareByDescending<CalendarBook> { it.completed }.thenByDescending { it.durationMs }),
                )
            }.filterValues { it.books.isNotEmpty() }
        }.flowOn(Dispatchers.Default)
    }

    /** The number of books the user aims to finish in [year], or null when no goal is set. */
    fun observeYearlyGoal(year: Int): Flow<Int?> = goals.observe(year).map { it?.books }

    suspend fun setYearlyGoal(year: Int, books: Int) {
        val now = clock.millis()
        goals.upsert(YearlyGoalEntity(year, books.coerceAtLeast(1), SyncStamp.created(now)))
    }

    private fun perDay(list: List<ReadingSessionEntity>, start: LocalDate, endExclusive: LocalDate): List<DayReading> {
        val byDay = list.groupBy { it.day }
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { it < endExclusive }
            .map { date ->
                val day = byDay[date.toEpochDay()].orEmpty()
                DayReading(date, day.sumOf { it.durationMs }, day.sumOf { it.pages })
            }
            .toList()
    }

    private fun perBook(list: List<ReadingSessionEntity>, books: Map<String, Book>): List<BookReading> =
        list.groupBy { it.bookId }
            .mapNotNull { (bookId, bookSessions) ->
                val book = books[bookId] ?: return@mapNotNull null
                val ordered = bookSessions.sortedBy { it.startedAt }
                val pages = ordered.sumOf { it.pages }
                BookReading(
                    book = book,
                    durationMs = ordered.sumOf { it.durationMs },
                    pages = pages,
                    startPosition = ordered.firstNotNullOfOrNull { it.startPosition },
                    endPosition = ordered.mapNotNull { it.endPosition }.maxOrNull(),
                    // From the pages turned, so that jumping around the book does not count as progress.
                    progressGained = book.positionCount?.takeIf { it > 0 }?.let { pages.toDouble() / it } ?: 0.0,
                )
            }
            .sortedByDescending { it.durationMs }

    private fun completed(list: List<ReadThroughEntity>, books: Map<String, Book>): List<CompletedBook> =
        list.mapNotNull { readThrough ->
            val book = books[readThrough.bookId] ?: return@mapNotNull null
            val day = readThrough.finishedOn ?: return@mapNotNull null
            CompletedBook(book, LocalDate.ofEpochDay(day), readThrough.id)
        }

    private fun <T> monthly(value: (Int) -> T): List<T> = (1..12).map(value)
}

/**
 * Adds up [sessions] and [completed] read-throughs, both of books that still exist, and what was
 * spent on [books]. Kept apart from the database so that it can be tested without one.
 */
internal fun allTimeStats(
    sessions: List<ReadingSessionEntity>,
    completed: List<CompletedBook>,
    books: List<Book>,
    today: LocalDate,
): AllTimeStats {
    val firstSession = sessions.minOfOrNull { it.day }?.let(LocalDate::ofEpochDay)
    val since = listOfNotNull(firstSession, completed.minOfOrNull { it.finishedOn }).minOrNull()
    // Counted from the month of the first reading: the time before it would only understate the average.
    val months = since?.let { ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(today)) + 1 } ?: 1
    val durationPerYear = sessions.groupingBy { LocalDate.ofEpochDay(it.day).year }
        .fold(0L) { total, session -> total + session.durationMs }
    val completedPerYear = completed.groupingBy { it.finishedOn.year }.eachCount()
    val rated = ratings(completed)

    val purchases = books.filter { it.purchasedOn != null && it.purchasePriceMinor != null }
    val mainCurrency = mainCurrency(purchases)
    val charted = purchases.filter { it.purchaseCurrency.orEmpty() == mainCurrency }

    return AllTimeStats(
        since = since,
        completed = completed,
        booksPerMonth = completed.size.toFloat() / months.coerceAtLeast(1),
        years = yearSpan(durationPerYear.keys + completedPerYear.keys, today.year).map { year ->
            YearReading(year, completedPerYear[year] ?: 0, durationPerYear[year] ?: 0L)
        },
        durationMs = sessions.sumOf { it.durationMs },
        pages = sessions.sumOf { it.pages },
        daysRead = sessions.groupBy { it.day }.count { (_, day) -> day.sumOf { it.durationMs } > 0 },
        ratingAverage = rated.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        ratingCounts = starCounts(rated),
        spending = spendingByCurrency(purchases),
        spendingPerYear = yearSpan(charted.mapNotNull { it.purchasedOn?.year }, today.year).associateWith { year ->
            charted.filter { it.purchasedOn?.year == year }.sumOf { it.purchasePriceMinor ?: 0L }
        },
        spendingCurrency = mainCurrency,
    )
}

/**
 * Every year from the first of [years] to the [current] one, or none when there is none to start
 * from. A year past the current one, recorded while the clock ran ahead, is kept as well.
 */
private fun yearSpan(years: Collection<Int>, current: Int): IntRange =
    if (years.isEmpty()) IntRange.EMPTY else years.min()..maxOf(years.max(), current)

/** The ratings of the books among [completed], each counted once however often it was read. */
private fun ratings(completed: List<CompletedBook>): List<Float> =
    completed.map { it.book }.distinctBy { it.id }.mapNotNull { it.rating }

/** Number of [ratings] per whole star, index 0 being one star. A half star counts as the next whole one. */
private fun starCounts(ratings: List<Float>): List<Int> =
    (1..5).map { star -> ratings.count { kotlin.math.ceil(it.toDouble()).toInt() == star } }

/** What [purchases] cost in total, per currency code. */
private fun spendingByCurrency(purchases: List<Book>): Map<String, Long> =
    purchases.groupBy { it.purchaseCurrency.orEmpty() }
        .mapValues { (_, list) -> list.sumOf { it.purchasePriceMinor ?: 0L } }

/** The currency most of [purchases] were made in, which is the one their chart is drawn in. */
private fun mainCurrency(purchases: List<Book>): String? =
    purchases.groupingBy { it.purchaseCurrency.orEmpty() }.eachCount().maxByOrNull { it.value }?.key
