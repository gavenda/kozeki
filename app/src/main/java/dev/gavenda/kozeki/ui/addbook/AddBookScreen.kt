package dev.gavenda.kozeki.ui.addbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.book.nameRes
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.MetadataResultItem
import dev.gavenda.kozeki.ui.components.SourceAttribution
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState
import dev.gavenda.kozeki.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddBookScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
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
        onSelect = viewModel::select,
        onAdd = viewModel::add,
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
    onSelect: (BookMetadata?) -> Unit,
    onAdd: (BookMetadata, Acquisition) -> Unit,
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
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_hint)) },
                    supportingText = {
                        Text(stringResource(R.string.add_book_searching_in, stringResource(state.source.nameRes)))
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    trailingIcon = {
                        IconButton(onClick = submit, enabled = state.query.trim().length >= 3) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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

                    else -> LazyColumn {
                        items(results, key = { it.sourceId }) { result ->
                            MetadataResultItem(
                                result = result,
                                onClick = { onSelect(result) },
                                trailingContent = if (result.sourceId in state.added) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.add_book_already_added),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                        item {
                            SourceAttribution(state.source, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { result ->
        ModalBottomSheet(onDismissRequest = { onSelect(null) }, sheetState = rememberExpandedSheetState()) {
            ResultDetails(result, onAdd)
        }
    }
}

/** Errors the user fixes in Settings rather than by retrying. */
private val SetupErrors = setOf(LookupError.NOT_CONFIGURED, LookupError.SIGNED_OUT, LookupError.UNAUTHORIZED)

@Composable
private fun ResultDetails(result: BookMetadata, onAdd: (BookMetadata, Acquisition) -> Unit) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = listOfNotNull(result.title, result.subtitle).joinToString(": "),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        if (result.authors.isNotEmpty()) {
            Text(
                text = result.authors.joinToString(", "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val details = listOfNotNull(
            result.publisher,
            result.publishedDate,
            result.pageCount?.let { pluralStringResource(R.plurals.pages_count, it, it) },
            result.isbn13,
        )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 8) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAdd(result, Acquisition.WISHLIST) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.add_book_to_wishlist))
            }
            FilledTonalButton(
                onClick = { onAdd(result, Acquisition.PURCHASED) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.add_book_as_purchased))
            }
        }
    }
}

private val PreviewResults = listOf(
    BookMetadata(
        source = MetadataSource.GOOGLE_BOOKS,
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
        source = MetadataSource.GOOGLE_BOOKS,
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
            state = AddBookUiState(query = "le guin", results = PreviewResults, added = setOf("b")),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onQueryChange = {},
            onSearch = {},
            onSelect = {},
            onAdd = { _, _ -> },
            onOpenSettings = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AddBookNotConfiguredPreview() {
    AppTheme {
        AddBookContent(
            state = AddBookUiState(error = LookupError.NOT_CONFIGURED),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onQueryChange = {},
            onSearch = {},
            onSelect = {},
            onAdd = { _, _ -> },
            onOpenSettings = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ResultDetailsPreview() {
    AppTheme {
        Surface { ResultDetails(PreviewResults.first(), onAdd = { _, _ -> }) }
    }
}
