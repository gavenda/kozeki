package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.CompletedBook
import dev.gavenda.kozeki.ui.statistics.longestFinished
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LongestBooksTest {

    private var readThroughs = 0

    private fun finished(book: Book) = CompletedBook(book, LocalDate.of(2026, 10, 9), "read-${readThroughs++}")

    private fun paper(id: String, pages: Int?, ownPages: Int? = null) =
        Book(id = id, title = id, pageCount = pages, physicalPageCount = ownPages)

    private fun epub(id: String, pages: Int?, positions: Int? = null) =
        Book(id = id, title = id, pageCount = pages, positionCount = positions, inLibrary = true)

    private fun ranked(completed: List<CompletedBook>, physical: Boolean, limit: Int = 8) =
        longestFinished(completed, physical, limit).map { (book, pages) -> book.id to pages }

    @Test
    fun `physical books and EPUBs are ranked apart, longest first`() {
        val completed = listOf(
            finished(paper("paperback", 384)),
            finished(epub("novella", 120)),
            finished(paper("doorstop", 900)),
            finished(epub("epic", 1100)),
        )

        assertEquals(listOf("doorstop" to 900, "paperback" to 384), ranked(completed, physical = true))
        assertEquals(listOf("epic" to 1100, "novella" to 120), ranked(completed, physical = false))
    }

    @Test
    fun `a physical copy is as long as the user said it is`() {
        val completed = listOf(finished(paper("hardback", pages = 384, ownPages = 412)))

        assertEquals(listOf("hardback" to 412), ranked(completed, physical = true))
    }

    @Test
    fun `an EPUB without a page count goes by its positions`() {
        val completed = listOf(finished(epub("unmatched", pages = null, positions = 272)))

        assertEquals(listOf("unmatched" to 272), ranked(completed, physical = false))
    }

    @Test
    fun `a book finished twice is listed once, and one of unknown length not at all`() {
        val twice = paper("twice", 300)
        val completed = listOf(finished(twice), finished(twice), finished(paper("unknown", null)), finished(epub("blank", 0)))

        assertEquals(listOf("twice" to 300), ranked(completed, physical = true))
        assertEquals(emptyList<Pair<String, Int>>(), ranked(completed, physical = false))
    }

    @Test
    fun `only the longest few are kept`() {
        val completed = (1..5).map { finished(paper("book-$it", it * 100)) }

        assertEquals(listOf("book-5" to 500, "book-4" to 400), ranked(completed, physical = true, limit = 2))
    }
}
