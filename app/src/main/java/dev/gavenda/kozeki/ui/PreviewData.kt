package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.BookReading
import dev.gavenda.kozeki.data.model.CalendarBook
import dev.gavenda.kozeki.data.model.CalendarDay
import dev.gavenda.kozeki.data.model.CompletedBook
import dev.gavenda.kozeki.data.model.DailyStats
import dev.gavenda.kozeki.data.model.DayReading
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookReview
import dev.gavenda.kozeki.data.metadata.BookReviews
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.Note
import dev.gavenda.kozeki.data.model.PeriodStats
import dev.gavenda.kozeki.data.model.ReadOutcome
import dev.gavenda.kozeki.data.model.ReadThrough
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.TimelineEntry
import dev.gavenda.kozeki.data.model.YearStats
import java.time.LocalDate
import java.time.YearMonth

/** Sample content for `@Preview` composables. Never used at runtime. */
object PreviewData {

    private const val MINUTE = 60_000L
    private const val DAY = 86_400_000L
    private val Today: LocalDate = LocalDate.of(2026, 10, 4)
    private val Now: Long = Today.toEpochDay() * DAY + 20 * 60 * MINUTE

    val books: List<Book> = listOf(
        Book(
            id = "1",
            title = "The Left Hand of Darkness",
            authors = listOf("Ursula K. Le Guin"),
            authorRefs = listOf(AuthorRef("1", "Ursula K. Le Guin")),
            description = "A lone human emissary is sent to Winter, an alien world whose inhabitants " +
                "can choose and change their gender, to bring it into a growing intergalactic civilization.",
            publisher = "Ace Books",
            publishedDate = "1969-03-01",
            language = "en",
            pageCount = 304,
            isbn13 = "9780441478125",
            categories = listOf("Fiction", "Science Fiction"),
            state = ReadingState.READING,
            acquisition = Acquisition.PURCHASED,
            isFavorite = true,
            source = MetadataSource.HARDCOVER,
            sourceUrl = "https://hardcover.app/books/example",
            matchStatus = MatchStatus.MATCHED,
            inLibrary = true,
            hasFile = true,
            progression = 0.42,
            position = 128,
            positionCount = 304,
            chapterTitle = "The Question of Sex",
            chapterIndex = 7,
            chapterCount = 20,
            lastReadAt = Now,
            purchasePriceMinor = 1299,
            purchaseCurrency = "USD",
            purchasedOn = Today.minusDays(40),
            purchaseLocation = "Kobo",
        ),
        Book(
            id = "2",
            title = "Piranesi",
            authors = listOf("Susanna Clarke"),
            pageCount = 272,
            state = ReadingState.PAUSED,
            acquisition = Acquisition.DOWNLOADED,
            matchStatus = MatchStatus.MATCHED,
            inLibrary = true,
            hasFile = true,
            progression = 0.18,
            position = 49,
            positionCount = 272,
            chapterTitle = "Part Two: The Other",
            chapterIndex = 2,
            chapterCount = 7,
        ),
        Book(
            id = "3",
            title = "A Memory Called Empire",
            authors = listOf("Arkady Martine"),
            pageCount = 462,
            state = ReadingState.COMPLETED,
            acquisition = Acquisition.DOWNLOADED,
            isFavorite = true,
            rating = 4.5f,
            matchStatus = MatchStatus.MATCHED,
            inLibrary = true,
            hasFile = true,
            progression = 1.0,
            positionCount = 462,
        ),
        Book(
            id = "4",
            title = "Gideon the Ninth",
            authors = listOf("Tamsyn Muir"),
            authorRefs = listOf(AuthorRef("2", "Tamsyn Muir")),
            state = ReadingState.PLANNED,
            acquisition = Acquisition.DOWNLOADED,
            matchStatus = MatchStatus.NEEDS_REVIEW,
            inLibrary = true,
            hasFile = false,
        ),
        Book(
            id = "5",
            title = "The Dispossessed",
            subtitle = "An Ambiguous Utopia",
            authors = listOf("Ursula K. Le Guin"),
            pageCount = 387,
            state = ReadingState.PLANNED,
            acquisition = Acquisition.WISHLIST,
            source = MetadataSource.HARDCOVER,
            matchStatus = MatchStatus.MATCHED,
        ),
        Book(
            id = "6",
            title = "Children of Time",
            authors = listOf("Adrian Tchaikovsky"),
            pageCount = 600,
            state = ReadingState.READING,
            acquisition = Acquisition.PURCHASED,
            physicalPage = 212,
            physicalPageCount = 600,
            purchasePriceMinor = 899,
            purchaseCurrency = "USD",
            purchasedOn = Today.minusDays(3),
            matchStatus = MatchStatus.MATCHED,
        ),
        Book(
            id = "7",
            title = "Blindsight",
            authors = listOf("Peter Watts"),
            state = ReadingState.DROPPED,
            acquisition = Acquisition.DOWNLOADED,
            inLibrary = true,
            hasFile = true,
            progression = 0.31,
        ),
    )

    val library: List<Book> = books.filter { it.inLibrary }

    val notes: List<Note> = listOf(
        Note("n1", "1", "Shifgrethor: prestige, face, the unspoken rules of who may advise whom.", "The Question of Sex", Now),
        Note("n2", "1", "The ice crossing chapters are the heart of the book.", null, Now - DAY),
    )

    val reviews = BookReviews(
        averageRating = 4.2f,
        ratingsCount = 1834,
        reviewsCount = 212,
        reviews = listOf(
            BookReview(
                id = "v1",
                reviewer = "Shevek",
                rating = 5f,
                text = "Slow to begin and then impossible to put down. The journey across the ice is some of " +
                    "the best writing about trust between two people I have read anywhere.",
                reviewedOn = Today.minusDays(40).toEpochDay(),
                likes = 31,
            ),
            BookReview(
                id = "v2",
                reviewer = "Takver",
                rating = 3.5f,
                text = "The ending changes how the whole first half reads.",
                hasSpoilers = true,
                reviewedOn = Today.minusDays(300).toEpochDay(),
                likes = 4,
            ),
            BookReview(id = "v3", text = "More anthropology than plot, which is exactly what I wanted."),
            BookReview(id = "v4", reviewer = "Bedap", rating = 4f, text = "A book to reread every few years."),
        ),
    )

    val readThroughs: List<ReadThrough> = listOf(
        ReadThrough("r2", 2, Now - 12 * DAY),
        ReadThrough("r1", 1, Now - 400 * DAY, Now - 380 * DAY, ReadOutcome.COMPLETED),
    )

    private val week: List<DayReading> = (0..6).map { offset ->
        val date = Today.minusDays(5L - offset)
        DayReading(date, if (date > Today) 0 else (10 + offset * 7) * MINUTE, if (date > Today) 0 else 6 + offset * 3)
    }

    val dailyStats = DailyStats(
        date = Today,
        goalMinutes = 20,
        durationMs = 49 * MINUTE,
        pages = 31,
        books = listOf(
            BookReading(books[0], 35 * MINUTE, 22, 106, 128, 0.07),
            BookReading(books[1], 14 * MINUTE, 9, 40, 49, 0.03),
        ),
        timeline = listOf(
            TimelineEntry(books[0], Now - 11 * 60 * MINUTE, 20 * MINUTE, "Soliloquy in Mishnory"),
            TimelineEntry(books[1], Now - 6 * 60 * MINUTE, 14 * MINUTE, "Part Two: The Other"),
            TimelineEntry(books[0], Now - 40 * MINUTE, 15 * MINUTE, "The Question of Sex"),
        ),
        completed = emptyList(),
        week = week,
    )

    val weekStats = PeriodStats(
        start = week.first().date,
        endInclusive = week.last().date,
        goalMinutes = 20,
        days = week,
        durationMs = week.sumOf { it.durationMs },
        pages = week.sumOf { it.pages },
        sessionCount = 11,
        books = dailyStats.books,
        completed = listOf(CompletedBook(books[2], Today.minusDays(2), 1)),
    )

    val monthStats: PeriodStats = run {
        val month = YearMonth.from(Today)
        val days = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            val read = date <= Today && day % 3 != 0
            DayReading(date, if (read) (8 + day * 5 % 50) * MINUTE else 0, if (read) 4 + day % 17 else 0)
        }
        weekStats.copy(
            start = month.atDay(1),
            endInclusive = month.atEndOfMonth(),
            days = days,
            durationMs = days.sumOf { it.durationMs },
            pages = days.sumOf { it.pages },
        )
    }

    val yearStats = YearStats(
        year = 2026,
        goalBooks = 24,
        completed = listOf(
            CompletedBook(books[2], Today.minusDays(2), 1),
            CompletedBook(books[0], Today.minusDays(120), 1),
            CompletedBook(books[6], Today.minusDays(200), 1),
        ),
        completedPerMonth = listOf(2, 1, 3, 1, 0, 2, 4, 1, 2, 1, 0, 0),
        durationPerMonth = listOf(11, 6, 14, 9, 2, 10, 21, 8, 12, 5, 0, 0).map { it * 60 * MINUTE },
        pagesPerMonth = listOf(610, 300, 820, 410, 90, 560, 1180, 430, 640, 260, 0, 0),
        durationMs = 98 * 60 * MINUTE,
        pages = 5300,
        ratingAverage = 4.1f,
        ratingCounts = listOf(0, 1, 3, 6, 7),
        spending = mapOf("USD" to 15_640L),
        spendingPerMonth = listOf(1299, 0, 2598, 899, 0, 1999, 3497, 0, 2449, 2899, 0, 0),
        spendingCurrency = "USD",
    )

    val calendarMonth: YearMonth = YearMonth.from(Today)

    val calendarDays: Map<LocalDate, CalendarDay> = (1..Today.dayOfMonth).filter { it % 4 != 0 }.associate { day ->
        val date = calendarMonth.atDay(day)
        date to CalendarDay(
            date = date,
            books = buildList {
                add(CalendarBook(books[day % 3], (15 + day) * MINUTE, completed = day == 2))
                if (day % 2 == 0) add(CalendarBook(books[(day + 1) % 3], 9 * MINUTE, completed = false))
            },
        )
    }
}
