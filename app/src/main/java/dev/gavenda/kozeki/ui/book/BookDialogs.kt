package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * Asks before [books] are removed, showing which ones: one from its own page, or all the ones
 * picked in the library.
 */
@Composable
fun DeleteBooksDialog(books: List<Book>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val count = books.size
    AlertDialog(
        onDismissRequest = onDismiss,
        // With an icon the dialog centres its headline, as Material 3 lays out a dialog that has one.
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(pluralStringResource(R.plurals.delete_books_title, count, count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(pluralStringResource(R.plurals.delete_books_message, count))
                // The list scrolls when many books are picked, between dividers that mark where it
                // ends and the actions begin.
                Column {
                    HorizontalDivider()
                    // Three and a half rows: the cut-off one says there is more to scroll to.
                    LazyColumn(Modifier.heightIn(max = 266.dp)) {
                        items(books, key = { it.id }) { book ->
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BookCover(
                                    book = book,
                                    modifier = Modifier.width(40.dp),
                                    shape = MaterialTheme.shapes.small,
                                    showTitleOnPlaceholder = false,
                                )
                                Column {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (book.authors.isNotEmpty()) {
                                        Text(
                                            text = book.authorLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    pluralStringResource(R.plurals.delete_books_confirm, count),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(pluralStringResource(R.plurals.delete_books_keep, count)) }
        },
    )
}

@PreviewLightDark
@Composable
private fun DeleteBookDialogPreview() {
    AppTheme { DeleteBooksDialog(books = PreviewData.books.take(1), onConfirm = {}, onDismiss = {}) }
}

@PreviewLightDark
@Composable
private fun DeleteBooksDialogPreview() {
    AppTheme { DeleteBooksDialog(books = PreviewData.books, onConfirm = {}, onDismiss = {}) }
}
