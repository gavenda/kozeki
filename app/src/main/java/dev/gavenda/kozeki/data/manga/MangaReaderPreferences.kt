package dev.gavenda.kozeki.data.manga

import android.content.Context
import androidx.core.content.edit

/** How the pages of a manga follow one another. */
enum class MangaReadingMode {
    /** Paged, turned by moving left, as manga are printed. */
    RIGHT_TO_LEFT,

    /** Paged, turned by moving right. */
    LEFT_TO_RIGHT,

    /** One long strip scrolled down, as webtoons are made. */
    VERTICAL,
}

/**
 * The reading mode a manga is read in, remembered for the manga, and the one last picked for those
 * not read before. Kept on this device only.
 */
class MangaReaderPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("manga_reader", Context.MODE_PRIVATE)

    /**
     * The mode [bookId] is read in: the one picked for it. Failing that a webtoon, which its
     * [categories] say, is a strip scrolled down, since paging a webtoon right to left is never
     * right for it. Failing that the one picked last for any other manga, else right to left.
     */
    fun mode(bookId: String, categories: List<String>): MangaReadingMode =
        read(prefs.getString(bookKey(bookId), null))
            ?: MangaReadingMode.VERTICAL.takeIf { looksLikeWebtoon(categories) }
            ?: read(prefs.getString(DEFAULT, null))
            ?: MangaReadingMode.RIGHT_TO_LEFT

    /**
     * Remembers [mode] for [bookId]. It becomes what manga not read before open in, unless the
     * pick was for a webtoon, which says nothing about how the rest are best read.
     */
    fun setMode(bookId: String, mode: MangaReadingMode, categories: List<String>) = prefs.edit {
        putString(bookKey(bookId), mode.name)
        if (!looksLikeWebtoon(categories)) putString(DEFAULT, mode.name)
    }

    private fun read(name: String?): MangaReadingMode? = MangaReadingMode.entries.find { it.name == name }

    private fun bookKey(bookId: String) = "mode_$bookId"

    companion object {
        private const val DEFAULT = "mode_default"

        /** Genres sources give to what is drawn as one long vertical strip, for reading on a phone. */
        private val WEBTOON_GENRES = setOf("webtoon", "webtoons", "manhwa", "manhua", "long strip", "web comic", "webcomic")

        /** Whether the [categories] (a manga's genres and tags) mark it as a webtoon. */
        fun looksLikeWebtoon(categories: List<String>): Boolean =
            categories.any { it.trim().lowercase() in WEBTOON_GENRES }
    }
}
