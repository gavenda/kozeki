package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.ui.reader.anchorProgression
import dev.gavenda.kozeki.ui.reader.indexOfChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterLocationTest {

    // Two chapters in the first file, one in the second, and an entry that points nowhere.
    private val anchors = listOf(0 to 0.0, 0 to 0.6, 1 to 0.0, null to 0.0, 2 to 0.3)

    @Test
    fun `chapters sharing a file are told apart by how far in they start`() {
        assertEquals(0, indexOfChapter(anchors, spineIndex = 0, progression = 0.2))
        assertEquals(1, indexOfChapter(anchors, spineIndex = 0, progression = 0.6))
        assertEquals(1, indexOfChapter(anchors, spineIndex = 0, progression = 0.95))
    }

    @Test
    fun `a file with no entry of its own belongs to the chapter before it`() {
        assertEquals(2, indexOfChapter(anchors, spineIndex = 1, progression = 0.5))
        assertEquals(2, indexOfChapter(anchors, spineIndex = 2, progression = 0.1))
        assertEquals(4, indexOfChapter(anchors, spineIndex = 2, progression = 0.4))
        assertEquals(4, indexOfChapter(anchors, spineIndex = 7, progression = 0.0))
    }

    @Test
    fun `before the first entry, or with no known place, there is no chapter`() {
        assertNull(indexOfChapter(listOf(1 to 0.0), spineIndex = 0, progression = 0.9))
        assertNull(indexOfChapter(anchors, spineIndex = null, progression = 0.5))
    }

    @Test
    fun `an anchor is placed by its offset in the markup`() {
        val markup = "<html><body>" + "x".repeat(488) + "<h2 id=\"chap02\">Two</h2>" + "y".repeat(476) + "</body></html>"
        assertEquals(0.5, anchorProgression(markup, "chap02")!!, 0.01)
        assertEquals(0.5, anchorProgression(markup.replace("id=\"chap02\"", "name='chap02'"), "chap02")!!, 0.01)
        assertNull(anchorProgression(markup, "chap03"))
        assertNull(anchorProgression(markup, "chap0"))
    }
}
