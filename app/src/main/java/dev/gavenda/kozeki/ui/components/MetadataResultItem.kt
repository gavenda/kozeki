package dev.gavenda.kozeki.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as selectedSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * A search result from a metadata source: thumbnail, title, authors and edition details. With
 * [onOpenAuthor], an author the source keeps a page for is a button of their own that calls it.
 * With [onLongClick] it can be picked, as the library's books are: [selected] tints it and ticks
 * its cover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetadataResultItem(
    result: BookMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    onOpenAuthor: ((AuthorRef) -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    )
    val selectLabel = stringResource(R.string.selection_select)
    // On a picked row's tint the text takes the colours made for it.
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val titleColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurface)
    val detailColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    // Laid out by hand rather than as a ListItem: that one asks its content for a baseline while
    // measuring, and Compose crashes on it when a lazy list measures a reused row ahead of time.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(container) }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = selectLabel.takeIf { onLongClick != null },
            )
            .semantics { if (onLongClick != null) selectedSemantics = selected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(CoverAspectRatio)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            ResultCoverImage(result.coverUrl, result.title, showTitle = false)
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Icon(painterResource(R.drawable.check_48px), contentDescription = null, modifier = Modifier.padding(3.dp).size(18.dp))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(result.title, result.subtitle).joinToString(": "),
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            CompositionLocalProvider(
                LocalContentColor provides detailColor,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium,
            ) {
                if (result.authors.isNotEmpty()) {
                    if (onOpenAuthor == null) {
                        Text(result.authors.joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        AuthorLinks(result.authors, result.authorRefs, onOpenAuthor)
                    }
                }
                val details = listOfNotNull(result.publishedDate?.take(4), result.publisher).joinToString(" · ")
                val rating = result.averageRating
                if (details.isNotEmpty() || rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (details.isNotEmpty()) {
                            Text(
                                text = if (rating != null) "$details · " else details,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (rating != null) AverageRating(rating)
                    }
                }
            }
        }
        trailingContent?.invoke()
    }
}

/**
 * A search result as a cover in a grid, its title and authors under it, as the library's covers
 * are. [owned] is the user's own copy, marked on the cover's corner. Picked as [MetadataResultItem] is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetadataResultCover(
    result: BookMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    owned: Book? = null,
    onOpenAuthor: ((AuthorRef) -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    )
    val selectLabel = stringResource(R.string.selection_select)
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val titleColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurface)
    val detailColor by animateColorAsState(if (selected) onContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    // A picked cover steps back from the edge, so the tint behind it frames it.
    val inset by animateDpAsState(if (selected) 6.dp else 0.dp)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .drawBehind { drawRect(container) }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = selectLabel.takeIf { onLongClick != null },
            )
            .semantics { if (onLongClick != null) selectedSemantics = selected },
    ) {
        Box(
            modifier = Modifier
                .padding(inset)
                .fillMaxWidth()
                .aspectRatio(CoverAspectRatio)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            ResultCoverImage(result.coverUrl, result.title)
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Icon(painterResource(R.drawable.check_48px), contentDescription = null, modifier = Modifier.padding(3.dp).size(18.dp))
                }
            }
            // Where the library puts a book's state, on a backing the art still shows through.
            if (owned != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                ) {
                    OwnedBadge(owned, Modifier.padding(3.dp))
                }
            }
        }
        Text(
            text = result.title,
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp),
        )
        // Inset from the item's rounded clip, which would otherwise cut into the last line.
        val authorModifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
        if (result.authors.isNotEmpty()) {
            if (onOpenAuthor == null) {
                Text(
                    text = result.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = detailColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = authorModifier,
                )
            } else {
                AuthorLinks(
                    authors = result.authors,
                    refs = result.authorRefs,
                    onOpenAuthor = onOpenAuthor,
                    modifier = authorModifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = detailColor,
                )
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * The cover of a result, filling its box: a [Skeleton] while it loads, then the image. A result
 * with no cover, or one that failed to load, is bound as the user's own books with none are, with
 * its [title] on it, or only its first letter where [showTitle] is false for a small thumbnail.
 */
@Composable
private fun ResultCoverImage(url: String?, title: String, showTitle: Boolean = true) {
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            url == null || state is AsyncImagePainter.State.Error -> CoverPlaceholder(title, showTitle)
            state !is AsyncImagePainter.State.Success -> Skeleton(Modifier.fillMaxSize())
        }
    }
}

/** What the source's readers rate a book on average, as five stars and the figure out of 5. */
@Composable
private fun AverageRating(rating: Float, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.rating_value, rating)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RatingBar(rating = rating, starSize = 14.dp)
        Text(stringResource(R.string.reviews_average, rating), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@PreviewLightDark
@Composable
private fun MetadataResultItemPreview() {
    AppTheme {
        Surface {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetadataResultItem(
                    result = BookMetadata(
                        source = MetadataSource.HARDCOVER,
                        sourceId = "abc",
                        title = "The Dispossessed",
                        subtitle = "An Ambiguous Utopia",
                        authors = listOf("Ursula K. Le Guin"),
                        publisher = "Harper Voyager",
                        publishedDate = "1974-05-01",
                        pageCount = 387,
                        averageRating = 4.2f,
                    ),
                    onClick = {},
                )
                MetadataResultItem(
                    result = BookMetadata(
                        source = MetadataSource.HARDCOVER,
                        sourceId = "def",
                        title = "Good Omens",
                        authors = listOf("Terry Pratchett", "Neil Gaiman", "Paul Kidby"),
                        authorRefs = listOf(AuthorRef("1", "Terry Pratchett"), AuthorRef("2", "Neil Gaiman")),
                        publishedDate = "1990-05-10",
                    ),
                    onClick = {},
                    onOpenAuthor = {},
                )
            }
        }
    }
}
