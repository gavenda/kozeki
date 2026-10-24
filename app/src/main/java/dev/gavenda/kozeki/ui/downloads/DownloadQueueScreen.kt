package dev.gavenda.kozeki.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.ChapterDownload
import dev.gavenda.kozeki.data.comic.QueuedDownload
import dev.gavenda.kozeki.data.model.ComicChapter
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.narrowIconButton
import dev.gavenda.kozeki.ui.components.wideIconButton
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.appBarIconButtonColors
import dev.gavenda.kozeki.ui.downloadQueueViewModel
import dev.gavenda.kozeki.ui.koinViewModel
import dev.gavenda.kozeki.ui.theme.AppTheme

/** What can be done to the queue from its screen. */
data class DownloadQueueActions(
    val onOpenComic: (bookId: String) -> Unit = {},
    val onCancel: (QueuedDownload) -> Unit = {},
    val onCancelComic: (bookId: String) -> Unit = {},
    val onRetry: (QueuedDownload) -> Unit = {},
    val onMoveToFront: (QueuedDownload) -> Unit = {},
    val onRetryFailed: () -> Unit = {},
    val onCancelAll: () -> Unit = {},
)

/** The chapters of comics waiting to be downloaded or coming in, a screen of its own. */
@Composable
fun DownloadQueueScreen(
    onBack: () -> Unit,
    onOpenComic: (String) -> Unit,
    viewModel: DownloadQueueViewModel = koinViewModel { downloadQueueViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadQueueContent(
        state = state,
        onBack = onBack,
        actions = remember(viewModel, onOpenComic) {
            DownloadQueueActions(
                onOpenComic = onOpenComic,
                onCancel = viewModel::cancel,
                onCancelComic = viewModel::cancelComic,
                onRetry = viewModel::retry,
                onMoveToFront = viewModel::moveToFront,
                onRetryFailed = viewModel::retryFailed,
                onCancelAll = viewModel::cancelAll,
            )
        },
    )
}

@Composable
fun DownloadQueueContent(
    state: DownloadQueueUiState,
    onBack: () -> Unit,
    actions: DownloadQueueActions,
    modifier: Modifier = Modifier,
) {
    // The bar takes the surface container once the page scrolls under it, as the library's does.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text(stringResource(R.string.downloads_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!state.loading && !state.isEmpty) {
                            Text(
                                pluralStringResource(R.plurals.downloads_chapter_count, state.chapterCount, state.chapterCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_48px),
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    if (state.anyFailed) {
                        FilledTonalIconButton(
                            onClick = actions.onRetryFailed,
                            shapes = IconButtonDefaults.shapes(),
                            colors = OptionColor.VIOLET.appBarIconButtonColors(),
                            modifier = Modifier.wideIconButton(),
                        ) {
                            Icon(
                                painterResource(R.drawable.refresh_48px),
                                contentDescription = stringResource(R.string.downloads_retry_failed),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (!state.isEmpty && !state.loading) QueueMenu(actions)
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                HaloLoadingIndicator()
            }

            state.isEmpty -> Box(Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    expression = Expression.CONTENT,
                    icon = painterResource(R.drawable.download_48px),
                    title = stringResource(R.string.downloads_empty_title),
                    message = stringResource(R.string.downloads_empty_message),
                )
            }

            else -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    state.comics.forEach { comic ->
                        item(key = "comic-${comic.book.id}", contentType = "comic") {
                            ComicHeader(
                                comic = comic,
                                onOpen = { actions.onOpenComic(comic.book.id) },
                                onCancel = { actions.onCancelComic(comic.book.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        items(
                            comic.chapters,
                            key = { "chapter-${comic.book.id}-${it.chapter.url}" },
                            contentType = { "chapter" },
                        ) { item ->
                            QueuedChapterRow(item, actions, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

/** The top bar's menu, for the whole queue. */
@Composable
private fun QueueMenu(actions: DownloadQueueActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(
            onClick = { expanded = true },
            shapes = IconButtonDefaults.shapes(),
            colors = appBarIconButtonColors(),
            modifier = Modifier.narrowIconButton(),
        ) {
            Icon(
                painterResource(R.drawable.more_vert_48px),
                contentDescription = stringResource(R.string.downloads_more_actions),
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_cancel_all)) },
                onClick = {
                    expanded = false
                    actions.onCancelAll()
                },
            )
        }
    }
}

/** A comic over its chapters in the queue: a tap opens its page. */
@Composable
private fun ComicHeader(comic: QueuedComic, onOpen: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(comic.book, Modifier.width(40.dp), shape = MaterialTheme.shapes.small, showTitleOnPlaceholder = false)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                comic.book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.downloads_chapter_count, comic.chapters.size, comic.chapters.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                painterResource(R.drawable.close_48px),
                contentDescription = stringResource(R.string.downloads_cancel_comic),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * A chapter in the queue: the halo as on the comic's page, faded while it waits and following the
 * pages as they come, with a menu to fetch it next, try again or take it out.
 */
@Composable
private fun QueuedChapterRow(item: QueuedDownload, actions: DownloadQueueActions, modifier: Modifier = Modifier) {
    val state = item.state
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { (fadeIn(tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut(tween(90))) },
                label = "queued chapter",
            ) { shown ->
                when (shown) {
                    is ChapterDownload.Downloading -> {
                        val label = stringResource(R.string.comic_download_running)
                        HaloLoadingIndicator(Modifier.size(24.dp).semantics { contentDescription = label }, progress = { shown.progress })
                    }

                    is ChapterDownload.Failed -> Icon(
                        painterResource(R.drawable.error_fill_48px),
                        contentDescription = stringResource(R.string.comic_download_failed),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )

                    // Waiting; a downloaded chapter has left the queue.
                    else -> {
                        val label = stringResource(R.string.comic_download_queued)
                        HaloLoadingIndicator(Modifier.size(24.dp).alpha(0.5f).semantics { contentDescription = label })
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.chapter.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (state) {
                    is ChapterDownload.Downloading -> when (val total = state.total) {
                        null -> stringResource(R.string.downloads_listing_pages)
                        else -> stringResource(R.string.downloads_pages_progress, state.done, total)
                    }
                    is ChapterDownload.Failed -> stringResource(R.string.comic_download_failed)
                    else -> stringResource(R.string.comic_download_queued)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is ChapterDownload.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (state is ChapterDownload.Downloading) {
                Spacer(Modifier.height(4.dp))
                LinearWavyProgressIndicator(progress = { state.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
        ChapterMenu(item, actions)
    }
}

@Composable
private fun ChapterMenu(item: QueuedDownload, actions: DownloadQueueActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.more_vert_48px),
                contentDescription = stringResource(R.string.downloads_chapter_actions),
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            when (item.state) {
                is ChapterDownload.Failed -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.comic_download_retry)) },
                    onClick = {
                        expanded = false
                        actions.onRetry(item)
                    },
                )

                ChapterDownload.Queued -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloads_move_to_front)) },
                    onClick = {
                        expanded = false
                        actions.onMoveToFront(item)
                    },
                )

                else -> Unit
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.comic_download_cancel)) },
                onClick = {
                    expanded = false
                    actions.onCancel(item)
                },
            )
        }
    }
}

private fun previewChapter(n: Int) = ComicChapter(
    url = "/chapter/$n",
    name = "Chapter $n",
    number = n.toFloat(),
    scanlator = null,
    uploadedAt = null,
    sourceOrder = n,
    read = false,
    lastPageRead = 0,
    pageCount = null,
)

@ScreenPreviews
@Composable
private fun DownloadQueuePreview() {
    val first = PreviewData.books[0].copy(id = "c1", title = "The Howlring", source = MetadataSource.COMICS)
    val second = PreviewData.books[1].copy(id = "c2", title = "A Comic With a Rather Long Title That Wraps Onto Two Lines", source = MetadataSource.COMICS)
    AppTheme {
        DownloadQueueContent(
            state = DownloadQueueUiState(
                comics = listOf(
                    QueuedComic(
                        first,
                        listOf(
                            QueuedDownload("c1", previewChapter(12), ChapterDownload.Downloading(8, 21)),
                            QueuedDownload("c1", previewChapter(13), ChapterDownload.Queued),
                            QueuedDownload("c1", previewChapter(14), ChapterDownload.Failed(RuntimeException())),
                        ),
                    ),
                    QueuedComic(second, listOf(QueuedDownload("c2", previewChapter(1), ChapterDownload.Queued))),
                ),
                loading = false,
            ),
            onBack = {},
            actions = DownloadQueueActions(),
        )
    }
}

@ScreenPreviews
@Composable
private fun DownloadQueueEmptyPreview() {
    AppTheme {
        DownloadQueueContent(state = DownloadQueueUiState(loading = false), onBack = {}, actions = DownloadQueueActions())
    }
}
