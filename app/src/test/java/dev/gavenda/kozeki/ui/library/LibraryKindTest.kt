package dev.gavenda.kozeki.ui.library

import dev.gavenda.kozeki.data.model.Book
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryKindTest {

    private val book = Book(id = "b", title = "A book")
    private val comic = Book(id = "m", title = "A comic", isComic = true)

    @Test
    fun `books are what is not comic`() {
        assertTrue(LibraryKind.BOOKS.matches(book))
        assertFalse(LibraryKind.BOOKS.matches(comic))
    }

    @Test
    fun `comics are what is comic`() {
        assertTrue(LibraryKind.COMICS.matches(comic))
        assertFalse(LibraryKind.COMICS.matches(book))
    }
}
