package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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

/**
 * The cover of a book that has none, bound like the one the app's character carries: a spine down
 * one side, and on the board a line that frames the title, with a scroll in each corner.
 *
 * It is drawn rather than loaded as an image, so it takes the theme's colours and stays sharp from
 * a day of the calendar to a book's own page. A cover too narrow for the scrolls has the line go
 * all the way round instead.
 */
@Composable
private fun CoverPlaceholder(book: Book, showTitle: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Stable per book, so a given title always gets the same colour.
    val (container, content) = when (book.title.hashCode().absoluteValue % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val shade = scheme.scrim
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(container)
            .drawWithCache {
                // Worked out when the cover is given its size, not every time it is drawn.
                val scrolls = if (size.width >= ScrollsFromWidth.toPx()) cornerScrolls(frameOf(size), size.width) else null
                onDrawBehind { drawBinding(content, shade, scrolls) }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shares of the width, as the spine and the frame are, so the title keeps inside the frame.
        Spacer(Modifier.weight(Spine + FrameInset + TitleGap))
        Box(Modifier.weight(1f - Spine - 2 * (FrameInset + TitleGap)), contentAlignment = Alignment.Center) {
            if (showTitle) {
                Text(
                    text = book.title,
                    color = content,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    textAlign = TextAlign.Center,
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
        Spacer(Modifier.weight(FrameInset + TitleGap))
    }
}

/** The spine, and on the board the frame: its line and [scrolls], or the line alone if there are none. */
private fun DrawScope.drawBinding(color: Color, shade: Color, scrolls: Path?) {
    val width = size.width
    // The thinnest lines would thin out to nothing on a small cover.
    val hairline = 1.dp.toPx()
    val line = max(width * FrameStroke, hairline)
    val frame = frameOf(size)

    // A book read from the right is bound on the right.
    scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
        val spine = width * Spine
        drawRect(shade.copy(alpha = SpineShade), size = Size(spine, size.height))
        drawLine(shade.copy(alpha = HingeShade), Offset(spine, 0f), Offset(spine, size.height), hairline)

        if (scrolls == null) {
            drawRoundRect(color, frame.topLeft, frame.size, CornerRadius(width * FrameCorner), Stroke(line))
        } else {
            // Each side's line stops short of the corners, where the scrolls are.
            val across = width * ScrollsAcross
            val down = width * ScrollsDown
            drawLine(color, Offset(frame.left + across, frame.top), Offset(frame.right - across, frame.top), line, StrokeCap.Round)
            drawLine(color, Offset(frame.left + across, frame.bottom), Offset(frame.right - across, frame.bottom), line, StrokeCap.Round)
            drawLine(color, Offset(frame.left, frame.top + down), Offset(frame.left, frame.bottom - down), line, StrokeCap.Round)
            drawLine(color, Offset(frame.right, frame.top + down), Offset(frame.right, frame.bottom - down), line, StrokeCap.Round)
            drawPath(
                path = scrolls,
                color = color,
                style = Stroke(max(width * ScrollStroke, hairline), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Where the frame's line runs on a cover of [size]: in from the edges of the board, which the spine is not part of. */
private fun frameOf(size: Size): Rect {
    val inset = size.width * FrameInset
    return Rect(size.width * Spine + inset, inset, size.width - inset, size.height - inset)
}

/** The scrolls in all four corners of [frame], on a cover [width] wide. Every corner is the first one mirrored. */
private fun cornerScrolls(frame: Rect, width: Float): Path = Path().apply {
    for ((x, inwardsX) in listOf(frame.left to 1f, frame.right to -1f)) {
        for ((y, inwardsY) in listOf(frame.top to 1f, frame.bottom to -1f)) {
            // One scroll lies along the line across the top or bottom, the other along the line down the side.
            scroll(AcrossFirst, AcrossSecond) { Offset(x + inwardsX * it.x * width, y + inwardsY * it.y * width) }
            scroll(DownFirst, DownSecond) { Offset(x + inwardsX * it.y * width, y + inwardsY * it.x * width) }
        }
    }
}

/**
 * Adds a scroll: two spirals joined by a stem into an S. They are laid out along a line of the
 * frame, and [place] puts a point of theirs where it belongs on the cover.
 */
private fun Path.scroll(first: Spiral, second: Spiral, place: (Offset) -> Offset) {
    // Both wind the same way, which is what makes an S of them and not a C. The first comes out
    // of its eye to end on the side away from the line, the second to end on the line.
    val unwinding = first.points(endAngle = PI.toFloat() / 2)
    val winding = second.points(endAngle = -PI.toFloat() / 2).asReversed()
    // The stem leaves one spiral and joins the other going the way each is already heading.
    val reach = StemReach * (winding.first() - unwinding.last()).getDistance()
    val leaving = place(unwinding.last() + heading(from = unwinding[unwinding.size - 2], to = unwinding.last()) * reach)
    val joining = place(winding.first() + heading(from = winding[1], to = winding.first()) * reach)

    place(unwinding.first()).let { moveTo(it.x, it.y) }
    unwinding.drop(1).forEach { point -> place(point).let { lineTo(it.x, it.y) } }
    place(winding.first()).let { cubicTo(leaving.x, leaving.y, joining.x, joining.y, it.x, it.y) }
    winding.drop(1).forEach { point -> place(point).let { lineTo(it.x, it.y) } }
}

private fun heading(from: Offset, to: Offset): Offset = (to - from).let { it / it.getDistance() }

/**
 * One spiral of a scroll. Its middle is given as how far it is along the frame's line from the
 * corner ([x]) and how far in from that line ([y]).
 */
private class Spiral(val x: Float, val y: Float, val radius: Float, val turns: Float) {

    /** From the eye outwards, ending on the side of the spiral that [endAngle] points to. */
    fun points(endAngle: Float): List<Offset> {
        val steps = (turns * StepsPerTurn).roundToInt()
        return List(steps + 1) { step ->
            val progress = step.toFloat() / steps
            val angle = endAngle + (1 - progress) * turns * 2 * PI.toFloat()
            val distance = radius * (Eye + (1 - Eye) * progress)
            Offset(x + distance * cos(angle), y + distance * sin(angle))
        }
    }
}

// Measured off the artwork, as fractions of the cover's width.
private const val Spine = 0.075f
private const val FrameInset = 0.09f
private const val FrameStroke = 0.014f
private const val ScrollStroke = 0.0115f

/** How far a corner's scrolls reach along the top or bottom and down the side, before the frame's line takes over. */
private const val ScrollsAcross = 0.17f
private const val ScrollsDown = 0.22f

// The scroll along the top is the larger. The one down the side starts under it with a smaller curl.
private val AcrossFirst = Spiral(x = 0.034f, y = 0.034f, radius = 0.034f, turns = 1.2f)
private val AcrossSecond = Spiral(x = 0.131f, y = 0.033f, radius = 0.03f, turns = 1.2f)
private val DownFirst = Spiral(x = 0.101f, y = 0.021f, radius = 0.021f, turns = 0.85f)
private val DownSecond = Spiral(x = 0.178f, y = 0.034f, radius = 0.034f, turns = 1.2f)

/** How far out from its middle a spiral starts, as a fraction of its radius. */
private const val Eye = 0.3f

/** How far the stem carries on the way a spiral was heading, as a fraction of the stem's length. */
private const val StemReach = 0.45f
private const val StepsPerTurn = 24

/** The corners of the line that goes all the way round a small cover. */
private const val FrameCorner = 0.03f

/** Between the frame and the title, also a fraction of the cover's width. */
private const val TitleGap = 0.03f

/** A narrower cover's scrolls would close up into blots. */
private val ScrollsFromWidth = 80.dp

private const val SpineShade = 0.1f
private const val HingeShade = 0.22f

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
