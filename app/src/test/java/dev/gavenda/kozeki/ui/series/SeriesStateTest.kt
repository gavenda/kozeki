package dev.gavenda.kozeki.ui.series

import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.SeriesPage
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesStateTest {

    private fun remote(id: String, title: String, number: Double?) = BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = id,
        title = title,
        authors = listOf("Nozomu Mochitsuki"),
        series = "Tearmoon Empire (Light Novel)",
        seriesNumber = number,
    )

    private val page = SeriesPage(
        name = "Tearmoon Empire (Light Novel)",
        books = listOf(
            remote("1", "Tearmoon Empire: Volume 1", 1.0),
            remote("2", "Tearmoon Empire: Volume 2", 2.0),
            remote("3", "Tearmoon Empire: Volume 3", 3.0),
        ),
    )

    @Test
    fun `the user's books are those named in the series and those the source lists, the rest stay the source's`() {
        val books = listOf(
            // Linked to the source's volume 2, with no series of its own.
            Book(id = "a", title = "Tearmoon Empire: Volume 2", source = MetadataSource.HARDCOVER, sourceId = "2"),
            // Named in the series as the EPUB had it, under the name it was opened by.
            Book(id = "b", title = "A side story", series = "Tearmoon Empire", seriesNumber = 2.5),
            Book(id = "c", title = "Something else", series = "Dune", seriesNumber = 1.0),
        )

        val state = seriesState("Tearmoon Empire", page, books)

        assertEquals("Tearmoon Empire (Light Novel)", state.name)
        assertEquals(listOf("a" to 2.0, "b" to 2.5), state.owned.map { it.book.id to it.number })
        assertEquals(listOf("1", "3"), state.others.map { it.sourceId })
        assertFalse(state.notFound)
    }

    @Test
    fun `a book in both ways is listed once, with its own number`() {
        val books = listOf(
            Book(
                id = "a",
                title = "Tearmoon Empire: Volume 1",
                series = "Tearmoon Empire (Light Novel)",
                seriesNumber = 1.0,
                source = MetadataSource.HARDCOVER,
                sourceId = "1",
            ),
        )

        val state = seriesState("Tearmoon Empire (Light Novel)", page, books)

        assertEquals(listOf("a" to 1.0), state.owned.map { it.book.id to it.number })
        assertEquals(listOf("2", "3"), state.others.map { it.sourceId })
    }

    @Test
    fun `the user's books show before the source answers, and a series it lacks is not found`() {
        val books = listOf(Book(id = "a", title = "Dune", series = "Dune", seriesNumber = 1.0))

        assertEquals(listOf("a"), seriesState("Dune", null, books).owned.map { it.book.id })
        assertTrue(seriesState("Dune", SeriesPage(), books).notFound)
    }
}
