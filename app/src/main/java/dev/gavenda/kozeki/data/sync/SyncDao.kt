package dev.gavenda.kozeki.data.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.NoteEntity
import dev.gavenda.kozeki.data.db.PhysicalReadingEntity
import dev.gavenda.kozeki.data.db.ReadThroughEntity
import dev.gavenda.kozeki.data.db.ReadingSessionEntity
import dev.gavenda.kozeki.data.db.ShelfBookEntity
import dev.gavenda.kozeki.data.db.ShelfEntity
import dev.gavenda.kozeki.data.db.YearlyGoalEntity

// A row is waiting to be sent when it changed since it was last sent or received: its `updatedAt`
// differs from the `syncedAt` it was given then. Comparing for difference rather than order keeps an
// edit made on a phone whose clock runs behind from looking already sent.
private const val UNSENT = "(syncedAt IS NULL OR syncedAt != updatedAt)"

// Comics stay on the phone, and so does everything that hangs off one. MANGA is what they were saved as before.
private const val COMIC_IDS = "(SELECT id FROM books WHERE source IN ('COMICS', 'MANGA'))"
private const val NOT_COMIC = "(source IS NULL OR source NOT IN ('COMICS', 'MANGA'))"

// A book has two clocks, updatedAt for its details and lastReadAt for its reader's columns, and is
// waiting when the later of them differs from what it was last sent or received as (see BookMerge.kt).
private const val BOOK_VERSION = "MAX(updatedAt, COALESCE(lastReadAt, 0))"
private const val BOOK_UNSENT = "(syncedAt IS NULL OR syncedAt != $BOOK_VERSION)"

/**
 * What the sync reads and writes in Room. Only the book side of the library: comics, their
 * chapters and their AniList tracks are never sent.
 */
@Dao
interface SyncDao {

    // ---- Waiting to be sent ------------------------------------------------------------------

    @Query("SELECT * FROM books WHERE $BOOK_UNSENT AND $NOT_COMIC LIMIT :limit")
    suspend fun unsentBooks(limit: Int): List<BookEntity>

    @Query("SELECT * FROM read_throughs WHERE $UNSENT AND bookId NOT IN $COMIC_IDS LIMIT :limit")
    suspend fun unsentReadThroughs(limit: Int): List<ReadThroughEntity>

    @Query("SELECT * FROM reading_sessions WHERE $UNSENT AND bookId NOT IN $COMIC_IDS LIMIT :limit")
    suspend fun unsentReadingSessions(limit: Int): List<ReadingSessionEntity>

    @Query("SELECT * FROM physical_readings WHERE $UNSENT AND bookId NOT IN $COMIC_IDS LIMIT :limit")
    suspend fun unsentPhysicalReadings(limit: Int): List<PhysicalReadingEntity>

    @Query("SELECT * FROM notes WHERE $UNSENT AND bookId NOT IN $COMIC_IDS LIMIT :limit")
    suspend fun unsentNotes(limit: Int): List<NoteEntity>

    @Query("SELECT * FROM yearly_goals WHERE $UNSENT LIMIT :limit")
    suspend fun unsentYearlyGoals(limit: Int): List<YearlyGoalEntity>

    @Query("SELECT * FROM shelves WHERE $UNSENT LIMIT :limit")
    suspend fun unsentShelves(limit: Int): List<ShelfEntity>

    @Query("SELECT * FROM shelf_books WHERE $UNSENT AND bookId NOT IN $COMIC_IDS LIMIT :limit")
    suspend fun unsentShelfBooks(limit: Int): List<ShelfBookEntity>

    /** How many rows are waiting, for the progress of the first sync. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM books WHERE $BOOK_UNSENT AND $NOT_COMIC)" +
            " + (SELECT COUNT(*) FROM read_throughs WHERE $UNSENT AND bookId NOT IN $COMIC_IDS)" +
            " + (SELECT COUNT(*) FROM reading_sessions WHERE $UNSENT AND bookId NOT IN $COMIC_IDS)" +
            " + (SELECT COUNT(*) FROM physical_readings WHERE $UNSENT AND bookId NOT IN $COMIC_IDS)" +
            " + (SELECT COUNT(*) FROM notes WHERE $UNSENT AND bookId NOT IN $COMIC_IDS)" +
            " + (SELECT COUNT(*) FROM yearly_goals WHERE $UNSENT)" +
            " + (SELECT COUNT(*) FROM shelves WHERE $UNSENT)" +
            " + (SELECT COUNT(*) FROM shelf_books WHERE $UNSENT AND bookId NOT IN $COMIC_IDS)",
    )
    suspend fun unsentCount(): Int

    /** Books to be copied to the account, for the screen that asks to. */
    @Query("SELECT COUNT(*) FROM books WHERE deletedAt IS NULL AND $NOT_COMIC")
    suspend fun bookCount(): Int

    // A row removed before it was ever sent is nothing the server needs to hear about.
    @Query("UPDATE books SET syncedAt = $BOOK_VERSION WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentBooks()

    @Query("UPDATE read_throughs SET syncedAt = updatedAt WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentReadThroughs()

    @Query("UPDATE reading_sessions SET syncedAt = updatedAt WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentReadingSessions()

    @Query("UPDATE physical_readings SET syncedAt = updatedAt WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentPhysicalReadings()

    @Query("UPDATE notes SET syncedAt = updatedAt WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentNotes()

    @Query("UPDATE yearly_goals SET syncedAt = updatedAt WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentYearlyGoals()

    @Query("UPDATE shelves SET syncedAt = updatedAt WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentShelves()

    @Query("UPDATE shelf_books SET syncedAt = updatedAt WHERE syncedAt IS NULL AND deletedAt IS NOT NULL")
    suspend fun settleUnsentShelfBooks()

    // ---- Sent ---------------------------------------------------------------------------------
    // Only if the row is still the version that was sent: one edited meanwhile stays waiting.

    @Query("UPDATE books SET syncedAt = :version WHERE id = :id AND $BOOK_VERSION = :version")
    suspend fun sentBook(id: String, version: Long)

    @Query("UPDATE read_throughs SET syncedAt = :updatedAt WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun sentReadThrough(id: String, updatedAt: Long)

    @Query("UPDATE reading_sessions SET syncedAt = :updatedAt WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun sentReadingSession(id: String, updatedAt: Long)

    @Query("UPDATE physical_readings SET syncedAt = :updatedAt WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun sentPhysicalReading(id: String, updatedAt: Long)

    @Query("UPDATE notes SET syncedAt = :updatedAt WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun sentNote(id: String, updatedAt: Long)

    @Query("UPDATE yearly_goals SET syncedAt = :updatedAt WHERE year = :year AND updatedAt = :updatedAt")
    suspend fun sentYearlyGoal(year: Int, updatedAt: Long)

    @Query("UPDATE shelves SET syncedAt = :updatedAt WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun sentShelf(id: String, updatedAt: Long)

    @Query("UPDATE shelf_books SET syncedAt = :updatedAt WHERE shelfId = :shelfId AND bookId = :bookId AND updatedAt = :updatedAt")
    suspend fun sentShelfBook(shelfId: String, bookId: String, updatedAt: Long)

    // ---- Received -----------------------------------------------------------------------------

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun book(id: String): BookEntity?

    @Query("SELECT updatedAt FROM read_throughs WHERE id = :id")
    suspend fun readThroughUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM reading_sessions WHERE id = :id")
    suspend fun readingSessionUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM physical_readings WHERE id = :id")
    suspend fun physicalReadingUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM notes WHERE id = :id")
    suspend fun noteUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM yearly_goals WHERE year = :year")
    suspend fun yearlyGoalUpdatedAt(year: Int): Long?

    @Query("SELECT updatedAt FROM shelves WHERE id = :id")
    suspend fun shelfUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM shelf_books WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun shelfBookUpdatedAt(shelfId: String, bookId: String): Long?

    @Upsert suspend fun putBook(row: BookEntity)
    @Upsert suspend fun putReadThrough(row: ReadThroughEntity)
    @Upsert suspend fun putReadingSession(row: ReadingSessionEntity)
    @Upsert suspend fun putPhysicalReading(row: PhysicalReadingEntity)
    @Upsert suspend fun putNote(row: NoteEntity)
    @Upsert suspend fun putYearlyGoal(row: YearlyGoalEntity)
    @Upsert suspend fun putShelf(row: ShelfEntity)
    @Upsert suspend fun putShelfBook(row: ShelfBookEntity)

    /** A shelf removed elsewhere takes its comics off it here too, which the server never knew were on it. */
    @Query(
        "UPDATE shelf_books SET deletedAt = :at, updatedAt = :at, syncedAt = :at " +
            "WHERE shelfId = :shelfId AND deletedAt IS NULL AND bookId IN $COMIC_IDS",
    )
    suspend fun removeComicsFromShelf(shelfId: String, at: Long)

    // ---- Starting over ------------------------------------------------------------------------

    /** Everything is sent again, for after a backup was put back over what was synced. */
    @Query("UPDATE books SET syncedAt = NULL")
    suspend fun unsendBooks()

    @Query("UPDATE read_throughs SET syncedAt = NULL")
    suspend fun unsendReadThroughs()

    @Query("UPDATE reading_sessions SET syncedAt = NULL")
    suspend fun unsendReadingSessions()

    @Query("UPDATE physical_readings SET syncedAt = NULL")
    suspend fun unsendPhysicalReadings()

    @Query("UPDATE notes SET syncedAt = NULL")
    suspend fun unsendNotes()

    @Query("UPDATE yearly_goals SET syncedAt = NULL")
    suspend fun unsendYearlyGoals()

    @Query("UPDATE shelves SET syncedAt = NULL")
    suspend fun unsendShelves()

    @Query("UPDATE shelf_books SET syncedAt = NULL")
    suspend fun unsendShelfBooks()

    // Another account signed in: the books of the last one go, and the new one's come down.
    // Comics stay, being nobody's on the server. Shelves go only once nothing is left on them.

    @Query("DELETE FROM read_throughs WHERE bookId NOT IN $COMIC_IDS")
    suspend fun forgetReadThroughs()

    @Query("DELETE FROM reading_sessions WHERE bookId NOT IN $COMIC_IDS")
    suspend fun forgetReadingSessions()

    @Query("DELETE FROM physical_readings WHERE bookId NOT IN $COMIC_IDS")
    suspend fun forgetPhysicalReadings()

    @Query("DELETE FROM notes WHERE bookId NOT IN $COMIC_IDS")
    suspend fun forgetNotes()

    @Query("DELETE FROM shelf_books WHERE bookId NOT IN $COMIC_IDS")
    suspend fun forgetShelfBooks()

    @Query("DELETE FROM shelves WHERE id NOT IN (SELECT shelfId FROM shelf_books)")
    suspend fun forgetEmptyShelves()

    @Query("DELETE FROM yearly_goals")
    suspend fun forgetYearlyGoals()

    @Query("DELETE FROM books WHERE $NOT_COMIC")
    suspend fun forgetBooks()

    // ---- Covers -------------------------------------------------------------------------------

    /** Cover files the books that sync name, so they can be sent and fetched with them. */
    @Query("SELECT DISTINCT coverFile FROM books WHERE coverFile IS NOT NULL AND deletedAt IS NULL AND $NOT_COMIC")
    suspend fun coverFiles(): List<String>
}
