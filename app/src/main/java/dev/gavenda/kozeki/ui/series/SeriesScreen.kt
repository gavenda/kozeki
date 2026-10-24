package dev.gavenda.kozeki.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.formatSeriesNumber
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.AddSelectedToolbar
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.BulkAddedSnackbarEffect
import dev.gavenda.kozeki.ui.components.ResultSelection
import dev.gavenda.kozeki.ui.components.SelectionToolbarRoom
import dev.gavenda.kozeki.ui.components.onClick
import dev.gavenda.kozeki.ui.components.onLongClick
import dev.gavenda.kozeki.ui.components.rememberResultSelection
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.MetadataResultItem
import dev.gavenda.kozeki.ui.components.OwnedBadge
import dev.gavenda.kozeki.ui.components.Portrait
import dev.gavenda.kozeki.ui.components.SpeechBubble
import dev.gavenda.kozeki.ui.koinViewModel
import dev.gavenda.kozeki.ui.seriesViewModel
import dev.gavenda.kozeki.ui.theme.AppTheme

/** A series: the user's books in it, then the rest of it on the metadata source. */
@Composable
fun SeriesScreen(
    name: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenResult: (BookMetadata) -> Unit,
    viewModel: SeriesViewModel = koinViewModel(key = "series-$name") { seriesViewModel(name) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    BulkAddedSnackbarEffect(viewModel.added, snackbarHostState)
    SeriesContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenBook = onOpenBook,
        onOpenResult = onOpenResult,
        snackbarHostState = snackbarHostState,
        onAddAll = viewModel::addAll,
    )
}

@Composable
fun SeriesContent(
    state: SeriesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenResult: (BookMetadata) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAddAll: (List<BookMetadata>, Acquisition) -> Unit = { _, _ -> },
) {
    // Only the books of the series the user does not have can be picked, to be added together.
    val selection = rememberResultSelection()
    val picked = selection.of(state.others)
    // The bar takes the surface container once the page scrolls under it, as the library's does.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_48px),
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp),
                // Room for the last row above the toolbar.
                contentPadding = PaddingValues(bottom = if (selection.active) SelectionToolbarRoom else 0.dp),
            ) {
                if (state.owned.isNotEmpty()) {
                    sectionHeader("owned-header", R.string.series_your_books)
                    items(state.owned, key = { "owned-${it.book.id}" }, contentType = { "owned" }) { entry ->
                        OwnedSeriesRow(entry, onClick = { onOpenBook(entry.book.id) })
                    }
                }
                remoteSection(state, selection, onRetry, onOpenResult)
            }
            if (picked.isNotEmpty()) {
                AddSelectedToolbar(
                    count = picked.size,
                    allSelected = picked.size == state.others.size,
                    comics = false,
                    onClear = selection::clear,
                    onSelectAll = { selection.selectAll(state.others) },
                    onAdd = { acquisition ->
                        onAddAll(picked, acquisition)
                        selection.clear()
                    },
                )
            }
        }
    }
}

/** What the source has of the series beyond the user's books, or why it has nothing to show. */
private fun LazyListScope.remoteSection(
    state: SeriesUiState,
    selection: ResultSelection,
    onRetry: () -> Unit,
    onOpenResult: (BookMetadata) -> Unit,
) {
    when {
        state.loading -> item(key = "loading", contentType = "loading") {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { HaloLoadingIndicator() }
        }

        state.error != null -> item(key = "error", contentType = "empty") {
            EmptyState(
                expression = Expression.DISMAYED,
                icon = painterResource(R.drawable.error_fill_48px),
                title = stringResource(R.string.series_error_title),
                message = stringResource(state.error.message),
                action = { Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } },
            )
        }

        state.notFound -> item(key = "not-found", contentType = "empty") {
            EmptyState(
                expression = Expression.DUMBFOUNDED,
                icon = painterResource(R.drawable.search_off_48px),
                title = stringResource(R.string.series_not_found_title),
                message = stringResource(R.string.series_not_found),
            )
        }

        state.others.isEmpty() -> item(key = "end", contentType = "end") {
            // As the library ends: centred, and the bubble kept to the width of an empty state's text.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Portrait(Expression.HAPPY, Modifier.width(96.dp))
                SpeechBubble(
                    stringResource(R.string.series_end_bubble),
                    Modifier.weight(1f, fill = false).widthIn(max = 360.dp),
                )
            }
        }

        else -> {
            sectionHeader("others-header", if (state.owned.isEmpty()) R.string.series_books else R.string.series_more)
            items(state.others, key = { "other-${it.sourceId}" }, contentType = { "other" }) { result ->
                MetadataResultItem(
                    result = result,
                    onClick = selection.onClick(result, owned = false) { onOpenResult(result) },
                    trailingContent = result.seriesNumber?.let { number -> { SeriesNumber(number) } },
                    selected = result.sourceId in selection.ids,
                    onLongClick = selection.onLongClick(result, owned = false),
                )
            }
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, text: Int) {
    item(key = key, contentType = "header") {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun OwnedSeriesRow(entry: OwnedInSeries, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val book = entry.book
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, Modifier.width(48.dp), shape = MaterialTheme.shapes.small, showTitleOnPlaceholder = false)
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(book.title, book.subtitle).joinToString(": "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotEmpty()) {
                Text(
                    text = book.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        entry.number?.let { SeriesNumber(it) }
        OwnedBadge(book)
    }
}

@Composable
private fun SeriesNumber(number: Double) {
    Text(
        text = stringResource(R.string.series_number, formatSeriesNumber(number)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val PreviewOthers = listOf(
    BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = "2",
        title = "Dune Messiah",
        authors = listOf("Frank Herbert"),
        publishedDate = "1969",
        series = "Dune",
        seriesNumber = 2.0,
    ),
    BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = "3",
        title = "Children of Dune",
        authors = listOf("Frank Herbert"),
        publishedDate = "1976",
        series = "Dune",
        seriesNumber = 3.0,
    ),
)

@ScreenPreviews
@Composable
private fun SeriesPreview() {
    AppTheme {
        SeriesContent(
            state = SeriesUiState(
                name = "Dune",
                owned = listOf(
                    OwnedInSeries(
                        Book(id = "1", title = "Dune", authors = listOf("Frank Herbert"), acquisition = Acquisition.PURCHASED),
                        1.0,
                    ),
                ),
                others = PreviewOthers,
                loading = false,
            ),
            onBack = {},
            onRetry = {},
            onOpenBook = {},
            onOpenResult = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun SeriesErrorPreview() {
    AppTheme {
        SeriesContent(
            state = SeriesUiState(name = "Dune", loading = false, error = LookupError.OFFLINE),
            onBack = {},
            onRetry = {},
            onOpenBook = {},
            onOpenResult = {},
        )
    }
}
