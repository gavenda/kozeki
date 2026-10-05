package dev.gavenda.kozeki.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import dev.gavenda.kozeki.data.model.MetadataSource
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<BookEntity>>

    /** Books with an EPUB attached, most recently read first. */
    @Query(
        "SELECT * FROM books WHERE deletedAt IS NULL AND fileHash IS NOT NULL " +
            "ORDER BY COALESCE(lastReadAt, createdAt) DESC",
    )
    fun observeLibrary(): Flow<List<BookEntity>>

    /** Wishlist and purchased entries that have no EPUB yet. */
    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND fileHash IS NULL ORDER BY createdAt DESC")
    fun observeWithoutFile(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE fileHash = :hash AND deletedAt IS NULL LIMIT 1")
    suspend fun findByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE source = :source AND sourceId = :sourceId AND deletedAt IS NULL")
    suspend fun findBySource(source: MetadataSource, sourceId: String): List<BookEntity>

    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND fileHash IS NULL")
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

    @Upsert
    suspend fun upsert(book: BookEntity)

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

    @Upsert
    suspend fun upsert(readThrough: ReadThroughEntity)

    @Query("UPDATE read_throughs SET deletedAt = :now, updatedAt = :now WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun softDeleteForBook(bookId: String, now: Long)

    @Query("UPDATE read_throughs SET bookId = :to, updatedAt = :now WHERE bookId = :from")
    suspend fun moveToBook(from: String, to: String, now: Long)
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
