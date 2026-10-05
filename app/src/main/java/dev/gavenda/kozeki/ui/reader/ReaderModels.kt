package dev.gavenda.kozeki.ui.reader

import dev.gavenda.kozeki.data.settings.ReaderFont
import dev.gavenda.kozeki.data.settings.ReaderPreferences
import dev.gavenda.kozeki.data.settings.ReaderTheme
import org.readium.navigator.web.fixedlayout.FixedWebRenditionState
import org.readium.navigator.web.reflowable.ReflowableWebRenditionState
import org.readium.navigator.web.reflowable.preferences.ReflowableWebPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.use

/** Readium's two Compose navigators, one per EPUB layout. */
sealed interface Rendition {
    data class Reflowable(val state: ReflowableWebRenditionState) : Rendition
    data class Fixed(val state: FixedWebRenditionState) : Rendition
}

/** One entry of the table of contents, flattened so nesting is just an indent. */
data class TocEntry(
    val title: String,
    val href: Url,
    val level: Int,
    /** Index in the reading order of the resource this entry points into. */
    val spineIndex: Int?,
    /** How far into that resource the entry starts, 0.0 to 1.0. See [locateTocEntries]. */
    val progression: Double = 0.0,
)

fun Publication.flattenedToc(): List<TocEntry> {
    val entries = mutableListOf<TocEntry>()
    fun visit(links: List<Link>, level: Int) {
        for (link in links) {
            val url = link.url()
            val title = link.title?.trim().orEmpty()
            if (title.isNotEmpty()) {
                entries += TocEntry(title, url, level, readingOrder.indexOfFirstWithHref(url.removeFragment()))
            }
            visit(link.children, level + 1)
        }
    }
    visit(tableOfContents, 0)
    return entries
}

/**
 * Works out where inside its resource each entry starts. Many EPUBs put several chapters in one
 * file and point at them with `#anchors`; without this the reader could only ever name the first
 * chapter of the file it is in.
 *
 * The position is the anchor's offset in the markup, which stands in for its position on screen.
 * That is close for prose and drifts in image-heavy files, so a chapter label can change a page
 * early or late.
 */
suspend fun Publication.locateTocEntries(entries: List<TocEntry>): List<TocEntry> {
    val anchored = entries.withIndex().filter { (_, entry) -> entry.spineIndex != null && entry.href.fragment != null }
    if (anchored.isEmpty()) return entries

    val located = entries.toMutableList()
    for ((spineIndex, group) in anchored.groupBy { it.value.spineIndex }) {
        val link = readingOrder.getOrNull(spineIndex ?: continue) ?: continue
        val markup = get(link)?.use { it.read().getOrNull() }?.decodeToString() ?: continue
        for ((index, entry) in group) {
            val progression = anchorProgression(markup, entry.href.fragment.orEmpty()) ?: continue
            located[index] = entry.copy(progression = progression)
        }
    }
    return located
}

/** Where the element with id (or legacy `name`) [fragment] sits in [markup], as a fraction of its length. */
internal fun anchorProgression(markup: String, fragment: String): Double? {
    if (markup.isEmpty() || fragment.isEmpty()) return null
    val escaped = Regex.escape(fragment)
    val match = Regex("""\b(?:id|name)\s*=\s*["']$escaped["']""").find(markup) ?: return null
    return match.range.first.toDouble() / markup.length
}

/**
 * The entry being read: the last one that starts at or before the given place. [anchors] holds
 * each entry's resource index and the progression inside it, in table-of-contents order.
 */
internal fun indexOfChapter(anchors: List<Pair<Int?, Double>>, spineIndex: Int?, progression: Double): Int? {
    if (spineIndex == null) return null
    return anchors.indexOfLast { (entrySpine, entryProgression) ->
        entrySpine != null &&
            (entrySpine < spineIndex || (entrySpine == spineIndex && entryProgression <= progression + CHAPTER_TOLERANCE))
    }.takeIf { it >= 0 }
}

fun List<TocEntry>.chapterIndexAt(spineIndex: Int?, progression: Double): Int? =
    indexOfChapter(map { it.spineIndex to it.progression }, spineIndex, progression)

/** A heading at the very top of the page can land a hair past the page's own start. */
private const val CHAPTER_TOLERANCE = 0.002

/** Where the reader is, as shown in its chrome. */
data class ReaderProgress(
    val progression: Double = 0.0,
    val position: Int? = null,
    val chapterTitle: String? = null,
    val tocIndex: Int? = null,
)

/** The app theme's surface colours as ARGB, so the page can match the rest of the app. */
data class ReaderColors(
    val background: Int,
    val text: Int,
    val isDark: Boolean,
)

/** Page background and text colour for [theme], as ARGB. */
fun ReaderPreferences.pageColors(system: ReaderColors): Pair<Int, Int> = when (theme) {
    ReaderTheme.SYSTEM -> system.background to system.text
    ReaderTheme.LIGHT -> 0xFFFFFFFF.toInt() to 0xFF121212.toInt()
    ReaderTheme.SEPIA -> 0xFFFAF4E8.toInt() to 0xFF121212.toInt()
    ReaderTheme.DARK -> 0xFF000000.toInt() to 0xFFFEFEFE.toInt()
}

fun ReaderPreferences.toReflowable(system: ReaderColors): ReflowableWebPreferences {
    val theme = when (theme) {
        ReaderTheme.SYSTEM -> ReflowableWebPreferences(
            backgroundColor = ReadiumColor(system.background),
            textColor = ReadiumColor(system.text),
            // Publisher colours assume a white page; on a dark one they become unreadable.
            overridePublisherColors = system.isDark,
        )
        ReaderTheme.LIGHT -> ReflowableWebPreferences.LightTheme
        ReaderTheme.SEPIA -> ReflowableWebPreferences.SepiaTheme
        ReaderTheme.DARK -> ReflowableWebPreferences.DarkTheme
    }
    return theme + ReflowableWebPreferences(
        fontSize = fontScale,
        fontFamily = when (font) {
            ReaderFont.PUBLISHER -> null
            ReaderFont.SERIF -> FontFamily.SERIF
            ReaderFont.SANS_SERIF -> FontFamily.SANS_SERIF
            ReaderFont.MONOSPACE -> FontFamily.MONOSPACE
        },
        scroll = scroll,
        textAlign = if (justify) TextAlign.JUSTIFY else null,
        lineHeight = lineHeight,
        // Without a cap a tablet page becomes one very wide column that is tiring to read.
        maximalLineLength = 1.0,
    )
}

/**
 * Measures one stretch of reading. Time only accrues between interactions, and a gap is capped, so
 * a book left open on the table does not count as hours of reading.
 */
class SessionTracker(private val now: () -> Long) {

    data class Snapshot(val progression: Double, val position: Int?, val chapterTitle: String?)

    data class Result(
        val startedAt: Long,
        val endedAt: Long,
        val activeMs: Long,
        /** Pages turned forward. Jumps through the contents or the slider are not reading. */
        val pages: Int,
        val start: Snapshot,
        val end: Snapshot,
    )

    private var startedAt = 0L
    private var lastActivityAt = 0L
    private var activeMs = 0L
    private var pages = 0
    private var start: Snapshot? = null
    private var latest: Snapshot? = null

    val isRunning: Boolean get() = start != null

    fun begin(snapshot: Snapshot) {
        if (isRunning) return
        startedAt = now()
        lastActivityAt = startedAt
        activeMs = 0L
        pages = 0
        start = snapshot
        latest = snapshot
    }

    /** A page turn or a tap. Pass the new position when there is one. */
    fun activity(snapshot: Snapshot? = null) {
        if (!isRunning) return
        val time = now()
        activeMs += (time - lastActivityAt).coerceIn(0L, IDLE_CAP_MS)
        lastActivityAt = time
        if (snapshot != null) {
            val from = latest?.position
            val to = snapshot.position
            if (from != null && to != null && to - from in 1..MAX_PAGE_STEP) pages += to - from
            latest = snapshot
        }
    }

    /** Ends the session. Returns null when it was too short to be worth keeping. */
    fun end(): Result? {
        val first = start ?: return null
        val last = latest ?: first
        activity()
        start = null
        latest = null
        if (activeMs < MIN_SESSION_MS) return null
        return Result(startedAt, lastActivityAt, activeMs, pages, first, last)
    }

    companion object {
        const val IDLE_CAP_MS = 5 * 60_000L
        const val MIN_SESSION_MS = 15_000L

        /** The most positions one page turn covers; a large tablet page holds several. */
        const val MAX_PAGE_STEP = 6
    }
}
