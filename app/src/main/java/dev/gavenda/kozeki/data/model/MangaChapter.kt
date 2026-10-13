package dev.gavenda.kozeki.data.model

/** A chapter of a manga as the UI sees it. */
data class MangaChapter(
    /** The chapter's URL on its source, which identifies it within its book. */
    val url: String,
    val name: String,
    /** The number its source gives it, or null when it gives none. */
    val number: Float?,
    val scanlator: String?,
    /** Epoch milliseconds, or null when the source does not say. */
    val uploadedAt: Long?,
    /** Place in the list the source returned it in. */
    val sourceOrder: Int,
    val read: Boolean,
    /** 0-based index of the last page reached. */
    val lastPageRead: Int,
    val pageCount: Int?,
    /** What the source asked to have handed back with the chapter, as JSON. */
    val memo: String? = null,
) {
    /** The number as it is written on a chapter: "12", not "12.0", and "12.5" as it is. */
    val numberText: String?
        get() = number?.let { if (it % 1f == 0f) it.toLong().toString() else it.toString() }
}

private val ChapterNumber =
    Regex("""(?i)\b(?:chapter|chap|ch|episode|ep)\.?\s*(\d+(?:\.\d+)?)""")

/**
 * The number a chapter's name gives it ("Chapter 12.5 - Title"), for the many sources that give no
 * number of their own. Null when the name has none.
 */
fun recognizeChapterNumber(name: String): Float? =
    ChapterNumber.find(name)?.groupValues?.get(1)?.toFloatOrNull()

/**
 * The chapters in the order they are read, the first chapter first. Sources list the newest chapter
 * first as a rule, but some list the oldest, and the numbers they give tell which.
 */
fun List<MangaChapter>.inReadingOrder(): List<MangaChapter> {
    if (size < 2) return this
    val bySource = sortedBy { it.sourceOrder }
    val first = bySource.firstNotNullOfOrNull { it.number }
    val last = bySource.asReversed().firstNotNullOfOrNull { it.number }
    // Only a list that is plainly counting up is kept as it is.
    val listedOldestFirst = first != null && last != null && first < last
    return if (listedOldestFirst) bySource else bySource.asReversed()
}

/**
 * Where reading goes on from: the first chapter, in reading order, that is not finished. Null when
 * every chapter is, or when there are none. [this] is in reading order.
 */
fun List<MangaChapter>.resumeChapter(): MangaChapter? = firstOrNull { !it.read }
