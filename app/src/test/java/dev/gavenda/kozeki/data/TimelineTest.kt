package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.db.PhysicalReadingEntity
import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.TimelineEntry
import dev.gavenda.kozeki.data.repository.timeline
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {

    private val epub = Book(id = "epub", title = "Piranesi")
    private val paper = Book(id = "paper", title = "Children of Time")
    private val books = listOf(epub, paper).associateBy { it.id }
    private var ids = 0

    private fun session(
        startedAt: Long,
        minutes: Long,
        startChapter: String? = null,
        endChapter: String? = null,
        bookId: String = epub.id,
    ) = ReadingSessionEntity(
        id = "session-${ids++}",
        bookId = bookId,
        readThroughId = null,
        startedAt = startedAt,
        endedAt = startedAt + minutes * 60_000,
        durationMs = minutes * 60_000,
        day = 0L,
        startProgression = 0.0,
        endProgression = 0.0,
        startChapter = startChapter,
        endChapter = endChapter,
        sync = SyncStamp.created(0L),
    )

    private fun pages(recordedAt: Long, from: Int, to: Int, bookId: String = paper.id) = PhysicalReadingEntity(
        id = "reading-${ids++}",
        bookId = bookId,
        readThroughId = null,
        recordedAt = recordedAt,
        day = 0L,
        startPage = from,
        endPage = to,
        sync = SyncStamp.created(0L),
    )

    @Test
    fun `a session is placed at its start, under the chapter it ended in`() {
        val entries = timeline(
            sessions = listOf(session(1_000, minutes = 20, startChapter = "One", endChapter = "Two")),
            physical = emptyList(),
            books = books,
        )

        assertEquals(listOf(TimelineEntry.Session(epub, 1_000, 20 * 60_000L, "Two")), entries)
    }

    @Test
    fun `a session that ended outside any chapter keeps the one it started in`() {
        val entries = timeline(listOf(session(1_000, minutes = 5, startChapter = "One")), emptyList(), books)

        assertEquals("One", (entries.single() as TimelineEntry.Session).chapter)
    }

    @Test
    fun `pages of a physical copy are placed at when they were entered`() {
        val entries = timeline(emptyList(), listOf(pages(7_000, from = 180, to = 212)), books)

        assertEquals(listOf(TimelineEntry.Pages(paper, 7_000, 180, 212)), entries)
    }

    @Test
    fun `sessions and pages share one timeline, in the order they happened`() {
        val entries = timeline(
            sessions = listOf(session(2_000, minutes = 10), session(9_000, minutes = 15)),
            physical = listOf(pages(1_000, from = 0, to = 30), pages(5_000, from = 30, to = 64)),
            books = books,
        )

        assertEquals(listOf(1_000L, 2_000L, 5_000L, 9_000L), entries.map { it.at })
        assertEquals(listOf(paper, epub, paper, epub), entries.map { it.book })
    }

    @Test
    fun `what was read of a book that is gone is left out`() {
        val entries = timeline(
            sessions = listOf(session(1_000, minutes = 10, bookId = "gone")),
            physical = listOf(pages(2_000, from = 0, to = 30, bookId = "gone"), pages(3_000, from = 0, to = 12)),
            books = books,
        )

        assertEquals(listOf(TimelineEntry.Pages(paper, 3_000, 0, 12)), entries)
    }
}
