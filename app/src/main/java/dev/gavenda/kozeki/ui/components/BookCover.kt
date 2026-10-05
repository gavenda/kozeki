package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.absoluteValue

/** Width over height of a typical book cover. */
const val CoverAspectRatio = 2f / 3f

/**
 * A book's cover, falling back to a generated one that shows the title. The fallback is always
 * drawn underneath, so a cover that fails to load never leaves a blank box.
 */
@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    showTitleOnPlaceholder: Boolean = true,
) {
    Box(
        modifier = modifier
            .aspectRatio(CoverAspectRatio)
            .clip(shape),
    ) {
        CoverPlaceholder(book, showTitleOnPlaceholder)
        book.coverModel?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CoverPlaceholder(book: Book, showTitle: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Stable per book, so a given title always gets the same colour.
    val (container, content) = when (book.title.hashCode().absoluteValue % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(container)
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showTitle) {
            Text(
                text = book.title,
                color = content,
                style = MaterialTheme.typography.labelMediumEmphasized,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = book.title.take(1).uppercase(),
                color = content,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BookCoverPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewData.books.take(3).forEach { BookCover(it, Modifier.width(96.dp)) }
            BookCover(PreviewData.books[3], Modifier.width(40.dp), showTitleOnPlaceholder = false)
        }
    }
}
