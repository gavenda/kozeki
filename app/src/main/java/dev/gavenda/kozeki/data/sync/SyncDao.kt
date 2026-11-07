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
// Books this device has that the account never had.
private const val NEVER_SENT = "(SELECT id FROM books WHERE syncedAt IS NULL AND $NOT_COMIC)"

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

    // ---- One book entered twice ---------------------------------------------------------------

    /** Every book that syncs and is not removed. */
    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND $NOT_COMIC")
    suspend fun liveBooks(): List<BookEntity>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: String)

    @Query("SELECT * FROM shelf_books WHERE bookId = :bookId")
    suspend fun shelfLinks(bookId: String): List<ShelfBookEntity>

    @Query("SELECT * FROM shelf_books WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun shelfLink(shelfId: String, bookId: String): ShelfBookEntity?

    @Query("DELETE FROM shelf_books WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun deleteShelfLink(shelfId: String, bookId: String)

    /**
     * A removed book and a kept one with the same EPUB, as (removed, kept): another device merged
     * the two, and the file this one holds under the removed book belongs to the kept one now.
     */
    @Query(
        "SELECT dead.id AS dropped, live.id AS kept FROM books dead JOIN books live ON live.fileHash = dead.fileHash " +
            "WHERE dead.deletedAt IS NOT NULL AND live.deletedAt IS NULL AND dead.id != live.id",
    )
    suspend fun mergedEpubs(): List<MergedBook>

    // Keeping the account's library over this device's: what was never sent goes, comics stay.

    @Query("DELETE FROM read_throughs WHERE bookId IN $NEVER_SENT")
    suspend fun dropUnsentReadThroughs()

    @Query("DELETE FROM reading_sessions WHERE bookId IN $NEVER_SENT")
    suspend fun dropUnsentReadingSessions()

    @Query("DELETE FROM physical_readings WHERE bookId IN $NEVER_SENT")
    suspend fun dropUnsentPhysicalReadings()

    @Query("DELETE FROM notes WHERE bookId IN $NEVER_SENT")
    suspend fun dropUnsentNotes()

    @Query("DELETE FROM shelf_books WHERE bookId IN $NEVER_SENT")
    suspend fun dropUnsentShelfBooks()

    @Query("DELETE FROM books WHERE syncedAt IS NULL AND $NOT_COMIC")
    suspend fun dropUnsentBooks()

    /** Shelves made here that the books going away leave empty. */
    @Query("DELETE FROM shelves WHERE syncedAt IS NULL AND id NOT IN (SELECT shelfId FROM shelf_books)")
    suspend fun dropUnsentEmptyShelves()

    // ---- Covers -------------------------------------------------------------------------------

    /**
     * Cover files the books that sync name, so they can be sent and fetched with them. [CoverRef.kept]
     * is whether the account keeps the picture: one the user chose, one from the EPUB, or one with
     * nowhere else to come from. A catalogue picture is fetched from [CoverRef.url] on each device
     * instead (`coverInAccount` in webapp/src/lib/covers.ts says the same).
     */
    @Query(
        "SELECT coverFile AS name, MAX(coverUrl) AS url, " +
            "MAX(customCover = 1 OR fileHash IS NOT NULL OR coverUrl IS NULL) AS kept " +
            "FROM books WHERE coverFile IS NOT NULL AND deletedAt IS NULL AND $NOT_COMIC GROUP BY coverFile",
    )
    suspend fun coverRefs(): List<CoverRef>
}

data class MergedBook(val dropped: String, val kept: String)

data class CoverRef(val name: String, val url: String?, val kept: Boolean)
