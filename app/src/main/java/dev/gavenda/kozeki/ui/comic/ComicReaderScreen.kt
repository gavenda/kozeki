package dev.gavenda.kozeki.ui.comic

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import dev.gavenda.kozeki.data.metadata.MetadataException
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.ui.CaptchaActivity
import dev.gavenda.kozeki.data.comic.ComicChapterPages
import dev.gavenda.kozeki.data.comic.ComicReadingMode
import dev.gavenda.kozeki.data.comic.PageImages
import dev.gavenda.kozeki.data.model.ComicChapter
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.appBarIconButtonColors
import dev.gavenda.kozeki.ui.components.narrowIconButton
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.iconButtonColors
import dev.gavenda.kozeki.ui.koinViewModel
import dev.gavenda.kozeki.ui.comicReaderViewModel
import dev.gavenda.kozeki.ui.reader.ImmersiveMode
import dev.gavenda.kozeki.ui.reader.KeepScreenOn
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

/** Where a chapter of a comic is read, a page at a time or as one long strip. */
@Composable
fun ComicReaderScreen(
    bookId: String,
    chapterUrl: String,
    onBack: () -> Unit,
    viewModel: ComicReaderViewModel = koinViewModel(key = "comic-$bookId-$chapterUrl") {
        comicReaderViewModel(bookId, chapterUrl)
    },
) {
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        ComicReaderUiState.Loading -> ComicReaderLoading()
        is ComicReaderUiState.Failed -> ComicReaderFailed(state, onBack, onRetry = viewModel::retry)
        is ComicReaderUiState.Ready -> ComicReaderReady(state, viewModel, onBack)
    }
}

@Composable
private fun ComicReaderLoading() {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { HaloLoadingIndicator() }
    }
}

@Composable
private fun ComicReaderFailed(state: ComicReaderUiState.Failed, onBack: () -> Unit, onRetry: () -> Unit) {
    // Once the captcha is solved the chapter is asked for again.
    val solve = rememberLauncherForActivityResult(CaptchaActivity.Contract()) { solved -> if (solved) onRetry() }
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            EmptyState(
                expression = Expression.SHOCKED,
                icon = painterResource(R.drawable.error_fill_48px),
                title = stringResource(R.string.reader_failed_title),
                message = stringResource(state.error?.message ?: R.string.comic_reader_chapter_missing),
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.captchaUrl != null) {
                            Button(onClick = { solve.launch(state.captchaUrl) }) {
                                Text(stringResource(R.string.action_open_webview))
                            }
                        } else if (state.error != null) {
                            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                        }
                        Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    }
                },
            )
        }
    }
}

@Composable
private fun ComicReaderReady(state: ComicReaderUiState.Ready, viewModel: ComicReaderViewModel, onBack: () -> Unit) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    ImmersiveMode(hideSystemBars = !chromeVisible)
    KeepScreenOn(interactions)

    // A new chapter or another mode starts its scroll state afresh, at the page the ViewModel holds.
    key(state.generation, mode) {
        val scope = rememberCoroutineScope()
        val pageCount = state.pages.size
        val initial = viewModel.currentPage.coerceIn(0, pageCount - 1)
        val pagerState = rememberPagerState(initialPage = initial) { pageCount + 1 }
        val strip = remember { StripState() }
        val vertical = mode == ComicReadingMode.VERTICAL
        // The strip's chapters change as the next one is laid out below the one on screen.
        val items by remember(state.segments, state.before, state.after) {
            derivedStateOf { strip.items(state.segments, state.before, state.after) }
        }
        // Each page is one item until it arrives, so the page to start at is that many items in.
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = if (vertical) items.firstIndexOf(state.chapter.url, initial) else 0,
        )
        if (vertical) {
            val urls = state.segments.map { it.chapter.url }
            LaunchedEffect(urls) { strip.track(state.segments) }
            // The list finds the item it showed by its key only within a hundred or so places of where
            // it was. A downloaded chapter is all slices at once, well over that, so when one above is
            // let go the list would stay at the same place and land a chapter on. The item is found
            // here instead, after the change and before the list is laid out again.
            val itemsForPosition = items
            SideEffect {
                val index = listState.firstVisibleItemIndex
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@SideEffect
                if (itemsForPosition.getOrNull(index)?.key == first.key) return@SideEffect
                val moved = itemsForPosition.indexOfFirst { it.key == first.key }
                if (moved >= 0) listState.requestScrollToItem(moved, listState.firstVisibleItemScrollOffset)
            }
        }
        val rtl = mode == ComicReadingMode.RIGHT_TO_LEFT
        // The strip outlives the chapter on screen, which changes as the next one is scrolled into.
        val latest by rememberUpdatedState(state)

        /**
         * The page of the strip that has been reached, and its chapter: the first one in view, or the
         * last page of a chapter once the end of it is in view. A short last page can never be the
         * first in view, since the list cannot scroll far enough, but it has been read all the same.
         */
        // The list says which items it has laid out by their keys. Their places in the list are no use:
        // pages arriving above the one in view shift them before the list has been laid out again.
        // Read through a state of its own, since `items` is replaced when chapters are added and a
        // lambda remembered from the first composition would go on reading the first.
        val itemsNow by rememberUpdatedState(items)
        val itemsByKey by remember { derivedStateOf { itemsNow.associateBy { it.key } } }
        val reached: () -> Pair<String, Int> = {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.let { itemsByKey[it.key] }
            val chapter = first?.chapter ?: latest.chapter.url
            var at = first?.page ?: 0
            // The last item of this chapter in view, which is not the last in view when the next chapter has begun.
            val visible = info.visibleItemsInfo.lastOrNull { itemsByKey[it.key]?.chapter == chapter }
            val last = visible?.let { itemsByKey[it.key] }
            if (last != null && visible.offset + visible.size <= info.viewportEndOffset) {
                val count = latest.segments.firstOrNull { it.chapter.url == chapter }?.pages?.size
                if (count != null && (last is StripItem.End || last is StripItem.Gap || last.page >= count - 1)) {
                    at = count - 1
                }
            }
            chapter to at
        }

        val page by remember {
            derivedStateOf {
                if (vertical) {
                    // Only the chapter on screen has a page to show in the controls.
                    reached().takeIf { it.first == latest.chapter.url }?.second ?: 0
                } else {
                    pagerState.currentPage
                }
            }
        }
        LaunchedEffect(vertical) {
            snapshotFlow {
                if (vertical) reached() else latest.chapter.url to pagerState.settledPage
            }
                .distinctUntilChanged()
                .collect { (chapter, page) ->
                    interactions++
                    viewModel.onPageChanged(chapter, page)
                }
        }

        // Pages of a chapter come into view before it is the one on screen, which is only so once its
        // first page reaches the top: they are asked for as they appear, not as the chapter changes.
        if (vertical) {
            LaunchedEffect(Unit) {
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo
                        .mapNotNull { itemsByKey[it.key] }
                        .filter {
                            it is StripItem.Placeholder || it is StripItem.Failed ||
                                it is StripItem.Slice || it is StripItem.Whole
                        }
                        .groupBy({ it.chapter }, { it.page })
                        .mapValues { (_, pages) -> pages.min() }
                }
                    .distinctUntilChanged()
                    .collect(viewModel::onPagesInView)
            }
        }

        // How wide the strip is drawn, which is how wide its slices are decoded.
        var widthPx by remember { mutableIntStateOf(0) }
        // The slices just past the screen, the way it is being scrolled, are decoded before they come
        // into view, and so is the next page that way: a page decoded in software is decoded whole,
        // which takes a second or so, and its slices come long after the screen has reached its top.
        // Places in the list can be stale while pages arrive, which only means a slice decoded for nothing.
        if (vertical) {
            LaunchedEffect(Unit) {
                var previous = listState.firstVisibleItemIndex
                // The next page is decoded on its own, since the slices just past the screen would
                // otherwise wait out the second it takes: a decode under way is not stopped by a scroll.
                var nextPage: StripItem.Slice? = null
                var nextPageJob: Job? = null
                val effect = this
                snapshotFlow {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    Triple(visible.firstOrNull()?.index ?: 0, visible.lastOrNull()?.index ?: 0, widthPx)
                }
                    .distinctUntilChanged()
                    .collectLatest { (first, last, width) ->
                        val forward = first >= previous
                        previous = first
                        val items = itemsNow
                        // The nearest slice of another page, which for a page decoded whole decodes all of it.
                        val edge = items.getOrNull(if (forward) last else first)
                        val beyond = if (forward) last + 1..minOf(last + PAGE_SEARCH_ITEMS, items.lastIndex)
                        else first - 1 downTo maxOf(first - PAGE_SEARCH_ITEMS, 0)
                        val next = beyond.firstNotNullOfOrNull { index ->
                            (items[index] as? StripItem.Slice)
                                ?.takeIf { it.chapter != edge?.chapter || it.page != edge.page }
                        }
                        if (next != null && next != nextPage) {
                            nextPage = next
                            nextPageJob?.cancel()
                            nextPageJob = effect.launch { StripSlices.load(next, width) }
                        }
                        val ahead = if (forward) last + 1..last + PREFETCH_ITEMS else first - 1 downTo first - PREFETCH_ITEMS
                        for (index in ahead) {
                            (items.getOrNull(index) as? StripItem.Slice)?.let { StripSlices.load(it, width) }
                        }
                    }
            }
        }

        val scrollTo: (Int) -> Unit = { target ->
            scope.launch {
                val index = target.coerceIn(0, pageCount)
                if (vertical) {
                    listState.scrollToItem(items.firstIndexOf(latest.chapter.url, index.coerceAtMost(latest.pages.size - 1)))
                } else {
                    pagerState.animateScrollToPage(index)
                }
            }
        }
        val toggleChrome = {
            viewModel.onInteraction()
            chromeVisible = !chromeVisible
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (vertical) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { widthPx = it.width }
                        .pointerInput(Unit) { detectTapGestures(onTap = { toggleChrome() }) },
                ) {
                    items(items, key = { it.key }, contentType = { it::class }) { item ->
                        StripRow(
                            item = item,
                            widthPx = widthPx,
                            progress = strip::progressOf,
                            onRetry = { chapter, page ->
                                state.segments.firstOrNull { it.chapter.url == chapter }?.pages?.retry(page)
                            },
                            onNext = viewModel::openNext,
                            onBack = onBack,
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = rtl,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    if (index < pageCount) {
                        FramedPage(
                            pages = state.pages,
                            index = index,
                            onTap = { fraction ->
                                // The edges turn the page, the middle brings up the controls.
                                val atStart = fraction < EDGE_FRACTION
                                val atEnd = fraction > 1f - EDGE_FRACTION
                                when {
                                    atStart -> scrollTo(if (rtl) index + 1 else index - 1)
                                    atEnd -> scrollTo(if (rtl) index - 1 else index + 1)
                                    else -> toggleChrome()
                                }
                            },
                        )
                    } else {
                        ChapterEnd(state.chapter, state.next, onNext = viewModel::openNext, onBack = onBack, dark = true)
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    TopChrome(state, mode, onBack, onMode = viewModel::setMode)
                    BottomChrome(
                        state = state,
                        page = page,
                        rtl = rtl,
                        onScrollTo = scrollTo,
                        onPrevious = viewModel::openPrevious,
                        onNext = viewModel::openNext,
                    )
                }
            }
        }
    }
}

/** The file of a page once it is there, and whether it is a long strip rather than a page. */
@Composable
private fun rememberPageImage(file: File): State<Boolean> =
    produceState(initialValue = false, file) { value = withContext(Dispatchers.IO) { isTall(file) } }

/** Whether the image is more than twice as tall as it is wide. Only its size is read. */
private fun isTall(file: File): Boolean {
    PageImages.sniff(file)?.let { format ->
        return PageImages.size(file, format)?.let { it.height > it.width * 2 } ?: false
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > bounds.outWidth * 2
}

/** A page of the paged modes: the whole page in view, with pinch and double tap to zoom. */
@Composable
private fun FramedPage(pages: ComicChapterPages, index: Int, onTap: (Float) -> Unit) {
    val state by pages.state(index).collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val loaded = state) {
            ComicChapterPages.State.Queue -> HaloLoadingIndicator()
            is ComicChapterPages.State.Loading -> HaloLoadingIndicator(progress = { loaded.progress })
            is ComicChapterPages.State.Error ->
                PageFailed(
                    onRetry = { pages.retry(index) },
                    captchaUrl = (loaded.cause as? MetadataException.Captcha)?.url,
                    modifier = Modifier.fillMaxSize(),
                )

            is ComicChapterPages.State.Ready -> {
                val tall by rememberPageImage(loaded.file)
                var width by remember { mutableIntStateOf(1) }
                ZoomableAsyncImage(
                    model = loaded.file,
                    contentDescription = stringResource(R.string.comic_reader_page_description, index + 1),
                    modifier = Modifier.fillMaxSize().onSizeChanged { width = it.width.coerceAtLeast(1) },
                    // A long strip is read down its length at the screen's width, not shrunk to fit.
                    contentScale = if (tall) ContentScale.FillWidth else ContentScale.Fit,
                    alignment = if (tall) Alignment.TopCenter else Alignment.Center,
                    onClick = { offset -> onTap(offset.x / width) },
                )
            }
        }
    }
}

@Composable
private fun PageFailed(onRetry: () -> Unit, captchaUrl: String?, modifier: Modifier = Modifier) {
    val solve = rememberLauncherForActivityResult(CaptchaActivity.Contract()) { solved -> if (solved) onRetry() }
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.comic_reader_page_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = { if (captchaUrl != null) solve.launch(captchaUrl) else onRetry() }) {
            Text(stringResource(if (captchaUrl != null) R.string.action_open_webview else R.string.action_retry))
        }
    }
}

/** After the last page: that the chapter is done, and what comes next. */
@Composable
internal fun ChapterEnd(
    finished: ComicChapter,
    next: ComicChapter?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    dark: Boolean,
) {
    val content = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.fillMaxWidth().height(EndHeight).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.check_circle_fill_48px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = finished.name,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            textAlign = TextAlign.Center,
        )
        if (next != null) {
            Button(onClick = onNext) { Text(next.name) }
        } else {
            Text(
                text = stringResource(R.string.comic_reader_end_latest),
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.comic_reader_end_back)) }
    }
}


@Composable
private fun TopChrome(
    state: ComicReaderUiState.Ready,
    mode: ComicReadingMode,
    onBack: () -> Unit,
    onMode: (ComicReadingMode) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA)) {
        Row(
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.arrow_back_48px),
                    contentDescription = stringResource(R.string.action_back),
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.bookTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.chapter.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                FilledTonalIconButton(
                    onClick = { menuOpen = true },
                    shapes = IconButtonDefaults.shapes(),
                    colors = appBarIconButtonColors(),
                    modifier = Modifier.narrowIconButton(),
                ) {
                    Icon(
                        painterResource(R.drawable.more_vert_48px),
                        contentDescription = stringResource(R.string.comic_reader_mode),
                        modifier = Modifier.size(24.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ComicReadingMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            leadingIcon = {
                                if (option == mode) {
                                    Icon(
                                        painterResource(R.drawable.check_48px),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } else {
                                    Box(Modifier.size(24.dp))
                                }
                            },
                            onClick = {
                                menuOpen = false
                                onMode(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomChrome(
    state: ComicReaderUiState.Ready,
    page: Int,
    rtl: Boolean,
    onScrollTo: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val lastIndex = state.pages.size - 1
    var dragging by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragging >= 0f) dragging.roundToInt() else page.coerceIn(0, lastIndex)
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA)) {
        Column(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Paged right to left, forward is leftwards, and the whole row is turned round to say so.
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gold, so the chapter steps stand apart from the violet slider between them.
                    FilledTonalIconButton(
                        onClick = onPrevious,
                        shapes = IconButtonDefaults.shapes(),
                        enabled = state.previous != null,
                        colors = OptionColor.GOLD.iconButtonColors(),
                        modifier = Modifier.narrowIconButton(),
                    ) {
                        Icon(
                            // The arrows mirror with the layout direction, which is turned round above.
                            painterResource(R.drawable.keyboard_arrow_left_48px),
                            contentDescription = stringResource(R.string.comic_reader_previous_chapter),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Slider(
                        value = shown.toFloat(),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            if (dragging >= 0f) onScrollTo(dragging.roundToInt())
                            dragging = -1f
                        },
                        valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(),
                        enabled = lastIndex > 0,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = onNext,
                        shapes = IconButtonDefaults.shapes(),
                        enabled = state.next != null,
                        colors = OptionColor.GOLD.iconButtonColors(),
                        modifier = Modifier.narrowIconButton(),
                    ) {
                        Icon(
                            painterResource(R.drawable.keyboard_arrow_right_48px),
                            contentDescription = stringResource(R.string.comic_reader_next_chapter),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    R.string.comic_reader_position,
                    shown + 1,
                    state.pages.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val ComicReadingMode.labelRes: Int
    get() = when (this) {
        ComicReadingMode.RIGHT_TO_LEFT -> R.string.comic_mode_rtl
        ComicReadingMode.LEFT_TO_RIGHT -> R.string.comic_mode_ltr
        ComicReadingMode.VERTICAL -> R.string.comic_mode_vertical
    }

/** The share of a page's width at each edge that turns the page. */
private const val EDGE_FRACTION = 0.3f

/** How many items past the screen the strip decodes ahead: a few screens of slices. */
private const val PREFETCH_ITEMS = 3

/** How far past the screen the next page is looked for: past a page of 32 slices, some 65,000 rows. */
private const val PAGE_SEARCH_ITEMS = 32
private const val CHROME_ALPHA = 0.94f
private val EndHeight = 320.dp
