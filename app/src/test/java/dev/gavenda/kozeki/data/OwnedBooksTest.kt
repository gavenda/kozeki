package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.OwnedBooks
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedBooksTest {

    private fun result(
        sourceId: String,
        title: String,
        vararg authors: String,
        isbn13: String? = null,
        isbn10: String? = null,
    ) = BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = sourceId,
        title = title,
        authors = authors.toList(),
        isbn13 = isbn13,
        isbn10 = isbn10,
    )

    private val owned = OwnedBooks(
        listOf(
            Book(id = "linked", title = "Dune", source = MetadataSource.HARDCOVER, sourceId = "312"),
            Book(id = "isbn13", title = "Left Hand", isbn13 = "9780441478125"),
            Book(id = "isbn10", title = "Geometry", isbn10 = "0306406152"),
            Book(id = "epub", title = "The Hobbit: Or There and Back Again", authors = listOf("Tolkien, J. R. R.")),
        ),
    )

    @Test
    fun `a linked book is found by its source id`() {
        assertTrue(result("312", "Dune Messiah") in owned)
    }

    @Test
    fun `an unlinked book is found by its isbn in either form`() {
        assertTrue(result("a", "The Left Hand of Darkness", isbn13 = "9780441478125") in owned)
        assertTrue(result("b", "The Left Hand of Darkness", isbn10 = "0441478123") in owned)
        assertTrue(result("c", "Error Correction", isbn13 = "9780306406157") in owned)
    }

    @Test
    fun `another edition is found by title and author`() {
        assertTrue(result("d", "The Hobbit", "J.R.R. Tolkien", isbn13 = "9780261103344") in owned)
    }

    @Test
    fun `the same title by someone else is not the same book`() {
        assertFalse(result("e", "The Hobbit", "Jane Doe") in owned)
        assertFalse(result("f", "The Hobbit") in owned)
        assertFalse(result("g", "Dune", "Frank Herbert") in owned)
    }

    @Test
    fun `among pairs the owned results with the books they are`() {
        val results = listOf(result("312", "Dune"), result("x", "Children of Dune", "Frank Herbert"))
        assertEquals(mapOf("312" to "linked"), owned.among(results).mapValues { it.value.id })
    }
}
