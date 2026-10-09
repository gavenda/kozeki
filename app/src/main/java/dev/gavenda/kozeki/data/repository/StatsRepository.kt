package dev.gavenda.kozeki.data.repository

import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.db.PhysicalReadingEntity
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
    private val physicalReadings = db.physicalReadingDao()
    private val readThroughs = db.readThroughDao()
    private val goals = db.yearlyGoalDao()

    fun observeDay(date: LocalDate, firstDayOfWeek: DayOfWeek): Flow<DailyStats> {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val weekEnd = weekStart.plusDays(7)
        return combine(
            sessions.observeBetween(weekStart.toEpochDay(), weekEnd.toEpochDay()),
            physicalReadings.observeBetween(weekStart.toEpochDay(), weekEnd.toEpochDay()),
            readThroughs.observeCompletedBetween(date.toEpochDay(), date.toEpochDay() + 1),
            library.observeAll(),
            settings.dailyGoalMinutes,
        ) { weekSessions, weekPhysical, finished, books, goal ->
            val byId = books.associateBy { it.id }
            val today = weekSessions.filter { it.day == date.toEpochDay() }
            val physical = weekPhysical.filter { it.day == date.toEpochDay() }
            val paper = paperOnly(physical, byId)
            DailyStats(
                date = date,
                goalMinutes = goal,
                durationMs = today.sumOf { it.durationMs },
                pages = today.sumOf { it.pages } + paper.sumOf { it.pages },
                books = perBook(today, paper, byId),
                timeline = timeline(today, physical, byId),
                completed = completed(finished, byId),
                week = perDay(weekSessions, paperOnly(weekPhysical, byId), weekStart, weekEnd),
            )
        }.flowOn(Dispatchers.Default)
    }

    /** Stats for the days from [start] to [endInclusive], used for both the week and the month view. */
    fun observePeriod(start: LocalDate, endInclusive: LocalDate): Flow<PeriodStats> {
        val end = endInclusive.plusDays(1)
        return combine(
            sessions.observeBetween(start.toEpochDay(), end.toEpochDay()),
            physicalReadings.observeBetween(start.toEpochDay(), end.toEpochDay()),
            readThroughs.observeCompletedBetween(start.toEpochDay(), end.toEpochDay()),
            library.observeAll(),
            settings.dailyGoalMinutes,
        ) { periodSessions, physical, finished, books, goal ->
            val byId = books.associateBy { it.id }
            val known = periodSessions.filter { it.bookId in byId }
            val paper = paperOnly(physical, byId)
            PeriodStats(
                start = start,
                endInclusive = endInclusive,
                goalMinutes = goal,
                days = perDay(known, paper, start, end),
                durationMs = known.sumOf { it.durationMs },
                pages = known.sumOf { it.pages } + paper.sumOf { it.pages },
                sessionCount = known.size,
                books = perBook(known, paper, byId),
                completed = completed(finished, byId),
            )
        }.flowOn(Dispatchers.Default)
    }

    fun observeYear(year: Int): Flow<YearStats> {
        val start = LocalDate.of(year, 1, 1)
        val end = start.plusYears(1)
        return combine(
            sessions.observeBetween(start.toEpochDay(), end.toEpochDay()),
            physicalReadings.observeBetween(start.toEpochDay(), end.toEpochDay()),
            readThroughs.observeCompletedBetween(start.toEpochDay(), end.toEpochDay()),
            library.observeAll(),
            goals.observe(year),
        ) { yearSessions, physical, finished, books, goal ->
            val byId = books.associateBy { it.id }
            val known = yearSessions.filter { it.bookId in byId }
            val paper = paperOnly(physical, byId)
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
                    known.filter { LocalDate.ofEpochDay(it.day).monthValue == month }.sumOf { it.pages } +
                        paper.filter { LocalDate.ofEpochDay(it.day).monthValue == month }.sumOf { it.pages }
                },
                durationMs = known.sumOf { it.durationMs },
                pages = known.sumOf { it.pages } + paper.sumOf { it.pages },
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
        physicalReadings.observeAll(),
        readThroughs.observeCompleted(),
        library.observeAll(),
    ) { allSessions, physical, finished, books ->
        val byId = books.associateBy { it.id }
        allTimeStats(
            sessions = allSessions.filter { it.bookId in byId },
            completed = completed(finished, byId),
            books = books,
            today = today,
            paper = paperOnly(physical, byId),
        )
    }.flowOn(Dispatchers.Default)

    /** Every day of [month] on which something was read or finished. */
    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, CalendarDay>> {
        val start = month.atDay(1)
        val end = month.plusMonths(1).atDay(1)
        return combine(
            sessions.observeBetween(start.toEpochDay(), end.toEpochDay()),
            physicalReadings.observeBetween(start.toEpochDay(), end.toEpochDay()),
            readThroughs.observeCompletedBetween(start.toEpochDay(), end.toEpochDay()),
            library.observeAll(),
        ) { monthSessions, physical, finished, books ->
            val byId = books.associateBy { it.id }
            val finishedByDay = finished.groupBy { it.finishedOn }
            val sessionsByDay = monthSessions.groupBy { it.day }
            val paperByDay = paperOnly(physical, byId).groupBy { it.day }

            (sessionsByDay.keys + paperByDay.keys + finishedByDay.keys.filterNotNull()).associate { day ->
                val durations = sessionsByDay[day].orEmpty()
                    .groupBy { it.bookId }
                    .mapValues { (_, list) -> list.sumOf { it.durationMs } }
                val paperIds = paperByDay[day].orEmpty().map { it.bookId }.toSet()
                val finishedIds = finishedByDay[day].orEmpty().map { it.bookId }.toSet()
                val date = LocalDate.ofEpochDay(day)
                date to CalendarDay(
                    date = date,
                    books = (durations.keys + paperIds + finishedIds)
                        .mapNotNull { id ->
                            byId[id]?.let { CalendarBook(it, durations[id] ?: 0L, id in finishedIds, id in paperIds) }
                        }
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

    private fun perDay(
        list: List<ReadingSessionEntity>,
        paper: List<PhysicalReadingEntity>,
        start: LocalDate,
        endExclusive: LocalDate,
    ): List<DayReading> {
        val byDay = list.groupBy { it.day }
        val paperByDay = paper.groupBy { it.day }
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { it < endExclusive }
            .map { date ->
                val day = byDay[date.toEpochDay()].orEmpty()
                val onPaper = paperByDay[date.toEpochDay()].orEmpty()
                DayReading(date, day.sumOf { it.durationMs }, day.sumOf { it.pages } + onPaper.sumOf { it.pages })
            }
            .toList()
    }

    /** What was read of each book: in the reader, most time first, then on [paper]. */
    private fun perBook(
        list: List<ReadingSessionEntity>,
        paper: List<PhysicalReadingEntity>,
        books: Map<String, Book>,
    ): List<BookReading> =
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
            .sortedByDescending { it.durationMs } + paperBooks(paper, books)

    private fun completed(list: List<ReadThroughEntity>, books: Map<String, Book>): List<CompletedBook> =
        list.mapNotNull { readThrough ->
            val book = books[readThrough.bookId] ?: return@mapNotNull null
            val day = readThrough.finishedOn ?: return@mapNotNull null
            CompletedBook(book, LocalDate.ofEpochDay(day), readThrough.id)
        }

    private fun <T> monthly(value: (Int) -> T): List<T> = (1..12).map(value)
}

/**
 * A day's reading in the order it happened: its [sessions] in the reader and the pages of
 * [physical] copies entered on it, leaving out books that no longer exist. Kept apart from the
 * database so that it can be tested without one.
 */
internal fun timeline(
    sessions: List<ReadingSessionEntity>,
    physical: List<PhysicalReadingEntity>,
    books: Map<String, Book>,
): List<TimelineEntry> {
    val read = sessions.mapNotNull { session ->
        val book = books[session.bookId] ?: return@mapNotNull null
        TimelineEntry.Session(book, session.startedAt, session.durationMs, session.endChapter ?: session.startChapter)
    }
    val entered = physical.mapNotNull { reading ->
        val book = books[reading.bookId] ?: return@mapNotNull null
        TimelineEntry.Pages(book, reading.recordedAt, reading.startPage, reading.endPage)
    }
    return (read + entered).sortedBy { it.at }
}

/**
 * The [physical] readings that the statistics tell a book by: those of books that have no EPUB.
 * A book with one is told by its sessions in the reader alone, so that reading it on paper as well
 * is not counted twice. Readings of books that no longer exist are left out too.
 */
internal fun paperOnly(physical: List<PhysicalReadingEntity>, books: Map<String, Book>): List<PhysicalReadingEntity> =
    physical.filter { books[it.bookId]?.inLibrary == false }

/**
 * What was read on [paper] of each book, most pages first. Nobody timed it, so there is no duration
 * and the pages are those of the physical copy.
 */
internal fun paperBooks(paper: List<PhysicalReadingEntity>, books: Map<String, Book>): List<BookReading> =
    paper.groupBy { it.bookId }
        .mapNotNull { (bookId, readings) ->
            val book = books[bookId] ?: return@mapNotNull null
            BookReading(
                book = book,
                durationMs = 0L,
                pages = readings.sumOf { it.pages },
                startPosition = readings.minOf { it.startPage },
                endPosition = readings.maxOf { it.endPage },
                physical = true,
            )
        }
        .sortedByDescending { it.pages }

private val PhysicalReadingEntity.pages: Int get() = endPage - startPage

/**
 * Adds up [sessions] and [completed] read-throughs, both of books that still exist, and what was
 * spent on [books]. What was read on [paper], of the books that have no EPUB, adds its pages and
 * its days but no time. Kept apart from the database so that it can be tested without one.
 */
internal fun allTimeStats(
    sessions: List<ReadingSessionEntity>,
    completed: List<CompletedBook>,
    books: List<Book>,
    today: LocalDate,
    paper: List<PhysicalReadingEntity> = emptyList(),
): AllTimeStats {
    val firstRead = (sessions.map { it.day } + paper.map { it.day }).minOrNull()?.let(LocalDate::ofEpochDay)
    val since = listOfNotNull(firstRead, completed.minOfOrNull { it.finishedOn }).minOrNull()
    // Counted from the month of the first reading: the time before it would only understate the average.
    val months = since?.let { ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(today)) + 1 } ?: 1
    val durationPerYear = sessions.groupingBy { LocalDate.ofEpochDay(it.day).year }
        .fold(0L) { total, session -> total + session.durationMs }
    val completedPerYear = completed.groupingBy { it.finishedOn.year }.eachCount()
    val paperYears = paper.map { LocalDate.ofEpochDay(it.day).year }
    val timedDays = sessions.groupBy { it.day }.filterValues { day -> day.sumOf { it.durationMs } > 0 }.keys
    val rated = ratings(completed)

    val purchases = books.filter { it.purchasedOn != null && it.purchasePriceMinor != null }
    val mainCurrency = mainCurrency(purchases)
    val charted = purchases.filter { it.purchaseCurrency.orEmpty() == mainCurrency }

    return AllTimeStats(
        since = since,
        completed = completed,
        booksPerMonth = completed.size.toFloat() / months.coerceAtLeast(1),
        years = yearSpan(durationPerYear.keys + completedPerYear.keys + paperYears, today.year).map { year ->
            YearReading(year, completedPerYear[year] ?: 0, durationPerYear[year] ?: 0L)
        },
        durationMs = sessions.sumOf { it.durationMs },
        pages = sessions.sumOf { it.pages } + paper.sumOf { it.pages },
        // A day read on paper took no time on record, and is a day read all the same.
        daysRead = (timedDays + paper.map { it.day }).size,
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
