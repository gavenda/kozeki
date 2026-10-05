package dev.gavenda.kozeki.ui.library

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveShoppingCart
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ImportResult
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.book.DeleteBookDialog
import dev.gavenda.kozeki.ui.book.DeleteBooksDialog
import dev.gavenda.kozeki.ui.book.PurchaseDialog
import dev.gavenda.kozeki.ui.components.AdaptiveColumns
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.ReadingStateOrder
import dev.gavenda.kozeki.ui.components.icon
import dev.gavenda.kozeki.ui.components.labelRes
import dev.gavenda.kozeki.ui.formatPercent
import dev.gavenda.kozeki.ui.search.SearchTopBar
import dev.gavenda.kozeki.ui.search.SearchUiState
import dev.gavenda.kozeki.ui.search.SearchViewModel
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.time.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/** MIME types offered by the file picker. Some providers report EPUBs as generic binary data. */
internal val EpubMimeTypes = arrayOf("application/epub+zip", "application/octet-stream")

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onAddBook: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
    searchViewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val search by searchViewModel.uiState.collectAsStateWithLifecycle()
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

    LibraryContent(
        state = state,
        search = search,
        snackbarHostState = snackbarHostState,
        onSearchQueryChange = searchViewModel::onQueryChange,
        onFilterSelected = viewModel::selectFilter,
        onImportClick = { picker.launch(EpubMimeTypes) },
        onAddBook = onAddBook,
        onBookClick = { onOpenBook(it.id) },
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
            )
        },
        onSettingsClick = onOpenSettings,
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
    onImportClick: () -> Unit,
    onAddBook: () -> Unit,
    onBookClick: (Book) -> Unit,
    selectionActions: SelectionActions,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()
    val selection = state.selection
    var editingPurchase by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    BackHandler(state.selecting) { selectionActions.onClear() }
    // A dialog belongs to the selection it was opened for, not to whatever is picked next.
    LaunchedEffect(state.selecting) {
        if (!state.selecting) {
            editingPurchase = false
            confirmingDelete = false
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
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            // The empty state has its own button; two of them side by side would be noise.
            // While books are picked, the selection toolbar takes this corner.
            if (!state.selecting && (!state.isLibraryEmpty || state.importing)) {
                AddBooksMenu(importing = state.importing, onImportClick = onImportClick, onAddBook = onAddBook)
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
                    modifier = Modifier.padding(bottom = FloatingToolbarDefaults.ScreenOffset),
                )
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            state.isLibraryEmpty -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
                modifier = Modifier.padding(innerPadding),
                action = {
                    Button(onClick = onImportClick) { Text(stringResource(R.string.library_add_import)) }
                    OutlinedButton(onClick = onAddBook) { Text(stringResource(R.string.library_add_find)) }
                },
            )

            else -> Column(Modifier.padding(top = innerPadding.calculateTopPadding())) {
                FilterRow(state, onFilterSelected)
                if (state.isFilterEmpty) {
                    EmptyState(
                        icon = Icons.Rounded.FilterListOff,
                        title = stringResource(R.string.library_filter_empty_title),
                        message = stringResource(R.string.library_filter_empty_message),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = AdaptiveColumns(minColumns = 3, minCellWidth = 112.dp),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            // Room for the floating action button to clear the last row.
                            bottom = innerPadding.calculateBottomPadding() + 96.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.favorites.isNotEmpty()) {
                            item(key = "header-favorites", span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(stringResource(R.string.library_favorites))
                            }
                            items(state.favorites, key = { it.id }) { book ->
                                LibraryBookItem(
                                book = book,
                                selecting = state.selecting,
                                selected = book.id in selection.ids,
                                actions = selectionActions,
                                onOpen = onBookClick,
                                modifier = Modifier.animateItem(),
                            )
                            }
                            if (state.others.isNotEmpty()) {
                                item(key = "header-others", span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(stringResource(R.string.library_other_books))
                                }
                            }
                        }
                        items(state.others, key = { it.id }) { book ->
                            LibraryBookItem(
                                book = book,
                                selecting = state.selecting,
                                selected = book.id in selection.ids,
                                actions = selectionActions,
                                onOpen = onBookClick,
                                modifier = Modifier.animateItem(),
                            )
                        }
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
            onSave = { price, currency, date, location ->
                editingPurchase = false
                selectionActions.onMarkPurchased(price, currency, date, location)
            },
            onDismiss = { editingPurchase = false },
        )
    }
    if (confirmingDelete && state.selecting) {
        val onConfirm = {
            confirmingDelete = false
            selectionActions.onDelete()
        }
        if (single != null) {
            DeleteBookDialog(title = single.title, onConfirm = onConfirm, onDismiss = { confirmingDelete = false })
        } else {
            DeleteBooksDialog(count = selection.size, onConfirm = onConfirm, onDismiss = { confirmingDelete = false })
        }
    }
}

/** Picking books in the grid, and what is then done to all of them at once. */
class SelectionActions(
    val onToggle: (Book) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onSetFavorite: (Boolean) -> Unit = {},
    val onSetState: (ReadingState) -> Unit = {},
    val onMarkPurchased: (priceMinor: Long?, currency: String?, purchasedOn: LocalDate, location: String?) -> Unit =
        { _, _, _, _ -> },
    val onUnmarkPurchased: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** The two ways a book gets in: as an EPUB from the device, or looked up online without a file. */
@Composable
private fun AddBooksMenu(importing: Boolean, onImportClick: () -> Unit, onAddBook: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    val label = stringResource(if (importing) R.string.importing else R.string.library_add)

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { if (!importing) expanded = it },
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                if (importing) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        },
        // The menu brings its own margins, and the scaffold has already applied the same ones.
        modifier = Modifier.offset(x = 16.dp, y = 16.dp),
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onImportClick()
            },
            text = { Text(stringResource(R.string.library_add_import)) },
            icon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddBook()
            },
            text = { Text(stringResource(R.string.library_add_find)) },
            icon = { Icon(Icons.Rounded.TravelExplore, contentDescription = null) },
        )
    }
}

@Composable
private fun FilterRow(state: LibraryUiState, onFilterSelected: (LibraryFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryFilter.entries.forEach { filter ->
            val selected = filter == state.filter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.label)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
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

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookItem(
    book: Book,
    selecting: Boolean,
    selected: Boolean,
    actions: SelectionActions,
    onOpen: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    )
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
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.padding(3.dp).size(18.dp))
                }
            }
            if (book.isFavorite) {
                CoverBadge(Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = stringResource(R.string.favorite),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            // Why the book cannot be opened yet: wanted, owned without an EPUB, or the EPUB is elsewhere.
            val (badgeIcon, badgeLabel) = when {
                book.acquisition == Acquisition.WISHLIST -> Icons.Rounded.Bookmark to R.string.wishlist_tab
                !book.inLibrary -> Icons.Rounded.ShoppingBag to R.string.library_owned_no_epub
                !book.hasFile -> Icons.Rounded.CloudOff to R.string.book_file_missing
                else -> null to 0
            }
            if (badgeIcon != null) {
                CoverBadge(Modifier.align(Alignment.BottomStart)) {
                    Icon(badgeIcon, contentDescription = stringResource(badgeLabel), modifier = Modifier.size(14.dp))
                }
            }
        }
        // Shorter waves than the default, so a narrow grid cell still shows a few of them.
        LinearWavyProgressIndicator(
            progress = { book.overallProgression.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            wavelength = 24.dp,
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 3.dp, end = 4.dp),
        )
        // Inset from the item's rounded clip, which would otherwise cut into the last line.
        Text(
            text = statusLine(book),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun statusLine(book: Book): String = when (book.state) {
    ReadingState.READING -> formatPercent(book.overallProgression)
    ReadingState.PAUSED -> stringResource(R.string.library_paused_at, formatPercent(book.overallProgression))
    ReadingState.COMPLETED -> stringResource(R.string.state_completed)
    ReadingState.DROPPED -> stringResource(R.string.state_dropped)
    ReadingState.PLANNED -> book.authorLine.ifEmpty { stringResource(R.string.state_planned) }
}

@Composable
private fun CoverBadge(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.padding(5.dp)) { content() }
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
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.selection_clear))
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
            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.selection_select_all))
        }
        if (selection.favoritable.isNotEmpty()) {
            val favorites = selection.allFavorite
            IconButton(onClick = { actions.onSetFavorite(!favorites) }) {
                Icon(
                    if (favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(
                        if (favorites) R.string.favorite_remove else R.string.favorite_add,
                    ),
                )
            }
        }
        if (selection.stateChangeable.isNotEmpty()) {
            Box {
                IconButton(onClick = { stateMenuOpen = true }) {
                    Icon(
                        Icons.Rounded.Bookmarks,
                        contentDescription = stringResource(R.string.selection_change_state),
                    )
                }
                DropdownMenu(expanded = stateMenuOpen, onDismissRequest = { stateMenuOpen = false }) {
                    ReadingStateOrder.filter { it != selection.sharedState }.forEach { state ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.book_mark_as, stringResource(state.labelRes))) },
                            leadingIcon = { Icon(state.icon, contentDescription = null) },
                            onClick = {
                                stateMenuOpen = false
                                actions.onSetState(state)
                            },
                        )
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { moreOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.book_more_actions))
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
                        leadingIcon = { Icon(Icons.Rounded.ShoppingBag, contentDescription = null) },
                        onClick = {
                            moreOpen = false
                            if (single != null) {
                                onEditPurchase()
                            } else {
                                actions.onMarkPurchased(null, null, LocalDate.now(), null)
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
                                if (toWishlist) Icons.Rounded.Bookmark else Icons.Rounded.RemoveShoppingCart,
                                contentDescription = null,
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
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
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
                favorites = books.filter { it.isFavorite },
                others = books.filterNot { it.isFavorite },
                counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) },
                totalBooks = books.size,
            ),
            search = SearchUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onSearchQueryChange = {},
            onFilterSelected = {},
            onImportClick = {},
            onAddBook = {},
            onBookClick = {},
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
                favorites = books.filter { it.isFavorite },
                others = books.filterNot { it.isFavorite },
                counts = LibraryFilter.entries.associateWith { filter -> books.count(filter::matches) },
                totalBooks = books.size,
                selection = LibrarySelection(books.take(3)),
            ),
            search = SearchUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onSearchQueryChange = {},
            onFilterSelected = {},
            onImportClick = {},
            onAddBook = {},
            onBookClick = {},
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
            onImportClick = {},
            onAddBook = {},
            onBookClick = {},
            selectionActions = SelectionActions(),
            onSettingsClick = {},
        )
    }
}
