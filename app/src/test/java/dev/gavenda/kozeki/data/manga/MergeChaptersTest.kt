package dev.gavenda.kozeki.data.manga

import dev.gavenda.kozeki.data.db.MangaChapterEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeChaptersTest {

    private fun remote(url: String, name: String = url, number: Float = -1f) = SChapter.create().apply {
        this.url = url
        this.name = name
        chapter_number = number
    }

    private fun row(url: String, read: Boolean = false, page: Int = 0, order: Int = 0, deleted: Long? = null) =
        MangaChapterEntity(
            bookId = "b",
            url = url,
            name = url,
            sourceOrder = order,
            read = read,
            lastPageRead = page,
            sync = SyncStamp(createdAt = 1, updatedAt = 1, deletedAt = deleted),
        )

    @Test
    fun `new chapters are added in the order listed`() {
        val result = mergeChapters("b", emptyList(), listOf(remote("/3"), remote("/2"), remote("/1")), now = 10)

        assertEquals(listOf("/3", "/2", "/1"), result.map { it.url })
        assertEquals(listOf(0, 1, 2), result.map { it.sourceOrder })
        assertTrue(result.all { !it.read && it.lastPageRead == 0 && it.sync.createdAt == 10L })
    }

    @Test
    fun `what the user did survives a sync`() {
        val existing = listOf(row("/1", read = true, page = 9, order = 5))

        val result = mergeChapters("b", existing, listOf(remote("/2"), remote("/1", name = "Renamed")), now = 10)

        val kept = result.single { it.url == "/1" }
        assertEquals(true, kept.read)
        assertEquals(9, kept.lastPageRead)
        assertEquals("Renamed", kept.name)
        assertEquals(1, kept.sourceOrder)
    }

    @Test
    fun `a chapter that is as it was is not written again`() {
        val existing = listOf(row("/1", order = 0))

        assertTrue(mergeChapters("b", existing, listOf(remote("/1")), now = 10).isEmpty())
    }

    @Test
    fun `a chapter the source dropped is put away with its progress`() {
        val existing = listOf(row("/1", read = true, page = 4))

        val result = mergeChapters("b", existing, listOf(remote("/2")), now = 10)

        val gone = result.single { it.url == "/1" }
        assertEquals(10L, gone.sync.deletedAt)
        assertEquals(true, gone.read)
    }

    @Test
    fun `a chapter that comes back is restored`() {
        val existing = listOf(row("/1", read = true, deleted = 5))

        val back = mergeChapters("b", existing, listOf(remote("/1")), now = 10).single()

        assertNull(back.sync.deletedAt)
        assertEquals(true, back.read)
    }

    @Test
    fun `a chapter listed twice is one chapter`() {
        val result = mergeChapters("b", emptyList(), listOf(remote("/1"), remote("/1")), now = 10)

        assertEquals(1, result.size)
    }
}
