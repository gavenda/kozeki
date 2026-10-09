package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * How the user has [book], as an icon and what it stands for: wanted, bought, or an EPUB that was
 * imported with no purchase on record. Buying the book of an imported EPUB puts the icon of a
 * purchase in the place of the EPUB's. An EPUB that is not on this device says so instead, since
 * that is what keeps the book from being opened.
 */
fun ownershipBadge(book: Book): Pair<ImageVector, Int> = when {
    book.acquisition == Acquisition.WISHLIST -> Icons.Rounded.Bookmark to R.string.wishlist_tab
    !book.inLibrary -> Icons.Rounded.ShoppingBag to R.string.library_owned_no_epub
    !book.hasFile -> Icons.Rounded.CloudOff to R.string.book_file_missing
    book.acquisition == Acquisition.PURCHASED -> Icons.Rounded.ShoppingBag to R.string.purchased_tab
    // The icon that importing an EPUB goes by.
    else -> Icons.Rounded.UploadFile to R.string.library_imported_epub
}

/**
 * Marks a result of a metadata source as [book], one the user already has, and says how: on the
 * wishlist, bought, or as an imported EPUB. One being read shows how far along it is instead, as
 * it does in the library.
 */
@Composable
fun OwnedBadge(book: Book, modifier: Modifier = Modifier) {
    if (book.state == ReadingState.READING) {
        ReadingProgressRing(book.overallProgression, modifier)
        return
    }
    // The same icons the library grid puts on a cover.
    val (icon, label) = ownershipBadge(book)
    Box(
        modifier = modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(label),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@PreviewLightDark
@Composable
private fun OwnedBadgePreview() {
    AppTheme {
        Surface {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnedBadge(Book(id = "a", title = "Wanted"))
                OwnedBadge(Book(id = "b", title = "Bought", acquisition = Acquisition.PURCHASED))
                OwnedBadge(
                    Book(id = "c", title = "Imported", acquisition = Acquisition.DOWNLOADED, inLibrary = true, hasFile = true),
                )
                OwnedBadge(
                    Book(id = "e", title = "Then bought", acquisition = Acquisition.PURCHASED, inLibrary = true, hasFile = true),
                )
                OwnedBadge(Book(id = "f", title = "Elsewhere", acquisition = Acquisition.DOWNLOADED, inLibrary = true))
                OwnedBadge(
                    Book(
                        id = "d",
                        title = "Reading",
                        acquisition = Acquisition.DOWNLOADED,
                        inLibrary = true,
                        state = ReadingState.READING,
                        progression = 0.42,
                    ),
                )
            }
        }
    }
}
