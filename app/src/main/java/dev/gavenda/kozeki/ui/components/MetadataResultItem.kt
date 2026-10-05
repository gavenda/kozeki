package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * A search result from a metadata source: thumbnail, title, authors and edition details. With
 * [onOpenAuthor], an author the source keeps a page for is a button of their own that calls it.
 */
@Composable
fun MetadataResultItem(
    result: BookMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    onOpenAuthor: ((AuthorRef) -> Unit)? = null,
) {
    // Laid out by hand rather than as a ListItem: that one asks its content for a baseline while
    // measuring, and Compose crashes on it when a lazy list measures a reused row ahead of time.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            result.coverUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(result.title, result.subtitle).joinToString(": "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
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
