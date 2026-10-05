package dev.gavenda.kozeki.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Material's limit: past this a search bar stops stretching and sits in the middle instead. */
private val SearchBarMaxWidth = 720.dp

/** Draws a text field as empty, whatever it holds. */
private val HideText = OutputTransformation { delete(0, length) }

/**
 * The search bar above a list of books. It looks through every book in Kozeki, not only the list
 * it sits on, and opens into the results grouped by where each book lives: the library, the
 * wishlist or the purchased list.
 */
@Composable
fun BookSearchBar(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded
    val hasQuery by remember { derivedStateOf { textFieldState.text.isNotEmpty() } }

    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { currentOnQueryChange(it) }
    }
    // A query left in the closed bar would read as a filter on the list underneath it.
    LaunchedEffect(expanded) {
        if (!expanded) textFieldState.clearText()
    }

    // Composed twice, in the bar and again in the open search, which is a window of its own.
    val inputField = @Composable { fieldModifier: Modifier ->
        val keyboard = LocalSoftwareKeyboardController.current
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            // Results follow every keystroke, so all the search key has left to do is make room.
            onSearch = { keyboard?.hide() },
            // The field sets the width of the bar, and of the results that drop down from it.
            modifier = fieldModifier.fillMaxWidth(),
            placeholder = {
                // The field already announces itself as a search field.
                Text(stringResource(R.string.search_books_hint), Modifier.clearAndSetSemantics {})
            },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = if (expanded && hasQuery) {
                {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            } else {
                null
            },
            // Clearing the query takes a frame or two longer than closing, and the closed bar
            // must not flash it in the meantime.
            outputTransformation = if (expanded) null else HideText,
        )
    }
    val results: @Composable ColumnScope.() -> Unit = {
        SearchResults(
            state = state,
            onBookClick = { book ->
                // Once the search is closing, a second tap must not pick a second book.
                if (searchBarState.targetValue == SearchBarValue.Expanded) {
                    scope.launch {
                        // The open search floats above the page the book opens in, so it closes
                        // first. The spring closing it keeps settling long after it is out of
                        // sight, and waiting for that would leave a pause before the page moves.
                        val opening = launch {
                            snapshotFlow { searchBarState.currentValue }.first { it == SearchBarValue.Collapsed }
                            onBookClick(book)
                        }
                        searchBarState.animateToCollapsed()
                        // If the search stayed open after all, the pick is forgotten.
                        opening.cancel()
                    }
                }
            },
        )
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // A key typed into a dialog makes Android hand focus to the first field of the page behind
        // it. Were that the closed bar, the search would open on top of the dialog.
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        SearchBar(
            state = searchBarState,
            inputField = { inputField(Modifier.focusProperties { canFocus = windowFocused }) },
            modifier = Modifier.widthIn(max = SearchBarMaxWidth),
        )
        // With room to spare the results drop down from the bar; a phone gives them the whole screen.
        val roomy = currentWindowAdaptiveInfoV2().windowSizeClass.isAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
        )
        if (roomy) {
            ExpandedDockedSearchBar(state = searchBarState, inputField = { inputField(Modifier) }, content = results)
        } else {
            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = { inputField(Modifier) },
                content = results,
            )
        }
    }
}

@Composable
private fun SearchResults(state: SearchUiState, onBookClick: (Book) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.query.isEmpty() -> SearchMessage(
            icon = Icons.Rounded.Search,
            title = stringResource(R.string.search_books_hint),
            message = stringResource(R.string.search_books_prompt),
            modifier = modifier,
        )

        state.isEmpty -> SearchMessage(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.search_no_results),
            message = stringResource(R.string.search_books_no_results, state.query),
            modifier = modifier,
        )

        else -> LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
            group(R.string.nav_library, state.library, onBookClick)
            group(R.string.wishlist_tab, state.wishlist, onBookClick)
            group(R.string.purchased_tab, state.purchased, onBookClick)
        }
    }
}

/** One group of results under its heading. A group nothing was found for is left out altogether. */
private fun LazyListScope.group(@StringRes title: Int, books: List<Book>, onBookClick: (Book) -> Unit) {
    if (books.isEmpty()) return
    item(key = title, contentType = "heading") {
        Text(
            text = stringResource(R.string.search_group_count, stringResource(title), books.size),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .animateItem()
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() },
        )
    }
    items(books, key = { it.id }, contentType = { "book" }) { book ->
        SearchResultItem(book, onClick = { onBookClick(book) }, Modifier.animateItem())
    }
}

@Composable
private fun SearchResultItem(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            BookCover(book, Modifier.width(48.dp), MaterialTheme.shapes.small, showTitleOnPlaceholder = false)
        },
        supportingContent = if (book.authors.isNotEmpty()) {
            { Text(book.authorLine, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        // The open search has a background of its own, which a list item's would stripe.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** What the open search shows instead of results. It scrolls, since the keyboard leaves it little room. */
@Composable
private fun SearchMessage(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier.verticalScroll(rememberScrollState())) {
        EmptyState(icon = icon, title = title, message = message)
    }
}

@PreviewLightDark
@Composable
private fun BookSearchBarPreview() {
    AppTheme {
        Surface {
            // Searches the sample books for real, so the preview can be typed into.
            val index = remember { SearchIndex(PreviewData.books) }
            var query by remember { mutableStateOf("") }
            BookSearchBar(
                state = index.search(query),
                onQueryChange = { query = it },
                onBookClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchResultsPreview() {
    AppTheme {
        Surface(color = SearchBarDefaults.colors().containerColor) {
            SearchResults(state = SearchIndex(PreviewData.books).search("e"), onBookClick = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchNoResultsPreview() {
    AppTheme {
        Surface(color = SearchBarDefaults.colors().containerColor) {
            SearchResults(state = SearchUiState(query = "silmarillion"), onBookClick = {})
        }
    }
}
