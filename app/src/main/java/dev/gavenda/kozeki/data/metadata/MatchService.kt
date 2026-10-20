package dev.gavenda.kozeki.data.metadata

import dev.gavenda.kozeki.data.comic.UpdateCheckProgress
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Maps imported EPUBs to records in the metadata source. */
class MatchService(
    private val library: LibraryRepository,
    private val metadata: MetadataRepository,
) {
    /** Where books are matched by themselves. The source the user picks is for their own searches alone. */
    private val source = MetadataSource.HARDCOVER

    private val lookingUp = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The books a lookup is under way for right now: the one being asked about and the ones in
     * line behind it. A book that is merely waiting, for a connection say, is not among them.
     */
    val matching: StateFlow<Set<String>> = lookingUp.asStateFlow()

    private val _run = MutableStateFlow(LookupRun())

    /** How far the run through the books waiting for a lookup has got, for the library's sheet. */
    val run: StateFlow<LookupRun> = _run.asStateFlow()

    /**
     * A run through the waiting books: [progress] while it goes, null once over. [finished] is that
     * the last run got through every book, rather than being stopped or giving up on a source.
     */
    data class LookupRun(val progress: UpdateCheckProgress? = null, val finished: Boolean = false)

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
        } catch (_: MetadataException.Unavailable) {
            Outcome.UNAVAILABLE
        } catch (_: MetadataException.Unauthorized) {
            Outcome.UNAVAILABLE
        } catch (_: MetadataException) {
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
     * Asks the source each book (not comic) is linked to for its details again, past the cache, and
     * puts what it says in place of what is kept, as linking it did. A book linked to nothing, or to
     * a source nobody is signed in to, is counted in [UpdateCheckProgress.skipped]. A book its source
     * cannot answer for now is left as it is. [pauseMs] is the wait between books.
     * [onProgress] hears of each book before it is asked, and once more when all are done.
     * Returns whether every book was answered.
     */
    suspend fun refreshAll(
        pauseMs: Long = 0,
        onProgress: (UpdateCheckProgress) -> Unit = {},
    ): Boolean {
        val books = library.observeAll().first().filter { !it.isComic }
        val available = MetadataSource.matchable.filter { metadata.unavailableReason(it) == null }.toSet()
        val linked = books.filter { it.source in available && it.sourceId != null }.sortedBy { it.title.lowercase() }
        val skipped = books.size - linked.size
        var complete = true
        for ((index, book) in linked.withIndex()) {
            val source = book.source ?: continue
            val sourceId = book.sourceId ?: continue
            onProgress(UpdateCheckProgress(checked = index, total = linked.size, current = book.title, skipped = skipped))
            try {
                metadata.book(source, sourceId, fresh = true)?.let { library.applyMetadata(book.id, it) }
            } catch (_: MetadataException) {
                complete = false
            }
            delay(pauseMs)
        }
        onProgress(UpdateCheckProgress(checked = linked.size, total = linked.size, current = null, skipped = skipped))
        return complete
    }

    /**
     * Works through every book still waiting for a lookup and fetches covers that could not be
     * downloaded earlier.
     *
     * @return true when something should be retried later.
     */
    suspend fun matchPending(): Boolean {
        var retry = false
        val pendingBooks = library.pendingMatches()
        val pending = pendingBooks.map { it.id }
        lookingUp.update { it + pending }
        var finished = false
        try {
            for ((index, book) in pendingBooks.withIndex()) {
                _run.value = LookupRun(UpdateCheckProgress(checked = index, total = pending.size, current = book.title))
                val outcome = match(book.id)
                lookingUp.update { it - book.id }
                when (outcome) {
                    Outcome.RETRY -> retry = true
                    // No point hammering a source that cannot answer; the rest would fail the same way.
                    Outcome.UNAVAILABLE -> return retry
                    else -> Unit
                }
            }
            finished = true
        } finally {
            // Also when the run is cut short, so nothing is left looking busy with no lookup behind it.
            lookingUp.update { it - pending.toSet() }
            // A run with nothing to look up was never shown, so it has nothing to finish.
            _run.value = LookupRun(finished = finished && pending.isNotEmpty())
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
    @Suppress("DestructuringDeclaration")
    private suspend fun linkAuthors(): Boolean {
        for (book in library.withoutAuthorRefs()) {
            // A source without pages for authors has no IDs to give, however often it is asked.
            val linkedTo = book.source?.takeIf { it.hasAuthorPages } ?: continue
            val sourceId = book.sourceId ?: continue
            try {
                // Only the ones the book is already credited to: the names shown are not replaced.
                val refs = metadata.book(linkedTo, sourceId)?.authorRefs.orEmpty().filter { it.name in book.authors }
                if (refs.isNotEmpty()) library.setAuthorRefs(book.id, refs)
            } catch (_: MetadataException.Unavailable) {
                return false
            } catch (_: MetadataException.Unauthorized) {
                return false
            } catch (_: MetadataException) {
                return true
            }
        }
        return false
    }
}
