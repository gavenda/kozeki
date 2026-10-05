package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.components.LoadMoreEffect
import dev.gavenda.kozeki.ui.components.MetadataResultItem
import dev.gavenda.kozeki.ui.components.SourcePicker
import dev.gavenda.kozeki.ui.components.loadingMoreItem
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState
import dev.gavenda.kozeki.ui.components.sourceAttributionItem
import dev.gavenda.kozeki.ui.theme.AppTheme

/** Lets the user confirm or search for the record a book should be linked to. */
@Composable
fun MatchSheet(
    state: MatchUiState,
    onQueryChange: (String) -> Unit,
    onSourceChange: (MetadataSource) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onPick: (BookMetadata) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberExpandedSheetState(),
    ) {
        MatchSheetContent(state, onQueryChange, onSourceChange, onSearch, onLoadMore, onPick)
    }
}

@Composable
private fun MatchSheetContent(
    state: MatchUiState,
    onQueryChange: (String) -> Unit,
    onSourceChange: (MetadataSource) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onPick: (BookMetadata) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.match_sheet_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        // The field keeps its own text: the state reaches it a moment after each keystroke, and a
        // field fed text older than what was typed puts the cursor back.
        var query by remember { mutableStateOf(state.query) }
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        SourcePicker(
            source = state.source,
            onSourceChange = onSourceChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            state.error != null -> SheetMessage(stringResource(state.error.message))

            state.results.isEmpty() -> SheetMessage(stringResource(R.string.search_no_results))

            else -> {
                val listState = rememberLazyListState()
                LoadMoreEffect(listState, state.results.size, state.canLoadMore, onLoadMore)
                LazyColumn(state = listState) {
                    sourceAttributionItem(state.source)
                    items(state.results, key = { it.sourceId }) { result ->
                        MetadataResultItem(result = result, onClick = { onPick(result) })
                    }
                    loadingMoreItem(state.loadingMore)
                }
            }
        }
    }
}

@Composable
private fun SheetMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
    )
}

@PreviewLightDark
@Composable
private fun MatchSheetContentPreview() {
    AppTheme {
        Surface {
            MatchSheetContent(
                state = MatchUiState(
                    open = true,
                    query = "The Dispossessed Le Guin",
                    results = listOf(
                        BookMetadata(
                            source = MetadataSource.HARDCOVER,
                            sourceId = "a",
                            title = "The Dispossessed",
                            authors = listOf("Ursula K. Le Guin"),
                            publisher = "Harper Voyager",
                            publishedDate = "1974",
                            pageCount = 387,
                        ),
                        BookMetadata(
                            source = MetadataSource.HARDCOVER,
                            sourceId = "b",
                            title = "The Dispossessed",
                            subtitle = "A Novel",
                            authors = listOf("Ursula K. Le Guin"),
                            publishedDate = "2003",
                        ),
                    ),
                ),
                onQueryChange = {},
                onSourceChange = {},
                onSearch = {},
                onLoadMore = {},
                onPick = {},
            )
        }
    }
}
