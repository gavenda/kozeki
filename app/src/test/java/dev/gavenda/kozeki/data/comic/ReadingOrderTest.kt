package dev.gavenda.kozeki.data.comic

import dev.gavenda.kozeki.data.model.ComicChapter
import dev.gavenda.kozeki.data.model.inReadingOrder
import dev.gavenda.kozeki.data.model.recognizeChapterNumber
import dev.gavenda.kozeki.data.model.resumeChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingOrderTest {

    private fun chapter(url: String, order: Int, number: Float? = null, read: Boolean = false) = ComicChapter(
        url = url,
        name = url,
        number = number,
        scanlator = null,
        uploadedAt = null,
        sourceOrder = order,
        read = read,
        lastPageRead = 0,
        pageCount = null,
    )

    @Test
    fun `a list that starts with the newest is read from its end`() {
        val listed = listOf(chapter("c3", 0, 3f), chapter("c2", 1, 2f), chapter("c1", 2, 1f))

        assertEquals(listOf("c1", "c2", "c3"), listed.inReadingOrder().map { it.url })
    }

    @Test
    fun `a list that starts with the oldest is kept`() {
        val listed = listOf(chapter("c1", 0, 1f), chapter("c2", 1, 2f), chapter("c3", 2, 3f))

        assertEquals(listOf("c1", "c2", "c3"), listed.inReadingOrder().map { it.url })
    }

    @Test
    fun `chapters without numbers are taken to be listed newest first`() {
        val listed = listOf(chapter("b", 0), chapter("a", 1))

        assertEquals(listOf("a", "b"), listed.inReadingOrder().map { it.url })
    }

    @Test
    fun `reading goes on from the first chapter not finished`() {
        val ordered = listOf(chapter("c1", 2, 1f, read = true), chapter("c2", 1, 2f), chapter("c3", 0, 3f))

        assertEquals("c2", ordered.resumeChapter()?.url)
    }

    @Test
    fun `nothing is left to resume once every chapter is read`() {
        assertNull(listOf(chapter("c1", 0, 1f, read = true)).resumeChapter())
    }

    @Test
    fun `numbers are written without a trailing zero`() {
        assertEquals("12", chapter("x", 0, 12f).numberText)
        assertEquals("12.5", chapter("x", 0, 12.5f).numberText)
        assertNull(chapter("x", 0, null).numberText)
    }

    @Test
    fun `a chapter number is read from the name when the source gives none`() {
        assertEquals(1f, recognizeChapterNumber("Chapter 1 - Tutorial (1)"))
        assertEquals(12.5f, recognizeChapterNumber("Ch. 12.5: Extra"))
        assertEquals(164f, recognizeChapterNumber("Chapter 164 - Gnometree"))
        assertNull(recognizeChapterNumber("Prologue"))
    }
}
