package dev.gavenda.kozeki.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.PublicationStatus
import dev.gavenda.kozeki.data.model.ReadOutcome
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.TrackStatus
import kotlinx.serialization.Serializable

/**
 * A book the user tracks. It may or may not have an EPUB attached: [fileHash] identifies the
 * imported file, but the file itself lives outside the database and is never synced, so a row
 * can legitimately exist without its file (after a restore, or on another device).
 *
 * This and the other rows the user's reading is made of are written to a backup as they are, so a
 * new column needs a default for the backups made before it to still be read.
 */
@Entity(
    tableName = "books",
    indices = [
        Index("state"),
        Index("fileHash"),
        Index("source", "sourceId"),
    ],
)
@Serializable
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    /** Who among [authors] the metadata source keeps a page for, by its ID for them. */
    @ColumnInfo(defaultValue = "[]") val authorRefs: List<AuthorRef> = emptyList(),
    val description: String? = null,
    val publisher: String? = null,
    /** The series the book belongs to, as the user or the EPUB names it. */
    val series: String? = null,
    /** Where the book stands in [series]: 1, 2, or 1.5 for one that goes between them. */
    val seriesNumber: Double? = null,
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
    /** [coverFile] is a picture the user chose, which no cover from an EPUB or a source replaces. */
    @ColumnInfo(defaultValue = "0") val customCover: Boolean = false,

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
    /** Anything else worth keeping about the purchase, as the user typed it. */
    val purchaseMemo: String? = null,

    val source: MetadataSource? = null,
    val sourceId: String? = null,
    /** The book's page on the source, shown as a link back to it. */
    val sourceUrl: String? = null,
    val matchStatus: MatchStatus = MatchStatus.NONE,
    /**
     * What a Mihon source asked to have handed back with the comic, as JSON. Only comics have any, and
     * a source that keeps none leaves it null.
     */
    val sourceMemo: String? = null,

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
    /** Chapters of a comic that came out since its page was last opened. Opening the page puts it back to 0. */
    @ColumnInfo(defaultValue = "0") val newChapters: Int = 0,
    /** Epoch milliseconds a comic's source was last asked for its chapters in the background, or 0 for never. */
    @ColumnInfo(defaultValue = "0") val chaptersCheckedAt: Long = 0L,

    /** Page reached in a physical copy, entered by hand and kept apart from the EPUB position above. */
    val physicalPage: Int? = null,
    /** Pages in the user's physical copy, when its edition does not have [pageCount] pages. */
    val physicalPageCount: Int? = null,

    @Embedded val sync: SyncStamp,
)

/** Whether the book is a comic: read chapter by chapter through a Mihon source, with no file of its own. */
val BookEntity.isComic: Boolean get() = source == MetadataSource.COMICS

/** Whether the book is on the user's shelves: an EPUB was imported for it, or it is a comic being read online. */
val BookEntity.isInLibrary: Boolean get() = fileHash != null || isComic

/**
 * A chapter of a comic, as its source listed it, with where the user is in it. Rows are written to a
 * backup as they are, so a new column needs a default. Chapters are looked up in the source again
 * whenever the book is opened, so what is kept for good is what the user did: [read] and [lastPageRead].
 */
@Entity(
    tableName = "comic_chapters",
    primaryKeys = ["bookId", "url"],
    indices = [Index("bookId")],
)
@Serializable
data class ComicChapterEntity(
    val bookId: String,
    /** The chapter's URL on its source, which is what the source knows it by. */
    val url: String,
    val name: String,
    /** The number the source gives it, or -1 when it gives none. */
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
    /** Epoch milliseconds the source says it was uploaded at, or 0 when it does not say. */
    val uploadedAt: Long = 0L,
    /** Place in the list the source returned it in, 0 being the first. Sources usually list the newest first. */
    val sourceOrder: Int = 0,
    val read: Boolean = false,
    /** 0-based index of the last page reached. */
    val lastPageRead: Int = 0,
    /** Pages the chapter has, known once it has been opened. */
    val pageCount: Int? = null,
    /** What the source asked to have handed back with the chapter, as JSON, or null when it keeps none. */
    val memo: String? = null,
    @Embedded val sync: SyncStamp,
)

/** One pass through a book, so re-reads keep their own dates and sessions. */
@Entity(
    tableName = "read_throughs",
    indices = [Index("bookId"), Index("finishedAt")],
)
@Serializable
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
@Serializable
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

/**
 * Pages read in a physical copy, put on record when the user moves its page on. Nobody timed it,
 * so it has no duration. Unlike a session it does not stay as written: taking the page back
 * removes what was recorded past it.
 */
@Entity(
    tableName = "physical_readings",
    indices = [Index("bookId"), Index("day")],
)
@Serializable
data class PhysicalReadingEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val readThroughId: String?,
    /** When the page reached was entered, which is as close to when it was read as is known. */
    val recordedAt: Long,
    /** Epoch day [recordedAt] fell on, in the device's zone at the time. */
    val day: Long,
    /** The page the copy was on before. */
    val startPage: Int,
    val endPage: Int,
    @Embedded val sync: SyncStamp,
)

@Entity(
    tableName = "notes",
    indices = [Index("bookId")],
)
@Serializable
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
@Serializable
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

/** A comic as it stands on the user's AniList. The progress and status here are what AniList was last told or told us. */
@Entity(tableName = "comic_tracks")
@Serializable
data class ComicTrackEntity(
    @PrimaryKey val bookId: String,
    /** The comic's ID on AniList. */
    val mediaId: Long,
    /** The user's list entry for it, once there is one. */
    val entryId: Long? = null,
    /** The title AniList knows it by. */
    val title: String,
    val siteUrl: String? = null,
    /** Its cover on AniList. Null for a track made before covers were kept, until it is refreshed. */
    val coverUrl: String? = null,
    /** Null while its publication is going on and AniList does not know the end. */
    val totalChapters: Int? = null,
    /** Whether it is still coming out. Null for a track made before this was kept, until it is refreshed. */
    val publication: PublicationStatus? = null,
    val status: TrackStatus = TrackStatus.PLANNING,
    /** The highest chapter read, as a whole number. */
    val progress: Int = 0,
    /** 0 to 100 whatever scale the account shows, 0 for none. */
    val score: Int = 0,
    /** Epoch day. */
    val startedOn: Long? = null,
    val completedOn: Long? = null,
    /** AniList has not been told the latest, because it could not be reached. */
    val pendingPush: Boolean = false,
    @Embedded val sync: SyncStamp,
)

/** A named collection of the user's books and comics. Written to a backup as it is, so a new column needs a default. */
@Entity(tableName = "shelves")
@Serializable
data class ShelfEntity(
    @PrimaryKey val id: String,
    val name: String,
    @Embedded val sync: SyncStamp,
)

/**
 * A book being on a shelf. A removal is a tombstone like any other, and putting the book back
 * writes the row over again.
 */
@Entity(
    tableName = "shelf_books",
    primaryKeys = ["shelfId", "bookId"],
    indices = [Index("bookId")],
)
@Serializable
data class ShelfBookEntity(
    val shelfId: String,
    val bookId: String,
    @Embedded val sync: SyncStamp,
)
