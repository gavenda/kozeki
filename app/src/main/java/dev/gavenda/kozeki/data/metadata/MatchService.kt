package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.repository.LibraryRepository

/** Maps imported EPUBs to records in the metadata source. */
class MatchService(
    private val library: LibraryRepository,
    private val metadata: MetadataRepository,
) {
    /** Where books are matched by themselves. The source the user picks is for their own searches alone. */
    private val source = MetadataSource.HARDCOVER

    enum class Outcome {
        MATCHED,
        NEEDS_REVIEW,
        NOT_FOUND,

        /** Nobody is signed in to the source. Trying again by itself will not help. */
        UNAVAILABLE,

        /** Offline or rate-limited. Worth trying again later. */
        RETRY,
    }

    /**
     * Looks [bookId] up by ISBN, then by title and author. Only a confident hit is applied; a
     * merely plausible one is left for the user to confirm.
     */
    suspend fun match(bookId: String): Outcome {
        val book = library.getBook(bookId) ?: return Outcome.NOT_FOUND
        if (metadata.unavailableReason(source) != null) return Outcome.UNAVAILABLE

        return try {
            val byIsbn = book.isbn13?.let { metadata.findByIsbn(source, it).firstOrNull() }
            if (byIsbn != null) {
                library.applyMetadata(bookId, byIsbn)
                return Outcome.MATCHED
            }

            val ranked = candidates(book, source)
            val confident = BookMatcher.confidentMatch(ranked)
            when {
                confident != null -> {
                    library.applyMetadata(bookId, confident)
                    Outcome.MATCHED
                }

                ranked.isNotEmpty() -> {
                    library.setMatchStatus(bookId, MatchStatus.NEEDS_REVIEW)
                    Outcome.NEEDS_REVIEW
                }

                else -> {
                    library.setMatchStatus(bookId, MatchStatus.NOT_FOUND)
                    Outcome.NOT_FOUND
                }
            }
        } catch (e: MetadataException.Unavailable) {
            Outcome.UNAVAILABLE
        } catch (e: MetadataException.Unauthorized) {
            Outcome.UNAVAILABLE
        } catch (e: MetadataException) {
            Outcome.RETRY
        }
    }

    /** Possible matches for [book] on [source], best first. Served from the cache once fetched. */
    suspend fun candidates(book: Book, source: MetadataSource): List<BookMatcher.Scored> {
        val results = metadata.searchByTitleAndAuthor(
            source = source,
            title = BookMatcher.mainTitle(book.title).trim(),
            author = book.authors.firstOrNull(),
        )
        return BookMatcher.rank(book.title, book.authors, results)
    }

    /**
     * Works through every book still waiting for a lookup and fetches covers that could not be
     * downloaded earlier.
     *
     * @return true when something should be retried later.
     */
    suspend fun matchPending(): Boolean {
        var retry = false
        for (book in library.pendingMatches()) {
            when (match(book.id)) {
                Outcome.RETRY -> retry = true
                // No point hammering a source that cannot answer; the rest would fail the same way.
                Outcome.UNAVAILABLE -> return retry
                else -> Unit
            }
        }
        library.downloadMissingCovers()
        return linkAuthors() || retry
    }

    /**
     * Gives the books linked before authors' IDs were kept the IDs of theirs, which is what lets
     * an author be opened from the library.
     *
     * @return true when something should be retried later.
     */
    private suspend fun linkAuthors(): Boolean {
        for (book in library.withoutAuthorRefs()) {
            // A source without pages for authors has no IDs to give, however often it is asked.
            val linkedTo = book.source?.takeIf { it.hasAuthorPages } ?: continue
            val sourceId = book.sourceId ?: continue
            try {
                // Only the ones the book is already credited to: the names shown are not replaced.
                val refs = metadata.book(linkedTo, sourceId)?.authorRefs.orEmpty().filter { it.name in book.authors }
                if (refs.isNotEmpty()) library.setAuthorRefs(book.id, refs)
            } catch (e: MetadataException.Unavailable) {
                return false
            } catch (e: MetadataException.Unauthorized) {
                return false
            } catch (e: MetadataException) {
                return true
            }
        }
        return false
    }
}
