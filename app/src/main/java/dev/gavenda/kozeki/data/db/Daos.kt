package dev.gavenda.kozeki.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import dev.gavenda.kozeki.data.model.MetadataSource
import kotlinx.coroutines.flow.Flow

/** A shelf with how many books of the library are on it. */
data class ShelfRow(
    @Embedded val shelf: ShelfEntity,
    val bookCount: Int,
)

/** The authors column on its own, which Room reads as the list it holds. */
data class BookAuthors(val authors: List<String>)

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<BookEntity>>

    /** Books with an EPUB attached, and comics, which are read online, most recently read first. */
    @Query(
        "SELECT * FROM books WHERE deletedAt IS NULL AND (fileHash IS NOT NULL OR source = 'COMICS') " +
            "ORDER BY COALESCE(lastReadAt, createdAt) DESC",
    )
    fun observeLibrary(): Flow<List<BookEntity>>

    /** Wishlist and purchased entries that have no EPUB yet. */
    @Query(
        "SELECT * FROM books WHERE deletedAt IS NULL AND fileHash IS NULL AND (source IS NULL OR source != 'COMICS') " +
            "ORDER BY createdAt DESC",
    )
    fun observeWithoutFile(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE fileHash = :hash AND deletedAt IS NULL LIMIT 1")
    suspend fun findByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE source = :source AND sourceId = :sourceId AND deletedAt IS NULL")
    suspend fun findBySource(source: MetadataSource, sourceId: String): List<BookEntity>

    /** The entries an imported EPUB could complete. A comic has no EPUB to wait for. */
    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND fileHash IS NULL AND (source IS NULL OR source != 'COMICS')")
    suspend fun withoutFile(): List<BookEntity>

    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND matchStatus = 'PENDING'")
    suspend fun pendingMatches(): List<BookEntity>

    /** Books linked to the source before their authors' IDs were kept. */
    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND sourceId IS NOT NULL AND authorRefs = '[]'")
    suspend fun withoutAuthorRefs(): List<BookEntity>

    /** Every place a book was bought, most recent purchase first, as suggestions for the next one. */
    @Query(
        "SELECT purchaseLocation FROM books WHERE deletedAt IS NULL AND purchaseLocation IS NOT NULL " +
            "GROUP BY purchaseLocation COLLATE NOCASE ORDER BY MAX(purchasedOn) DESC, MAX(updatedAt) DESC",
    )
    fun observePurchaseLocations(): Flow<List<String>>

    /** The authors of every book, the one changed last first, as suggestions for the next one. */
    @Query("SELECT authors FROM books WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAuthors(): Flow<List<BookAuthors>>

    /** Every publisher a book has, the one used last first, as suggestions for the next one. */
    @Query(
        "SELECT publisher FROM books WHERE deletedAt IS NULL AND publisher IS NOT NULL AND publisher != '' " +
            "GROUP BY publisher COLLATE NOCASE ORDER BY MAX(updatedAt) DESC",
    )
    fun observePublishers(): Flow<List<String>>

    /** Every series a book is in, the one used last first, as suggestions for the next one. */
    @Query(
        "SELECT series FROM books WHERE deletedAt IS NULL AND series IS NOT NULL AND series != '' " +
            "GROUP BY series COLLATE NOCASE ORDER BY MAX(updatedAt) DESC",
    )
    fun observeSeries(): Flow<List<String>>

    /** Comics worth asking their source about, the one asked longest ago first. */
    @Query(
        "SELECT * FROM books WHERE source = 'COMICS' AND deletedAt IS NULL AND sourceId IS NOT NULL " +
            "AND acquisition != 'WISHLIST' AND state NOT IN ('COMPLETED', 'DROPPED') ORDER BY chaptersCheckedAt",
    )
    suspend fun getForUpdateCheck(): List<BookEntity>

    @Query("UPDATE books SET chaptersCheckedAt = :now WHERE id = :id")
    suspend fun markChaptersChecked(id: String, now: Long)

    @Upsert
    suspend fun upsert(book: BookEntity)

    /**
     * Counts chapters that came out, or with [count] of 0 clears the count. Not an edit of the book,
     * so [BookEntity.sync] is left alone.
     */
    @Query("UPDATE books SET newChapters = CASE WHEN :count = 0 THEN 0 ELSE newChapters + :count END WHERE id = :id")
    suspend fun addNewChapters(id: String, count: Int)

    /**
     * A comic's place, from how many of its chapters are read. Unlike [updateProgress] it leaves
     * [BookEntity.lastReadAt] alone, since marking chapters read is not reading.
     */
    @Query(
        "UPDATE books SET progression = CASE WHEN state = 'COMPLETED' THEN 1.0 ELSE :progression END, " +
            "chapterTitle = :chapterTitle, chapterIndex = :chapterIndex, chapterCount = :chapterCount, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun updateChapterProgress(
        id: String,
        progression: Double,
        chapterTitle: String?,
        chapterIndex: Int?,
        chapterCount: Int?,
        now: Long,
    )

    /** Wishlist entries cannot be favorites; this drops the flag from rows written before that held. */
    @Query("UPDATE books SET isFavorite = 0, updatedAt = :now WHERE acquisition = 'WISHLIST' AND isFavorite = 1")
    suspend fun clearWishlistFavorites(now: Long)

    /** Wishlist entries are always Planned; this brings back rows written before that held. */
    @Query("UPDATE books SET state = 'PLANNED', updatedAt = :now WHERE acquisition = 'WISHLIST' AND state != 'PLANNED'")
    suspend fun planWishlist(now: Long)

    /**
     * An imported EPUB used to be filed as purchased. Such a row has a file but no purchase date,
     * which a recorded purchase always has, so it becomes a download.
     */
    @Query(
        "UPDATE books SET acquisition = 'DOWNLOADED', updatedAt = :now WHERE acquisition = 'PURCHASED' " +
            "AND fileHash IS NOT NULL AND purchasedOn IS NULL AND purchasePriceMinor IS NULL",
    )
    suspend fun unassumePurchases(now: Long)

    /**
     * Targeted so that frequent progress writes from the reader never clobber other edits. A
     * completed book stays at 100% even while it is being browsed again.
     */
    @Query(
        "UPDATE books SET locator = :locator, " +
            "progression = CASE WHEN state = 'COMPLETED' THEN 1.0 ELSE :progression END, position = :position, " +
            "positionCount = :positionCount, chapterTitle = :chapterTitle, chapterIndex = :chapterIndex, " +
            "chapterCount = :chapterCount, lastReadAt = :now, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        locator: String?,
        progression: Double,
        position: Int?,
        positionCount: Int?,
        chapterTitle: String?,
        chapterIndex: Int?,
        chapterCount: Int?,
        now: Long,
    )
}

@Dao
interface ComicChapterDao {

    @Query("SELECT * FROM comic_chapters WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY sourceOrder")
    fun observeForBook(bookId: String): Flow<List<ComicChapterEntity>>

    @Query("SELECT * FROM comic_chapters WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY sourceOrder")
    suspend fun getForBook(bookId: String): List<ComicChapterEntity>

    /** When the book's chapters were put up, latest first, for those whose source says. */
    @Query(
        "SELECT uploadedAt FROM comic_chapters WHERE bookId = :bookId AND deletedAt IS NULL AND uploadedAt > 0 " +
            "ORDER BY uploadedAt DESC",
    )
    suspend fun getUploadTimes(bookId: String): List<Long>

    /** Every row of the book, the ones whose chapter left the source included. */
    @Query("SELECT * FROM comic_chapters WHERE bookId = :bookId")
    suspend fun getAllForBook(bookId: String): List<ComicChapterEntity>

    @Query("SELECT * FROM comic_chapters WHERE bookId = :bookId AND url = :url AND deletedAt IS NULL")
    suspend fun get(bookId: String, url: String): ComicChapterEntity?

    @Upsert
    suspend fun upsertAll(chapters: List<ComicChapterEntity>)

    /**
     * [pageCount] only replaces what is there when it is known. A chapter that is read stays read:
     * opening it again to look at a page is not reading it unread, which [markRead] is for.
     */
    @Query(
        "UPDATE comic_chapters SET read = CASE WHEN :read THEN 1 ELSE read END, lastPageRead = :lastPageRead, " +
            "pageCount = COALESCE(:pageCount, pageCount), updatedAt = :now WHERE bookId = :bookId AND url = :url",
    )
    suspend fun updateProgress(bookId: String, url: String, read: Boolean, lastPageRead: Int, pageCount: Int?, now: Long)

    /** Marking a chapter unread puts it back to its first page. */
    @Query(
        "UPDATE comic_chapters SET read = :read, lastPageRead = CASE WHEN :read THEN lastPageRead ELSE 0 END, " +
            "updatedAt = :now WHERE bookId = :bookId AND url IN (:urls)",
    )
    suspend fun markRead(bookId: String, urls: List<String>, read: Boolean, now: Long)

    @Query("UPDATE comic_chapters SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun softDeleteForBook(bookId: String, now: Long)
}

@Dao
interface ComicTrackDao {

    @Query("SELECT * FROM comic_tracks WHERE bookId = :bookId AND deletedAt IS NULL")
    fun observe(bookId: String): Flow<ComicTrackEntity?>

    @Query("SELECT * FROM comic_tracks WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun get(bookId: String): ComicTrackEntity?

    @Query("SELECT * FROM comic_tracks WHERE pendingPush = 1 AND deletedAt IS NULL")
    suspend fun pending(): List<ComicTrackEntity>

    @Upsert
    suspend fun upsert(track: ComicTrackEntity)
}

@Dao
interface ReadThroughDao {

    @Query("SELECT * FROM read_throughs WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY number DESC")
    fun observeForBook(bookId: String): Flow<List<ReadThroughEntity>>

    @Query(
        "SELECT * FROM read_throughs WHERE bookId = :bookId AND deletedAt IS NULL AND finishedAt IS NULL " +
            "ORDER BY number DESC LIMIT 1",
    )
    suspend fun getOpen(bookId: String): ReadThroughEntity?

    @Query("SELECT * FROM read_throughs WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY number DESC LIMIT 1")
    suspend fun getLatest(bookId: String): ReadThroughEntity?

    @Query("SELECT COALESCE(MAX(number), 0) FROM read_throughs WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun maxNumber(bookId: String): Int

    @Query(
        "SELECT * FROM read_throughs WHERE deletedAt IS NULL AND outcome = 'COMPLETED' " +
            "AND finishedOn >= :fromDay AND finishedOn < :toDay ORDER BY finishedAt",
    )
    fun observeCompletedBetween(fromDay: Long, toDay: Long): Flow<List<ReadThroughEntity>>

    @Query("SELECT * FROM read_throughs WHERE deletedAt IS NULL AND outcome = 'COMPLETED' ORDER BY finishedAt")
    fun observeCompleted(): Flow<List<ReadThroughEntity>>

    @Upsert
    suspend fun upsert(readThrough: ReadThroughEntity)

    @Query("UPDATE read_throughs SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun softDeleteForBook(bookId: String, now: Long)

    @Query("UPDATE read_throughs SET bookId = :to, updatedAt = :now WHERE bookId = :from")
    suspend fun moveToBook(from: String, to: String, now: Long)

    /**
     * Read-throughs count up from one per book, but two entries merged into one each bring their
     * own count. A book left with two of the same number has all of its read-throughs numbered
     * again, in the order they were started.
     */
    @Query(
        "UPDATE read_throughs SET updatedAt = :now, number = (" +
            "SELECT COUNT(*) FROM read_throughs AS earlier " +
            "WHERE earlier.bookId = read_throughs.bookId AND earlier.deletedAt IS NULL AND (" +
            "earlier.startedAt < read_throughs.startedAt OR " +
            "(earlier.startedAt = read_throughs.startedAt AND earlier.id <= read_throughs.id))) " +
            "WHERE deletedAt IS NULL AND bookId IN (" +
            "SELECT bookId FROM read_throughs WHERE deletedAt IS NULL GROUP BY bookId, number HAVING COUNT(*) > 1)",
    )
    suspend fun renumberDuplicates(now: Long)
}

@Dao
interface ReadingSessionDao {

    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query(
        "SELECT * FROM reading_sessions WHERE deletedAt IS NULL AND day >= :fromDay AND day < :toDay " +
            "ORDER BY startedAt",
    )
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE deletedAt IS NULL ORDER BY startedAt")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeForBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COUNT(*) FROM reading_sessions WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun countForBook(bookId: String): Int

    @Query("UPDATE reading_sessions SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun softDeleteForBook(bookId: String, now: Long)

    @Query("UPDATE reading_sessions SET bookId = :to, updatedAt = :now WHERE bookId = :from")
    suspend fun moveToBook(from: String, to: String, now: Long)
}

@Dao
interface PhysicalReadingDao {

    @Insert
    suspend fun insert(reading: PhysicalReadingEntity)

    @Query(
        "SELECT * FROM physical_readings WHERE deletedAt IS NULL AND day >= :fromDay AND day < :toDay " +
            "ORDER BY recordedAt",
    )
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<PhysicalReadingEntity>>

    @Query("SELECT * FROM physical_readings WHERE deletedAt IS NULL ORDER BY recordedAt")
    fun observeAll(): Flow<List<PhysicalReadingEntity>>

    @Query("SELECT * FROM physical_readings WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY recordedAt")
    fun observeForBook(bookId: String): Flow<List<PhysicalReadingEntity>>

    @Query("SELECT COUNT(*) FROM physical_readings WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun countForBook(bookId: String): Int

    /**
     * Removes what one read-through has on record as read from [page] onwards. A reading that only
     * ends past it is left for [cutShortAt].
     */
    @Query(
        "UPDATE physical_readings SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId " +
            "AND readThroughId IS :readThroughId AND deletedAt IS NULL AND startPage >= :page",
    )
    suspend fun softDeleteFrom(bookId: String, readThroughId: String?, page: Int, now: Long)

    /** Ends at [page] the readings of one read-through that went past it. */
    @Query(
        "UPDATE physical_readings SET endPage = :page, updatedAt = :now WHERE bookId = :bookId " +
            "AND readThroughId IS :readThroughId AND deletedAt IS NULL AND endPage > :page",
    )
    suspend fun cutShortAt(bookId: String, readThroughId: String?, page: Int, now: Long)

    @Query("UPDATE physical_readings SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun softDeleteForBook(bookId: String, now: Long)

    @Query("UPDATE physical_readings SET bookId = :to, updatedAt = :now WHERE bookId = :from")
    suspend fun moveToBook(from: String, to: String, now: Long)
}

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun softDeleteForBook(bookId: String, now: Long)

    @Query("UPDATE notes SET bookId = :to, updatedAt = :now WHERE bookId = :from")
    suspend fun moveToBook(from: String, to: String, now: Long)
}

@Dao
interface YearlyGoalDao {

    @Query("SELECT * FROM yearly_goals WHERE year = :year AND deletedAt IS NULL")
    fun observe(year: Int): Flow<YearlyGoalEntity?>

    @Upsert
    suspend fun upsert(goal: YearlyGoalEntity)
}

@Dao
interface MetadataCacheDao {

    @Query("SELECT * FROM metadata_cache WHERE `key` = :key")
    suspend fun get(key: String): MetadataCacheEntity?

    @Upsert
    suspend fun upsert(entry: MetadataCacheEntity)

    @Query("DELETE FROM metadata_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM metadata_cache")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM metadata_cache")
    fun observeCount(): Flow<Int>
}

@Dao
interface ShelfDao {

    @Query(
        "SELECT s.*, (SELECT COUNT(*) FROM shelf_books sb JOIN books b ON b.id = sb.bookId " +
            "WHERE sb.shelfId = s.id AND sb.deletedAt IS NULL AND b.deletedAt IS NULL) AS bookCount " +
            "FROM shelves s WHERE s.deletedAt IS NULL ORDER BY s.name COLLATE NOCASE",
    )
    fun observeShelves(): Flow<List<ShelfRow>>

    @Query("SELECT bookId FROM shelf_books WHERE shelfId = :shelfId AND deletedAt IS NULL")
    fun observeBookIds(shelfId: String): Flow<List<String>>

    /** Every live pairing of a shelf and a book on it. */
    @Query(
        "SELECT sb.* FROM shelf_books sb JOIN shelves s ON s.id = sb.shelfId JOIN books b ON b.id = sb.bookId " +
            "WHERE sb.deletedAt IS NULL AND s.deletedAt IS NULL AND b.deletedAt IS NULL",
    )
    fun observeMemberships(): Flow<List<ShelfBookEntity>>

    @Query(
        "SELECT sb.shelfId FROM shelf_books sb JOIN shelves s ON s.id = sb.shelfId " +
            "WHERE sb.bookId = :bookId AND sb.deletedAt IS NULL AND s.deletedAt IS NULL",
    )
    fun observeShelfIdsFor(bookId: String): Flow<List<String>>

    /** Which of [bookIds] are on which shelf, for telling what a set of books shares. */
    @Query(
        "SELECT sb.* FROM shelf_books sb JOIN shelves s ON s.id = sb.shelfId " +
            "WHERE sb.bookId IN (:bookIds) AND sb.deletedAt IS NULL AND s.deletedAt IS NULL",
    )
    suspend fun membershipsOf(bookIds: List<String>): List<ShelfBookEntity>

    @Query("SELECT * FROM shelves WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): ShelfEntity?

    @Upsert
    suspend fun upsert(shelf: ShelfEntity)

    @Upsert
    suspend fun putBooks(rows: List<ShelfBookEntity>)

    @Query(
        "UPDATE shelf_books SET deletedAt = :now, updatedAt = :now " +
            "WHERE shelfId = :shelfId AND bookId IN (:bookIds) AND deletedAt IS NULL",
    )
    suspend fun removeBooks(shelfId: String, bookIds: List<String>, now: Long)

    @Query("UPDATE shelf_books SET deletedAt = :now, updatedAt = :now WHERE shelfId = :shelfId AND deletedAt IS NULL")
    suspend fun removeAllBooks(shelfId: String, now: Long)

    @Query("UPDATE shelf_books SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun removeBookEverywhere(bookId: String, now: Long)

    @Query("UPDATE shelves SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE shelves SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

/**
 * The library as a whole, for copying it into a backup and putting one back. Rows that were
 * removed are left out of a copy, and putting one back leaves none of what was here before.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM books WHERE deletedAt IS NULL")
    suspend fun books(): List<BookEntity>

    @Query("SELECT * FROM read_throughs WHERE deletedAt IS NULL")
    suspend fun readThroughs(): List<ReadThroughEntity>

    @Query("SELECT * FROM reading_sessions WHERE deletedAt IS NULL")
    suspend fun readingSessions(): List<ReadingSessionEntity>

    @Query("SELECT * FROM physical_readings WHERE deletedAt IS NULL")
    suspend fun physicalReadings(): List<PhysicalReadingEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    suspend fun notes(): List<NoteEntity>

    @Query("SELECT * FROM yearly_goals WHERE deletedAt IS NULL")
    suspend fun yearlyGoals(): List<YearlyGoalEntity>

    @Query("SELECT * FROM comic_chapters WHERE deletedAt IS NULL")
    suspend fun comicChapters(): List<ComicChapterEntity>

    @Query("SELECT * FROM comic_tracks WHERE deletedAt IS NULL")
    suspend fun comicTracks(): List<ComicTrackEntity>

    @Query("SELECT * FROM shelves WHERE deletedAt IS NULL")
    suspend fun shelves(): List<ShelfEntity>

    @Query("SELECT * FROM shelf_books WHERE deletedAt IS NULL")
    suspend fun shelfBooks(): List<ShelfBookEntity>

    @Query("DELETE FROM shelves")
    suspend fun clearShelves()

    @Query("DELETE FROM shelf_books")
    suspend fun clearShelfBooks()

    @Upsert
    suspend fun putShelves(rows: List<ShelfEntity>)

    @Upsert
    suspend fun putShelfBooks(rows: List<ShelfBookEntity>)

    @Query("DELETE FROM comic_tracks")
    suspend fun clearComicTracks()

    @Upsert
    suspend fun putComicTracks(rows: List<ComicTrackEntity>)

    @Query("DELETE FROM books")
    suspend fun clearBooks()

    @Query("DELETE FROM read_throughs")
    suspend fun clearReadThroughs()

    @Query("DELETE FROM reading_sessions")
    suspend fun clearReadingSessions()

    @Query("DELETE FROM physical_readings")
    suspend fun clearPhysicalReadings()

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM yearly_goals")
    suspend fun clearYearlyGoals()

    @Query("DELETE FROM comic_chapters")
    suspend fun clearComicChapters()

    // Upserts, so a backup that names a row twice still goes in.
    @Upsert
    suspend fun putBooks(rows: List<BookEntity>)

    @Upsert
    suspend fun putReadThroughs(rows: List<ReadThroughEntity>)

    @Upsert
    suspend fun putReadingSessions(rows: List<ReadingSessionEntity>)

    @Upsert
    suspend fun putPhysicalReadings(rows: List<PhysicalReadingEntity>)

    @Upsert
    suspend fun putNotes(rows: List<NoteEntity>)

    @Upsert
    suspend fun putYearlyGoals(rows: List<YearlyGoalEntity>)

    @Upsert
    suspend fun putComicChapters(rows: List<ComicChapterEntity>)
}
