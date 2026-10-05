package dev.gavenda.kozeki.ui.addbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.LoadMoreEffect
import dev.gavenda.kozeki.ui.components.MetadataResultDetails
import dev.gavenda.kozeki.ui.components.MetadataResultItem
import dev.gavenda.kozeki.ui.components.OwnedBadge
import dev.gavenda.kozeki.ui.components.loadingMoreItem
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState
import dev.gavenda.kozeki.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddBookScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AddBookViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is AddBookEvent.Added -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(
                            if (event.acquisition == Acquisition.WISHLIST) {
                                R.string.add_book_added_wishlist
                            } else {
                                R.string.add_book_added_purchased
                            },
                            event.title,
                        ),
                        actionLabel = resources.getString(R.string.action_view),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenBook(event.bookId)
                }
            }
        }
    }

    AddBookContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onLoadMore = viewModel::loadMore,
        onSelect = viewModel::select,
        onAdd = viewModel::add,
        onOpenAuthor = onOpenAuthor,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun AddBookContent(
    state: AddBookUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (BookMetadata?) -> Unit,
    onAdd: (BookMetadata, Acquisition) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
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
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp)) {
                // Dressed as the search bar of the library, though it is a plain field: it does not
                // open into results.
                val searchBarColors = SearchBarDefaults.colors()
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
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
                            enabled = state.query.trim().length >= 3,
                            // The library's icon never dims, so neither does this one.
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                )

                val results = state.results
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                    state.error != null -> EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.add_book_error_title),
                        message = stringResource(state.error.message),
                        action = if (state.error in SetupErrors) {
                            { Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) } }
                        } else {
                            null
                        },
                    )

                    results == null -> EmptyState(
                        icon = Icons.Rounded.TravelExplore,
                        title = stringResource(R.string.add_book_prompt_title),
                        message = stringResource(R.string.add_book_prompt_message),
                    )

                    results.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.search_no_results),
                        message = stringResource(R.string.add_book_no_results_message),
                    )

                    else -> {
                        val listState = rememberLazyListState()
                        LoadMoreEffect(listState, results.size, state.canLoadMore, onLoadMore)
                        LazyColumn(state = listState) {
                            items(results, key = { it.sourceId }) { result ->
                                MetadataResultItem(
                                    result = result,
                                    onClick = { onSelect(result) },
                                    trailingContent = state.owned[result.sourceId]?.let { book -> { OwnedBadge(book) } },
                                    onOpenAuthor = onOpenAuthor,
                                )
                            }
                            loadingMoreItem(state.loadingMore)
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { result ->
        ModalBottomSheet(onDismissRequest = { onSelect(null) }, sheetState = rememberExpandedSheetState()) {
            MetadataResultDetails(result, onAdd, onOpenAuthor)
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
            state = AddBookUiState(query = "le guin", results = PreviewResults, owned = mapOf("b" to Book(id = "b", title = "The Lathe of Heaven", acquisition = Acquisition.PURCHASED))),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onQueryChange = {},
            onSearch = {},
            onLoadMore = {},
            onSelect = {},
            onAdd = { _, _ -> },
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
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onQueryChange = {},
            onSearch = {},
            onLoadMore = {},
            onSelect = {},
            onAdd = { _, _ -> },
            onOpenAuthor = {},
            onOpenSettings = {},
        )
    }
}
