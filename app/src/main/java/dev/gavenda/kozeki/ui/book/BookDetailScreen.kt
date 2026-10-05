package dev.gavenda.kozeki.ui.book

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.BookReview
import dev.gavenda.kozeki.data.metadata.BookReviews
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MatchStatus
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.Note
import dev.gavenda.kozeki.data.model.ReadOutcome
import dev.gavenda.kozeki.data.model.ReadThrough
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.RatingBar
import dev.gavenda.kozeki.ui.components.ReadingStateOrder
import dev.gavenda.kozeki.ui.components.icon
import dev.gavenda.kozeki.ui.components.labelRes
import dev.gavenda.kozeki.ui.formatDate
import dev.gavenda.kozeki.ui.formatDuration
import dev.gavenda.kozeki.ui.formatPercent
import dev.gavenda.kozeki.ui.formatPrice
import dev.gavenda.kozeki.ui.library.EpubMimeTypes
import dev.gavenda.kozeki.ui.library.importMessage
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.text.NumberFormat
import java.time.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Everything the detail screen can ask for. Defaults make previews short. */
@Immutable
class BookDetailActions(
    val onBack: () -> Unit = {},
    val onRead: () -> Unit = {},
    val onImportEpub: () -> Unit = {},
    val onSetState: (ReadingState) -> Unit = {},
    val onReadAgain: () -> Unit = {},
    val onSetFavorite: (Boolean) -> Unit = {},
    val onSetRating: (Float?) -> Unit = {},
    val onSetPurchase: (Acquisition, Long?, String?, LocalDate?, String?) -> Unit = { _, _, _, _, _ -> },
    val onSetPhysicalProgress: (Int?, Int?) -> Unit = { _, _ -> },
    val onSaveNote: (String?, String) -> Unit = { _, _ -> },
    val onDeleteNote: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onOpenMatch: () -> Unit = {},
    val onRetryMatch: () -> Unit = {},
    val onUnlink: () -> Unit = {},
    val onMatchQueryChange: (String) -> Unit = {},
    val onSearchMatch: () -> Unit = {},
    val onLoadMoreMatches: () -> Unit = {},
    val onApplyMatch: (BookMetadata) -> Unit = {},
    val onCloseMatch: () -> Unit = {},
    val onRetryReviews: () -> Unit = {},
)

@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
    viewModel: BookDetailViewModel = koinViewModel(key = bookId) { parametersOf(bookId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachEpub(uri)
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                BookDetailEvent.Deleted -> onBack()
                is BookDetailEvent.ImportFinished ->
                    snackbarHostState.showSnackbar(importMessage(resources, listOf(event.result)))
            }
        }
    }

    val actions = remember(viewModel, bookId) {
        BookDetailActions(
            onBack = onBack,
            onRead = { onRead(bookId) },
            onImportEpub = { picker.launch(EpubMimeTypes) },
            onSetState = viewModel::setState,
            onReadAgain = viewModel::readAgain,
            onSetFavorite = viewModel::setFavorite,
            onSetRating = viewModel::setRating,
            onSetPurchase = viewModel::setPurchase,
            onSetPhysicalProgress = viewModel::setPhysicalProgress,
            onSaveNote = viewModel::saveNote,
            onDeleteNote = viewModel::deleteNote,
            onDelete = viewModel::delete,
            onOpenMatch = viewModel::openMatch,
            onRetryMatch = viewModel::retryAutomaticMatch,
            onUnlink = viewModel::unlink,
            onMatchQueryChange = viewModel::onMatchQueryChange,
            onSearchMatch = viewModel::searchMatch,
            onLoadMoreMatches = viewModel::loadMoreMatches,
            onApplyMatch = viewModel::applyMatch,
            onCloseMatch = viewModel::closeMatch,
            onRetryReviews = viewModel::retryReviews,
        )
    }

    BookDetailContent(state, snackbarHostState, actions)
}

@Composable
fun BookDetailContent(
    state: BookDetailUiState,
    snackbarHostState: SnackbarHostState,
    actions: BookDetailActions,
    modifier: Modifier = Modifier,
) {
    val book = state.book
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var editingPurchase by rememberSaveable { mutableStateOf(false) }
    var editingPhysicalProgress by rememberSaveable { mutableStateOf(false) }
    // "" means a new note is being written; null means no note dialog.
    var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingAllReviews by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // The header carries the title, so the bar only takes it over once the header's copy, which
    // sits at the very top of the list, has scrolled under it.
    var titleHeight by remember { mutableIntStateOf(0) }
    val titleInBar by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                (titleHeight > 0 && listState.firstVisibleItemScrollOffset >= titleHeight)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = book != null && titleInBar,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        Text(book?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (book != null) {
                        if (book.canFavorite) {
                            IconToggleButton(checked = book.isFavorite, onCheckedChange = actions.onSetFavorite) {
                                Icon(
                                    if (book.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = stringResource(
                                        if (book.isFavorite) R.string.favorite_remove else R.string.favorite_add,
                                    ),
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.book_more_actions),
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.match_find)) },
                                    onClick = {
                                        menuOpen = false
                                        actions.onOpenMatch()
                                    },
                                )
                                if (book.source != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.match_unlink)) },
                                        onClick = {
                                            menuOpen = false
                                            actions.onUnlink()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        confirmingDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (book == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                if (state.loading) LoadingIndicator() else Text(stringResource(R.string.book_not_found))
            }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "header") { Header(book, actions.onSetRating, onTitleHeight = { titleHeight = it }) }
                item(key = "primary") { PrimaryAction(book, state.importing, actions) }
                if (book.canChangeState) {
                    item(key = "state") { StateSelector(book.state, actions.onSetState) }
                }
                if (book.inLibrary) {
                    item(key = "progress") { ProgressCard(book, state.readingTimeMs, state.readThroughs) }
                }
                if (book.canTrackPhysical) {
                    item(key = "physical-progress") {
                        PhysicalProgressCard(book, onEdit = { editingPhysicalProgress = true })
                    }
                }
                matchNotice(book)?.let { notice ->
                    item(key = "match") { MatchNoticeCard(notice, actions) }
                }
                item(key = "about") { AboutSection(book) }
                item(key = "ownership") {
                    OwnershipSection(book, onEdit = { editingPurchase = true }, onSetPurchase = actions.onSetPurchase)
                }
                item(key = "notes-header") {
                    SectionTitle(stringResource(R.string.notes_title)) {
                        TextButton(onClick = { editingNoteId = "" }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.note_add))
                        }
                    }
                }
                if (state.notes.isEmpty()) {
                    item(key = "notes-empty") { MutedText(stringResource(R.string.notes_empty)) }
                } else {
                    items(state.notes, key = { "note-${it.id}" }) { note ->
                        NoteCard(note, onClick = { editingNoteId = note.id })
                    }
                }
                if (state.readThroughs.isNotEmpty()) {
                    item(key = "history") { HistorySection(state.readThroughs) }
                }
                state.reviews?.let { reviewsState ->
                    val reviews = reviewsState.reviews
                    item(key = "reviews-header") { ReviewsHeader(reviews) }
                    when {
                        reviews == null -> item(key = "reviews-status") {
                            ReviewsStatus(reviewsState, actions.onRetryReviews)
                        }

                        reviews.reviews.isEmpty() -> item(key = "reviews-empty") {
                            MutedText(stringResource(R.string.reviews_empty))
                        }

                        else -> {
                            val shown = if (showingAllReviews) reviews.reviews else reviews.reviews.take(REVIEWS_PREVIEW)
                            items(shown, key = { "review-${it.id}" }) { ReviewCard(it) }
                            if (shown.size < reviews.reviews.size) {
                                item(key = "reviews-more") {
                                    TextButton(onClick = { showingAllReviews = true }) {
                                        Text(
                                            pluralStringResource(
                                                R.plurals.reviews_show_all,
                                                reviews.reviews.size,
                                                reviews.reviews.size,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (book != null) {
        if (confirmingDelete) {
            DeleteBookDialog(
                title = book.title,
                onConfirm = {
                    confirmingDelete = false
                    actions.onDelete()
                },
                onDismiss = { confirmingDelete = false },
            )
        }
        if (editingPurchase) {
            PurchaseDialog(
                book = book,
                lastCurrency = state.lastCurrency,
                locationHistory = state.purchaseLocations,
                onSave = { price, currency, date, location ->
                    editingPurchase = false
                    actions.onSetPurchase(Acquisition.PURCHASED, price, currency, date, location)
                },
                onDismiss = { editingPurchase = false },
            )
        }
        if (editingPhysicalProgress) {
            PhysicalProgressDialog(
                book = book,
                onSave = { page, pageCount ->
                    editingPhysicalProgress = false
                    actions.onSetPhysicalProgress(page, pageCount)
                },
                onDismiss = { editingPhysicalProgress = false },
            )
        }
        editingNoteId?.let { noteId ->
            val note = state.notes.firstOrNull { it.id == noteId }
            NoteDialog(
                note = note,
                onSave = { text ->
                    actions.onSaveNote(note?.id, text)
                    editingNoteId = null
                },
                onDelete = note?.let {
                    {
                        actions.onDeleteNote(it.id)
                        editingNoteId = null
                    }
                },
                onDismiss = { editingNoteId = null },
            )
        }
        if (state.match.open) {
            MatchSheet(
                state = state.match,
                onQueryChange = actions.onMatchQueryChange,
                onSearch = actions.onSearchMatch,
                onLoadMore = actions.onLoadMoreMatches,
                onPick = actions.onApplyMatch,
                onDismiss = actions.onCloseMatch,
            )
        }
    }
}

@Composable
private fun Header(book: Book, onSetRating: (Float?) -> Unit, onTitleHeight: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BookCover(book, Modifier.width(128.dp), shape = MaterialTheme.shapes.large)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.onSizeChanged { onTitleHeight(it.height) },
            )
            book.subtitle?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (book.authors.isNotEmpty()) {
                Text(
                    text = book.authorLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            RatingBar(rating = book.rating, onRatingChange = onSetRating)
            if (book.rating != null) {
                TextButton(onClick = { onSetRating(null) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.rating_clear))
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(book: Book, importing: Boolean, actions: BookDetailActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            book.canRead -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = actions.onRead,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        stringResource(
                            when {
                                book.state == ReadingState.COMPLETED -> R.string.book_open
                                book.progression > 0.0 -> R.string.book_continue
                                else -> R.string.book_start
                            },
                        ),
                    )
                }
                if (book.state == ReadingState.COMPLETED || book.state == ReadingState.DROPPED) {
                    FilledTonalButton(onClick = actions.onReadAgain, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.book_read_again))
                    }
                }
            }

            else -> {
                if (book.inLibrary) MutedText(stringResource(R.string.book_file_missing_message))
                OutlinedButton(
                    onClick = actions.onImportEpub,
                    enabled = !importing,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (importing) {
                        LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                    } else {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.import_epub))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StateSelector(current: ReadingState, onSetState: (ReadingState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.book_status))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingStateOrder.forEach { state ->
                val selected = state == current
                FilterChip(
                    selected = selected,
                    onClick = { onSetState(state) },
                    label = { Text(stringResource(state.labelRes)) },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Rounded.Check else state.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(book: Book, readingTimeMs: Long, readThroughs: List<ReadThrough>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressLabel(stringResource(R.string.progress_epub))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatPercent(book.progression), style = MaterialTheme.typography.displaySmallEmphasized)
                val readingNumber = readThroughs.maxOfOrNull { it.number } ?: 1
                if (readingNumber > 1) {
                    Text(
                        text = stringResource(R.string.book_reading_number, readingNumber),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            LinearWavyProgressIndicator(
                progress = { book.progression.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            book.chapterTitle?.let { chapter ->
                Text(chapter, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (book.chapterIndex != null && book.chapterCount != null) {
                    Stat(
                        label = stringResource(R.string.book_chapter),
                        value = stringResource(R.string.x_of_y, book.chapterIndex, book.chapterCount),
                    )
                }
                if (book.position != null && book.positionCount != null) {
                    Stat(
                        label = stringResource(R.string.book_page),
                        value = stringResource(R.string.x_of_y, book.position, book.positionCount),
                    )
                }
                Stat(label = stringResource(R.string.book_time_read), value = formatDuration(readingTimeMs))
            }
        }
    }
}

/**
 * Where the user is in a physical copy, which only they can tell. Until they do, it is a short
 * invitation rather than an empty meter.
 */
@Composable
private fun PhysicalProgressCard(book: Book, onEdit: () -> Unit) {
    val page = book.physicalPage
    if (page == null) {
        OutlinedCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProgressLabel(stringResource(R.string.progress_physical))
                    MutedText(stringResource(R.string.physical_prompt))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.physical_set))
                }
            }
        }
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressLabel(stringResource(R.string.progress_physical))
            val progression = book.physicalProgression
            if (progression != null) {
                Text(formatPercent(progression), style = MaterialTheme.typography.displaySmallEmphasized)
                LinearWavyProgressIndicator(progress = { progression.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pageCount = book.physicalPageCount
                Stat(
                    label = stringResource(R.string.book_page),
                    value = if (pageCount != null) stringResource(R.string.x_of_y, page, pageCount) else page.toString(),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.physical_update))
                }
            }
        }
    }
}

/** Names which copy of the book a progress card is about. */
@Composable
private fun ProgressLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

private enum class MatchNotice { PENDING, NEEDS_REVIEW, NOT_FOUND }

private fun matchNotice(book: Book): MatchNotice? = when (book.matchStatus) {
    MatchStatus.PENDING -> MatchNotice.PENDING
    MatchStatus.NEEDS_REVIEW -> MatchNotice.NEEDS_REVIEW
    MatchStatus.NOT_FOUND -> MatchNotice.NOT_FOUND
    MatchStatus.MATCHED, MatchStatus.NONE -> null
}

@Composable
private fun MatchNoticeCard(notice: MatchNotice, actions: BookDetailActions) {
    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    when (notice) {
                        MatchNotice.PENDING -> R.string.match_pending_title
                        MatchNotice.NEEDS_REVIEW -> R.string.match_review_title
                        MatchNotice.NOT_FOUND -> R.string.match_not_found_title
                    },
                ),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            MutedText(
                stringResource(
                    when (notice) {
                        MatchNotice.PENDING -> R.string.match_pending_message
                        MatchNotice.NEEDS_REVIEW -> R.string.match_review_message
                        MatchNotice.NOT_FOUND -> R.string.match_not_found_message
                    },
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = actions.onOpenMatch, shapes = ButtonDefaults.shapes()) {
                    Text(
                        stringResource(
                            if (notice == MatchNotice.NEEDS_REVIEW) R.string.match_choose else R.string.match_search,
                        ),
                    )
                }
                if (notice == MatchNotice.NOT_FOUND) {
                    TextButton(onClick = actions.onRetryMatch) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(book: Book) {
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.book_about))
        book.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .animateContentSize()
                    .clickable(
                        onClickLabel = stringResource(if (expanded) R.string.action_show_less else R.string.action_show_more),
                    ) { expanded = !expanded },
            )
        }
        val details = buildList {
            book.publisher?.let { add(stringResource(R.string.detail_publisher) to it) }
            book.publishedDate?.let { add(stringResource(R.string.detail_published) to it) }
            book.pageCount?.let { add(stringResource(R.string.detail_pages) to it.toString()) }
            book.language?.let { add(stringResource(R.string.detail_language) to it.uppercase()) }
            (book.isbn13 ?: book.isbn10)?.let { add(stringResource(R.string.detail_isbn) to it) }
            if (book.categories.isNotEmpty()) {
                add(stringResource(R.string.detail_categories) to book.categories.joinToString(", "))
            }
        }
        if (book.description == null && details.isEmpty()) {
            MutedText(stringResource(R.string.book_no_details))
        }
        details.forEach { (label, value) ->
            Row {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(112.dp),
                )
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val source = book.source
        val url = book.sourceUrl
        if (source != null && url != null) {
            TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.book_view_on_source, stringResource(source.nameRes)))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

internal val MetadataSource.nameRes: Int
    get() = when (this) {
        MetadataSource.HARDCOVER -> R.string.source_hardcover
    }

@Composable
private fun OwnershipSection(
    book: Book,
    onEdit: () -> Unit,
    onSetPurchase: (Acquisition, Long?, String?, LocalDate?, String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.purchase_title))
        // Having the EPUB is not having bought it: without a purchase such a book is a download, not a wish.
        val unbought = if (book.inLibrary) Acquisition.DOWNLOADED else Acquisition.WISHLIST
        ConnectedButtonGroup(
            options = listOf(
                stringResource(if (book.inLibrary) R.string.not_purchased else R.string.wishlist_tab),
                stringResource(R.string.purchased_tab),
            ),
            selectedIndex = if (book.acquisition == Acquisition.PURCHASED) 1 else 0,
            onSelect = { index ->
                if (index == 0) {
                    onSetPurchase(unbought, null, null, null, null)
                } else {
                    onSetPurchase(
                        Acquisition.PURCHASED,
                        book.purchasePriceMinor,
                        book.purchaseCurrency,
                        book.purchasedOn,
                        book.purchaseLocation,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (book.acquisition == Acquisition.PURCHASED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = book.purchasePriceMinor?.let { formatPrice(it, book.purchaseCurrency) }
                            ?: stringResource(R.string.purchase_no_price),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    book.purchasedOn?.let { date ->
                        MutedText(stringResource(R.string.purchase_bought_on, formatDate(date)))
                    }
                    book.purchaseLocation?.let { MutedText(it) }
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
            MutedText(listOfNotNull(note.chapterTitle, formatDate(note.createdAt)).joinToString(" · "))
        }
    }
}

@Composable
private fun HistorySection(readThroughs: List<ReadThrough>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.history_title))
        readThroughs.forEach { readThrough ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.history_reading, readThrough.number, readThrough.number),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(112.dp),
                )
                val finished = readThrough.finishedAt
                Text(
                    text = when {
                        finished == null -> stringResource(R.string.history_started, formatDate(readThrough.startedAt))
                        readThrough.outcome == ReadOutcome.DROPPED ->
                            stringResource(R.string.history_dropped, formatDate(finished))
                        else -> stringResource(R.string.history_completed, formatDate(finished))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How many reviews are shown before the user asks for the rest. */
private const val REVIEWS_PREVIEW = 3

/** The section title with, once known, what the source's readers rate the book overall. */
@Composable
private fun ReviewsHeader(reviews: BookReviews?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.reviews_title))
        if (reviews == null) return@Column
        reviews.averageRating?.let { average ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.reviews_average, average),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                RatingBar(rating = average, starSize = 18.dp)
            }
        }
        if (reviews.ratingsCount > 0 || reviews.reviewsCount > 0) {
            val counts = NumberFormat.getIntegerInstance()
            MutedText(
                listOf(
                    pluralStringResource(
                        R.plurals.reviews_ratings_count,
                        reviews.ratingsCount,
                        counts.format(reviews.ratingsCount),
                    ),
                    pluralStringResource(
                        R.plurals.reviews_reviews_count,
                        reviews.reviewsCount,
                        counts.format(reviews.reviewsCount),
                    ),
                ).joinToString(" · "),
            )
        }
    }
}

/** Stands in for the reviews while they load, or says why there are none to show. */
@Composable
private fun ReviewsStatus(state: ReviewsUiState, onRetry: () -> Unit) {
    val error = state.error
    if (error == null) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MutedText(stringResource(error.message))
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun ReviewCard(review: BookReview) {
    // A review that gives the story away stays folded until the user asks for it.
    var revealed by rememberSaveable(review.id) { mutableStateOf(!review.hasSpoilers) }
    var expanded by rememberSaveable(review.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = review.reviewer ?: stringResource(R.string.reviews_anonymous),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                review.rating?.let { RatingBar(rating = it, starSize = 16.dp) }
            }
            if (revealed) {
                Text(
                    text = review.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .animateContentSize()
                        .clickable(
                            onClickLabel = stringResource(
                                if (expanded) R.string.action_show_less else R.string.action_show_more,
                            ),
                        ) { expanded = !expanded },
                )
            } else {
                MutedText(stringResource(R.string.reviews_spoilers))
                TextButton(onClick = { revealed = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.reviews_spoilers_show))
                }
            }
            val footer = listOfNotNull(
                review.reviewedOn?.let { formatDate(LocalDate.ofEpochDay(it)) },
                review.likes.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.reviews_likes, it, it) },
            ).joinToString(" · ")
            if (footer.isNotEmpty()) MutedText(footer)
        }
    }
}

@Composable
private fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@ScreenPreviews
@Composable
private fun BookDetailReadingPreview() {
    AppTheme {
        BookDetailContent(
            state = BookDetailUiState(
                loading = false,
                book = PreviewData.books[0],
                notes = PreviewData.notes,
                readThroughs = PreviewData.readThroughs,
                readingTimeMs = 5 * 3_600_000L + 20 * 60_000L,
                reviews = ReviewsUiState(reviews = PreviewData.reviews),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            actions = BookDetailActions(),
        )
    }
}

@ScreenPreviews
@Composable
private fun BookDetailPhysicalPreview() {
    AppTheme {
        BookDetailContent(
            state = BookDetailUiState(loading = false, book = PreviewData.books[5]),
            snackbarHostState = remember { SnackbarHostState() },
            actions = BookDetailActions(),
        )
    }
}

@ScreenPreviews
@Composable
private fun BookDetailWishlistPreview() {
    AppTheme {
        BookDetailContent(
            state = BookDetailUiState(
                loading = false,
                book = PreviewData.books[4],
                reviews = ReviewsUiState(error = LookupError.MISSING_PERMISSION),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            actions = BookDetailActions(),
        )
    }
}

@ScreenPreviews
@Composable
private fun BookDetailNeedsReviewPreview() {
    AppTheme {
        BookDetailContent(
            state = BookDetailUiState(loading = false, book = PreviewData.books[3]),
            snackbarHostState = remember { SnackbarHostState() },
            actions = BookDetailActions(),
        )
    }
}
