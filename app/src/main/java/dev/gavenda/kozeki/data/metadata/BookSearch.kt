package dev.gavenda.kozeki.data.metadata

/**
 * A list of books read one page at a time. Pages can repeat a book and can hold nothing worth showing, so
 * this hands out only books it has not handed out before, and reads past a page that had none.
 *
 * Meant for one caller at a time.
 */
class BookSearch(private val readPage: suspend (page: Int) -> BookPage) {

    /** The results of a search for [query]. */
    constructor(metadata: MetadataRepository, query: String) : this({ page -> metadata.search(query, page) })

    private var page = 0
    private val seen = mutableSetOf<String>()

    /** Whether [next] may still find something. */
    var hasMore = true
        private set

    /** The next books of the list, empty once it has run out. A failure leaves it where it was. */
    suspend fun next(): List<BookMetadata> {
        repeat(MAX_BARREN_PAGES) {
            if (!hasMore) return emptyList()
            val result = readPage(page + 1)
            page++
            hasMore = result.hasMore
            val fresh = result.books.filter { seen.add(it.sourceId) }
            if (fresh.isNotEmpty()) return fresh
        }
        // Page after page of nothing: not worth more of the quota.
        hasMore = false
        return emptyList()
    }

    private companion object {
        const val MAX_BARREN_PAGES = 3
    }
}
