package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.repository.allTimeStats
import dev.gavenda.kozeki.data.repository.withoutComicPages
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicStatsTest {

    private val today = LocalDate.of(2026, 10, 25)
    private val book = Book(id = "book", title = "The Dispossessed")
    private val comic = Book(id = "comic", title = "Solo Leveling", isComic = true)
    private val books = listOf(book, comic).associateBy { it.id }

    private fun session(of: Book, pages: Int, chapters: Int = 0) = ReadingSessionEntity(
        id = "session-${of.id}-$pages-$chapters",
        bookId = of.id,
        readThroughId = null,
        startedAt = 0L,
        endedAt = 0L,
        durationMs = 10 * 60_000L,
        day = today.toEpochDay(),
        startProgression = 0.0,
        endProgression = 0.0,
        pages = pages,
        chapters = chapters,
        sync = SyncStamp.created(0L),
    )

    @Test
    fun `a comic is counted in chapters, not pages`() {
        val sessions = withoutComicPages(
            listOf(
                session(book, pages = 30),
                // Recorded before comics were counted in chapters: its pages are left out.
                session(comic, pages = 45, chapters = 2),
                session(comic, pages = 0, chapters = 3),
            ),
            books,
        )

        val stats = allTimeStats(sessions, emptyList(), books.values.toList(), today)

        assertEquals(30, stats.pages)
        assertEquals(5, stats.chapters)
        assertEquals(30 * 60_000L, stats.durationMs)
    }
}

class EstimatedChaptersTest {

    @Test
    fun `a chapter turned through to its last page counts`() {
        assertEquals(1, estimate(start = 0, end = 19, turned = 19, same = true, endCount = 20))
    }

    @Test
    fun `jumping to the last page does not count`() {
        assertEquals(0, estimate(start = 2, end = 17, turned = 6, same = true, endCount = 18))
    }

    @Test
    fun `stopping short of the last page does not count`() {
        assertEquals(0, estimate(start = 0, end = 12, turned = 12, same = true, endCount = 30))
    }

    @Test
    fun `moving on to the next chapter counts the one left`() {
        assertEquals(1, estimate(start = 1, end = 2, turned = 8, same = false, startCount = 9, endCount = 30))
    }

    @Test
    fun `jumping to another chapter counts nothing`() {
        assertEquals(0, estimate(start = 3, end = 0, turned = 0, same = false, startCount = 40, endCount = 40))
    }

    private fun estimate(start: Int, end: Int, turned: Int, same: Boolean, startCount: Int? = null, endCount: Int?) =
        dev.gavenda.kozeki.data.db.estimatedChapters(start, end, turned, same, startCount, endCount)
}
