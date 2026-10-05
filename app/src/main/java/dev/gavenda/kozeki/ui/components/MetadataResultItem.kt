package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.theme.AppTheme

/** A search result from a metadata source: thumbnail, title, authors and edition details. */
@Composable
fun MetadataResultItem(
    result: BookMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
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
        },
        supportingContent = {
            Column {
                if (result.authors.isNotEmpty()) {
                    Text(result.authors.joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val details = listOfNotNull(
                    result.publishedDate?.take(4),
                    result.publisher,
                    result.pageCount?.let { pluralStringResource(R.plurals.pages_count, it, it) },
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = trailingContent,
    ) {
        Text(
            text = listOfNotNull(result.title, result.subtitle).joinToString(": "),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Google's terms ask for this attribution wherever its book results are shown. */
@Composable
fun SourceAttribution(source: MetadataSource, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (source) {
                MetadataSource.GOOGLE_BOOKS -> R.string.attribution_google
                MetadataSource.HARDCOVER -> R.string.attribution_hardcover
            },
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun MetadataResultItemPreview() {
    AppTheme {
        Surface {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetadataResultItem(
                    result = BookMetadata(
                        source = MetadataSource.GOOGLE_BOOKS,
                        sourceId = "abc",
                        title = "The Dispossessed",
                        subtitle = "An Ambiguous Utopia",
                        authors = listOf("Ursula K. Le Guin"),
                        publisher = "Harper Voyager",
                        publishedDate = "1974-05-01",
                        pageCount = 387,
                    ),
                    onClick = {},
                )
                SourceAttribution(MetadataSource.GOOGLE_BOOKS, Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
