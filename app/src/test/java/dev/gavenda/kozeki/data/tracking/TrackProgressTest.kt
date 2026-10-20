package dev.gavenda.kozeki.data.tracking

import dev.gavenda.kozeki.data.db.ComicTrackEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.ComicChapter
import dev.gavenda.kozeki.data.model.TrackStatus
import dev.gavenda.kozeki.data.tracking.anilist.ScoreFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TrackProgressTest {

    private fun chapter(url: String, number: Float?, read: Boolean = false) = ComicChapter(
        url = url,
        name = url,
        number = number,
        scanlator = null,
        uploadedAt = null,
        sourceOrder = 0,
        read = read,
        lastPageRead = 0,
        pageCount = null,
    )

    private fun track(
        status: TrackStatus = TrackStatus.PLANNING,
        progress: Int = 0,
        total: Int? = 10,
        startedOn: Long? = null,
        completedOn: Long? = null,
    ) = ComicTrackEntity(
        bookId = "b",
        mediaId = 1,
        title = "Comic",
        status = status,
        progress = progress,
        totalChapters = total,
        startedOn = startedOn,
        completedOn = completedOn,
        sync = SyncStamp.created(0),
    )

    @Test
    fun `progress is the highest chapter number read, whole`() {
        val chapters = listOf(
            chapter("a", 1f, read = true),
            chapter("b", 12.5f, read = true),
            chapter("c", 20f, read = false),
            chapter("d", null, read = true),
        )

        assertEquals(12, highestChapterRead(chapters))
    }

    @Test
    fun `nothing read is no progress`() {
        assertEquals(0, highestChapterRead(listOf(chapter("a", 1f), chapter("b", null, read = true))))
    }

    @Test
    fun `only chapters with a number up to the progress are marked read`() {
        val chapters = listOf(
            chapter("a", 1f, read = true),
            chapter("b", 2f),
            chapter("c", 3.5f),
            chapter("d", 3f),
            chapter("e", null),
        )

        assertEquals(listOf("b", "d"), chaptersToMarkRead(chapters, 3))
        assertEquals(emptyList<String>(), chaptersToMarkRead(chapters, 0))
    }

    @Test
    fun `reading the first chapter starts a planned comic`() {
        val next = advance(track(), progress = 1, today = 100)

        assertEquals(TrackStatus.CURRENT, next.status)
        assertEquals(1, next.progress)
        assertEquals(100L, next.startedOn)
        assertNull(next.completedOn)
    }

    @Test
    fun `reading the last chapter completes it`() {
        val next = advance(track(TrackStatus.CURRENT, progress = 9, startedOn = 5), progress = 10, today = 100)

        assertEquals(TrackStatus.COMPLETED, next.status)
        assertEquals(5L, next.startedOn)
        assertEquals(100L, next.completedOn)
    }

    @Test
    fun `progress never goes back`() {
        val before = track(TrackStatus.CURRENT, progress = 7)

        assertSame(before, advance(before, progress = 4, today = 100))
        assertSame(before, advance(before, progress = 7, today = 100))
    }

    @Test
    fun `a comic with no known end is not completed by reading`() {
        val next = advance(track(TrackStatus.CURRENT, progress = 50, total = null), progress = 200, today = 100)

        assertEquals(TrackStatus.CURRENT, next.status)
    }

    @Test
    fun `reading goes on from a pause or a drop`() {
        assertEquals(TrackStatus.CURRENT, advance(track(TrackStatus.PAUSED, 3), 4, 100).status)
        assertEquals(TrackStatus.CURRENT, advance(track(TrackStatus.DROPPED, 3), 4, 100).status)
    }

    @Test
    fun `completing from the app fills in the end and the dates`() {
        val next = withStatus(track(TrackStatus.CURRENT, progress = 4), TrackStatus.COMPLETED, today = 100)

        assertEquals(TrackStatus.COMPLETED, next.status)
        assertEquals(10, next.progress)
        assertEquals(100L, next.startedOn)
        assertEquals(100L, next.completedOn)
    }

    @Test
    fun `a comic being repeated stays so when the book goes back to reading`() {
        val before = track(TrackStatus.REPEATING)

        assertSame(before, withStatus(before, TrackStatus.CURRENT, today = 100))
    }

    @Test
    fun `scores convert to the scale of the account and back`() {
        assertEquals(7, ScoreFormat.POINT_10.toPosition(70))
        assertEquals(70, ScoreFormat.POINT_10.toScore(7))
        assertEquals(4, ScoreFormat.POINT_5.toPosition(80))
        assertEquals(80, ScoreFormat.POINT_5.toScore(4))
        assertEquals(0, ScoreFormat.POINT_3.toPosition(0))
        assertEquals(2, ScoreFormat.POINT_3.toPosition(60))
        assertEquals(84, ScoreFormat.POINT_3.toScore(3))
        assertEquals(85, ScoreFormat.POINT_10_DECIMAL.toScore(85))
        assertEquals(100, ScoreFormat.POINT_10_DECIMAL.positions)
    }
}
