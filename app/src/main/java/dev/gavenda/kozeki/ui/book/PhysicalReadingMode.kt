package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.LabelTooltip
import dev.gavenda.kozeki.ui.components.TooltipIconButton
import dev.gavenda.kozeki.ui.reader.KeepScreenOn
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** What the reading mode asks of the book's screen. */
class PhysicalReadingActions(
    val onTurn: (page: Int, sittingSince: Long) -> Unit = { _, _ -> },
    val onOpen: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    /** The app went out of sight, or came back. */
    val onHide: () -> Unit = {},
    val onShow: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The whole screen given to a paper copy being read: one wide button that moves the page on, a
 * short narrow one beside it that takes back a tap made by mistake, and a timer whose time counts
 * as reading. Each tap is saved as it is made, and the pages of one sitting are kept as one
 * reading ([PhysicalReadingActions.onTurn]'s `sittingSince`).
 *
 * On a phone the cover fills the screen behind a translucent card; on a wide screen it stands to
 * the left. Either way the card and the buttons sit together in the middle.
 */
@Composable
fun PhysicalReadingMode(book: Book, clock: PhysicalSitting.Clock, actions: PhysicalReadingActions) {
    // The page is kept here while the screen is open, so quick taps never wait on the database.
    var page by rememberSaveable { mutableIntStateOf(book.physicalPage ?: 0) }
    val sittingSince = rememberSaveable { System.currentTimeMillis() }
    var turns by rememberSaveable { mutableIntStateOf(0) }
    val total = book.physicalPageCount?.takeIf { it > 0 }
    KeepScreenOn(turns)

    LaunchedEffect(Unit) { actions.onOpen() }
    // Time out of sight is not reading, and what was read until then is kept in case the app is
    // never come back to.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(actions)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> current.onHide()
                Lifecycle.Event.ON_START -> current.onShow()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun turn(to: Int) {
        page = to
        turns++
        actions.onTurn(to, sittingSince)
    }

    Dialog(
        onDismissRequest = actions.onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PhysicalReadingContent(
            book = book,
            page = page,
            total = total,
            clock = clock,
            onNext = { turn(page + 1) },
            onPrevious = { turn(page - 1) },
            actions = actions,
        )
    }
}

@Composable
private fun PhysicalReadingContent(
    book: Book,
    page: Int,
    total: Int?,
    clock: PhysicalSitting.Clock,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    actions: PhysicalReadingActions,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            val cover = book.coverModel
            // On a phone the cover is the background, darkened a little so the card stands out.
            if (!wide && cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            val onCover = !wide && cover != null
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (cover != null) {
                            BookCover(book, Modifier.fillMaxHeight(), shape = MaterialTheme.shapes.large)
                        }
                        Column(
                            Modifier.weight(1f, fill = false).widthIn(max = 480.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            BookAndPage(book, page, total)
                            PageButtons(page, total, onNext, onPrevious, Modifier.width(360.dp))
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (onCover) {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        ) {
                            Box(Modifier.padding(24.dp)) { BookAndPage(book, page, total) }
                        }
                        PageButtons(page, total, onNext, onPrevious, Modifier.widthIn(max = 480.dp).fillMaxWidth())
                    }
                }
                TopRow(clock, onCover, actions, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** The way out on one side, and the sitting's timer on the other. */
@Composable
private fun TopRow(clock: PhysicalSitting.Clock, onCover: Boolean, actions: PhysicalReadingActions, modifier: Modifier) {
    // Over a cover, both sit on the surface colour so they stay readable.
    val chip = if (onCover) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Row(modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onCover) {
            val back = stringResource(R.string.action_back)
            LabelTooltip(back) {
                FilledTonalIconButton(
                    onClick = actions.onClose,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = chip),
                ) {
                    Icon(painterResource(R.drawable.keyboard_arrow_left_48px), back, Modifier.size(24.dp))
                }
            }
        } else {
            TooltipIconButton(
                icon = R.drawable.keyboard_arrow_left_48px,
                label = stringResource(R.string.action_back),
                onClick = actions.onClose,
            )
        }
        Spacer(Modifier.weight(1f))
        SittingTimer(clock, chip, actions)
    }
}

@Composable
private fun SittingTimer(clock: PhysicalSitting.Clock, color: Color, actions: PhysicalReadingActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(clock.runningSince) {
        while (clock.runningSince != null) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    val elapsed = clock.baseMs + (clock.runningSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    val running = clock.runningSince != null
    Surface(shape = CircleShape, color = color) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.timer_fill_48px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(clockText(elapsed), style = MaterialTheme.typography.titleMedium)
            val label = stringResource(if (running) R.string.physical_timer_pause else R.string.physical_timer_resume)
            LabelTooltip(label) {
                IconButton(onClick = if (running) actions.onPause else actions.onResume, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(if (running) R.drawable.pause_fill_48px else R.drawable.play_arrow_fill_48px),
                        contentDescription = label,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/** "4:05", "24:13" or "1:02:05". */
private fun clockText(ms: Long): String {
    val seconds = ms / 1_000
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}

@Composable
private fun BookAndPage(book: Book, page: Int, total: Int?) {
    val leather = MaterialTheme.colorScheme.tertiary
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        Spacer(Modifier.size(4.dp))
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
}

/** A wide button for the next page, and beside it a short, narrow one that takes a tap back. */
@Composable
private fun PageButtons(page: Int, total: Int?, onNext: () -> Unit, onPrevious: () -> Unit, modifier: Modifier) {
    val height = ButtonDefaults.LargeContainerHeight
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow),
                ),
            ) {
                Icon(
                    painterResource(R.drawable.undo_48px),
                    contentDescription = previousLabel,
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
            }
        }
        Button(
            onClick = onNext,
            enabled = total == null || page < total,
            shapes = ButtonDefaults.shapesFor(height),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
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

@ScreenPreviews
@Composable
private fun PhysicalReadingPreview() {
    AppTheme {
        PhysicalReadingContent(
            book = PreviewData.books[5],
            page = 142,
            total = 320,
            clock = PhysicalSitting.Clock(24 * 60_000L + 13_000L, null),
            onNext = {},
            onPrevious = {},
            actions = PhysicalReadingActions(),
        )
    }
}
