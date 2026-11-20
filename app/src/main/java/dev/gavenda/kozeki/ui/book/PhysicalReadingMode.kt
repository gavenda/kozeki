package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.LabelTooltip
import dev.gavenda.kozeki.ui.components.TooltipIconButton
import dev.gavenda.kozeki.ui.reader.KeepScreenOn
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * The whole screen given to a paper copy being read: one wide button that moves the page on, and
 * a narrow one beside it that takes back a tap made by mistake. Each tap is saved as it is made,
 * and the pages of one sitting are kept as one reading ([onTurn]'s `sittingSince`).
 */
@Composable
fun PhysicalReadingMode(
    book: Book,
    onTurn: (page: Int, sittingSince: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // The page is kept here while the screen is open, so quick taps never wait on the database.
    var page by rememberSaveable { mutableIntStateOf(book.physicalPage ?: 0) }
    val sittingSince = rememberSaveable { System.currentTimeMillis() }
    var turns by rememberSaveable { mutableIntStateOf(0) }
    val total = book.physicalPageCount?.takeIf { it > 0 }
    KeepScreenOn(turns)

    fun turn(to: Int) {
        page = to
        turns++
        onTurn(to, sittingSince)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                PhysicalReadingContent(
                    book = book,
                    page = page,
                    total = total,
                    onNext = { turn(page + 1) },
                    onPrevious = { turn(page - 1) },
                    onClose = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PhysicalReadingContent(
    book: Book,
    page: Int,
    total: Int?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val leather = MaterialTheme.colorScheme.tertiary
    Column(Modifier.widthIn(max = 600.dp).fillMaxSize().padding(16.dp)) {
        TooltipIconButton(
            icon = R.drawable.close_48px,
            label = stringResource(R.string.action_close),
            onClick = onClose,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            if (book.coverModel != null) {
                BookCover(
                    book,
                    Modifier.weight(1f, fill = false).widthIn(max = 200.dp),
                    shape = MaterialTheme.shapes.large,
                )
            }
            Text(
                book.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotEmpty()) {
                Text(
                    book.authors.joinToString(", "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.book_page), style = MaterialTheme.typography.labelLarge, color = leather)
            Text(
                if (total != null) stringResource(R.string.x_of_y, page, total) else page.toString(),
                style = MaterialTheme.typography.displayMediumEmphasized,
            )
            if (total != null) {
                LinearWavyProgressIndicator(
                    progress = { (page.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = leather,
                    trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        val height = ButtonDefaults.ExtraLargeContainerHeight
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Narrow, so that a thumb reaching for the next page does not land on it.
            val previousLabel = stringResource(R.string.physical_previous_page)
            LabelTooltip(previousLabel) {
                FilledTonalIconButton(
                    onClick = onPrevious,
                    enabled = page > 0,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    modifier = Modifier.size(
                        IconButtonDefaults.extraLargeContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow),
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.keyboard_arrow_left_48px),
                        contentDescription = previousLabel,
                        modifier = Modifier.size(IconButtonDefaults.extraLargeIconSize),
                    )
                }
            }
            Button(
                onClick = onNext,
                enabled = total == null || page < total,
                shapes = ButtonDefaults.shapesFor(height),
                colors = ButtonDefaults.buttonColors(
                    containerColor = leather,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier.weight(1f).heightIn(min = height),
            ) {
                Text(
                    stringResource(R.string.physical_next_page),
                    style = ButtonDefaults.textStyleFor(height),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
                Icon(
                    painterResource(R.drawable.keyboard_arrow_right_48px),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun PhysicalReadingPreview() {
    AppTheme {
        Surface {
            PhysicalReadingContent(
                book = PreviewData.books[5],
                page = 142,
                total = 320,
                onNext = {},
                onPrevious = {},
                onClose = {},
            )
        }
    }
}
