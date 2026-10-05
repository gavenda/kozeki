package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * One column of a [ColumnChart].
 *
 * @param label Shown under the column; leave empty to skip it when columns are dense.
 * @param valueText The value as the reader should see it, such as "24m".
 */
data class ChartBar(val label: String, val value: Float, val valueText: String)

/** A horizontal line across a [ColumnChart] that the columns are read against, such as a goal. */
data class ChartReference(val value: Float, val label: String)

/**
 * A single-series column chart. Every column is the same colour, since length already carries the
 * value. Only one value is written out: the selected column's, or the tallest when none is
 * selected. Tapping anywhere in a column's slot selects it, so the target is the slot, not the mark.
 *
 * @param contentDescription Read out in place of the drawing; it should carry the values.
 * @param axisText Formats the axis ticks, which are rounded to clean numbers.
 * @param wholeNumbers The values are counts, so a tick is never placed between two integers.
 */
@Composable
fun ColumnChart(
    bars: List<ChartBar>,
    contentDescription: String,
    axisText: (Float) -> String,
    modifier: Modifier = Modifier,
    reference: ChartReference? = null,
    wholeNumbers: Boolean = false,
    height: Dp = 180.dp,
) {
    var selectedIndex by remember(bars) { mutableStateOf<Int?>(null) }
    val currentBars by rememberUpdatedState(bars)

    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = MaterialTheme.colorScheme.tertiary
    val referenceColor = MaterialTheme.colorScheme.onSurfaceVariant

    val top = niceCeiling(maxOf(bars.maxOfOrNull { it.value } ?: 0f, reference?.value ?: 0f))
    val middleIsClean = !wholeNumbers || (top / 2) % 1f == 0f
    val ticks = if (middleIsClean) listOf(0f, top / 2, top) else listOf(0f, top)
    val tickLayouts = ticks.map { textMeasurer.measure(axisText(it), axisStyle) }
    val tickWidth = tickLayouts.maxOfOrNull { it.size.width } ?: 0
    val currentTickWidth by rememberUpdatedState(tickWidth)
    val labelLayouts = bars.map { if (it.label.isEmpty()) null else textMeasurer.measure(it.label, axisStyle) }
    val highlighted = selectedIndex ?: bars.indices.maxByOrNull { bars[it].value }?.takeIf { bars[it].value > 0f }
    val valueLayout = highlighted?.let { textMeasurer.measure(bars[it].valueText, valueStyle) }
    val referenceLayout = reference?.let { textMeasurer.measure(it.label, axisStyle) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val gutter = currentTickWidth + 8.dp.toPx()
                    val count = currentBars.size
                    if (count == 0 || offset.x < gutter) return@detectTapGestures
                    val slot = (size.width - gutter) / count
                    val index = ((offset.x - gutter) / slot).toInt().coerceIn(0, count - 1)
                    selectedIndex = if (selectedIndex == index) null else index
                }
            },
    ) {
        if (bars.isEmpty()) return@Canvas

        val gutter = tickWidth + 8.dp.toPx()
        val labelBand = (labelLayouts.filterNotNull().maxOfOrNull { it.size.height } ?: 0) + 6.dp.toPx()
        val headroom = (valueLayout?.size?.height ?: 0) + 6.dp.toPx()
        val plot = Rect(gutter, headroom, size.width, size.height - labelBand)
        val slot = plot.width / bars.size
        // Capped so wide charts get air between columns instead of fat blocks.
        val barWidth = minOf(24.dp.toPx(), slot - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())
        fun y(value: Float): Float = plot.bottom - (if (top > 0f) value / top else 0f) * plot.height

        ticks.forEachIndexed { index, tick ->
            val lineY = y(tick)
            drawLine(gridColor, Offset(plot.left, lineY), Offset(plot.right, lineY), strokeWidth = 1.dp.toPx())
            val layout = tickLayouts[index]
            drawText(layout, topLeft = Offset(gutter - 8.dp.toPx() - layout.size.width, lineY - layout.size.height / 2f))
        }

        bars.forEachIndexed { index, bar ->
            val centre = plot.left + slot * (index + 0.5f)
            val barTop = y(bar.value)
            if (bar.value > 0f) {
                val radius = minOf(4.dp.toPx(), barWidth / 2, plot.bottom - barTop)
                // Rounded where the data ends, square where it meets the baseline.
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(centre - barWidth / 2, barTop, centre + barWidth / 2, plot.bottom),
                            topLeft = CornerRadius(radius),
                            topRight = CornerRadius(radius),
                        ),
                    )
                }
                val dimmed = selectedIndex != null && selectedIndex != index
                drawPath(path, barColor, alpha = if (dimmed) 0.45f else 1f)
            }
            labelLayouts[index]?.let { layout ->
                drawText(layout, topLeft = Offset(centre - layout.size.width / 2f, plot.bottom + 6.dp.toPx()))
            }
        }

        if (reference != null && referenceLayout != null && reference.value > 0f) {
            val lineY = y(reference.value)
            drawLine(referenceColor, Offset(plot.left, lineY), Offset(plot.right, lineY), strokeWidth = 1.dp.toPx())
            drawText(
                referenceLayout,
                topLeft = Offset(plot.right - referenceLayout.size.width, lineY - referenceLayout.size.height - 2.dp.toPx()),
            )
        }

        if (highlighted != null && valueLayout != null) {
            val centre = plot.left + slot * (highlighted + 0.5f)
            val x = (centre - valueLayout.size.width / 2f)
                .coerceIn(plot.left, (plot.right - valueLayout.size.width).coerceAtLeast(plot.left))
            drawText(valueLayout, topLeft = Offset(x, y(bars[highlighted].value) - valueLayout.size.height - 4.dp.toPx()))
        }
    }
}

/** Rounds up to 1, 2 or 5 times a power of ten, so axis ticks land on clean numbers. */
internal fun niceCeiling(value: Float): Float {
    if (value <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(value.toDouble())))
    val fraction = value / magnitude
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return (nice * magnitude).toFloat().coerceAtLeast(ceil(value))
}

/** One row of [RankedBars]. */
data class RankedBar(val label: String, val value: Float, val valueText: String)

/** Horizontal bars for comparing a handful of named things, each labelled with its value at the tip. */
@Composable
fun RankedBars(
    bars: List<RankedBar>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 104.dp,
) {
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bars.forEach { bar ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(labelWidth),
                )
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (bar.value / max).coerceIn(0f, 1f))
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    )
                }
                Text(bar.valueText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A ratio against a goal, with the figure in the middle. The ring stops at full; the figure does not. */
@Composable
fun GoalRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxSize(),
        )
        Text(label, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

/** A small ring for a strip of days, with [content] in the middle. */
@Composable
fun MiniRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.tertiaryContainer,
            strokeWidth = 3.dp,
            gapSize = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}

/** A headline number with its label. The value keeps the text colour; it is not a data mark. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
        }
    }
}

@PreviewLightDark
@Composable
private fun ColumnChartPreview() {
    AppTheme {
        Surface {
            ColumnChart(
                bars = listOf(12f, 31f, 0f, 24f, 45f, 18f, 9f).mapIndexed { index, minutes ->
                    ChartBar(listOf("M", "T", "W", "T", "F", "S", "S")[index], minutes, "${minutes.toInt()}m")
                },
                contentDescription = "Minutes read per day",
                axisText = { "${it.toInt()}m" },
                reference = ChartReference(20f, "Goal"),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun RankedBarsPreview() {
    AppTheme {
        Surface {
            RankedBars(
                bars = listOf(
                    RankedBar("★★★★★", 7f, "7"),
                    RankedBar("★★★★", 6f, "6"),
                    RankedBar("★★★", 3f, "3"),
                    RankedBar("★★", 1f, "1"),
                    RankedBar("★", 0f, "0"),
                ),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun MetersPreview() {
    AppTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoalRing(progress = 0.82f, label = "82%")
                MiniRing(progress = 0.6f) { Text("27", style = MaterialTheme.typography.labelMedium) }
                StatTile(label = "Time read", value = "9h 20m", modifier = Modifier.fillMaxHeight())
            }
        }
    }
}
