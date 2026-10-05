package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.AuthorRef
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * What a metadata source says about one of its books, with the ways to add it. An author the
 * source keeps a page for is a chip that calls [onOpenAuthor].
 */
@Composable
fun MetadataResultDetails(
    result: BookMetadata,
    onAdd: (BookMetadata, Acquisition) -> Unit,
    onOpenAuthor: (AuthorRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = listOfNotNull(result.title, result.subtitle).joinToString(": "),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        val (linked, unlinked) = result.authors.map { it to result.authorRef(it) }.partition { it.second != null }
        if (linked.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linked.forEach { (name, ref) ->
                    AssistChip(
                        onClick = { onOpenAuthor(ref!!) },
                        label = { Text(name) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
        if (unlinked.isNotEmpty()) {
            Text(
                text = unlinked.joinToString(", ") { it.first },
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

@PreviewLightDark
@Composable
private fun MetadataResultDetailsPreview() {
    AppTheme {
        Surface {
            MetadataResultDetails(
                result = BookMetadata(
                    source = MetadataSource.HARDCOVER,
                    sourceId = "a",
                    title = "Good Omens",
                    subtitle = "The Nice and Accurate Prophecies of Agnes Nutter, Witch",
                    authors = listOf("Terry Pratchett", "Neil Gaiman", "Paul Kidby"),
                    authorRefs = listOf(AuthorRef("1", "Terry Pratchett"), AuthorRef("2", "Neil Gaiman")),
                    description = "The world will end on Saturday. Next Saturday, in fact. Just after tea.",
                    publishedDate = "1990-05-10",
                    pageCount = 412,
                    isbn13 = "9780060853983",
                ),
                onAdd = { _, _ -> },
                onOpenAuthor = {},
            )
        }
    }
}
