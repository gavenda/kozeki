package dev.gavenda.kozeki.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaChapterPagesTest {

    @Test
    fun `the page in view loads first and then its neighbours`() {
        assertEquals(listOf(5, 4, 6, 7, 8, 9), MangaChapterPages.loadOrder(index = 5, count = 20))
    }

    @Test
    fun `nothing past the few pages ahead is asked for`() {
        val order = MangaChapterPages.loadOrder(index = 0, count = 200)

        assertEquals(listOf(0, 1, 2, 3, 4), order)
    }

    @Test
    fun `the end of a chapter keeps to the pages there are`() {
        assertEquals(listOf(9, 8), MangaChapterPages.loadOrder(index = 9, count = 10))
    }

    @Test
    fun `a chapter of one page loads that page`() {
        assertEquals(listOf(0), MangaChapterPages.loadOrder(index = 0, count = 1))
    }

    @Test
    fun `chunks grow from the start size to the end size`() {
        val chunks = MangaChapterPages.rollingChunked((0..14).toList(), start = 3, end = 5)

        assertEquals(listOf(3, 4, 5, 3), chunks.map { it.size })
        assertEquals((0..14).toList(), chunks.flatten())
    }

    @Test
    fun `a window of a few pages is two chunks at most`() {
        val chunks = MangaChapterPages.rollingChunked(MangaChapterPages.loadOrder(10, 100), start = 3, end = 5)

        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.size <= 5 })
    }

    @Test
    fun `genres that mark a webtoon are told by their words`() {
        assertTrue(MangaReaderPreferences.looksLikeWebtoon(listOf("Action", "Manhwa")))
        assertTrue(MangaReaderPreferences.looksLikeWebtoon(listOf(" Webtoon ")))
        assertTrue(MangaReaderPreferences.looksLikeWebtoon(listOf("Long Strip")))
        assertFalse(MangaReaderPreferences.looksLikeWebtoon(listOf("Action", "Fantasy")))
        assertFalse(MangaReaderPreferences.looksLikeWebtoon(emptyList()))
    }
}
