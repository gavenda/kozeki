@file:OptIn(ExperimentalSerializationApi::class)

package dev.gavenda.kozeki.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames

/** What [MetadataSource.COMICS] and the library's comics were saved as before they were called comics. */
const val LEGACY_COMICS = "MANGA"

/** Where a book is in its reading lifecycle. */
enum class ReadingState {
    PLANNED,
    READING,
    COMPLETED,
    DROPPED,
    PAUSED,
}

/** How the user stands with a book: only wanting it, having bought it, or just having its EPUB. */
enum class Acquisition {
    WISHLIST,
    PURCHASED,
    /**
     * An EPUB was imported, or it is a comic read online, and no purchase is on record. Having the
     * means to read it says nothing about buying it.
     */
    DOWNLOADED,
}

/** The online catalogues book metadata can be fetched from. */
enum class MetadataSource(
    /** Whether the source carries what its readers wrote about a book. */
    val hasReviews: Boolean,
    /** Whether the source keeps a page for each author, which is what gives them an ID. */
    val hasAuthorPages: Boolean,
) {
    HARDCOVER(hasReviews = true, hasAuthorPages = true),

    /** A catalogue and nothing more: no written reviews, and authors are names alone. */
    GOOGLE_BOOKS(hasReviews = false, hasAuthorPages = false),

    /**
     * The comic sources of the Mihon extensions installed on the device, standing in as one
     * catalogue. Its books are read through the extension that found them, so they are not
     * something an EPUB can be matched to. Saved as MANGA before it was called comics, and still read under that name.
     */
    @JsonNames(LEGACY_COMICS)
    COMICS(hasReviews = false, hasAuthorPages = false),
    ;

    companion object {
        /** The sources an imported EPUB can be matched against: the ones that describe books. */
        val matchable: List<MetadataSource> = entries.filter { it != COMICS }

        /** The source saved as [name], or null for none. Reads the name comics were saved under before. */
        fun named(name: String?): MetadataSource? =
            entries.find { it.name == name } ?: COMICS.takeIf { name == LEGACY_COMICS }
    }
}

/** State of the link between a local book and a record in a [MetadataSource]. */
enum class MatchStatus {
    /** No lookup wanted, for example after the user unlinked the book. */
    NONE,

    /** A lookup is still owed, typically because the book was imported offline. */
    PENDING,

    /** Candidates were found but none was a confident match; the user has to pick. */
    NEEDS_REVIEW,

    MATCHED,

    /** The source was asked and had nothing. */
    NOT_FOUND,
}

/** How a read-through ended. Open read-throughs have no outcome. */
enum class ReadOutcome {
    COMPLETED,
    DROPPED,
}

/** Whether a comic is still coming out, as AniList has it. */
enum class PublicationStatus {
    RELEASING,
    FINISHED,
    HIATUS,
    CANCELLED,
    NOT_YET_RELEASED,
}

/** Where a comic stands on the user's AniList, which names its states differently from the app. */
enum class TrackStatus {
    CURRENT,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,
    REPEATING,
    ;

    /** The state of the book that goes with it. */
    val readingState: ReadingState
        get() = when (this) {
            CURRENT, REPEATING -> ReadingState.READING
            PLANNING -> ReadingState.PLANNED
            COMPLETED -> ReadingState.COMPLETED
            DROPPED -> ReadingState.DROPPED
            PAUSED -> ReadingState.PAUSED
        }

    companion object {
        fun of(state: ReadingState): TrackStatus = when (state) {
            ReadingState.PLANNED -> PLANNING
            ReadingState.READING -> CURRENT
            ReadingState.COMPLETED -> COMPLETED
            ReadingState.DROPPED -> DROPPED
            ReadingState.PAUSED -> PAUSED
        }
    }
}
