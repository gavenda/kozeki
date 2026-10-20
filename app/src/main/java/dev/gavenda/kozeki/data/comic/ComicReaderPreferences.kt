package dev.gavenda.kozeki.data.comic

import android.content.Context
import androidx.core.content.edit

/** How the pages of a comic follow one another. */
enum class ComicReadingMode {
    /** Paged, turned by moving left, as comics are printed. */
    RIGHT_TO_LEFT,

    /** Paged, turned by moving right. */
    LEFT_TO_RIGHT,

    /** One long strip scrolled down, as webtoons are made. */
    VERTICAL,
}

/**
 * The reading mode a comic is read in, remembered for the comic, and the one last picked for those
 * not read before. Kept on this device only.
 */
class ComicReaderPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("comic_reader", Context.MODE_PRIVATE)

    init {
        // Before comics were called comics these were kept as "manga_reader". Carried over once, then that file goes.
        val app = context.applicationContext
        val legacy = app.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            if (prefs.all.isEmpty()) {
                prefs.edit { legacy.all.forEach { (key, value) -> if (value is String) putString(key, value) } }
            }
            app.deleteSharedPreferences(LEGACY_NAME)
        }
    }

    /**
     * The mode [bookId] is read in: the one picked for it. Failing that a webtoon, which its
     * [categories] say, is a strip scrolled down, since paging a webtoon right to left is never
     * right for it. Failing that the one picked last for any other comics, else right to left.
     */
    fun mode(bookId: String, categories: List<String>): ComicReadingMode =
        read(prefs.getString(bookKey(bookId), null))
            ?: ComicReadingMode.VERTICAL.takeIf { looksLikeWebtoon(categories) }
            ?: read(prefs.getString(DEFAULT, null))
            ?: ComicReadingMode.RIGHT_TO_LEFT

    /**
     * Remembers [mode] for [bookId]. It becomes what comics not read before open in, unless the
     * pick was for a webtoon, which says nothing about how the rest are best read.
     */
    fun setMode(bookId: String, mode: ComicReadingMode, categories: List<String>) = prefs.edit {
        putString(bookKey(bookId), mode.name)
        if (!looksLikeWebtoon(categories)) putString(DEFAULT, mode.name)
    }

    private fun read(name: String?): ComicReadingMode? = ComicReadingMode.entries.find { it.name == name }

    private fun bookKey(bookId: String) = "mode_$bookId"

    companion object {
        private const val DEFAULT = "mode_default"
        private const val LEGACY_NAME = "manga_reader"

        /** Genres sources give to what is drawn as one long vertical strip, for reading on a phone. */
        private val WEBTOON_GENRES = setOf("webtoon", "webtoons", "manhwa", "manhua", "long strip", "web comic", "webcomic")

        /** Whether the [categories] (a comic's genres and tags) mark it as a webtoon. */
        fun looksLikeWebtoon(categories: List<String>): Boolean =
            categories.any { it.trim().lowercase() in WEBTOON_GENRES }
    }
}
