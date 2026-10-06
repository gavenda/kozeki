package dev.gavenda.kozeki.ui.library

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveShoppingCart
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.book.DeleteBooksDialog
import dev.gavenda.kozeki.ui.book.PurchaseDialog
import dev.gavenda.kozeki.ui.components.AdaptiveColumns
import dev.gavenda.kozeki.ui.components.AuthorLinks
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.ReadingProgressRing
import dev.gavenda.kozeki.ui.components.ReadingStateOrder
import dev.gavenda.kozeki.ui.components.icon
import dev.gavenda.kozeki.ui.components.labelRes
import dev.gavenda.kozeki.ui.components.outlinedIcon
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
    onOpenAuthor: (AuthorRef) -> Unit,
    onAddBook: () -> Unit,
    onAddManually: () -> Unit,
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
        onSortSelected = viewModel::selectSort,
        onDisplaySelected = viewModel::selectDisplay,
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
    onSortSelected: (LibrarySort) -> Unit,
    onDisplaySelected: (LibraryDisplay) -> Unit,
    onImportClick: () -> Unit,
    onAddBook: () -> Unit,
    onAddManually: () -> Unit,
    onBookClick: (Book) -> Unit,
    onAuthorClick: (AuthorRef) -> Unit,
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
                AddBooksMenu(state.importing, onImportClick, onAddBook, onAddManually)
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

            // Scrolls, because on a short window it does not all fit, and a column that cannot
            // scroll squashes its last buttons into whatever height is left.
            state.isLibraryEmpty -> Box(Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.library_empty_message),
                    action = {
                        // The same three ways in, with the same icons, as the menu that takes over once there are books.
                        Button(onClick = onImportClick) {
                            AddButtonContent(Icons.Rounded.UploadFile, R.string.library_add_import)
                        }
                        OutlinedButton(onClick = onAddBook) {
                            AddButtonContent(Icons.Rounded.TravelExplore, R.string.library_add_find)
                        }
                        OutlinedButton(onClick = onAddManually) {
                            AddButtonContent(Icons.Rounded.EditNote, R.string.library_add_manual)
                        }
                    },
                )
            }

            else -> Column(Modifier.padding(top = innerPadding.calculateTopPadding())) {
                FilterRow(state, onFilterSelected, onSortSelected, onDisplaySelected)
                if (state.isFilterEmpty) {
                    EmptyState(
                        icon = Icons.Rounded.FilterListOff,
                        title = stringResource(R.string.library_filter_empty_title),
                        message = stringResource(R.string.library_filter_empty_message),
                    )
                } else {
                    // A list is the same grid with rows for cells, so the place scrolled to and the
                    // headings carry over. A wide window sets the rows side by side.
                    val list = state.display == LibraryDisplay.LIST
                    // A row brings its own margins, which the tint of a picked one fills.
                    val edge = if (list) 8.dp else 16.dp
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
                        verticalArrangement = Arrangement.spacedBy(if (list) 0.dp else 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        state.sections.forEach { section ->
                            section.heading?.let { heading ->
                                item(key = "header-${heading.name}", span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(
                                        text = stringResource(heading.label),
                                        // In line with the text of the rows under it.
                                        modifier = if (list) Modifier.padding(horizontal = 8.dp) else Modifier,
                                    )
                                }
                            }
                            items(section.books, key = { it.id }, contentType = { state.display }) { book ->
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
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
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
        DeleteBooksDialog(selection.books, onConfirm = onConfirm, onDismiss = { confirmingDelete = false })
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

@Composable
private fun AddButtonContent(icon: ImageVector, @StringRes label: Int) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(stringResource(label))
}

/**
 * The ways a book gets in: as an EPUB from the device, looked up online without a file, or typed
 * in by hand.
 */
@Composable
private fun AddBooksMenu(
    importing: Boolean,
    onImportClick: () -> Unit,
    onAddBook: () -> Unit,
    onAddManually: () -> Unit,
) {
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
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddManually()
            },
            text = { Text(stringResource(R.string.library_add_manual)) },
            icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    state: LibraryUiState,
    onFilterSelected: (LibraryFilter) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onDisplaySelected: (LibraryDisplay) -> Unit,
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
                Icon(Icons.Rounded.FilterAlt, contentDescription = stringResource(R.string.filter_books))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(state.filter.label),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                DisplayToggle(state.display, onDisplaySelected)
                SortMenu(state.sort, onSortSelected)
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
    }
}

/** Flips between covers and a list. The button shows the layout a tap leads to. */
@Composable
private fun DisplayToggle(display: LibraryDisplay, onDisplaySelected: (LibraryDisplay) -> Unit) {
    val other = if (display == LibraryDisplay.LIST) LibraryDisplay.COVERS else LibraryDisplay.LIST
    IconButton(onClick = { onDisplaySelected(other) }) {
        Icon(
            imageVector = if (other == LibraryDisplay.LIST) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
            contentDescription = stringResource(
                if (other == LibraryDisplay.LIST) R.string.display_as_list else R.string.display_as_covers,
            ),
        )
    }
}

@Composable
private fun SortMenu(sort: LibrarySort, onSortSelected: (LibrarySort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort_books))
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
                        // An empty slot keeps the labels of the other orders in line.
                        if (option == sort) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                )
            }
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
    onOpenAuthor: (AuthorRef) -> Unit,
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
            val (badgeIcon, badgeLabel) = availabilityBadge(book)
            // The heart sits above that badge, and takes its corner when there is none.
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (book.isFavorite) {
                    CoverBadge {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = stringResource(R.string.favorite),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (badgeIcon != null) {
                    CoverBadge {
                        Icon(badgeIcon, contentDescription = stringResource(badgeLabel), modifier = Modifier.size(14.dp))
                    }
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
                            imageVector = book.state.outlinedIcon,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = book.authorLine.ifEmpty { statusLine(book) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = authorModifier,
            )
        }
    }
}

/** One book of the list: a small cover, with what the grid shows as badges spelled out beside it. */
@OptIn(ExperimentalFoundationApi::class)
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
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.padding(3.dp).size(18.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotEmpty()) {
                // While picking, a tap on a name picks the book like a tap anywhere else on it.
                if (selecting) {
                    Text(
                        text = book.authorLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    AuthorLinks(
                        authors = book.authors,
                        refs = book.authorRefs,
                        onOpenAuthor = onOpenAuthor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val (badgeIcon, badgeLabel) = availabilityBadge(book)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                if (book.isFavorite) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = stringResource(R.string.favorite),
                        modifier = Modifier.size(14.dp),
                        tint = tint,
                    )
                }
                if (badgeIcon != null) {
                    Icon(badgeIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
                }
                // There is room here to say why the book cannot be opened, rather than only hint at it.
                Text(
                    text = if (badgeIcon != null) stringResource(badgeLabel) else stringResource(book.state.labelRes),
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

/** Why the book cannot be opened yet: wanted, owned without an EPUB, or the EPUB is elsewhere. Null when it can. */
private fun availabilityBadge(book: Book): Pair<ImageVector?, Int> = when {
    book.acquisition == Acquisition.WISHLIST -> Icons.Rounded.Bookmark to R.string.wishlist_tab
    !book.inLibrary -> Icons.Rounded.ShoppingBag to R.string.library_owned_no_epub
    !book.hasFile -> Icons.Rounded.CloudOff to R.string.book_file_missing
    else -> null to 0
}

/** A book's state in words, for when there is no author to name and for screen readers. */
@Composable
private fun statusLine(book: Book): String = when (book.state) {
    ReadingState.READING -> formatPercent(book.overallProgression)
    else -> stringResource(book.state.labelRes)
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
                    text = { Text(stringResource(R.string.action_remove)) },
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
                display = LibraryDisplay.LIST,
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
