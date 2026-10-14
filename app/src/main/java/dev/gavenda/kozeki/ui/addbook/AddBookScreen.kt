package dev.gavenda.kozeki.ui.addbook

import dev.gavenda.kozeki.ui.CaptchaActivity
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
import dev.gavenda.kozeki.data.manga.MangaProvider
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.addBookViewModel
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.LoadMoreEffect
import dev.gavenda.kozeki.ui.components.MetadataResultItem
import dev.gavenda.kozeki.ui.components.OwnedBadge
import dev.gavenda.kozeki.ui.components.SourcePicker
import dev.gavenda.kozeki.ui.components.loadingMoreItem
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
    AddBookContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onSourceChange = viewModel::setSource,
        onMangaSourceChange = viewModel::setMangaSource,
        onSearch = viewModel::search,
        onLoadMore = viewModel::loadMore,
        onOpenResult = onOpenResult,
        onOpenBook = onOpenBook,
        onOpenAuthor = onOpenAuthor,
        onOpenSettings = onOpenSettings,
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
    onMangaSourceChange: (Long) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = {
        keyboard?.hide()
        onSearch()
    }

    Scaffold(
        modifier = modifier,
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
                    placeholder = { Text(stringResource(R.string.search_hint)) },
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

                SourcePicker(
                    source = state.source,
                    onSourceChange = onSourceChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )

                if (state.source == MetadataSource.MANGA && state.mangaSources.isNotEmpty()) {
                    MangaSourceChips(
                        options = state.mangaSources,
                        selectedId = state.mangaSourceId,
                        onSelect = onMangaSourceChange,
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
                        title = stringResource(R.string.add_book_manga_none_title),
                        message = stringResource(R.string.add_book_manga_none_message),
                        // Where the extensions that were found, and why the others were not, are listed.
                        action = {
                            Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings_manga_title)) }
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
                        title = stringResource(R.string.add_book_prompt_title),
                        message = stringResource(R.string.add_book_prompt_message),
                    )

                    results.isEmpty() -> EmptyState(
                        expression = Expression.WORRIED,
                        icon = painterResource(R.drawable.search_off_48px),
                        title = stringResource(R.string.add_book_no_results_title),
                        message = stringResource(
                            if (state.source == MetadataSource.MANGA) {
                                R.string.add_book_manga_no_results_message
                            } else {
                                R.string.add_book_no_results_message
                            },
                        ),
                    )

                    else -> {
                        val listState = rememberLazyListState()
                        LoadMoreEffect(listState, results.size, state.canLoadMore, onLoadMore)
                        LazyColumn(state = listState) {
                            sourceAttributionItem(state.source)
                            items(results, key = { it.sourceId }) { result ->
                                val owned = state.owned[result.sourceId]
                                MetadataResultItem(
                                    result = result,
                                    // One the user already has opens as their book; there is nothing left to add.
                                    onClick = { if (owned != null) onOpenBook(owned.id) else onOpenResult(result) },
                                    trailingContent = owned?.let { book -> { OwnedBadge(book) } },
                                    onOpenAuthor = onOpenAuthor,
                                    sourceLabel = state.mangaLabel(result),
                                )
                            }
                            loadingMoreItem(state.loadingMore)
                        }
                    }
                }
            }
        }
    }

}

/** The chip of the manga source [result] came from, when it is one of the sources listed. */
private fun AddBookUiState.mangaLabel(result: BookMetadata): String? {
    if (result.source != MetadataSource.MANGA) return null
    val id = MangaProvider.parseSourceId(result.sourceId)?.first ?: return null
    return mangaSources.firstOrNull { it.id == id }?.label
}

/** The manga sources to search, one of them picked. Scrolls when the extensions have many. */
@Composable
private fun MangaSourceChips(
    options: List<MangaSourceOption>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.id }) { option ->
            FilterChip(
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
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
private fun AddBookMangaPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(
                source = MetadataSource.MANGA,
                mangaSources = listOf(
                    MangaSourceOption(1L, "MangaDex"),
                    MangaSourceOption(2L, "Asura Scans"),
                    MangaSourceOption(3L, "Comick"),
                ),
                mangaSourceId = 1L,
                results = listOf(
                    BookMetadata(
                        source = MetadataSource.MANGA,
                        sourceId = "1:/title/a",
                        title = "Frieren: Beyond Journey's End",
                        authors = listOf("Kanehito Yamada", "Tsukasa Abe"),
                    ),
                    BookMetadata(
                        source = MetadataSource.MANGA,
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
private fun AddBookNoMangaSourcesPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(source = MetadataSource.MANGA, error = LookupError.NO_EXTENSIONS),
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
