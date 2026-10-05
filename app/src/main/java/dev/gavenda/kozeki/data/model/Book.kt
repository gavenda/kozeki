package dev.gavenda.kozeki.data.model

import java.io.File
import java.time.LocalDate

/** A book as the UI sees it. */
data class Book(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val categories: List<String> = emptyList(),
    /** Absolute path of the cover kept on the device. */
    val coverPath: String? = null,
    val coverUrl: String? = null,
    val state: ReadingState = ReadingState.PLANNED,
    val acquisition: Acquisition = Acquisition.WISHLIST,
    val isFavorite: Boolean = false,
    val rating: Float? = null,
    val purchasePriceMinor: Long? = null,
    val purchaseCurrency: String? = null,
    val purchasedOn: LocalDate? = null,
    val purchaseLocation: String? = null,
    val source: MetadataSource? = null,
    val sourceUrl: String? = null,
    val matchStatus: MatchStatus = MatchStatus.NONE,
    /** An EPUB was imported for this book. */
    val inLibrary: Boolean = false,
    /** The EPUB is on this device. False after a restore, until the file is imported again. */
    val hasFile: Boolean = false,
    /** Readium locator of the last read position, as JSON. */
    val locator: String? = null,
    val progression: Double = 0.0,
    val position: Int? = null,
    val positionCount: Int? = null,
    val chapterTitle: String? = null,
    val chapterIndex: Int? = null,
    val chapterCount: Int? = null,
    val lastReadAt: Long? = null,
    /** Page reached in a physical copy, entered by hand. Null while no physical copy is tracked. */
    val physicalPage: Int? = null,
    /** Pages in the physical copy: what the user entered, else [pageCount]. */
    val physicalPageCount: Int? = null,
    val addedAt: Long = 0L,
) {
    val authorLine: String get() = authors.joinToString(", ")

    /** What to hand to the image loader: the local file when there is one, else the remote cover. */
    val coverModel: Any? get() = coverPath?.let(::File) ?: coverUrl

    val canRead: Boolean get() = inLibrary && hasFile

    /** Reading a physical copy is tracked for any book the user has, with or without an EPUB. */
    val canTrackPhysical: Boolean get() = acquisition != Acquisition.WISHLIST

    /** 0.0 to 1.0 through the physical copy, or null while the page or the page count is unknown. */
    val physicalProgression: Double?
        get() {
            val page = physicalPage ?: return null
            val count = physicalPageCount?.takeIf { it > 0 } ?: return null
            return (page.toDouble() / count).coerceIn(0.0, 1.0)
        }

    /**
     * One figure for where there is room for only one, such as the library grid: whichever of the
     * EPUB and the physical copy is further along.
     */
    val overallProgression: Double
        get() = when {
            state == ReadingState.COMPLETED -> 1.0
            inLibrary -> maxOf(progression, physicalProgression ?: 0.0)
            else -> physicalProgression ?: 0.0
        }

    /** Only a book the user has can be a favorite; one that is merely wanted has not been read yet. */
    val canFavorite: Boolean get() = acquisition != Acquisition.WISHLIST

    /** A book that is merely wanted is always Planned; the other states are for books the user has. */
    val canChangeState: Boolean get() = acquisition != Acquisition.WISHLIST
}

data class Note(
    val id: String,
    val bookId: String,
    val text: String,
    val chapterTitle: String? = null,
    val createdAt: Long = 0L,
)

data class ReadThrough(
    val id: String,
    val number: Int,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val outcome: ReadOutcome? = null,
)

/** Where the reader currently is, as written back to the book. */
data class ReadingProgress(
    val locatorJson: String,
    val progression: Double,
    val position: Int?,
    val positionCount: Int?,
    val chapterTitle: String?,
    val chapterIndex: Int?,
    val chapterCount: Int?,
)

/** A finished stretch of reading, before it is stored. */
data class SessionDraft(
    val bookId: String,
    val readThroughId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val startProgression: Double,
    val endProgression: Double,
    val startPosition: Int?,
    val endPosition: Int?,
    val pages: Int,
    val startChapter: String?,
    val endChapter: String?,
)

sealed interface ImportResult {
    val title: String?

    data class Imported(val bookId: String, override val title: String) : ImportResult

    /** An existing entry received its EPUB: a wishlist or purchased book, or one whose file was missing. */
    data class Attached(val bookId: String, override val title: String) : ImportResult

    data class AlreadyInLibrary(val bookId: String, override val title: String) : ImportResult

    data class Failed(val reason: Reason, override val title: String?) : ImportResult {
        enum class Reason { NOT_AN_EPUB, PROTECTED, UNREADABLE }
    }
}
