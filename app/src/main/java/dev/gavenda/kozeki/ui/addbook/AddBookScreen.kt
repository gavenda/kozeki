package dev.gavenda.kozeki.ui.addbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.CaptchaActivity
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.addBookViewModel
import dev.gavenda.kozeki.ui.components.AddSelectedToolbar
import dev.gavenda.kozeki.ui.components.BulkAddedSnackbarEffect
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.LoadMoreEffect
import dev.gavenda.kozeki.ui.components.MetadataResultItem
import dev.gavenda.kozeki.ui.components.OwnedBadge
import dev.gavenda.kozeki.ui.components.SelectionToolbarRoom
import dev.gavenda.kozeki.ui.components.loadingMoreItem
import dev.gavenda.kozeki.ui.components.nameRes
import dev.gavenda.kozeki.ui.components.onClick
import dev.gavenda.kozeki.ui.components.onLongClick
import dev.gavenda.kozeki.ui.components.rememberResultSelection
import dev.gavenda.kozeki.ui.components.sourceAttributionItem
import dev.gavenda.kozeki.ui.koinViewModel
import dev.gavenda.kozeki.ui.theme.AppTheme

@Composable
fun AddBookScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenResult: (BookMetadata) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AddBookViewModel = koinViewModel { addBookViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    BulkAddedSnackbarEffect(viewModel.added, snackbarHostState)
    AddBookContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onSourceChange = viewModel::setSource,
        onComicsChange = viewModel::setComics,
        onComicSourceChange = viewModel::setComicSource,
        onSearch = viewModel::search,
        onLoadMore = viewModel::loadMore,
        onOpenResult = onOpenResult,
        onOpenBook = onOpenBook,
        onOpenAuthor = onOpenAuthor,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        onAddAll = viewModel::addAll,
    )
}

@Composable
fun AddBookContent(
    state: AddBookUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSourceChange: (MetadataSource) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenResult: (BookMetadata) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onComicsChange: (Boolean) -> Unit = {},
    onComicSourceChange: (Long) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAddAll: (List<BookMetadata>, Acquisition) -> Unit = { _, _ -> },
) {
    // The results the user does not have can be picked, to be added together. What was picked
    // in one catalogue is not carried to the next.
    val selection = rememberResultSelection()
    LaunchedEffect(state.source, state.comicSourceId) { selection.clear() }
    val addable = state.results.orEmpty().filter { it.sourceId !in state.owned }
    val picked = selection.of(addable)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = {
        keyboard?.hide()
        onSearch()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_book_title)) },
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
            Column(Modifier.widthIn(max = 720.dp)) {
                // Dressed as the search bar of the library, though it is a plain field: it does not
                // open into results.
                val searchBarColors = SearchBarDefaults.colors()
                // The field keeps its own text: the state reaches it a moment after each keystroke,
                // and a field fed text older than what was typed puts the cursor back.
                var query by remember { mutableStateOf(state.query) }
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                if (state.source == MetadataSource.COMICS) R.string.add_book_search_comics else R.string.add_book_search_books,
                            ),
                        )
                    },
                    singleLine = true,
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = searchBarColors.containerColor,
                        unfocusedContainerColor = searchBarColors.containerColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    leadingIcon = {
                        IconButton(
                            onClick = submit,
                            enabled = query.trim().length >= state.minQueryLength,
                            // The library's icon never dims, so neither does this one.
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(painterResource(R.drawable.search_48px), contentDescription = stringResource(R.string.action_search), modifier = Modifier.size(24.dp))
                        }
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    query = ""
                                    onQueryChange("")
                                },
                            ) {
                                Icon(painterResource(R.drawable.close_48px), contentDescription = stringResource(R.string.search_clear), modifier = Modifier.size(24.dp))
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                )

                val comics = state.source == MetadataSource.COMICS
                ConnectedButtonGroup(
                    options = listOf(
                        stringResource(R.string.add_book_kind_books),
                        stringResource(R.string.add_book_kind_comics),
                    ),
                    selectedIndex = if (comics) 1 else 0,
                    onSelect = { onComicsChange(it == 1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )

                // Books have their catalogues as comics have their sources: one picked, as a chip.
                if (comics) {
                    if (state.comicSources.isNotEmpty()) {
                        SourceChips(
                            options = state.comicSources,
                            key = { it.id },
                            label = { it.label },
                            selected = { it.id == state.comicSourceId },
                            onSelect = { onComicSourceChange(it.id) },
                        )
                    }
                } else {
                    val bookSources = MetadataSource.matchable
                    val labels = bookSources.map { stringResource(it.nameRes) }
                    SourceChips(
                        options = bookSources,
                        key = { it.name },
                        label = { labels[bookSources.indexOf(it)] },
                        selected = { it == state.source },
                        onSelect = onSourceChange,
                    )
                }

                val solveCaptcha = rememberLauncherForActivityResult(CaptchaActivity.Contract()) { solved ->
                    if (solved) onSearch()
                }
                val results = state.results
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        HaloLoadingIndicator()
                    }

                    // Nothing to look through yet: she says so, as she does when a book is not found.
                    state.error == LookupError.NO_EXTENSIONS -> EmptyState(
                        expression = Expression.DOWNCAST,
                        icon = painterResource(R.drawable.auto_stories_off_48px),
                        title = stringResource(R.string.add_book_comic_none_title),
                        message = stringResource(R.string.add_book_comic_none_message),
                        // Where the extensions that were found, and why the others were not, are listed.
                        action = {
                            Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings_comic_title)) }
                        },
                    )

                    state.error != null -> EmptyState(
                        expression = Expression.DISMAYED,
                        icon = painterResource(R.drawable.error_fill_48px),
                        title = stringResource(R.string.add_book_error_title),
                        message = stringResource(state.error.message),
                        action = if (state.captchaUrl != null) {
                            {
                                Button(onClick = { solveCaptcha.launch(state.captchaUrl) }) {
                                    Text(stringResource(R.string.action_open_webview))
                                }
                            }
                        } else if (state.error in SetupErrors) {
                            { Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) } }
                        } else {
                            null
                        },
                    )

                    results == null -> EmptyState(
                        expression = Expression.STUDIOUS,
                        icon = painterResource(R.drawable.travel_explore_48px),
                        title = stringResource(if (comics) R.string.add_book_comic_prompt_title else R.string.add_book_prompt_title),
                        message = stringResource(if (comics) R.string.add_book_comic_prompt_message else R.string.add_book_prompt_message),
                    )

                    results.isEmpty() -> EmptyState(
                        expression = Expression.WORRIED,
                        icon = painterResource(R.drawable.search_off_48px),
                        title = stringResource(if (comics) R.string.add_book_comic_no_results_title else R.string.add_book_no_results_title),
                        message = stringResource(if (comics) R.string.add_book_comic_no_results_message else R.string.add_book_no_results_message),
                    )

                    else -> {
                        val listState = rememberLazyListState()
                        LoadMoreEffect(listState, results.size, state.canLoadMore, onLoadMore)
                        LazyColumn(
                            state = listState,
                            // Room for the last row above the toolbar.
                            contentPadding = PaddingValues(bottom = if (selection.active) SelectionToolbarRoom else 0.dp),
                        ) {
                            sourceAttributionItem(state.source)
                            items(results, key = { it.sourceId }) { result ->
                                val owned = state.owned[result.sourceId]
                                MetadataResultItem(
                                    result = result,
                                    // One the user already has opens as their book; there is nothing left to add.
                                    onClick = selection.onClick(result, owned = owned != null) {
                                        if (owned != null) onOpenBook(owned.id) else onOpenResult(result)
                                    },
                                    trailingContent = owned?.let { book -> { OwnedBadge(book) } },
                                    // While picking, a tap on a name picks the row like the rest of it.
                                    onOpenAuthor = onOpenAuthor.takeIf { !selection.active },
                                    selected = result.sourceId in selection.ids,
                                    onLongClick = selection.onLongClick(result, owned = owned != null),
                                )
                            }
                            loadingMoreItem(state.loadingMore)
                        }
                    }
                }
            }
            if (picked.isNotEmpty()) {
                AddSelectedToolbar(
                    count = picked.size,
                    allSelected = picked.size == addable.size,
                    comics = state.source == MetadataSource.COMICS,
                    onClear = selection::clear,
                    onSelectAll = { selection.selectAll(addable) },
                    onAdd = { acquisition ->
                        onAddAll(picked, acquisition)
                        selection.clear()
                    },
                )
            }
        }
    }

}

/** The sources to search, one of them picked: comic sources or book catalogues. Scrolls when there are many. */
@Composable
private fun <T> SourceChips(
    options: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = key) { option ->
            FilterChip(
                selected = selected(option),
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/** Errors the user fixes in Settings rather than by retrying. */
private val SetupErrors = setOf(LookupError.SIGNED_OUT, LookupError.UNAUTHORIZED)

private val PreviewResults = listOf(
    BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = "a",
        title = "The Dispossessed",
        subtitle = "An Ambiguous Utopia",
        authors = listOf("Ursula K. Le Guin"),
        description = "Shevek, a brilliant physicist, decides to take action. He will seek answers, " +
            "question the unquestionable, and attempt to tear down the walls of hatred.",
        publisher = "Harper Voyager",
        publishedDate = "1974-05-01",
        pageCount = 387,
        isbn13 = "9780061054884",
    ),
    BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = "b",
        title = "The Lathe of Heaven",
        authors = listOf("Ursula K. Le Guin"),
        publishedDate = "1971",
        pageCount = 184,
    ),
)

@ScreenPreviews
@Composable
private fun AddBookResultsPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(
                query = "le guin",
                results = PreviewResults,
                owned = mapOf(
                    "a" to Book(
                        id = "a",
                        title = "The Dispossessed",
                        acquisition = Acquisition.DOWNLOADED,
                        inLibrary = true,
                        state = ReadingState.READING,
                        progression = 0.42,
                    ),
                    "b" to Book(id = "b", title = "The Lathe of Heaven", acquisition = Acquisition.PURCHASED),
                ),
            ),
            onBack = {},
            onQueryChange = {},
            onSourceChange = {},
            onSearch = {},
            onLoadMore = {},
            onOpenResult = {},
            onOpenBook = {},
            onOpenAuthor = {},
            onOpenSettings = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AddBookSignedOutPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(error = LookupError.SIGNED_OUT),
            onBack = {},
            onQueryChange = {},
            onSourceChange = {},
            onSearch = {},
            onLoadMore = {},
            onOpenResult = {},
            onOpenBook = {},
            onOpenAuthor = {},
            onOpenSettings = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AddBookGoogleBooksPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(
                query = "le guin",
                source = MetadataSource.GOOGLE_BOOKS,
                results = PreviewResults.map { it.copy(source = MetadataSource.GOOGLE_BOOKS) },
            ),
            onBack = {},
            onQueryChange = {},
            onSourceChange = {},
            onSearch = {},
            onLoadMore = {},
            onOpenResult = {},
            onOpenBook = {},
            onOpenAuthor = {},
            onOpenSettings = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AddBookComicPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(
                source = MetadataSource.COMICS,
                comicSources = listOf(
                    ComicSourceOption(1L, "MangaDex"),
                    ComicSourceOption(2L, "Asura Scans"),
                    ComicSourceOption(3L, "Comick"),
                ),
                comicSourceId = 1L,
                results = listOf(
                    BookMetadata(
                        source = MetadataSource.COMICS,
                        sourceId = "1:/title/a",
                        title = "Frieren: Beyond Journey's End",
                        authors = listOf("Kanehito Yamada", "Tsukasa Abe"),
                    ),
                    BookMetadata(
                        source = MetadataSource.COMICS,
                        sourceId = "1:/title/b",
                        title = "Dungeon Meshi",
                        authors = listOf("Ryoko Kui"),
                    ),
                ),
            ),
            onBack = {},
            onQueryChange = {},
            onSourceChange = {},
            onSearch = {},
            onLoadMore = {},
            onOpenResult = {},
            onOpenBook = {},
            onOpenAuthor = {},
            onOpenSettings = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AddBookNoComicSourcesPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(source = MetadataSource.COMICS, error = LookupError.NO_EXTENSIONS),
            onBack = {},
            onQueryChange = {},
            onSourceChange = {},
            onSearch = {},
            onLoadMore = {},
            onOpenResult = {},
            onOpenBook = {},
            onOpenAuthor = {},
            onOpenSettings = {},
        )
    }
}
