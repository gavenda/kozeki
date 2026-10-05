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
enum class MetadataSource(
    /** Whether the source carries what its readers wrote about a book. */
    val hasReviews: Boolean,
    /** Whether the source keeps a page for each author, which is what gives them an ID. */
    val hasAuthorPages: Boolean,
) {
    HARDCOVER(hasReviews = true, hasAuthorPages = true),

    /** A catalogue and nothing more: no written reviews, and authors are names alone. */
    GOOGLE_BOOKS(hasReviews = false, hasAuthorPages = false),
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
