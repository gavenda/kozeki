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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
    val onBlur: (Boolean) -> Unit = {},
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
fun PhysicalReadingMode(book: Book, clock: PhysicalSitting.Clock, blurred: Boolean, actions: PhysicalReadingActions) {
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
            blurred = blurred,
            onNext = {
                // Turning a page is reading, so a paused timer starts again.
                if (clock.runningSince == null) actions.onResume()
                turn(page + 1)
            },
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
    blurred: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    actions: PhysicalReadingActions,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            val cover = book.coverModel
            // On a phone the cover is the background, darkened a little so the card stands out.
            // Where it sits is kept, so that the card can draw the part of it behind itself.
            var coverOrigin by remember { mutableStateOf(Offset.Zero) }
            if (!wide && cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().onGloballyPositioned { coverOrigin = it.positionInRoot() },
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            val onCover = !wide && cover != null
            // What the frosted parts blur: the cover as it fills the screen.
            val backdrop = if (onCover && blurred) Backdrop(cover!!, maxWidth, maxHeight, coverOrigin) else null
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
                            PageButtons(page, total, clock, onNext, onPrevious, actions, Modifier.width(360.dp))
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    ) {
                        Frosted(
                            backdrop = backdrop,
                            shape = MaterialTheme.shapes.extraLarge,
                            color = panelColor(onCover, blurred),
                            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                BookAndPage(book, page, total)
                                PageButtons(page, total, clock, onNext, onPrevious, actions, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                TopRow(clock, onCover, blurred, backdrop, actions, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** The cover as it fills a screen of [width] by [height], its corner at [origin] in the window. */
private class Backdrop(val cover: Any, val width: Dp, val height: Dp, val origin: Offset)

/** The colour of what sits over the cover: see-through over it, more so when frosted. */
@Composable
private fun panelColor(onCover: Boolean, blurred: Boolean): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(
        alpha = when {
            !onCover -> 1f
            blurred -> 0.6f
            else -> 0.82f
        },
    )

/**
 * [content] on [color] in [shape]; with a [backdrop], over the part of it behind, blurred, so that
 * it looks like frosted glass.
 */
@Composable
private fun Frosted(
    backdrop: Backdrop?,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.clip(shape).onGloballyPositioned { origin = it.positionInRoot() }) {
        if (backdrop != null) FrostedCover(backdrop.cover, backdrop.width, backdrop.height, origin - backdrop.origin)
        Box(Modifier.matchParentSize().background(color))
        content()
    }
}

/**
 * The part of the cover, filling a screen of [width] by [height], that lies behind a card [offset]
 * from its corner, blurred.
 */
@Composable
private fun BoxScope.FrostedCover(cover: Any, width: Dp, height: Dp, offset: Offset) {
    Box(Modifier.matchParentSize().clipToBounds()) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(width, height)
                .offset { IntOffset(-offset.x.roundToInt(), -offset.y.roundToInt()) }
                .blur(24.dp),
        )
        // The same darkening as the background, so the two meet without a seam.
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)))
    }
}

/** The way out on one side, and the sitting's timer on the other. */
@Composable
private fun TopRow(
    clock: PhysicalSitting.Clock,
    onCover: Boolean,
    blurred: Boolean,
    backdrop: Backdrop?,
    actions: PhysicalReadingActions,
    modifier: Modifier,
) {
    // Over a cover, they sit on the card's see-through colour, and are frosted when it is.
    val chip = panelColor(onCover, blurred)
    Row(modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onCover) {
            val back = stringResource(R.string.action_back)
            Frosted(backdrop, CircleShape, chip) {
                TooltipIconButton(icon = R.drawable.arrow_back_48px, label = back, onClick = actions.onClose)
            }
        } else {
            TooltipIconButton(
                icon = R.drawable.arrow_back_48px,
                label = stringResource(R.string.action_back),
                onClick = actions.onClose,
            )
        }
        Spacer(Modifier.weight(1f))
        // Only a card over the cover can be frosted, so only the phone's layout has the button.
        if (onCover) {
            val label = stringResource(if (blurred) R.string.physical_cover_unblur else R.string.physical_cover_blur)
            Frosted(backdrop, CircleShape, chip) {
                TooltipIconButton(
                    icon = if (blurred) R.drawable.blur_off_48px else R.drawable.blur_on_48px,
                    label = label,
                    onClick = { actions.onBlur(!blurred) },
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        SittingTimer(clock, chip, backdrop)
    }
}

@Composable
private fun SittingTimer(clock: PhysicalSitting.Clock, color: Color, backdrop: Backdrop?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(clock.runningSince) {
        while (clock.runningSince != null) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    val elapsed = clock.baseMs + (clock.runningSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    Frosted(backdrop, CircleShape, color) {
        Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(IconButtonDefaults.smallContainerSize()), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.timer_fill_48px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(clockText(elapsed), style = MaterialTheme.typography.titleMedium)
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
    val accent = MaterialTheme.colorScheme.primary
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
        Text(stringResource(R.string.book_page), style = MaterialTheme.typography.labelLarge, color = accent)
        Text(
            if (total != null) stringResource(R.string.x_of_y, page, total) else page.toString(),
            style = MaterialTheme.typography.displayMediumEmphasized,
        )
        if (total != null) {
            LinearWavyProgressIndicator(
                progress = { (page.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }
}

/**
 * A wide icon button for the next page, and before it a short, narrow one that takes a tap back
 * and the timer's pause.
 */
@Composable
private fun PageButtons(
    page: Int,
    total: Int?,
    clock: PhysicalSitting.Clock,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    actions: PhysicalReadingActions,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val previousLabel = stringResource(R.string.physical_previous_page)
        LabelTooltip(previousLabel) {
            FilledTonalIconButton(
                onClick = onPrevious,
                enabled = page > 0,
                shapes = IconButtonDefaults.shapes(),
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
        // The timer's pause, between the two, where a hand already is.
        val running = clock.runningSince != null
        val timerLabel = stringResource(if (running) R.string.physical_timer_pause else R.string.physical_timer_resume)
        LabelTooltip(timerLabel) {
            OutlinedIconButton(
                onClick = if (running) actions.onPause else actions.onResume,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(
                    painterResource(if (running) R.drawable.pause_fill_48px else R.drawable.play_arrow_fill_48px),
                    contentDescription = timerLabel,
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
            }
        }
        val nextLabel = stringResource(R.string.physical_next_page)
        // As wide as the row allows, so that it is hard to miss.
        Box(Modifier.weight(1f)) {
            LabelTooltip(nextLabel) {
                FilledIconButton(
                    onClick = onNext,
                    enabled = total == null || page < total,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(IconButtonDefaults.largeContainerSize().height),
                ) {
                    Icon(
                        painterResource(R.drawable.keyboard_arrow_right_48px),
                        contentDescription = nextLabel,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
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
            blurred = false,
            onNext = {},
            onPrevious = {},
            actions = PhysicalReadingActions(),
        )
    }
}
