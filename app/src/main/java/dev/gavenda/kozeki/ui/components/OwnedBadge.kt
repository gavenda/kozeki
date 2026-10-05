package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * Marks a result of a metadata source as [book], one the user already has, and says where it is
 * kept: on the wishlist, among the purchases still without an EPUB, or in the library.
 */
@Composable
fun OwnedBadge(book: Book, modifier: Modifier = Modifier) {
    // The same icons the library grid puts on a cover.
    val (icon, label) = when {
        book.acquisition == Acquisition.WISHLIST -> Icons.Rounded.Bookmark to R.string.wishlist_tab
        !book.inLibrary -> Icons.Rounded.ShoppingBag to R.string.library_owned_no_epub
        else -> Icons.AutoMirrored.Rounded.LibraryBooks to R.string.nav_library
    }
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
                OwnedBadge(Book(id = "c", title = "Imported", acquisition = Acquisition.DOWNLOADED, inLibrary = true))
            }
        }
    }
}
