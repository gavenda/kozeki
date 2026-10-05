package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.MetadataSource

/** The user's books, prepared once to tell which results of a metadata source are already among them. */
class OwnedBooks(books: List<Book> = emptyList()) {

    private val bySourceId: Map<Pair<MetadataSource, String>, Book> = buildMap {
        for (book in books) {
            val source = book.source ?: continue
            putIfAbsent(source to (book.sourceId ?: continue), book)
        }
    }

    private val byIsbn: Map<String, Book> = buildMap {
        for (book in books) isbnsOf(book.isbn13, book.isbn10).forEach { putIfAbsent(it, book) }
    }

    private val byTitle: Map<String, List<Book>> = books.groupBy { titleKey(it.title) }

    /**
     * The book of the user's that [result] is: the one linked to it, one with the same ISBN, or
     * one with the same title by the same author. The last catches an imported EPUB that was
     * never matched, and one whose ISBN belongs to another edition than the result's.
     */
    fun find(result: BookMetadata): Book? =
        bySourceId[result.source to result.sourceId]
            ?: isbnsOf(result.isbn13, result.isbn10).firstNotNullOfOrNull(byIsbn::get)
            ?: byTitle[titleKey(result.title)].orEmpty().firstOrNull { book ->
                book.authors.any { own -> result.authors.any { BookMatcher.authorSimilarity(own, it) >= SAME_AUTHOR } }
            }

    operator fun contains(result: BookMetadata): Boolean = find(result) != null

    /** Those among [results] the user already has, by source ID, each with the book it is. */
    fun among(results: List<BookMetadata>): Map<String, Book> = buildMap {
        for (result in results) find(result)?.let { put(result.sourceId, it) }
    }

    private fun isbnsOf(isbn13: String?, isbn10: String?): List<String> =
        listOfNotNull(isbn13, isbn10).mapNotNull(Isbn::toIsbn13)

    private fun titleKey(title: String): String = BookMatcher.normalize(BookMatcher.mainTitle(title))

    private companion object {
        const val SAME_AUTHOR = 0.9
    }
}
