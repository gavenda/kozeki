package dev.gavenda.kozeki.data.model

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

    /** An EPUB was imported but no purchase is on record. Having the file says nothing about buying it. */
    DOWNLOADED,
}

/** The online catalogues book metadata can be fetched from. */
enum class MetadataSource {
    HARDCOVER,
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
