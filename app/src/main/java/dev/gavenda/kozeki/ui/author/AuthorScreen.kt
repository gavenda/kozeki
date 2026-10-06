package dev.gavenda.kozeki.ui.author

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.Author
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.LookupError
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.LoadMoreEffect
import dev.gavenda.kozeki.ui.components.MetadataResultItem
import dev.gavenda.kozeki.ui.components.OwnedBadge
import dev.gavenda.kozeki.ui.components.loadingMoreItem
import dev.gavenda.kozeki.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** An author as the metadata source describes them, above the books it lists as theirs. */
@Composable
fun AuthorScreen(
    authorId: String,
    name: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenResult: (BookMetadata) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    viewModel: AuthorViewModel = koinViewModel(key = authorId) { parametersOf(authorId, name) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AuthorContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onLoadMore = viewModel::loadMore,
        onOpenResult = onOpenResult,
        onOpenBook = onOpenBook,
        // A co-author has a page of their own; this author's is the one already open.
        onOpenAuthor = { if (it.id != authorId) onOpenAuthor(it) },
    )
}

@Composable
fun AuthorContent(
    state: AuthorUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenResult: (BookMetadata) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val author = state.author
    // The header carries the name, so the bar only takes it over once the header's copy has
    // scrolled under it. The name is centred on the photo, so that is the photo's midpoint.
    val nameOffset = with(LocalDensity.current) { (HeaderVerticalPadding + PhotoSize / 2).roundToPx() }
    val nameScrolledAway by remember(listState, nameOffset) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > nameOffset }
    }
    val titleInBar = when {
        state.loading -> false
        // Nothing else on these states says whose page this is.
        state.error != null || state.notFound || author == null -> true
        else -> nameScrolledAway
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = titleInBar,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
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
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                state.error != null -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.author_error_title),
                    message = stringResource(state.error.message),
                    action = { Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } },
                )

                state.notFound || author == null -> EmptyState(
                    icon = Icons.Rounded.PersonOff,
                    title = stringResource(R.string.author_error_title),
                    message = stringResource(R.string.author_not_found),
                )

                else -> {
                    LoadMoreEffect(listState, state.books.size, state.canLoadMore, onLoadMore)
                    LazyColumn(state = listState, modifier = Modifier.widthIn(max = 720.dp)) {
                        item(key = "author", contentType = "author") { AuthorHeader(author) }
                        if (state.books.isEmpty() && !state.canLoadMore) {
                            item(key = "no-books", contentType = "no-books") {
                                EmptyState(
                                    icon = Icons.Rounded.SearchOff,
                                    title = stringResource(R.string.author_no_books_title),
                                    message = stringResource(R.string.author_no_books_message),
                                )
                            }
                        } else {
                            item(key = "books", contentType = "books") {
                                Text(
                                    text = stringResource(R.string.author_books),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(state.books, key = { it.sourceId }, contentType = { "book" }) { result ->
                            val owned = state.owned[result.sourceId]
                            MetadataResultItem(
                                result = result,
                                // One the user already has opens as their book; there is nothing left to add.
                                onClick = { if (owned != null) onOpenBook(owned.id) else onOpenResult(result) },
                                trailingContent = owned?.let { book -> { OwnedBadge(book) } },
                            )
                        }
                        loadingMoreItem(state.loadingMore)
                    }
                }
            }
        }
    }

}

@Composable
private fun AuthorHeader(author: Author, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = HeaderVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(PhotoSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
                author.imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.author_photo, author.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(author.name, style = MaterialTheme.typography.headlineSmallEmphasized)
                val born = author.bornYear
                val died = author.deathYear
                val details = listOfNotNull(
                    when {
                        born != null && died != null -> stringResource(R.string.author_years, born, died)
                        born != null -> stringResource(R.string.author_born, born)
                        else -> null
                    },
                    author.location,
                    author.booksCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.book_count, it, it) },
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        author.bio?.let { bio ->
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(
                text = bio,
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
    }
}

private val HeaderVerticalPadding = 8.dp
private val PhotoSize = 88.dp

private val PreviewAuthor = Author(
    id = "1",
    name = "Ursula K. Le Guin",
    bio = "Ursula Kroeber Le Guin was an American author best known for her works of speculative " +
        "fiction, including the science fiction of the Hainish universe and the Earthsea fantasy " +
        "series. She was first published in 1959, and her literary career spanned nearly sixty years.",
    bornYear = 1929,
    deathYear = 2018,
    location = "Portland, Oregon",
    booksCount = 214,
)

private val PreviewBooks = listOf(
    BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = "a",
        title = "The Dispossessed",
        subtitle = "An Ambiguous Utopia",
        authors = listOf("Ursula K. Le Guin"),
        publishedDate = "1974-05-01",
        pageCount = 387,
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
private fun AuthorPreview() {
    AppTheme {
        AuthorContent(
            state = AuthorUiState(
                name = PreviewAuthor.name,
                author = PreviewAuthor,
                loading = false,
                books = PreviewBooks,
                owned = mapOf("b" to Book(id = "b", title = "The Lathe of Heaven", acquisition = Acquisition.PURCHASED)),
            ),
            onBack = {},
            onRetry = {},
            onLoadMore = {},
            onOpenResult = {},
            onOpenBook = {},
            onOpenAuthor = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AuthorErrorPreview() {
    AppTheme {
        AuthorContent(
            state = AuthorUiState(name = PreviewAuthor.name, loading = false, error = LookupError.OFFLINE),
            onBack = {},
            onRetry = {},
            onLoadMore = {},
            onOpenResult = {},
            onOpenBook = {},
            onOpenAuthor = {},
        )
    }
}
