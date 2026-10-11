package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * Ownership badge. Strictly for three states: Wishlisted, Purchased, EPUB.
 */
@Composable
fun ownershipBadge(book: Book): Pair<Painter, Int> = when(book.acquisition) {
    // In the wishlist
    Acquisition.WISHLIST -> {
        painterResource(R.drawable.kid_star_48px) to R.string.wishlist_tab
    }
    // Purchased
    Acquisition.PURCHASED -> {
        painterResource(R.drawable.purchased_book_fill_48px) to R.string.purchased_tab
    }
    // EPUB
    Acquisition.DOWNLOADED -> {
        painterResource(R.drawable.library_books_fill_48px) to R.string.library_imported_epub
    }
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
        modifier = modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
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
                    Book(
                        id = "f",
                        title = "Elsewhere",
                        acquisition = Acquisition.DOWNLOADED,
                        inLibrary = true
                    )
                )
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
