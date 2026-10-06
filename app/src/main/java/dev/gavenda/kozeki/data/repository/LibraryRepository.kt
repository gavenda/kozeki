package dev.gavenda.kozeki.data.repository

import android.net.Uri
import androidx.room.withTransaction
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.KozekiDatabase
import dev.gavenda.kozeki.data.db.NoteEntity
import dev.gavenda.kozeki.data.db.ReadThroughEntity
import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.epub.EpubInfo
import dev.gavenda.kozeki.data.epub.Readium
import dev.gavenda.kozeki.data.epub.extractInfo
import dev.gavenda.kozeki.data.files.BookStorage
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMatcher
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.CoverDownloader
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.BookDraft
import dev.gavenda.kozeki.data.model.ImportResult
import dev.gavenda.kozeki.data.model.Isbn
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.Note
import dev.gavenda.kozeki.data.model.ReadOutcome
import dev.gavenda.kozeki.data.model.ReadThrough
import dev.gavenda.kozeki.data.model.ReadingProgress
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.SessionDraft
import dev.gavenda.kozeki.data.model.singleLine
import dev.gavenda.kozeki.data.model.singleLines
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.util.use

/** Books, their lifecycle and everything the reader writes back. */
class LibraryRepository(
    private val db: KozekiDatabase,
    private val storage: BookStorage,
    private val readium: Readium,
    private val covers: CoverDownloader,
    private val clock: Clock,
    private val appScope: CoroutineScope,
) {
    private val books = db.bookDao()
    private val readThroughs = db.readThroughDao()
    private val sessions = db.readingSessionDao()
    private val notes = db.noteDao()

    init {
        appScope.launch {
            books.clearWishlistFavorites(clock.millis())
            books.planWishlist(clock.millis())
            books.unassumePurchases(clock.millis())
            readThroughs.renumberDuplicates(clock.millis())
        }
    }

    // ---- Queries ---------------------------------------------------------------------------

    fun observeLibrary(): Flow<List<Book>> = books.observeLibrary().mapBooks()

    fun observeWithoutFile(): Flow<List<Book>> = books.observeWithoutFile().mapBooks()

    fun observeAll(): Flow<List<Book>> = books.observeAll().mapBooks()

    fun observeBook(id: String): Flow<Book?> =
        books.observe(id).map { it?.toBook() }.flowOn(Dispatchers.IO)

    /** Places typed in for earlier purchases, most recent first. */
    fun observePurchaseLocations(): Flow<List<String>> = books.observePurchaseLocations().distinctUntilChanged()

    suspend fun getBook(id: String): Book? = books.get(id)?.toBook()

    fun observeNotes(bookId: String): Flow<List<Note>> = notes.observeForBook(bookId).map { list ->
        list.map { Note(it.id, it.bookId, it.text, it.chapterTitle, it.sync.createdAt) }
    }

    /** Total time spent reading [bookId] in the built-in reader, in milliseconds. */
    fun observeReadingTime(bookId: String): Flow<Long> =
        sessions.observeForBook(bookId).map { list -> list.sumOf { it.durationMs } }

    fun observeReadThroughs(bookId: String): Flow<List<ReadThrough>> =
        readThroughs.observeForBook(bookId).map { list ->
            list.map { ReadThrough(it.id, it.number, it.startedAt, it.finishedAt, it.outcome) }
        }

    // ---- Import ----------------------------------------------------------------------------

    /**
     * Copies the EPUB behind [uri] into the app and creates or completes a book record for it.
     * Works fully offline: metadata comes from the EPUB itself and a lookup is left pending.
     *
     * @param attachTo A book the user explicitly picked to receive the file. Without it, a wishlist
     *   or purchased entry for the same book is found automatically.
     */
    suspend fun importEpub(uri: Uri, attachTo: String? = null): ImportResult {
        val staged = try {
            storage.copyToStaging(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.Failed(ImportResult.Failed.Reason.UNREADABLE, null)
        }
        try {
            books.findByHash(staged.sha256)?.let { existing ->
                return if (storage.hasEpub(existing.id)) {
                    staged.file.delete()
                    ImportResult.AlreadyInLibrary(existing.id, existing.title)
                } else {
                    // The record survived a restore or came from another device; give it its file back.
                    storage.adopt(staged, existing.id)
                    books.upsert(existing.copy(sync = existing.sync.touched(clock.millis())))
                    ImportResult.Attached(existing.id, existing.title)
                }
            }

            val info = try {
                readium.open(staged.file).use { it.extractInfo() }
            } catch (e: Readium.OpenException) {
                staged.file.delete()
                val reason = when (e.failure) {
                    Readium.OpenFailure.PROTECTED -> ImportResult.Failed.Reason.PROTECTED
                    Readium.OpenFailure.NOT_AN_EPUB -> ImportResult.Failed.Reason.NOT_AN_EPUB
                }
                return ImportResult.Failed(reason, staged.displayName)
            }

            val now = clock.millis()
            val title = info.title ?: titleFromFileName(staged.displayName)
            val wanted = attachTo?.let { books.get(it) }?.takeIf { !storage.hasEpub(it.id) }
                ?: findEntryAwaitingFile(title, info)

            if (wanted != null) {
                storage.adopt(staged, wanted.id)
                // A cover the user chose stays; the EPUB's own is still there if they remove it.
                val cover = info.cover?.takeIf { !wanted.customCover }?.let { storage.saveCover(wanted.id, it) }
                if (cover != null) storage.deleteCover(wanted.coverFile)
                books.upsert(
                    wanted.copy(
                        // The file alone is no purchase: a recorded one stays, a wish becomes a download.
                        acquisition = if (wanted.acquisition == Acquisition.PURCHASED) {
                            Acquisition.PURCHASED
                        } else {
                            Acquisition.DOWNLOADED
                        },
                        fileHash = staged.sha256,
                        fileSize = staged.size,
                        originalFileName = staged.displayName,
                        positionCount = info.positionCount,
                        coverFile = cover ?: wanted.coverFile,
                        language = wanted.language ?: info.language,
                        isbn13 = wanted.isbn13 ?: info.isbn13,
                        sync = wanted.sync.touched(now),
                    ),
                )
                return ImportResult.Attached(wanted.id, wanted.title)
            }

            val bookId = UUID.randomUUID().toString()
            storage.adopt(staged, bookId)
            books.upsert(
                BookEntity(
                    id = bookId,
                    title = title,
                    subtitle = info.subtitle,
                    authors = info.authors,
                    description = info.description,
                    publisher = info.publisher,
                    publishedDate = info.publishedDate,
                    language = info.language,
                    isbn13 = info.isbn13,
                    isbn10 = info.isbn13?.let(Isbn::toIsbn10),
                    categories = info.subjects,
                    coverFile = info.cover?.let { storage.saveCover(bookId, it) },
                    state = ReadingState.PLANNED,
                    acquisition = Acquisition.DOWNLOADED,
                    matchStatus = MatchStatus.PENDING,
                    fileHash = staged.sha256,
                    fileSize = staged.size,
                    originalFileName = staged.displayName,
                    positionCount = info.positionCount,
                    sync = SyncStamp.created(now),
                ),
            )
            return ImportResult.Imported(bookId, title)
        } catch (e: Exception) {
            staged.file.delete()
            throw e
        }
    }

    /** A wishlist or purchased entry for the same book, so the import completes it instead of duplicating it. */
    private suspend fun findEntryAwaitingFile(title: String, info: EpubInfo): BookEntity? {
        val candidates = books.withoutFile()
        info.isbn13?.let { isbn ->
            candidates.firstOrNull { it.isbn13 == isbn || it.isbn10?.let(Isbn::toIsbn13) == isbn }?.let { return it }
        }
        val normalizedTitle = BookMatcher.normalize(BookMatcher.mainTitle(title))
        val author = info.authors.firstOrNull() ?: return null
        return candidates.firstOrNull { candidate ->
            BookMatcher.normalize(BookMatcher.mainTitle(candidate.title)) == normalizedTitle &&
                candidate.authors.any { BookMatcher.authorSimilarity(it, author) >= 0.9 }
        }
    }

    private fun titleFromFileName(name: String?): String =
        name?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled"

    // ---- Adding and metadata ---------------------------------------------------------------

    /** Adds a book found in a metadata source to the wishlist or the purchased list. */
    suspend fun addFromMetadata(metadata: BookMetadata, acquisition: Acquisition): String {
        books.findBySource(metadata.source, metadata.sourceId).firstOrNull()?.let { return it.id }

        val bookId = UUID.randomUUID().toString()
        books.upsert(
            BookEntity(
                id = bookId,
                title = metadata.title,
                subtitle = metadata.subtitle,
                authors = metadata.authors,
                authorRefs = metadata.authorRefs,
                description = metadata.description,
                publisher = metadata.publisher,
                publishedDate = metadata.publishedDate,
                language = metadata.language,
                pageCount = metadata.pageCount,
                isbn10 = metadata.isbn10,
                isbn13 = metadata.isbn13,
                categories = metadata.categories,
                coverUrl = metadata.coverUrl,
                state = ReadingState.PLANNED,
                acquisition = acquisition,
                purchasedOn = if (acquisition == Acquisition.PURCHASED) today().toEpochDay() else null,
                source = metadata.source,
                sourceId = metadata.sourceId,
                sourceUrl = metadata.infoUrl,
                matchStatus = MatchStatus.MATCHED,
                sync = SyncStamp.created(clock.millis()),
            ),
        )
        appScope.launch { downloadMissingCover(bookId) }
        return bookId
    }

    /**
     * Adds a book the user typed in themselves. It is linked to no source and no lookup is queued
     * for it, since going without one is the point. [cover] is a picture they picked for it.
     */
    suspend fun addManually(draft: BookDraft, acquisition: Acquisition, cover: Uri? = null): String {
        val bookId = UUID.randomUUID().toString()
        val isbn13 = draft.isbn?.let(Isbn::toIsbn13)
        val coverFile = cover?.let { storage.saveCover(bookId, it) }
        books.upsert(
            BookEntity(
                id = bookId,
                title = draft.title.singleLine(),
                subtitle = draft.subtitle.tidied(),
                authors = draft.authors.singleLines(),
                description = draft.description.tidied(),
                publisher = draft.publisher.tidied(),
                publishedDate = draft.publishedDate.tidied(),
                pageCount = draft.pageCount?.takeIf { it > 0 },
                isbn10 = isbn13?.let(Isbn::toIsbn10),
                isbn13 = isbn13,
                coverFile = coverFile,
                customCover = coverFile != null,
                state = ReadingState.PLANNED,
                acquisition = acquisition,
                purchasedOn = if (acquisition == Acquisition.PURCHASED) today().toEpochDay() else null,
                matchStatus = MatchStatus.NONE,
                sync = SyncStamp.created(clock.millis()),
            ),
        )
        return bookId
    }

    /**
     * Replaces what describes [bookId] with what the user typed. The link to its source stays, as
     * do the pages of the authors who are still named.
     */
    suspend fun updateDetails(bookId: String, draft: BookDraft) = edit(bookId) { book ->
        val authors = draft.authors.singleLines()
        val isbn13 = draft.isbn?.let(Isbn::toIsbn13)
        book.copy(
            title = draft.title.singleLine().ifEmpty { book.title },
            subtitle = draft.subtitle.tidied(),
            authors = authors,
            authorRefs = book.authorRefs.filter { it.name in authors },
            description = draft.description.tidied(),
            publisher = draft.publisher.tidied(),
            publishedDate = draft.publishedDate.tidied(),
            pageCount = draft.pageCount?.takeIf { it > 0 },
            isbn10 = isbn13?.let(Isbn::toIsbn10),
            isbn13 = isbn13,
        )
    }

    /**
     * Makes the picture behind [uri] the cover of [bookId], in place of whatever cover it had.
     *
     * @return false when the picture could not be read, which leaves the book as it was.
     */
    suspend fun setCustomCover(bookId: String, uri: Uri): Boolean {
        val name = storage.saveCover(bookId, uri) ?: return false
        var replaced: String? = name
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            replaced = book.coverFile
            books.upsert(book.copy(coverFile = name, customCover = true, sync = book.sync.touched(clock.millis())))
        }
        storage.deleteCover(replaced)
        return true
    }

    /** Drops the cover the user chose and goes back to the one from the EPUB, or else from the source. */
    suspend fun removeCustomCover(bookId: String) {
        val book = books.get(bookId)?.takeIf { it.customCover } ?: return
        edit(bookId) { it.copy(coverFile = null, customCover = false) }
        storage.deleteCover(book.coverFile)
        val fromEpub = if (storage.hasEpub(bookId)) {
            runCatching { readium.open(storage.epubFile(bookId)).use { it.extractInfo().cover } }.getOrNull()
        } else {
            null
        }
        if (fromEpub != null) {
            val name = storage.saveCover(bookId, fromEpub)
            edit(bookId) { it.copy(coverFile = name) }
        } else {
            downloadMissingCover(bookId)
        }
    }

    /**
     * Links [bookId] to [metadata] and takes over its descriptive fields. A cover that came out of
     * the EPUB is kept, since sources often only have small thumbnails.
     */
    suspend fun applyMetadata(bookId: String, metadata: BookMetadata) {
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            books.upsert(
                book.copy(
                    title = metadata.title,
                    subtitle = metadata.subtitle ?: book.subtitle,
                    authors = metadata.authors.ifEmpty { book.authors },
                    authorRefs = metadata.authorRefs,
                    description = metadata.description ?: book.description,
                    publisher = metadata.publisher ?: book.publisher,
                    publishedDate = metadata.publishedDate ?: book.publishedDate,
                    language = book.language ?: metadata.language,
                    pageCount = metadata.pageCount ?: book.pageCount,
                    isbn10 = metadata.isbn10 ?: book.isbn10,
                    isbn13 = metadata.isbn13 ?: book.isbn13,
                    categories = metadata.categories.ifEmpty { book.categories },
                    coverUrl = metadata.coverUrl ?: book.coverUrl,
                    source = metadata.source,
                    sourceId = metadata.sourceId,
                    sourceUrl = metadata.infoUrl,
                    matchStatus = MatchStatus.MATCHED,
                    sync = book.sync.touched(clock.millis()),
                ),
            )
        }
        absorbDuplicateEntry(bookId)
        appScope.launch { downloadMissingCover(bookId) }
    }

    suspend fun setMatchStatus(bookId: String, status: MatchStatus) = edit(bookId) { it.copy(matchStatus = status) }

    /** Breaks the link to the metadata source; the fields already copied stay. */
    suspend fun unlink(bookId: String) = edit(bookId) {
        it.copy(
            // Their IDs mean nothing away from the source.
            authorRefs = emptyList(),
            source = null,
            sourceId = null,
            sourceUrl = null,
            matchStatus = MatchStatus.NONE,
        )
    }

    suspend fun pendingMatches(): List<Book> = books.pendingMatches().map { it.toBook() }

    /** Linked books whose authors cannot be opened yet, having been linked before their IDs were kept. */
    suspend fun withoutAuthorRefs(): List<Book> =
        books.withoutAuthorRefs().filter { it.authors.isNotEmpty() }.map { it.toBook() }

    suspend fun setAuthorRefs(bookId: String, refs: List<AuthorRef>) = edit(bookId) { it.copy(authorRefs = refs) }

    suspend fun downloadMissingCover(bookId: String) {
        val book = books.get(bookId) ?: return
        val url = book.coverUrl ?: return
        if (book.coverFile != null && storage.coverFile(book.coverFile).isFile) return
        val bytes = covers.download(url) ?: return
        val name = storage.saveCover(bookId, bytes)
        // Reached with a custom cover only when its file is gone, as on another device.
        edit(bookId) { it.copy(coverFile = name, customCover = false) }
    }

    /** Retries covers that could not be fetched when their book was added, for example while offline. */
    suspend fun downloadMissingCovers() {
        books.observeAll().first()
            .filter { it.coverUrl != null && (it.coverFile == null || !storage.coverFile(it.coverFile).isFile) }
            .forEach { downloadMissingCover(it.id) }
    }

    /**
     * After an import is matched online it can turn out to be a book already on the wishlist.
     * The imported record survives and takes over what the user had entered on the other one.
     */
    private suspend fun absorbDuplicateEntry(bookId: String) {
        var orphanedCover: String? = null
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            val source = book.source ?: return@withTransaction
            val sourceId = book.sourceId ?: return@withTransaction
            if (book.fileHash == null) return@withTransaction
            val other = books.findBySource(source, sourceId)
                .firstOrNull { it.id != bookId && it.fileHash == null } ?: return@withTransaction

            val now = clock.millis()
            readThroughs.moveToBook(other.id, bookId, now)
            // Each entry counted its own read-throughs from one, so together they may repeat a number.
            readThroughs.renumberDuplicates(now)
            sessions.moveToBook(other.id, bookId, now)
            notes.moveToBook(other.id, bookId, now)
            // A cover the user chose for the entry that goes away comes along too.
            val takeCover = other.customCover && !book.customCover && other.coverFile != null
            books.upsert(
                book.copy(
                    coverFile = if (takeCover) other.coverFile else book.coverFile,
                    customCover = book.customCover || takeCover,
                    state = if (book.state == ReadingState.PLANNED) other.state else book.state,
                    isFavorite = book.isFavorite || other.isFavorite,
                    rating = book.rating ?: other.rating,
                    acquisition = if (other.acquisition == Acquisition.PURCHASED) other.acquisition else book.acquisition,
                    purchasePriceMinor = book.purchasePriceMinor ?: other.purchasePriceMinor,
                    purchaseCurrency = book.purchaseCurrency ?: other.purchaseCurrency,
                    purchasedOn = book.purchasedOn ?: other.purchasedOn,
                    purchaseLocation = book.purchaseLocation ?: other.purchaseLocation,
                    physicalPage = book.physicalPage ?: other.physicalPage,
                    physicalPageCount = book.physicalPageCount ?: other.physicalPageCount,
                    sync = book.sync.touched(now),
                ),
            )
            books.upsert(other.copy(sync = other.sync.deleted(now)))
            orphanedCover = if (takeCover) book.coverFile else other.coverFile
        }
        storage.deleteCover(orphanedCover)
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    /**
     * Moves a book to [newState], keeping its read-throughs consistent: Reading and Paused have one
     * open, Completed and Dropped close it. Leaving Completed or Dropped takes that end back: the last
     * read-through is reopened, or given the other outcome, so a book no longer completed stops
     * counting as one. That makes a mis-tap undoable; use [readAgain] to start a new one.
     *
     * A wishlist entry is always Planned, so asking for any other state is ignored.
     */
    suspend fun setState(bookId: String, newState: ReadingState) {
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            if (book.state == newState) return@withTransaction
            if (book.acquisition == Acquisition.WISHLIST && newState != ReadingState.PLANNED) return@withTransaction
            val now = clock.millis()
            var progression = book.progression
            var physicalPage = book.physicalPage

            // The read-through the book's finished state closed. A change of mind is about that one.
            val closed = if (book.state in FINISHED_STATES) readThroughs.getLatest(bookId) else null
            // Completing pinned the progress to the end; any other state shows where the reader is.
            if (book.state == ReadingState.COMPLETED) progression = progressionOf(book.locator) ?: book.progression

            when (newState) {
                ReadingState.READING, ReadingState.PAUSED -> {
                    if (readThroughs.getOpen(bookId) == null) {
                        if (closed != null) reopen(closed, now) else startReadThrough(bookId, now)
                    }
                }

                ReadingState.COMPLETED, ReadingState.DROPPED -> {
                    // From the other finished state the outcome changes, with no new read-through.
                    val open = readThroughs.getOpen(bookId) ?: closed ?: startReadThrough(bookId, now)
                    val outcome =
                        if (newState == ReadingState.COMPLETED) ReadOutcome.COMPLETED else ReadOutcome.DROPPED
                    readThroughs.upsert(
                        open.copy(
                            finishedAt = now,
                            finishedOn = today().toEpochDay(),
                            outcome = outcome,
                            sync = open.sync.touched(now),
                        ),
                    )
                    if (newState == ReadingState.COMPLETED) {
                        progression = 1.0
                        // Without an EPUB it can only have been the physical copy that was finished.
                        if (book.fileHash == null && physicalPage != null) {
                            physicalPage = book.physicalPageCount ?: book.pageCount ?: physicalPage
                        }
                    }
                }

                ReadingState.PLANNED -> {
                    // A read-through nobody read in is noise; one with sessions waits to be resumed.
                    val open = readThroughs.getOpen(bookId) ?: closed?.let { reopen(it, now) }
                    if (open != null && sessions.countForBook(bookId) == 0) {
                        readThroughs.upsert(open.copy(sync = open.sync.deleted(now)))
                    }
                }
            }

            books.upsert(
                book.copy(
                    state = newState,
                    progression = progression,
                    physicalPage = physicalPage,
                    sync = book.sync.touched(now),
                ),
            )
        }
    }

    /** Starts a new read-through from the beginning, leaving earlier ones and their sessions intact. */
    suspend fun readAgain(bookId: String) {
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            val now = clock.millis()
            readThroughs.getOpen(bookId)?.let { open ->
                readThroughs.upsert(
                    open.copy(
                        finishedAt = now,
                        finishedOn = today().toEpochDay(),
                        outcome = ReadOutcome.DROPPED,
                        sync = open.sync.touched(now),
                    ),
                )
            }
            startReadThrough(bookId, now)
            books.upsert(
                book.copy(
                    state = ReadingState.READING,
                    locator = null,
                    progression = 0.0,
                    position = null,
                    chapterTitle = null,
                    chapterIndex = null,
                    physicalPage = book.physicalPage?.let { 0 },
                    sync = book.sync.touched(now),
                ),
            )
        }
    }

    /**
     * Records the page the user has reached in a physical copy, apart from the EPUB position the
     * reader keeps. A null [page] stops tracking the physical copy. [pageCount] is the length of
     * that copy; null falls back to the page count from the metadata source.
     *
     * Like opening the reader, a first page turns a planned or paused book into Reading, and the
     * last page completes it. A wishlist entry is only wanted, so it has no copy to track.
     */
    suspend fun setPhysicalProgress(bookId: String, page: Int?, pageCount: Int?) {
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            if (book.acquisition == Acquisition.WISHLIST) return@withTransaction
            val now = clock.millis()
            val count = pageCount?.takeIf { it > 0 }
            val total = count ?: book.pageCount?.takeIf { it > 0 }
            val reached = page?.coerceAtLeast(0)?.let { if (total != null) it.coerceAtMost(total) else it }
            books.upsert(
                book.copy(
                    physicalPage = reached,
                    physicalPageCount = count?.takeIf { it != book.pageCount },
                    lastReadAt = if (reached != null && reached != book.physicalPage) now else book.lastReadAt,
                    sync = book.sync.touched(now),
                ),
            )
            if (reached == null || reached == 0 || reached == book.physicalPage) return@withTransaction
            when {
                total != null && reached >= total -> setState(bookId, ReadingState.COMPLETED)
                book.state == ReadingState.PLANNED || book.state == ReadingState.PAUSED ->
                    setState(bookId, ReadingState.READING)
            }
        }
    }

    /** Takes back the end of [readThrough], so it no longer counts as completed or dropped. */
    private suspend fun reopen(readThrough: ReadThroughEntity, now: Long): ReadThroughEntity {
        val reopened = readThrough.copy(
            finishedAt = null,
            finishedOn = null,
            outcome = null,
            sync = readThrough.sync.touched(now),
        )
        readThroughs.upsert(reopened)
        return reopened
    }

    private suspend fun startReadThrough(bookId: String, now: Long): ReadThroughEntity {
        val readThrough = ReadThroughEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            number = readThroughs.maxNumber(bookId) + 1,
            startedAt = now,
            sync = SyncStamp.created(now),
        )
        readThroughs.upsert(readThrough)
        return readThrough
    }

    /** A wishlist entry is never a favorite, so asking for one is ignored. */
    suspend fun setFavorite(bookId: String, favorite: Boolean) =
        edit(bookId) { it.copy(isFavorite = favorite && it.acquisition != Acquisition.WISHLIST) }

    suspend fun setRating(bookId: String, rating: Float?) =
        edit(bookId) { it.copy(rating = rating?.coerceIn(0.5f, 5f)) }

    suspend fun setPurchase(
        bookId: String,
        acquisition: Acquisition,
        priceMinor: Long?,
        currency: String?,
        purchasedOn: LocalDate?,
        location: String?,
    ) {
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            // Without a purchase, a book with an EPUB is a download and one without is a wish.
            val target = when {
                acquisition == Acquisition.PURCHASED -> Acquisition.PURCHASED
                book.fileHash != null -> Acquisition.DOWNLOADED
                else -> Acquisition.WISHLIST
            }
            // A book that goes back to being wanted is Planned again, whatever it was marked as.
            if (target == Acquisition.WISHLIST) setState(bookId, ReadingState.PLANNED)
            edit(bookId) {
                if (target != Acquisition.PURCHASED) {
                    it.copy(
                        acquisition = target,
                        isFavorite = it.isFavorite && target != Acquisition.WISHLIST,
                        purchasePriceMinor = null,
                        purchaseCurrency = null,
                        purchasedOn = null,
                        purchaseLocation = null,
                    )
                } else {
                    it.copy(
                        acquisition = target,
                        purchasePriceMinor = priceMinor,
                        purchaseCurrency = currency.takeIf { priceMinor != null },
                        purchasedOn = (purchasedOn ?: today()).toEpochDay(),
                        purchaseLocation = location?.trim()?.ifEmpty { null },
                    )
                }
            }
        }
    }

    /** Removes the book everywhere, including its reading history and files. */
    suspend fun deleteBook(bookId: String) {
        var cover: String? = null
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            val now = clock.millis()
            readThroughs.softDeleteForBook(bookId, now)
            sessions.softDeleteForBook(bookId, now)
            notes.softDeleteForBook(bookId, now)
            books.upsert(book.copy(sync = book.sync.deleted(now)))
            cover = book.coverFile
        }
        storage.deleteBookFiles(bookId, cover)
    }

    // ---- Notes -----------------------------------------------------------------------------

    suspend fun saveNote(bookId: String, noteId: String?, text: String, chapterTitle: String? = null) {
        val now = clock.millis()
        val existing = noteId?.let { notes.get(it) }
        notes.upsert(
            existing?.copy(text = text.trim(), sync = existing.sync.touched(now))
                ?: NoteEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    text = text.trim(),
                    chapterTitle = chapterTitle,
                    sync = SyncStamp.created(now),
                ),
        )
    }

    suspend fun deleteNote(noteId: String) = notes.softDelete(noteId, clock.millis())

    // ---- Reader ----------------------------------------------------------------------------

    /**
     * Called when the reader opens a book. A planned or paused book becomes Reading; a completed or
     * dropped one keeps its state, since opening it to look something up is not a re-read.
     *
     * @return the read-through that sessions should be attributed to.
     */
    suspend fun beginReading(bookId: String): String? {
        val state = books.get(bookId)?.state ?: return null
        if (state == ReadingState.PLANNED || state == ReadingState.PAUSED) {
            setState(bookId, ReadingState.READING)
        }
        return (readThroughs.getOpen(bookId) ?: readThroughs.getLatest(bookId))?.id
    }

    suspend fun saveProgress(bookId: String, progress: ReadingProgress) {
        books.updateProgress(
            id = bookId,
            locator = progress.locatorJson,
            progression = progress.progression.coerceIn(0.0, 1.0),
            position = progress.position,
            positionCount = progress.positionCount,
            chapterTitle = progress.chapterTitle,
            chapterIndex = progress.chapterIndex,
            chapterCount = progress.chapterCount,
            now = clock.millis(),
        )
    }

    suspend fun recordSession(draft: SessionDraft) {
        val now = clock.millis()
        sessions.insert(
            ReadingSessionEntity(
                id = UUID.randomUUID().toString(),
                bookId = draft.bookId,
                readThroughId = draft.readThroughId,
                startedAt = draft.startedAt,
                endedAt = draft.endedAt,
                durationMs = draft.durationMs,
                day = Instant.ofEpochMilli(draft.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                startProgression = draft.startProgression,
                endProgression = draft.endProgression,
                startPosition = draft.startPosition,
                endPosition = draft.endPosition,
                pages = draft.pages,
                startChapter = draft.startChapter,
                endChapter = draft.endChapter,
                sync = SyncStamp.created(now),
            ),
        )
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private suspend fun edit(bookId: String, change: (BookEntity) -> BookEntity) {
        db.withTransaction {
            val book = books.get(bookId) ?: return@withTransaction
            books.upsert(change(book).copy(sync = book.sync.touched(clock.millis())))
        }
    }

    private fun String?.tidied(): String? = this?.trim()?.ifEmpty { null }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.systemDefault()))

    private fun progressionOf(locatorJson: String?): Double? = runCatching {
        JSONObject(locatorJson ?: return null).optJSONObject("locations")
            ?.optDouble("totalProgression")?.takeIf { !it.isNaN() }
    }.getOrNull()

    private fun Flow<List<BookEntity>>.mapBooks(): Flow<List<Book>> =
        map { list -> list.map { it.toBook() } }.flowOn(Dispatchers.IO)

    private fun BookEntity.toBook(): Book = Book(
        id = id,
        title = title,
        subtitle = subtitle,
        // Rows saved before names were tidied on the way in.
        authors = authors.singleLines(),
        authorRefs = authorRefs,
        description = description,
        publisher = publisher,
        publishedDate = publishedDate,
        language = language,
        pageCount = pageCount,
        isbn10 = isbn10,
        isbn13 = isbn13,
        categories = categories,
        coverPath = coverFile?.let(storage::coverFile)?.takeIf { it.isFile }?.absolutePath,
        coverUrl = coverUrl,
        hasCustomCover = customCover,
        state = state,
        acquisition = acquisition,
        isFavorite = isFavorite,
        rating = rating,
        purchasePriceMinor = purchasePriceMinor,
        purchaseCurrency = purchaseCurrency,
        purchasedOn = purchasedOn?.let(LocalDate::ofEpochDay),
        purchaseLocation = purchaseLocation,
        source = source,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        matchStatus = matchStatus,
        inLibrary = fileHash != null,
        hasFile = fileHash != null && storage.hasEpub(id),
        locator = locator,
        progression = progression,
        position = position,
        positionCount = positionCount,
        chapterTitle = chapterTitle,
        chapterIndex = chapterIndex,
        chapterCount = chapterCount,
        lastReadAt = lastReadAt,
        physicalPage = physicalPage,
        physicalPageCount = physicalPageCount ?: pageCount,
        addedAt = sync.createdAt,
    )

    private companion object {
        val FINISHED_STATES = setOf(ReadingState.COMPLETED, ReadingState.DROPPED)
    }
}
