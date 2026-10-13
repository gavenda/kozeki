package dev.gavenda.kozeki.ui.library

import dev.gavenda.kozeki.data.model.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryKindTest {

    private val book = Book(id = "b", title = "A book")
    private val manga = Book(id = "m", title = "A manga", isManga = true)

    @Test
    fun `books are what is not manga`() {
        assertTrue(LibraryKind.BOOKS.matches(book))
        assertFalse(LibraryKind.BOOKS.matches(manga))
    }

    @Test
    fun `manga are what is manga`() {
        assertTrue(LibraryKind.MANGA.matches(manga))
        assertFalse(LibraryKind.MANGA.matches(book))
    }

    @Test
    fun `the other kind is the one a toggle leads to`() {
        assertEquals(LibraryKind.MANGA, LibraryKind.BOOKS.other)
        assertEquals(LibraryKind.BOOKS, LibraryKind.MANGA.other)
    }
}
