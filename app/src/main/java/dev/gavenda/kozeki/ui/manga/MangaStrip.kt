package dev.gavenda.kozeki.ui.manga

import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.ui.CaptchaActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import dev.gavenda.kozeki.data.manga.PageImages
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.manga.MangaChapterPages
import dev.gavenda.kozeki.data.model.MangaChapter
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the strip is made of, top to bottom: the pages of every chapter laid out, with a break
 * between chapters. A long page is several items, each a slice of it, so that only the slices on
 * screen are ever decoded: a page of a webtoon can be many times taller than the largest bitmap a
 * phone will hold, and shrinking it to fit would make it blurry.
 */
@Immutable
internal sealed interface StripItem {
    /** The chapter the item belongs to, by its URL on the source. */
    val chapter: String

    /** The page of [chapter] the item is, or is nearest to. */
    val page: Int
    val key: String

    /** A page that has not arrived. */
    data class Placeholder(override val chapter: String, override val page: Int) : StripItem {
        override val key get() = "$chapter|$page|0"
    }

    data class Failed(
        override val chapter: String,
        override val page: Int,
        /** Where the captcha that stopped the page is solved, if one did. */
        val captchaUrl: String? = null,
    ) : StripItem {
        override val key get() = "$chapter|$page|0"
    }

    /** The rows [top] until [bottom] of the image in [file], which is [width] by [height] pixels. */
    @Immutable
    data class Slice(
        override val chapter: String,
        override val page: Int,
        val file: File,
        val width: Int,
        val height: Int,
        val top: Int,
        val bottom: Int,
        val index: Int,
    ) : StripItem {
        override val key get() = "$chapter|$page|$index"
    }

    /** A page the image decoders of the device cannot slice, drawn whole. */
    data class Whole(override val chapter: String, override val page: Int, val file: File) : StripItem {
        override val key get() = "$chapter|$page|0"
    }

    /** Above the first chapter: what comes before it, if anything. */
    data class Start(
        override val chapter: String,
        val current: MangaChapter,
        val previous: MangaChapter?,
    ) : StripItem {
        override val page get() = 0
        override val key get() = "start|$chapter"
    }

    /** Between two chapters: the one that is over and the one that begins. */
    data class Gap(
        override val chapter: String,
        override val page: Int,
        val finished: MangaChapter,
        val next: MangaChapter,
    ) : StripItem {
        override val key get() = "gap|$chapter"
    }

    /** Below the last chapter laid out: what comes after it, if anything. */
    data class End(
        override val chapter: String,
        override val page: Int,
        val finished: MangaChapter,
        val next: MangaChapter?,
    ) : StripItem {
        override val key get() = "end|$chapter"
    }
}

/** How a page that has arrived is told apart from one that has not. */
private sealed interface Arrived {
    data class Image(val file: File, val width: Int, val height: Int) : Arrived
    data class Unsliceable(val file: File) : Arrived
    data class Failed(val captchaUrl: String?) : Arrived
}

/**
 * What has arrived of the pages of the chapters in the strip, and from it the items. A page keeps
 * its place in the strip: what comes before it can only change height, never move it.
 */
@Stable
internal class StripState {

    private val arrived = mutableStateMapOf<String, Arrived>()

    /** How much of each page on its way has come, where its source says. Read as it is drawn. */
    private val progress = mutableStateMapOf<String, Float>()

    fun progressOf(chapter: String, page: Int): Float? = progress[key(chapter, page)]

    private fun key(chapter: String, page: Int) = "$chapter|$page"

    /** The items for [segments], which change as their pages arrive and are measured. */
    fun items(segments: List<ChapterSegment>, previous: MangaChapter?, next: MangaChapter?): List<StripItem> =
        buildList {
            segments.forEachIndexed { s, segment ->
                val url = segment.chapter.url
                if (s == 0) add(StripItem.Start(url, segment.chapter, previous))
                val last = segments.getOrNull(s - 1)
                if (last != null) {
                    add(StripItem.Gap(last.chapter.url, last.pages.size - 1, last.chapter, segment.chapter))
                }
                for (page in 0 until segment.pages.size) {
                    when (val state = arrived[key(url, page)]) {
                        null -> add(StripItem.Placeholder(url, page))
                        is Arrived.Failed -> add(StripItem.Failed(url, page, state.captchaUrl))
                        is Arrived.Unsliceable -> add(StripItem.Whole(url, page, state.file))
                        is Arrived.Image -> {
                            var top = 0
                            var index = 0
                            while (top < state.height) {
                                val bottom = minOf(top + SLICE_PX, state.height)
                                add(StripItem.Slice(url, page, state.file, state.width, state.height, top, bottom, index))
                                top = bottom
                                index++
                            }
                        }
                    }
                }
            }
            val end = segments.last()
            add(StripItem.End(end.chapter.url, end.pages.size - 1, end.chapter, next))
        }

    /** Follows the pages of [segments] as they load, for as long as they are in the strip. */
    suspend fun track(segments: List<ChapterSegment>) = coroutineScope {
        for (segment in segments) {
            val url = segment.chapter.url
            for (page in 0 until segment.pages.size) {
                launch {
                    segment.pages.state(page).collect { state ->
                        val key = key(url, page)
                        when (state) {
                            is MangaChapterPages.State.Ready ->
                                if (arrived[key] == null) arrived[key] = withContext(Dispatchers.IO) { measure(state.file) }

                            is MangaChapterPages.State.Error ->
                                arrived[key] = Arrived.Failed((state.cause as? MetadataException.Captcha)?.url)
                            is MangaChapterPages.State.Loading -> {
                                state.progress?.let { progress[key] = it } ?: progress.remove(key)
                                if (arrived[key] is Arrived.Failed) arrived.remove(key)
                            }
                            // Dropped and asked for again: the failure goes with the retry.
                            else -> {
                                progress.remove(key)
                                if (arrived[key] is Arrived.Failed) arrived.remove(key)
                            }
                        }
                        if (state is MangaChapterPages.State.Ready || state is MangaChapterPages.State.Error) progress.remove(key)
                    }
                }
            }
        }
    }

    private fun measure(file: File): Arrived {
        PageImages.sniff(file)?.let { format ->
            val size = PageImages.size(file, format) ?: return Arrived.Failed(null)
            return Arrived.Image(file, size.width, size.height)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Arrived.Unsliceable(file)
        // Not every format the device can show can be cut into slices.
        // Opening the decoder proves nothing: a format can open and then fail on every region.
        val sliceable = runCatching {
            val decoder = BitmapRegionDecoder.newInstance(file.path)
            try {
                val rows = minOf(bounds.outHeight, 16)
                decoder.decodeRegion(Rect(0, 0, bounds.outWidth, rows), BitmapFactory.Options()) != null
            } finally {
                decoder.recycle()
            }
        }.getOrDefault(false)
        return if (sliceable) Arrived.Image(file, bounds.outWidth, bounds.outHeight) else Arrived.Unsliceable(file)
    }
}

/** How many rows of the image a slice has. A slice is a fraction of the largest bitmap there can be. */
private const val SLICE_PX = 2048

/** The place in the list of the first item of [page] of [chapter]. */
internal fun List<StripItem>.firstIndexOf(chapter: String, page: Int): Int =
    indexOfFirst { it.chapter == chapter && it.page == page && it !is StripItem.Gap && it !is StripItem.Start }
        .coerceAtLeast(0)

/** One item of the strip. [widthPx] is how wide the strip is drawn. */
@Composable
internal fun StripRow(
    item: StripItem,
    widthPx: Int,
    onRetry: (chapter: String, page: Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    progress: (chapter: String, page: Int) -> Float? = { _, _ -> null },
) {
    when (item) {
        is StripItem.Placeholder -> Box(
            Modifier.fillMaxWidth().height(PlaceholderHeight),
            contentAlignment = Alignment.Center,
        ) { HaloLoadingIndicator(progress = { progress(item.chapter, item.page) }) }

        is StripItem.Failed -> Column(
            modifier = Modifier.fillMaxWidth().height(PlaceholderHeight).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.manga_reader_page_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            val solve = rememberLauncherForActivityResult(CaptchaActivity.Contract()) { solved ->
                if (solved) onRetry(item.chapter, item.page)
            }
            OutlinedButton(
                onClick = { if (item.captchaUrl != null) solve.launch(item.captchaUrl) else onRetry(item.chapter, item.page) },
            ) {
                Text(stringResource(if (item.captchaUrl != null) R.string.action_open_webview else R.string.action_retry))
            }
        }

        is StripItem.Whole -> WholePage(item, onRetry)

        is StripItem.Slice -> SliceImage(item, widthPx)
        is StripItem.Start -> StripBreak(
            // A chapter is always named by its title, as its source gives it.
            title = item.previous?.name ?: stringResource(R.string.manga_reader_start_first),
        )

        is StripItem.Gap -> StripBreak(
            title = item.finished.name,
            // The next chapter as its source titles it.
            detail = item.next.name,
            checked = true,
            opensNext = true,
        )

        is StripItem.End -> ChapterEnd(item.finished, item.next, onNext = onNext, onBack = onBack, dark = true)
    }
}

/** The break between two chapters, or at the top of the first: what ended and what begins. */
@Composable
private fun StripBreak(
    title: String,
    detail: String? = null,
    checked: Boolean = false,
    opensNext: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth().height(BreakHeight).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The chapter that is over: its mark sits close over its name, the two being one thing.
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (checked) {
                Icon(
                    painterResource(R.drawable.check_circle_fill_48px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        // The chapter that follows: its title with a small open book under it, again as one.
        if (detail != null || opensNext) {
            // A rule across nearly the whole width, in the middle of the room between them.
            Spacer(Modifier.height(BreakSpacing / 2))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(RuleWidth),
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
            )
            Spacer(Modifier.height(BreakSpacing / 2))
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(GroupSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
                if (opensNext) {
                    Icon(
                        painterResource(R.drawable.menu_book_48px),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * A slice of a page, decoded at the size it is drawn at, or at the nearest size above it a power of
 * two allows. Its height is known before it is decoded, so nothing shifts when it arrives.
 */
@Composable
private fun SliceImage(slice: StripItem.Slice, widthPx: Int) {
    val scale = if (slice.width > 0 && widthPx > 0) widthPx.toFloat() / slice.width else 1f
    // Heights are cut from where the page ends up in pixels, so rounding never opens a seam.
    val topPx = (slice.top * scale).roundToInt()
    val bottomPx = (slice.bottom * scale).roundToInt()
    val height = with(LocalDensity.current) { (bottomPx - topPx).coerceAtLeast(1).toDp() }

    // A slice decoded before, or ahead of the scroll, is drawn in the first frame it is on screen.
    val bitmap by produceState(StripSlices.cached(slice, widthPx), slice, widthPx) {
        value = StripSlices.load(slice, widthPx)
    }
    Box(Modifier.fillMaxWidth().height(height)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.manga_reader_page_description, slice.page + 1),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The slices decoded so far, kept so that one scrolled back to is not decoded again, and the
 * decoders of the pages they are cut from, so that a page is opened once for all of its slices.
 */
internal object StripSlices {

    /** Decodes share a few threads, so a fling does not start more at once than the phone can take. */
    private val dispatcher = Dispatchers.IO.limitedParallelism(3)

    /**
     * Room for two pages decoded in software, which come whole: the one being read and the next.
     * A webtoon page of 14,000 rows is some 70 MB at a phone's width.
     */
    private val bitmaps = object : LruCache<String, ImageBitmap>(192 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount
    }

    /** No more than a few pages are on screen or about to be at once. */
    private val decoders = object : LruCache<String, BitmapRegionDecoder>(4) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: BitmapRegionDecoder, newValue: BitmapRegionDecoder?) {
            oldValue.recycle()
        }
    }

    /**
     * A slice on screen and the same slice decoded ahead of it take the same one, so it is decoded
     * once; so do all the slices of a page decoded in software, which is decoded once for them all.
     */
    private val locks = Array(8) { Any() }

    private fun lock(key: String) = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]

    private fun key(slice: StripItem.Slice, widthPx: Int) =
        "${slice.file.path}|${slice.width}x${slice.height}|${slice.top}|$widthPx"

    fun cached(slice: StripItem.Slice, widthPx: Int): ImageBitmap? = bitmaps.get(key(slice, widthPx))

    /** The slice drawn [widthPx] wide, decoded if it has not been. Nothing before the strip is measured. */
    suspend fun load(slice: StripItem.Slice, widthPx: Int): ImageBitmap? {
        if (widthPx <= 0) return null
        val key = key(slice, widthPx)
        bitmaps.get(key)?.let { return it }
        return withContext(dispatcher) {
            val format = PageImages.sniff(slice.file)
            synchronized(lock(if (format != null) slice.file.path else key)) {
                bitmaps.get(key) ?: if (format != null) {
                    decodePage(slice, format, widthPx)
                } else {
                    decodeRegion(slice, widthPx)?.let { fit(it, widthPx) }?.also { bitmaps.put(key, it) }
                }
            }
        }
    }

    /** The bitmap at the screen's width, uploaded to the GPU before it is first drawn. */
    private fun fit(bitmap: Bitmap, widthPx: Int): ImageBitmap {
        // A power of two leaves most slices wider than the screen; drawn at its own width it costs less to upload.
        val fitted = if (bitmap.width > widthPx) {
            val height = (bitmap.height.toLong() * widthPx / bitmap.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, widthPx, height, true).also { if (it !== bitmap) bitmap.recycle() }
        } else {
            bitmap
        }
        fitted.prepareToDraw()
        return fitted.asImageBitmap()
    }

    /** The slice with the fewest pixels a power of two allows that is still as wide as the screen. */
    private fun decodeRegion(slice: StripItem.Slice, widthPx: Int): Bitmap? {
        var sample = 1
        while (slice.width / (sample * 2) >= widthPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        // A decoder pushed out of the cache by another page is closed, and opened again here.
        repeat(2) {
            val decoder = regionDecoder(slice.file) ?: return null
            try {
                return decoder.decodeRegion(Rect(0, slice.top, slice.width, slice.bottom), options)
            } catch (_: IllegalStateException) {
            } catch (_: OutOfMemoryError) {
                return null
            }
        }
        return null
    }

    private fun regionDecoder(file: File): BitmapRegionDecoder? = synchronized(decoders) {
        decoders.get(file.path)
            ?: runCatching { BitmapRegionDecoder.newInstance(file.path) }.getOrNull()?.also { decoders.put(file.path, it) }
    }

    /**
     * A page in a format decoded in software, which can only be decoded whole: it is cut into all of
     * its slices at once, and the page itself let go, so a page is never decoded twice while it is
     * read. Gives [slice] of them.
     */
    private fun decodePage(slice: StripItem.Slice, format: PageImages.Format, widthPx: Int): ImageBitmap? {
        val page = runCatching { PageImages.decode(slice.file.readBytes(), format, widthPx) }.getOrNull() ?: return null
        val scale = page.width.toFloat() / slice.width
        var wanted: ImageBitmap? = null
        for ((index, top) in (0 until slice.height step SLICE_PX).withIndex()) {
            val part = slice.copy(top = top, bottom = minOf(top + SLICE_PX, slice.height), index = index)
            val y = (part.top * scale).roundToInt().coerceIn(0, page.height - 1)
            val bottom = (part.bottom * scale).roundToInt().coerceIn(y + 1, page.height)
            val image = Bitmap.createBitmap(page, 0, y, page.width, bottom - y).asImageBitmap()
            bitmaps.put(key(part, widthPx), image)
            if (part.top == slice.top) wanted = image
        }
        wanted?.prepareToDraw()
        // A page of a single slice is that slice, and is kept.
        if (wanted?.asAndroidBitmap() !== page) page.recycle()
        return wanted
    }
}

/** A page drawn whole, with the way to ask for it again if it will not decode. */
@Composable
private fun WholePage(item: StripItem.Whole, onRetry: (chapter: String, page: Int) -> Unit) {
    var failed by remember(item.file) { mutableStateOf(false) }
    if (failed) {
        StripRow(StripItem.Failed(item.chapter, item.page), 0, onRetry, {}, {})
    } else {
        AsyncImage(
            model = item.file,
            contentDescription = stringResource(R.string.manga_reader_page_description, item.page + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
            onError = { failed = true },
        )
    }
}

private val PlaceholderHeight = 400.dp
private val BreakHeight = 360.dp

/** The room between the chapter that is over and the one that follows on a break, which is meant to be a pause. */
private val BreakSpacing = 56.dp

/** The share of the width the rule between the two spans. */
private const val RuleWidth = 0.8f

/** The room inside each of the two: a mark and its name, a title and its book. */
private val GroupSpacing = 8.dp
