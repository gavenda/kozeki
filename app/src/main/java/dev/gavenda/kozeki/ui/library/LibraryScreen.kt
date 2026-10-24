package dev.gavenda.kozeki.ui.library

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ImportResult
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.Shelf
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.book.DeleteBooksDialog
import dev.gavenda.kozeki.ui.book.PurchaseDialog
import dev.gavenda.kozeki.ui.components.AdaptiveColumns
import dev.gavenda.kozeki.ui.components.AuthorLinks
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.BookDisplay
import dev.gavenda.kozeki.ui.components.DisplayToggle
import dev.gavenda.kozeki.ui.components.DraggableScrollbar
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.narrowIconButton
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.Portrait
import dev.gavenda.kozeki.ui.components.ReadingProgressRing
import dev.gavenda.kozeki.ui.components.ReadingStateOrder
import dev.gavenda.kozeki.ui.components.SpeechBubble
import dev.gavenda.kozeki.ui.components.icon
import dev.gavenda.kozeki.ui.components.iconButtonColors
import dev.gavenda.kozeki.ui.components.labelRes
import dev.gavenda.kozeki.ui.components.outlinedIcon
import dev.gavenda.kozeki.ui.components.ownershipBadge
import dev.gavenda.kozeki.ui.formatPercent
import dev.gavenda.kozeki.ui.koinViewModel
import dev.gavenda.kozeki.ui.libraryViewModel
import dev.gavenda.kozeki.ui.search.SearchTopBar
import dev.gavenda.kozeki.ui.search.SearchUiState
import dev.gavenda.kozeki.ui.search.SearchViewModel
import dev.gavenda.kozeki.ui.searchViewModel
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

/** MIME types offered by the file picker. Some providers report EPUBs as generic binary data. */
internal val EpubMimeTypes = arrayOf("application/epub+zip", "application/octet-stream")

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    onAddBook: () -> Unit,
    onAddManually: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel { libraryViewModel() },
    searchViewModel: SearchViewModel = koinViewModel { searchViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val search by searchViewModel.uiState.collectAsStateWithLifecycle()
    val queuedDownloads by viewModel.queuedDownloads.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.ImportFinished -> snackbarHostState.showSnackbar(importMessage(resources, event.results))
            }
        }
    }
    // EPUBs opened with the app or shared to it come in here, where the import and its outcome show.
    LaunchedEffect(viewModel) { viewModel.importIncoming() }

    LibraryContent(
        state = state,
        search = search,
        snackbarHostState = snackbarHostState,
        onSearchQueryChange = searchViewModel::onQueryChange,
        onFilterSelected = viewModel::selectFilter,
        onSortSelected = viewModel::selectSort,
        onDisplaySelected = viewModel::selectDisplay,
        onKindSelected = viewModel::selectKind,
        shelfActions = remember(viewModel) {
            ShelfActions(
                onCreate = viewModel::createShelf,
                onRename = viewModel::renameShelf,
                onDelete = viewModel::deleteShelf,
                onPickBooksFor = viewModel::pickBooksFor,
                onAddBooks = viewModel::addToShelf,
            )
        },
        onImportClick = { picker.launch(EpubMimeTypes) },
        onAddBook = onAddBook,
        onAddManually = onAddManually,
        onBookClick = { onOpenBook(it.id) },
        onAuthorClick = onOpenAuthor,
        selectionActions = remember(viewModel) {
            SelectionActions(
                onToggle = viewModel::toggleSelection,
                onSelectAll = viewModel::selectAll,
                onClear = viewModel::clearSelection,
                onSetFavorite = viewModel::setFavorite,
                onSetState = viewModel::setState,
                onMarkPurchased = viewModel::markPurchased,
                onUnmarkPurchased = viewModel::unmarkPurchased,
                onDelete = viewModel::delete,
                onShelvesOf = viewModel::shelvesOfSelection,
                onChangeShelves = viewModel::changeShelves,
            )
        },
        onSettingsClick = onOpenSettings,
        queuedDownloads = queuedDownloads,
        onDownloadsClick = onOpenDownloads,
        onRefresh = viewModel::checkForUpdates,
        onRefreshMetadata = viewModel::refreshMetadata,
        onCancelRefresh = viewModel::cancelUpdateCheck,
        onUpToDateShown = viewModel::upToDateShown,
        onToggleGroup = viewModel::toggleGroup,
    )
}

/** One line summarising what an import did, for the snackbar. */
internal fun importMessage(resources: Resources, results: List<ImportResult>): String {
    val single = results.singleOrNull()
    if (single != null) {
        return when (single) {
            is ImportResult.Imported -> resources.getString(R.string.import_done, single.title)
            is ImportResult.Attached -> resources.getString(R.string.import_attached, single.title)
            is ImportResult.AlreadyInLibrary -> resources.getString(R.string.import_duplicate, single.title)
            is ImportResult.Failed -> when (single.reason) {
                ImportResult.Failed.Reason.PROTECTED -> resources.getString(R.string.import_failed_protected)
                ImportResult.Failed.Reason.NOT_AN_EPUB -> resources.getString(R.string.import_failed_not_epub)
                ImportResult.Failed.Reason.UNREADABLE -> resources.getString(R.string.import_failed_unreadable)
            }
        }
    }
    val added = results.count { it is ImportResult.Imported || it is ImportResult.Attached }
    val failed = results.count { it is ImportResult.Failed }
    val summary = resources.getQuantityString(R.plurals.import_summary, added, added, results.size)
    return if (failed == 0) summary else summary + " " + resources.getQuantityString(R.plurals.import_summary_failed, failed, failed)
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    search: SearchUiState,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onDisplaySelected: (BookDisplay) -> Unit,
    onImportClick: () -> Unit,
    onAddBook: () -> Unit,
    onAddManually: () -> Unit,
    onBookClick: (Book) -> Unit,
    onAuthorClick: (AuthorRef) -> Unit,
    selectionActions: SelectionActions,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onKindSelected: (LibraryKind) -> Unit = {},
    shelfActions: ShelfActions = ShelfActions(),
    onRefresh: () -> Unit = {},
    onRefreshMetadata: () -> Unit = {},
    onCancelRefresh: () -> Unit = {},
    onUpToDateShown: () -> Unit = {},
    onToggleGroup: (String) -> Unit = {},
    queuedDownloads: Int = 0,
    onDownloadsClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()
    val selection = state.selection
    var editingPurchase by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var updateSheetHidden by rememberSaveable { mutableStateOf(false) }
    var confirmingMetadataRefresh by rememberSaveable { mutableStateOf(false) }
    var shelfPrompt by remember { mutableStateOf<ShelfPrompt?>(null) }
    // The shelves of the picked books, once asked for; the sheet waits on them.
    var checklistCounts by remember { mutableStateOf<Map<String, Int>?>(null) }
    val shelfScope = rememberCoroutineScope()

    // A filter that was just picked starts from its top. Left alone, the grid holds on to the book
    // it was showing, which on the way back to every book is somewhere in the middle. Remembered
    // past leaving the screen, so that coming back from a book keeps the place.
    var shownFilter by rememberSaveable { mutableStateOf(state.filter) }
    LaunchedEffect(state.filter) {
        if (state.filter != shownFilter) {
            shownFilter = state.filter
            gridState.scrollToItem(0)
        }
    }

    // The same for the other kind, whose books are not the ones the grid was showing: left where it
    // was, it would open part way down a list of a different length, with the title still folded away.
    var shownKind by rememberSaveable { mutableStateOf(state.kind) }
    LaunchedEffect(state.kind) {
        if (state.kind != shownKind) {
            shownKind = state.kind
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
    }

    // More than a screen down and on the way back up is when the top is wanted, so that is when the
    // button that adds books offers it instead. Going down, or near the top, it adds books as ever.
    val turnDistance = with(LocalDensity.current) { ScrollTurnDistance.toPx() }
    val scrollTurn = remember(turnDistance) { ScrollTurn(turnDistance) }
    val offersTop by remember(scrollTurn) {
        derivedStateOf {
            val screenful = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            scrollTurn.towardTop && gridState.firstVisibleItemIndex >= screenful
        }
    }
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = {
        scope.launch { gridState.animateScrollToItem(0) }
        // The title comes back down with it, as it would had the way up been dragged.
        scope.launch {
            val bar = scrollBehavior.state
            animate(bar.heightOffset, 0f) { value, _ -> bar.heightOffset = value }
            bar.contentOffset = 0f
        }
    }

    BackHandler(state.selecting) { selectionActions.onClear() }
    // A dialog belongs to the selection it was opened for, not to whatever is picked next.
    LaunchedEffect(state.selecting) {
        if (!state.selecting) {
            editingPurchase = false
            confirmingDelete = false
            checklistCounts = null
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SearchTopBar(
                title = stringResource(R.string.nav_library),
                subtitle = if (state.loading) {
                    null
                } else {
                    pluralStringResource(R.plurals.book_count, state.totalBooks, state.totalBooks)
                },
                search = search,
                onSearchQueryChange = onSearchQueryChange,
                onBookClick = onBookClick,
                onSettingsClick = onSettingsClick,
                queuedDownloads = queuedDownloads,
                onDownloadsClick = onDownloadsClick,
                scrollBehavior = scrollBehavior,
                searchHint = if (state.kind == LibraryKind.COMICS) {
                    R.string.search_comics_hint
                } else {
                    R.string.search_books_hint
                },
            )
        },
        floatingActionButton = {
            // The empty state has its own button; two of them side by side would be noise.
            // While books are picked, the selection toolbar takes this corner.
            if (!state.selecting && (!state.isLibraryEmpty || state.importing)) {
                AddBooksMenu(
                    importing = state.importing,
                    offersTop = offersTop,
                    onScrollToTop = scrollToTop,
                    onImportClick = onImportClick,
                    onAddBook = onAddBook,
                    onAddManually = onAddManually,
                    onNewShelf = if (state.inShelves) ({ shelfPrompt = ShelfPrompt.Create }) else null,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.selecting) {
            Box(Modifier.fillMaxSize().padding(innerPadding).zIndex(1f), contentAlignment = Alignment.BottomCenter) {
                SelectionToolbar(
                    selection = selection,
                    allSelected = state.allSelected,
                    actions = selectionActions,
                    onEditPurchase = { editingPurchase = true },
                    onDelete = { confirmingDelete = true },
                    onOpenShelves = {
                        shelfScope.launch { checklistCounts = selectionActions.onShelvesOf() }
                    },
                    modifier = Modifier.padding(bottom = FloatingToolbarDefaults.ScreenOffset),
                )
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                HaloLoadingIndicator()
            }

            // Scrolls, because on a short window it does not all fit, and a column that cannot
            // scroll squashes its last buttons into whatever height is left.
            state.isLibraryEmpty -> Box(Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    expression = Expression.HAPPY,
                    icon = painterResource(R.drawable.shelves_fill_48px),
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.library_empty_message),
                    action = {
                        // The same three ways in, with the same icons, as the menu that takes over once there are books.
                        Button(onClick = onImportClick) {
                            AddButtonContent(painterResource(R.drawable.upload_file_fill_48px), R.string.library_add_import)
                        }
                        OutlinedButton(onClick = onAddBook) {
                            AddButtonContent(painterResource(R.drawable.travel_explore_48px), R.string.library_add_find)
                        }
                        OutlinedButton(onClick = onAddManually) {
                            AddButtonContent(painterResource(R.drawable.edit_note_fill_48px), R.string.library_add_manual)
                        }
                    },
                )
            }

            else -> Column(Modifier.padding(top = innerPadding.calculateTopPadding())) {
                FilterRow(state, onFilterSelected, onSortSelected, onDisplaySelected, onKindSelected)
                if (state.isShelvesEmpty) {
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        EmptyState(
                            expression = Expression.NEUTRAL,
                            icon = painterResource(R.drawable.shelves_fill_48px),
                            title = stringResource(R.string.shelves_empty_title),
                            message = stringResource(R.string.shelves_empty_message),
                            action = {
                                Button(onClick = { shelfPrompt = ShelfPrompt.Create }) {
                                    AddButtonContent(painterResource(R.drawable.add_48px), R.string.shelf_new)
                                }
                            },
                        )
                    }
                } else if (state.isFilterEmpty) {
                    EmptyState(
                        expression = Expression.NEUTRAL,
                        icon = painterResource(R.drawable.filter_alt_off_fill_48px),
                        title = stringResource(R.string.library_filter_empty_title),
                        message = stringResource(R.string.library_filter_empty_message),
                    )
                } else {
                    // A list is the same grid with rows for cells, so the place scrolled to and the
                    // headings carry over. A wide window sets the rows side by side.
                    val list = state.display == BookDisplay.LIST
                    // A row brings its own margins, which the tint of a picked one fills.
                    val edge = if (list) 8.dp else 16.dp
                    val rowGap = if (list) 0.dp else 16.dp
                    val pullState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        // The sheet shows the check, so the indicator goes back up once let go.
                        // Pulled again while the sheet is hidden, it brings the sheet back.
                        isRefreshing = false,
                        // Among the books it refreshes their details, which replaces them, so it asks
                        // first; one already running is only brought back. Elsewhere it checks the comics.
                        onRefresh = {
                            updateSheetHidden = false
                            when {
                                state.refreshing -> Unit
                                state.kind == LibraryKind.BOOKS -> confirmingMetadataRefresh = true
                                else -> onRefresh()
                            }
                        },
                        enabled = !state.selecting,
                        state = pullState,
                        indicator = {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        val shown = pullState.distanceFraction.coerceIn(0f, 1f)
                                        alpha = shown
                                        translationY = (shown - 1f) * size.height
                                    },
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 4.dp,
                                ) {
                                    HaloLoadingIndicator(Modifier.padding(8.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalGrid(
                            columns = if (list) {
                                AdaptiveColumns(minColumns = 1, minCellWidth = 360.dp)
                            } else {
                                AdaptiveColumns(minColumns = 3, minCellWidth = 112.dp)
                            },
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = edge,
                                end = edge,
                                top = 8.dp,
                                // Room for the floating action button to clear the last row.
                                bottom = innerPadding.calculateBottomPadding() + 96.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(if (list) 8.dp else 12.dp),
                            // The gap between rows of covers is under each cover rather than between every item,
                            // so folded headers sit one on the next as they do in the list.
                            modifier = Modifier.fillMaxSize().nestedScroll(scrollTurn),
                        ) {
                            // A book on several shelves is in the grid once for each, so its key says which.
                            fun LazyGridScope.sectionItems(prefix: String, sections: List<LibrarySection>) {
                                sections.forEach { section ->
                                    section.heading?.let { heading ->
                                        item(key = "${prefix}header-${heading.name}", span = { GridItemSpan(maxLineSpan) }) {
                                            SectionHeader(
                                                text = stringResource(heading.label),
                                                // In line with the text of the rows under it.
                                                modifier = if (list) Modifier.padding(horizontal = 8.dp) else Modifier.padding(bottom = rowGap),
                                            )
                                        }
                                    }
                                    items(section.books, key = { "$prefix${it.id}" }, contentType = { state.display }) { book ->
                                        if (list) {
                                            LibraryBookRow(
                                                book = book,
                                                selecting = state.selecting,
                                                selected = book.id in selection.ids,
                                                actions = selectionActions,
                                                onOpen = onBookClick,
                                                onOpenAuthor = onAuthorClick,
                                                modifier = Modifier.animateItem(),
                                            )
                                        } else {
                                            LibraryBookItem(
                                                book = book,
                                                selecting = state.selecting,
                                                selected = book.id in selection.ids,
                                                actions = selectionActions,
                                                onOpen = onBookClick,
                                                onOpenAuthor = onAuthorClick,
                                                modifier = Modifier.animateItem().padding(bottom = rowGap),
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.inShelves) {
                                state.shelfGroups.forEach { group ->
                                    val groupKey = state.groupKey(group)
                                    val collapsed = state.isCollapsed(groupKey)
                                    stickyHeader(key = "shelf-${group.shelf.id}") {
                                        ShelfHeader(
                                            shelf = group.shelf,
                                            collapsed = collapsed,
                                            onToggle = { onToggleGroup(groupKey) },
                                            // Extends over the grid's own side padding, so the books slide under all of it.
                                            modifier = Modifier.fillMaxWidth(),
                                            // In line with the rows' text in a list, with the covers' edge in a grid.
                                            indent = if (list) 8.dp else 0.dp,
                                            onAddBooks = { shelfActions.onPickBooksFor(group.shelf.id) },
                                            onRename = { shelfPrompt = ShelfPrompt.Rename(group.shelf) },
                                            onDelete = { shelfPrompt = ShelfPrompt.Delete(group.shelf) },
                                        )
                                    }
                                    if (collapsed) return@forEach
                                    if (group.sections.isEmpty()) {
                                        item(key = "shelf-${group.shelf.id}-empty", span = { GridItemSpan(maxLineSpan) }) {
                                            EmptyShelfLine(
                                                indent = if (list) 8.dp else 0.dp,
                                                onAddBooks = { shelfActions.onPickBooksFor(group.shelf.id) },
                                            )
                                        }
                                    }
                                    sectionItems("${group.shelf.id}/", group.sections)
                                }
                            } else if (state.bySeries) {
                                state.seriesGroups.forEach { group ->
                                    val key = group.name?.lowercase() ?: ""
                                    val groupKey = state.groupKey(group)
                                    val collapsed = state.isCollapsed(groupKey)
                                    stickyHeader(key = "series-$key") {
                                        GroupHeader(
                                            title = group.name ?: stringResource(R.string.library_no_series),
                                            count = group.books.size,
                                            collapsed = collapsed,
                                            onToggle = { onToggleGroup(groupKey) },
                                            modifier = Modifier.fillMaxWidth(),
                                            indent = if (list) 8.dp else 0.dp,
                                        )
                                    }
                                    if (!collapsed) sectionItems("series-$key/", group.sections)
                                }
                            } else {
                                sectionItems("", state.sections)
                            }
                            item(key = "end", span = { GridItemSpan(maxLineSpan) }) {
                                // Centred, and the bubble kept to the width of an empty state's text on a wide window.
                                // Inset in a list as the headers are, so it stays put when the layout changes.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                        .padding(horizontal = if (list) 8.dp else 0.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Portrait(Expression.CONTENT, Modifier.width(96.dp))
                                    SpeechBubble(
                                        stringResource(
                                            if (state.kind == LibraryKind.COMICS) {
                                                R.string.library_end_bubble_comics
                                            } else {
                                                R.string.library_end_bubble
                                            },
                                        ),
                                        Modifier.weight(1f, fill = false).widthIn(max = 360.dp),
                                    )
                                }
                            }
                        }
                        // Shows how far down a long library is while it moves, and takes it there
                        // when dragged. Stops short of the corner the floating action button has.
                        DraggableScrollbar(
                            state = gridState,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .fillMaxHeight()
                                .padding(top = 8.dp, bottom = innerPadding.calculateBottomPadding() + 96.dp),
                        )
                    }
                }
            }
        }
    }

    val single = selection.books.singleOrNull()
    if (editingPurchase && single != null) {
        PurchaseDialog(
            book = single,
            lastCurrency = state.lastCurrency,
            locationHistory = state.purchaseLocations,
            onSave = { price, currency, date, location, memo ->
                editingPurchase = false
                selectionActions.onMarkPurchased(price, currency, date, location, memo)
            },
            onDismiss = { editingPurchase = false },
        )
    }
    if (confirmingMetadataRefresh) {
        RefreshMetadataDialog(
            onConfirm = {
                confirmingMetadataRefresh = false
                onRefreshMetadata()
            },
            onDismiss = { confirmingMetadataRefresh = false },
        )
    }
    if (confirmingDelete && state.selecting) {
        val onConfirm = {
            confirmingDelete = false
            selectionActions.onDelete()
        }
        DeleteBooksDialog(selection.books, onConfirm = onConfirm, onDismiss = { confirmingDelete = false })
    }
    ShelfPrompts(
        prompt = shelfPrompt,
        onCreate = shelfActions.onCreate,
        onRename = shelfActions.onRename,
        onDelete = shelfActions.onDelete,
        onDismiss = { shelfPrompt = null },
    )
    checklistCounts?.let { counts ->
        ShelfChecklistSheet(
            shelves = state.shelves,
            memberCounts = counts,
            picked = selection.size,
            onApply = selectionActions.onChangeShelves,
            onDismiss = { checklistCounts = null },
        )
    }
    state.addingTo?.let { shelf ->
        ShelfAddBooksSheet(
            candidates = state.shelfCandidates,
            onAdd = { shelfActions.onAddBooks(shelf.id, it) },
            onDismiss = { shelfActions.onPickBooksFor(null) },
        )
    }
    // Hidden by hand, the sheet stays away until the next pull.
    UpdateCheckSheet(
        progress = state.updateCheck,
        kind = state.updateCheckKind,
        finished = state.updateCheckFinished,
        hidden = updateSheetHidden,
        upToDate = state.updateCheckUpToDate,
        onUpToDateShown = onUpToDateShown,
        onCancel = onCancelRefresh,
        onDismiss = { updateSheetHidden = true },
    )
}

/** Asks before every linked book's details are fetched again, as they replace what was changed by hand. */
@Composable
private fun RefreshMetadataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.refresh_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
        title = { Text(stringResource(R.string.metadata_refresh_confirm_title)) },
        text = { Text(stringResource(R.string.metadata_refresh_confirm_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.metadata_refresh_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Picking books in the grid, and what is then done to all of them at once. */
class SelectionActions(
    val onToggle: (Book) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onSetFavorite: (Boolean) -> Unit = {},
    val onSetState: (ReadingState) -> Unit = {},
    val onMarkPurchased: (
        priceMinor: Long?,
        currency: String?,
        purchasedOn: LocalDate,
        location: String?,
        memo: String?,
    ) -> Unit = { _, _, _, _, _ -> },
    val onUnmarkPurchased: () -> Unit = {},
    val onDelete: () -> Unit = {},
    /** For each shelf, how many of the picked books are on it. */
    val onShelvesOf: suspend () -> Map<String, Int> = { emptyMap() },
    val onChangeShelves: (ShelfChange) -> Unit = {},
)

/** Making and changing shelves, and putting books on one. */
class ShelfActions(
    val onCreate: (String) -> Unit = {},
    val onRename: (String, String) -> Unit = { _, _ -> },
    val onDelete: (String) -> Unit = {},
    /** Starts picking books for the shelf with this ID, or stops with null. */
    val onPickBooksFor: (String?) -> Unit = {},
    val onAddBooks: (shelfId: String, bookIds: List<String>) -> Unit = { _, _ -> },
)

@Composable
private fun AddButtonContent(icon: Painter, @StringRes label: Int) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(stringResource(label))
}

/** How far the grid has to be moved one way before [ScrollTurn] takes it for the way it is going. */
private val ScrollTurnDistance = 24.dp

/**
 * Tells which way a list was last moved by more than a nudge, [threshold] pixels of it. A finger
 * wavers as it lifts, and a button that changed with every pixel would change under it.
 */
internal class ScrollTurn(private val threshold: Float) : NestedScrollConnection {
    /** The list was last moved back toward its top. */
    var towardTop by mutableStateOf(false)
        private set

    /** How far it has gone since it last changed direction; above zero is toward the top. */
    private var run = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (delta != 0f) {
            run = if (delta > 0f == run > 0f) run + delta else delta
            if (run > threshold) towardTop = true else if (run < -threshold) towardTop = false
        }
        // Only watched: the list keeps all of it.
        return Offset.Zero
    }
}

/**
 * The ways a book gets in: as an EPUB from the device, looked up online without a file, or typed
 * in by hand. While [offersTop] holds, the button leads back to the top of the library instead,
 * unless its menu is open or an import is under way.
 */
@Composable
private fun AddBooksMenu(
    importing: Boolean,
    offersTop: Boolean,
    onScrollToTop: () -> Unit,
    onImportClick: () -> Unit,
    onAddBook: () -> Unit,
    onAddManually: () -> Unit,
    onNewShelf: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    val toTop = offersTop && !expanded && !importing
    val label = stringResource(
        when {
            importing -> R.string.importing
            toTop -> R.string.library_scroll_to_top
            else -> R.string.library_add
        },
    )

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { if (toTop) onScrollToTop() else if (!importing) expanded = it },
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                if (importing) {
                    HaloLoadingIndicator(Modifier.size(24.dp))
                } else {
                    AnimatedContent(
                        targetState = toTop,
                        transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                        label = "add or to top",
                    ) { top ->
                        if (top) {
                            Icon(painterResource(R.drawable.keyboard_arrow_up_48px), contentDescription = null, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                painter = if (checkedProgress > 0.5f) painterResource(R.drawable.close_48px) else painterResource(R.drawable.add_48px),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).animateIcon({ checkedProgress }),
                            )
                        }
                    }
                }
            }
        },
        // The menu brings its own margins, and the scaffold has already applied the same ones.
        modifier = Modifier.offset(x = 16.dp, y = 16.dp),
    ) {
        if (onNewShelf != null) {
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onNewShelf()
                },
                text = { Text(stringResource(R.string.shelf_new)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(painterResource(R.drawable.shelves_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            )
        }
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onImportClick()
            },
            text = { Text(stringResource(R.string.library_add_import)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = { Icon(painterResource(R.drawable.upload_file_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddBook()
            },
            text = { Text(stringResource(R.string.library_add_find)) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = { Icon(painterResource(R.drawable.travel_explore_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddManually()
            },
            text = { Text(stringResource(R.string.library_add_manual)) },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = { Icon(painterResource(R.drawable.edit_note_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
        )
    }
}

@Composable
private fun FilterRow(
    state: LibraryUiState,
    onFilterSelected: (LibraryFilter) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onDisplaySelected: (BookDisplay) -> Unit,
    onKindSelected: (LibraryKind) -> Unit,
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The chips may be tucked away, so the button that opens them says which is in effect.
            TextButton(
                onClick = { filtersOpen = !filtersOpen },
                modifier = Modifier.weight(1f, fill = false),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (state.filter == LibraryFilter.ALL) {
                        LocalContentColor.current
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                Icon(painterResource(R.drawable.filter_alt_fill_48px), contentDescription = stringResource(R.string.filter_books), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(state.filter.label),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                KindMenu(state.kind, onKindSelected)
                DisplayToggle(state.display, onDisplaySelected)
                SortMenu(state.sort, state.sortReversed, onSortSelected)
            }
        }
        // The chips slide out from under the row and push the books down, rather than cover them.
        AnimatedVisibility(
            visible = filtersOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.filter { it.shownFor(state.kind) }.forEach { filter ->
                    val selected = filter == state.filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(stringResource(filter.label)) },
                        leadingIcon = if (selected) {
                            { Icon(painterResource(R.drawable.check_48px), contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        trailingIcon = {
                            Text(
                                text = (state.counts[filter] ?: 0).toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Shows what the library is showing, books, comics or shelves, in the primary colour. A tap opens a
 * menu of all three, the one in use marked.
 */
@Composable
private fun KindMenu(kind: LibraryKind, onKindSelected: (LibraryKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                painter = painterResource(kind.icon),
                contentDescription = stringResource(R.string.library_kind_choose, stringResource(kind.label)),
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibraryKind.entries.forEach { option ->
                val selected = option == kind
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        expanded = false
                        onKindSelected(option)
                    },
                    leadingIcon = {
                        Icon(painterResource(option.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                    },
                    trailingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.check_48px), contentDescription = null, modifier = Modifier.size(24.dp)) }
                    } else {
                        null
                    },
                    colors = if (selected) {
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.primary,
                            leadingIconColor = MaterialTheme.colorScheme.primary,
                            trailingIconColor = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        MenuDefaults.itemColors()
                    },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(
    sort: LibrarySort,
    reversed: Boolean,
    onSortSelected: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.sort_48px), contentDescription = stringResource(R.string.sort_books), modifier = Modifier.size(24.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        expanded = false
                        onSortSelected(option)
                    },
                    leadingIcon = {
                        // The order in use shows which way it runs; picking it again turns it round.
                        // An empty slot keeps the labels of the other orders in line.
                        if (option == sort) {
                            val descending = sort.isDescending(reversed)
                            Icon(
                                painterResource(if (descending) R.drawable.arrow_downward_48px else R.drawable.arrow_upward_48px),
                                contentDescription = stringResource(if (descending) R.string.sort_descending else R.string.sort_ascending),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** A shelf's name over its books, with its menu. It is filled in, as the books scroll under it. */
@Composable
private fun ShelfHeader(
    shelf: Shelf,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAddBooks: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    indent: Dp = 8.dp,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GroupHeader(
        title = shelf.name,
        count = shelf.bookCount,
        collapsed = collapsed,
        onToggle = onToggle,
        modifier = modifier,
        indent = indent,
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painterResource(R.drawable.more_vert_48px),
                    contentDescription = stringResource(R.string.shelf_more_actions),
                    modifier = Modifier.size(24.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shelf_add_books)) },
                    leadingIcon = { Icon(painterResource(R.drawable.add_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    onClick = {
                        menuOpen = false
                        onAddBooks()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shelf_rename)) },
                    leadingIcon = { Icon(painterResource(R.drawable.edit_note_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shelf_delete)) },
                    leadingIcon = { Icon(painterResource(R.drawable.delete_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * The name of a run of books, a shelf or a series, pinned over them with how many there are, and
 * whatever [trailing] puts at its end. It is filled in, as the books scroll under it. A tap folds
 * the books away under it, or opens them again, and the arrow at its end turns to say which.
 */
@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    indent: Dp = 8.dp,
    trailing: @Composable () -> Unit = {},
) {
    val turn by animateFloatAsState(if (collapsed) 180f else 0f, label = "group arrow")
    val toggleLabel = stringResource(if (collapsed) R.string.library_group_expand else R.string.library_group_collapse)
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            // As tall as a header with a menu button, whether this one has one or not.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = toggleLabel, onClick = onToggle)
                // The same on both sides, so the arrow ends where the rule under it does.
                .padding(horizontal = indent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.shelf_book_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Up while the books show, down once they are folded away.
            // Leather, with the title. The button folds the books as the row does.
            FilledTonalIconButton(
                onClick = onToggle,
                shapes = IconButtonDefaults.shapes(),
                colors = OptionColor.LEATHER.iconButtonColors(),
                modifier = Modifier.padding(start = 8.dp).narrowIconButton(),
            ) {
                Icon(
                    painterResource(R.drawable.keyboard_arrow_up_48px),
                    contentDescription = toggleLabel,
                    modifier = Modifier.size(24.dp).rotate(turn),
                )
            }
            trailing()
        }
        // A faint grey rule, as wide as the books under it.
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = indent),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )
    }
}

/** What a shelf with nothing on it shows in place of its books. */
@Composable
private fun EmptyShelfLine(indent: Dp, onAddBooks: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.shelf_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = indent),
        )
        TextButton(onClick = onAddBooks) { Text(stringResource(R.string.shelf_add_books)) }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
private fun LibraryBookItem(
    book: Book,
    selecting: Boolean,
    selected: Boolean,
    actions: SelectionActions,
    onOpen: (Book) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    )
    // On a picked item's container the text takes the colours made for it.
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val titleColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurface)
    val subtitleColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    // A picked cover steps back from the edge, so the tint behind it frames it.
    val inset by animateDpAsState(if (selected) 6.dp else 0.dp)

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .drawBehind { drawRect(container) }
            .combinedClickable(
                // While picking, a tap picks too; opening a book waits until the selection is over.
                onClick = { if (selecting) actions.onToggle(book) else onOpen(book) },
                onLongClick = { actions.onToggle(book) },
                onLongClickLabel = stringResource(R.string.selection_select),
            )
            .semantics { if (selecting) this.selected = selected },
    ) {
        Box(Modifier.padding(inset)) {
            BookCover(book, Modifier.fillMaxWidth())
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Icon(painterResource(R.drawable.check_48px), contentDescription = null, modifier = Modifier.padding(3.dp).size(18.dp))
                }
            }
            // Chapters that came out since the comic was opened take the corner a pick would, which wins.
            if (book.newChapters > 0 && !selected) NewChaptersBadge(book.newChapters, Modifier.align(Alignment.TopStart).padding(6.dp))
            val (badgeIcon, badgeLabel) = ownershipBadge(book)
            // How the book is had goes in the lower left, with the heart above it.
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (book.isFavorite) {
                    CoverBadge {
                        Icon(
                            painterResource(R.drawable.favorite_fill_48px),
                            contentDescription = stringResource(R.string.favorite),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                CoverBadge {
                    Icon(badgeIcon, contentDescription = stringResource(badgeLabel), modifier = Modifier.size(14.dp))
                }
            }
            // A book not started yet has nothing to show for its state.
            if (book.state != ReadingState.PLANNED) {
                // In the upper right, on a backing the art still shows through.
                CoverBadge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    contentPadding = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                ) {
                    if (book.state == ReadingState.READING) {
                        ReadingProgressRing(book.overallProgression)
                    } else {
                        // As large as the ring, so the states read alike across the grid.
                        Icon(
                            painter = painterResource(book.state.outlinedIcon),
                            contentDescription = statusLine(book),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp),
        )
        // Inset from the item's rounded clip, which would otherwise cut into the last line.
        val authorModifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
        // Who wrote it stays put whatever the state, which the cover shows as a badge.
        // While picking, a tap on a name picks the book like a tap anywhere else on it.
        if (book.authors.isNotEmpty() && !selecting) {
            AuthorLinks(
                authors = book.authors,
                refs = book.authorRefs,
                onOpenAuthor = onOpenAuthor,
                modifier = authorModifier,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        } else {
            Text(
                text = book.authorLine.ifEmpty { statusLine(book) },
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = authorModifier,
            )
        }
    }
}

/** One book of the list: a small cover, with what the grid shows as badges spelled out beside it. */
@Composable
private fun LibraryBookRow(
    book: Book,
    selecting: Boolean,
    selected: Boolean,
    actions: SelectionActions,
    onOpen: (Book) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    )
    // On a picked item's container the text takes the colours made for it.
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val titleColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurface)
    val subtitleColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurfaceVariant)

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .drawBehind { drawRect(container) }
            .combinedClickable(
                // While picking, a tap picks too; opening a book waits until the selection is over.
                onClick = { if (selecting) actions.onToggle(book) else onOpen(book) },
                onLongClick = { actions.onToggle(book) },
                onLongClickLabel = stringResource(R.string.selection_select),
            )
            .semantics { if (selecting) this.selected = selected }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BookCover(book, Modifier.width(56.dp), MaterialTheme.shapes.small, showTitleOnPlaceholder = false)
            if (book.newChapters > 0 && !selected) {
                NewChaptersBadge(book.newChapters, Modifier.align(Alignment.TopStart).padding(2.dp))
            }
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Icon(painterResource(R.drawable.check_48px), contentDescription = null, modifier = Modifier.padding(3.dp).size(18.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotEmpty()) {
                // While picking, a tap on a name picks the book like a tap anywhere else on it.
                if (selecting) {
                    Text(
                        text = book.authorLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    AuthorLinks(
                        authors = book.authors,
                        refs = book.authorRefs,
                        onOpenAuthor = onOpenAuthor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor,
                    )
                }
            }
            book.series?.let { series ->
                val number = book.seriesNumberText
                Text(
                    text = if (number != null) stringResource(R.string.series_with_number, series, number) else series,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val (badgeIcon, badgeLabel) = ownershipBadge(book)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val tint = subtitleColor
                if (book.isFavorite) {
                    Icon(
                        painterResource(R.drawable.favorite_fill_48px),
                        contentDescription = stringResource(R.string.favorite),
                        modifier = Modifier.size(14.dp),
                        tint = tint,
                    )
                }
                // Described only where the words beside it are about something else.
                Icon(
                    painter = badgeIcon,
                    contentDescription = if (book.canRead) stringResource(badgeLabel) else null,
                    modifier = Modifier.size(14.dp),
                    tint = tint,
                )
                // Those words get the icon of what they say, or the one before would be taken for theirs.
                if (book.canRead) {
                    Icon(painterResource(book.state.icon), contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
                }
                // There is room here to say why the book cannot be opened, rather than only hint at it.
                Text(
                    text = stringResource(if (book.canRead) book.state.labelRes else badgeLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (book.state == ReadingState.READING) {
            ReadingProgressRing(book.overallProgression)
        }
    }
}

/** A book's state in words, for when there is no author to name and for screen readers. */
@Composable
private fun statusLine(book: Book): String = when (book.state) {
    ReadingState.READING -> formatPercent(book.overallProgression)
    else -> stringResource(book.state.labelRes)
}

/** How many chapters of a comic are new, on a backing in the colours that call for a look. */
@Composable
private fun NewChaptersBadge(count: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.new_chapters, count, count)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CoverBadge(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 5.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** Floats over the grid while books are picked: how many, and what can be done to them together. */
@Composable
private fun SelectionToolbar(
    selection: LibrarySelection,
    allSelected: Boolean,
    actions: SelectionActions,
    onEditPurchase: () -> Unit,
    onDelete: () -> Unit,
    onOpenShelves: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stateMenuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val countLabel = pluralStringResource(R.plurals.selection_count, selection.size, selection.size)

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        IconButton(onClick = actions.onClear) {
            Icon(painterResource(R.drawable.close_48px), contentDescription = stringResource(R.string.selection_clear), modifier = Modifier.size(24.dp))
        }
        Text(
            text = selection.size.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 4.dp, end = 12.dp)
                .semantics { contentDescription = countLabel },
        )
        IconButton(onClick = actions.onSelectAll, enabled = !allSelected) {
            Icon(painterResource(R.drawable.select_all_48px), contentDescription = stringResource(R.string.selection_select_all), modifier = Modifier.size(24.dp))
        }
        if (selection.favoritable.isNotEmpty()) {
            val favorites = selection.allFavorite
            IconButton(onClick = { actions.onSetFavorite(!favorites) }) {
                Icon(
                    if (favorites) painterResource(R.drawable.favorite_fill_48px) else painterResource(R.drawable.favorite_48px),
                    contentDescription = stringResource(
                        if (favorites) R.string.favorite_remove else R.string.favorite_add,
                    ),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (selection.stateChangeable.isNotEmpty()) {
            Box {
                IconButton(onClick = { stateMenuOpen = true }) {
                    Icon(
                        painterResource(R.drawable.label_fill_48px),
                        contentDescription = stringResource(R.string.selection_change_state),
                        modifier = Modifier.size(24.dp),
                    )
                }
                DropdownMenu(expanded = stateMenuOpen, onDismissRequest = { stateMenuOpen = false }) {
                    ReadingStateOrder.filter { it != selection.sharedState }.forEach { state ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.book_mark_as, stringResource(state.labelRes))) },
                            leadingIcon = { Icon(painterResource(state.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                            onClick = {
                                stateMenuOpen = false
                                actions.onSetState(state)
                            },
                        )
                    }
                }
            }
        }
        IconButton(onClick = onOpenShelves) {
            Icon(
                painterResource(R.drawable.shelves_48px),
                contentDescription = stringResource(R.string.shelf_add_to),
                modifier = Modifier.size(24.dp),
            )
        }
        Box {
            IconButton(onClick = { moreOpen = true }) {
                Icon(painterResource(R.drawable.more_vert_48px), contentDescription = stringResource(R.string.book_more_actions), modifier = Modifier.size(24.dp))
            }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                val single = selection.books.singleOrNull()
                // One book gets the full details; several are simply marked as bought today.
                if (single != null || selection.notPurchased.isNotEmpty()) {
                    val recorded = single?.acquisition == Acquisition.PURCHASED
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (recorded) R.string.purchase_dialog_title else R.string.wishlist_mark_purchased,
                                ),
                            )
                        },
                        leadingIcon = { Icon(painterResource(R.drawable.purchased_book_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                        onClick = {
                            moreOpen = false
                            if (single != null) {
                                onEditPurchase()
                            } else {
                                actions.onMarkPurchased(null, null, LocalDate.now(), null, null)
                            }
                        },
                    )
                }
                // Dropping the purchase leaves a wish, or a download when the book has its EPUB.
                if (selection.purchased.isNotEmpty()) {
                    val toWishlist = selection.purchased.none { it.inLibrary }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (toWishlist) R.string.wishlist_move_back else R.string.purchase_mark_not_purchased,
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (toWishlist) painterResource(R.drawable.kid_star_48px) else painterResource(R.drawable.purchased_book_off_fill_48px),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        onClick = {
                            moreOpen = false
                            actions.onUnmarkPurchased()
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove)) },
                    leadingIcon = { Icon(painterResource(R.drawable.delete_fill_48px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    onClick = {
                        moreOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun LibraryContentPreview() {
    val books = PreviewData.books
    AppTheme {
        LibraryContent(
            state = LibraryUiState(
                loading = false,
                filter = LibraryFilter.ALL,
                sections = librarySections(LibraryFilter.ALL, books),
                counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) },
                totalBooks = books.size,
            ),
            search = SearchUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onSearchQueryChange = {},
            onFilterSelected = {},
            onSortSelected = {},
            onDisplaySelected = {},
            onImportClick = {},
            onAddBook = {},
            onAddManually = {},
            onBookClick = {},
            onAuthorClick = {},
            selectionActions = SelectionActions(),
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun LibraryListPreview() {
    val books = PreviewData.books
    AppTheme {
        LibraryContent(
            state = LibraryUiState(
                loading = false,
                filter = LibraryFilter.ALL,
                display = BookDisplay.LIST,
                sections = librarySections(LibraryFilter.ALL, books),
                counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) },
                totalBooks = books.size,
                selection = LibrarySelection(books.take(1)),
            ),
            search = SearchUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onSearchQueryChange = {},
            onFilterSelected = {},
            onSortSelected = {},
            onDisplaySelected = {},
            onImportClick = {},
            onAddBook = {},
            onAddManually = {},
            onBookClick = {},
            onAuthorClick = {},
            selectionActions = SelectionActions(),
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun LibrarySelectionPreview() {
    val books = PreviewData.books
    AppTheme {
        LibraryContent(
            state = LibraryUiState(
                loading = false,
                filter = LibraryFilter.ALL,
                sections = librarySections(LibraryFilter.ALL, books),
                counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) },
                totalBooks = books.size,
                selection = LibrarySelection(books.take(3)),
            ),
            search = SearchUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onSearchQueryChange = {},
            onFilterSelected = {},
            onSortSelected = {},
            onDisplaySelected = {},
            onImportClick = {},
            onAddBook = {},
            onAddManually = {},
            onBookClick = {},
            onAuthorClick = {},
            selectionActions = SelectionActions(),
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun LibraryEmptyPreview() {
    AppTheme {
        LibraryContent(
            state = LibraryUiState(loading = false),
            search = SearchUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onSearchQueryChange = {},
            onFilterSelected = {},
            onSortSelected = {},
            onDisplaySelected = {},
            onImportClick = {},
            onAddBook = {},
            onAddManually = {},
            onBookClick = {},
            onAuthorClick = {},
            selectionActions = SelectionActions(),
            onSettingsClick = {},
        )
    }
}
