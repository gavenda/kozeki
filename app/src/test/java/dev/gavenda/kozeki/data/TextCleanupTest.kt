package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.model.singleLine
import dev.gavenda.kozeki.data.model.singleLineOrNull
import dev.gavenda.kozeki.data.model.singleLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextCleanupTest {

    @Test
    fun `collapses runs of spaces inside a name`() {
        assertEquals("Ursula K. Le Guin", "  Ursula   K.    Le Guin ".singleLine())
    }

    @Test
    fun `turns ideographic, no-break and typographic spaces into plain ones`() {
        assertEquals("川原 礫", "川原　　礫".singleLine())
        assertEquals("J. R. R. Tolkien", "J. R. R. Tolkien".singleLine())
    }

    @Test
    fun `flattens line breaks and indentation left over from the EPUB's XML`() {
        assertEquals("Frank Herbert", "\n\t\tFrank\r\n\t\tHerbert\n".singleLine())
    }

    @Test
    fun `drops zero-width characters without splitting the word`() {
        assertEquals("Le Guin", "﻿Le Gu​in".singleLine())
    }

    @Test
    fun `leaves a tidy name untouched`() {
        assertEquals("Gabriel García Márquez", "Gabriel García Márquez".singleLine())
    }

    @Test
    fun `a name of only whitespace is no name`() {
        assertNull(" 　\t".singleLineOrNull())
    }

    @Test
    fun `a list loses blanks and names that only differed by spacing`() {
        assertEquals(
            listOf("Terry Pratchett", "Neil Gaiman"),
            listOf("Terry  Pratchett", " ", "Neil Gaiman", "Terry Pratchett").singleLines(),
        )
    }
}
