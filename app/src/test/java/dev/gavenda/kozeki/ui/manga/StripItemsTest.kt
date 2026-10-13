package dev.gavenda.kozeki.ui.manga

import dev.gavenda.kozeki.data.model.MangaChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class StripItemsTest {

    private fun chapter(url: String) = MangaChapter(
        url = url,
        name = url,
        number = null,
        scanlator = null,
        uploadedAt = null,
        sourceOrder = 0,
        read = false,
        lastPageRead = 0,
        pageCount = null,
    )

    private val a = chapter("a")
    private val b = chapter("b")

    private val items = listOf(
        StripItem.Start("a", a, previous = null),
        StripItem.Placeholder("a", 0),
        StripItem.Placeholder("a", 1),
        StripItem.Gap("a", 1, finished = a, next = b),
        StripItem.Placeholder("b", 0),
        StripItem.End("b", 0, finished = b, next = null),
    )

    @Test
    fun `a page is found by its chapter and number, past the breaks`() {
        assertEquals(1, items.firstIndexOf("a", 0))
        assertEquals(2, items.firstIndexOf("a", 1))
        assertEquals(4, items.firstIndexOf("b", 0))
    }

    @Test
    fun `a page that is not there is the top`() {
        assertEquals(0, items.firstIndexOf("c", 0))
    }

    @Test
    fun `the keys of the items are all different`() {
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }
}
