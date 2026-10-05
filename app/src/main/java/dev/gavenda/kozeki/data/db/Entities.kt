package dev.gavenda.kozeki.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.ReadOutcome
import dev.gavenda.kozeki.data.model.ReadingState

/**
 * A book the user tracks. It may or may not have an EPUB attached: [fileHash] identifies the
 * imported file, but the file itself lives outside the database and is never synced, so a row
 * can legitimately exist without its file (after a restore, or on another device).
 */
@Entity(
    tableName = "books",
    indices = [
        Index("state"),
        Index("fileHash"),
        Index("source", "sourceId"),
    ],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val publisher: String? = null,
    /** As given by the source: "2019", "2019-05" or "2019-05-21". */
    val publishedDate: String? = null,
    val language: String? = null,
    /** Print page count reported by the metadata source. */
    val pageCount: Int? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val categories: List<String> = emptyList(),
    /** File name inside the covers directory. */
    val coverFile: String? = null,
    val coverUrl: String? = null,

    val state: ReadingState = ReadingState.PLANNED,
    val acquisition: Acquisition = Acquisition.WISHLIST,
    val isFavorite: Boolean = false,
    /** 0.5 to 5.0 in half-star steps. */
    val rating: Float? = null,
    /** Price in minor units of [purchaseCurrency], for example cents. */
    val purchasePriceMinor: Long? = null,
    val purchaseCurrency: String? = null,
    /** Epoch day of the purchase. */
    val purchasedOn: Long? = null,
    /** Where the book was bought, as the user typed it: a shop, a site, a city. */
    val purchaseLocation: String? = null,

    val source: MetadataSource? = null,
    val sourceId: String? = null,
    /** The book's page on the source, shown as a link back to it. */
    val sourceUrl: String? = null,
    val matchStatus: MatchStatus = MatchStatus.NONE,

    /** SHA-256 of the imported EPUB. Non-null means the book belongs in the Library. */
    val fileHash: String? = null,
    val fileSize: Long? = null,
    val originalFileName: String? = null,

    /** Readium locator of the last read position, as JSON. */
    val locator: String? = null,
    /** 0.0 to 1.0 through the whole book. */
    val progression: Double = 0.0,
    /** Readium positions stand in for pages, which reflowable EPUBs do not have. */
    val position: Int? = null,
    val positionCount: Int? = null,
    val chapterTitle: String? = null,
    /** 1-based index of the current table-of-contents entry. */
    val chapterIndex: Int? = null,
    val chapterCount: Int? = null,
    val lastReadAt: Long? = null,

    /** Page reached in a physical copy, entered by hand and kept apart from the EPUB position above. */
    val physicalPage: Int? = null,
    /** Pages in the user's physical copy, when its edition does not have [pageCount] pages. */
    val physicalPageCount: Int? = null,

    @Embedded val sync: SyncStamp,
)

/** One pass through a book, so re-reads keep their own dates and sessions. */
@Entity(
    tableName = "read_throughs",
    indices = [Index("bookId"), Index("finishedAt")],
)
data class ReadThroughEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    /** 1 for the first reading, 2 for the first re-read, and so on. */
    val number: Int,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val outcome: ReadOutcome? = null,
    /** Epoch day [finishedAt] fell on, in the device's zone at the time. */
    val finishedOn: Long? = null,
    @Embedded val sync: SyncStamp,
)

/** A stretch of reading in the built-in reader. Append-only, which keeps it trivially mergeable. */
@Entity(
    tableName = "reading_sessions",
    indices = [Index("bookId"), Index("day")],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val readThroughId: String?,
    val startedAt: Long,
    val endedAt: Long,
    /** Time actually spent reading, with idle gaps capped. */
    val durationMs: Long,
    /** Epoch day the session started on, in the device's zone at the time. */
    val day: Long,
    val startProgression: Double,
    val endProgression: Double,
    val startPosition: Int? = null,
    val endPosition: Int? = null,
    /** Pages turned forward during the session, in Readium positions. */
    val pages: Int = 0,
    val startChapter: String? = null,
    val endChapter: String? = null,
    @Embedded val sync: SyncStamp,
)

@Entity(
    tableName = "notes",
    indices = [Index("bookId")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val text: String,
    /** Where in the book the note was taken, when written from the reader. */
    val chapterTitle: String? = null,
    val progression: Double? = null,
    @Embedded val sync: SyncStamp,
)

@Entity(tableName = "yearly_goals")
data class YearlyGoalEntity(
    @PrimaryKey val year: Int,
    val books: Int,
    @Embedded val sync: SyncStamp,
)

/** Cached responses from a metadata source. Device-local and never synced. */
@Entity(tableName = "metadata_cache")
data class MetadataCacheEntity(
    @PrimaryKey val key: String,
    val source: MetadataSource,
    /** JSON list of normalised results, so a hit costs no request and no re-parsing of raw payloads. */
    val payload: String,
    val fetchedAt: Long,
    val expiresAt: Long,
)
