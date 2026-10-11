package dev.gavenda.kozeki.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.ui.theme.AppTheme

/** A face of Ui, the app's character, to go with what a screen has to say. */
enum class Expression(@param:DrawableRes val portrait: Int) {
    HAPPY(R.drawable.expression_happy),
    CONTENT(R.drawable.expression_content),
    NEUTRAL(R.drawable.expression_neutral),
    CURIOUS(R.drawable.expression_curious),
    STUDIOUS(R.drawable.expression_studious),
    ASLEEP(R.drawable.expression_asleep),
    DOWNCAST(R.drawable.expression_downcast),
    WORRIED(R.drawable.expression_worried),
    DISMAYED(R.drawable.expression_dismayed),
    DUMBFOUNDED(R.drawable.expression_dumbfounded),
    SHOCKED(R.drawable.expression_shocked),
}

/**
 * Ui from the shoulders up, in front of a plate of the theme's colour. The plate
 * cuts her off below, while her head stands clear of it above and her halo rises out of its top, so
 * this is taller than the plate, which is as wide as it is: 128dp unless given another width.
 */
@Composable
fun Portrait(expression: Expression, modifier: Modifier = Modifier) {
    val plate = MaterialShapes.Cookie9Sided.toShape()
    Box(modifier.width(128.dp).aspectRatio(PortraitWidth / PortraitHeight)) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(plate)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Image(
            painter = painterResource(expression.portrait),
            contentDescription = null,
            modifier = Modifier.matchParentSize().clip(remember(plate) { RisingShape(plate) }),
        )
    }
}

/**
 * Something Ui says, in a bubble with a tail on its start side: it goes after her [Portrait] in a
 * row, where the tail, halfway up the bubble, points back at her.
 */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, SpeechBubbleShape)
            .padding(start = BubbleTail + 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
    )
}

/** A rounded box, less a strip on its start side that only the tail crosses. */
private object SpeechBubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { BubbleTail.toPx() }
        val corner = with(density) { BubbleCorner.toPx() }
        val rtl = layoutDirection == LayoutDirection.Rtl
        // Where the tail meets the box, and where it comes to its point.
        val root = if (rtl) size.width - tail else tail
        val tip = if (rtl) size.width else 0f
        val middle = size.height / 2
        val box = Path().apply {
            val bounds = if (rtl) Rect(0f, 0f, root, size.height) else Rect(root, 0f, size.width, size.height)
            addRoundRect(RoundRect(bounds, CornerRadius(corner)))
        }
        val point = Path().apply {
            moveTo(root, middle - tail)
            lineTo(tip, middle)
            lineTo(root, middle + tail)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, box, point))
    }
}

private val BubbleTail = 8.dp
private val BubbleCorner = 20.dp

/** [plate] across the foot of the space, and all of the space above the middle of it. */
private class RisingShape(private val plate: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = size.width
        val top = size.height - side
        val foot = Path().apply {
            addOutline(plate.createOutline(Size(side, side), layoutDirection, density))
            translate(Offset(0f, top))
        }
        val above = Path().apply { addRect(Rect(0f, 0f, side, top + side / 2)) }
        return Outline.Generic(Path.combine(PathOperation.Union, foot, above))
    }
}

// The size of the artwork the portraits are cut to, in pixels: the plate is the square at its foot.
private const val PortraitWidth = 432f
private const val PortraitHeight = 546f

/**
 * Both themes at once, so the portraits can be judged in one look on a device, which shows a
 * preview only in the theme it is in.
 */
@Preview
@Composable
private fun PortraitPreview() {
    Column {
        listOf(false, true).forEach { dark ->
            AppTheme(isDark = dark) {
                Surface {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Expression.entries.forEach { Portrait(it, Modifier.width(88.dp)) }
                    }
                }
            }
        }
    }
}
