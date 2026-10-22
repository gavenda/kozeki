package dev.gavenda.kozeki.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.floor
import kotlin.math.max

/**
 * The halo that is the app's mark: two rings tied together at the four points of the compass,
 * around a dial with its hands at twelve and three.
 *
 * It is drawn rather than loaded as an image, so it stays sharp at any size, and it fills the size
 * it is given. Its colour is its own, the artwork's, whatever the theme: it is the one thing in the
 * app that does not take its colour from the theme.
 *
 * @param glow Lights the halo up as it is in the artwork, which only shows against something dark.
 * @param dial Left out when something else sits inside the rings.
 */
@Composable
fun HaloMark(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    dial: Boolean = true,
) {
    Halo(modifier, glow, dial, ringDegrees = { 0f }, firstHandDegrees = { 0f }, secondHandDegrees = { 90f })
}

/**
 * The halo at work, shown while something loads. It stands in for the stock loading indicator and
 * is the same 48dp unless given another size.
 *
 * While [progress] is null, or has no value yet, it is the plain wait, run like clockwork: the
 * rings drift one way while the hands go round the other at an even pace, the second five times as
 * fast as the first, so that it laps it once with each quarter turn of the first.
 *
 * With a [progress], from 0 to 1, the first hand goes round once as the work is done and the dial
 * fills behind it from twelve. The second hand keeps lapping it, easing out of each lap and into
 * the next and coming back to a quarter turn ahead, so that the halo still looks at work while the
 * progress stalls. The rings drift as in the plain wait.
 */
@Composable
fun HaloLoadingIndicator(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    progress: (() -> Float?)? = null,
) {
    val known = progress?.invoke()?.coerceIn(0f, 1f)
    val shown = animateFloatAsState(known ?: 0f, label = "progress")
    val transition = rememberInfiniteTransition(label = "halo")
    val quarterTurns = transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(4 * QuarterTurnMillis, easing = LinearEasing)),
        label = "quarter turns",
    )
    // Read where the halo is drawn, so each frame redraws it without recomposing anything.
    Halo(
        modifier = modifier.progressSemantics().size(48.dp),
        glow = glow,
        dial = true,
        ringDegrees = { -90f * quarterTurns.value },
        firstHandDegrees = { if (known != null) 360f * shown.value else 90f * quarterTurns.value },
        secondHandDegrees = {
            if (known != null) 360f * shown.value + 90f + 360f * lapProgress(quarterTurns.value)
            else 90f + 450f * quarterTurns.value
        },
        filledDegrees = { if (known != null) 360f * shown.value else 0f },
    )
}

/** How far the second hand is through its lap of the first, with progress. It starts and stops gently. */
private fun lapProgress(quarterTurns: Float): Float =
    FastOutSlowInEasing.transform(quarterTurns - floor(quarterTurns))

/**
 * The angles are in degrees clockwise, the hands' from twelve o'clock. [filledDegrees] is how far
 * clockwise from twelve the dial is filled.
 */
@Composable
private fun Halo(
    modifier: Modifier,
    glow: Boolean,
    dial: Boolean,
    ringDegrees: () -> Float,
    firstHandDegrees: () -> Float,
    secondHandDegrees: () -> Float,
    filledDegrees: () -> Float = { 0f },
) {
    val color = colorResource(R.color.halo)
    Box(modifier) {
        if (glow) {
            Canvas(
                Modifier
                    .matchParentSize()
                    // Not clipped, so the light spills past the halo's own edge.
                    .graphicsLayer {
                        val spread = size.minDimension * GlowSpread
                        renderEffect = BlurEffect(spread, spread, TileMode.Decal)
                    },
            ) {
                drawHalo(color.copy(alpha = GlowAlpha), dial, ringDegrees(), firstHandDegrees(), secondHandDegrees(), filledDegrees())
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawHalo(color, dial, ringDegrees(), firstHandDegrees(), secondHandDegrees(), filledDegrees())
        }
    }
}

private fun DrawScope.drawHalo(
    color: Color,
    dial: Boolean,
    ringDegrees: Float,
    firstHandDegrees: Float,
    secondHandDegrees: Float,
    filledDegrees: Float,
) {
    val radius = size.minDimension / 2
    // The thinnest lines would thin out to nothing on a small halo.
    val hairline = 1.dp.toPx()
    fun width(fraction: Float) = max(radius * fraction, hairline)
    fun spoke(degrees: Float, from: Float, to: Float, stroke: Float) = rotate(degrees) {
        drawLine(color, center - Offset(0f, radius * from), center - Offset(0f, radius * to), width(stroke))
    }

    drawCircle(color, radius * OuterRing, style = Stroke(width(OuterRingStroke)))
    drawCircle(color, radius * InnerRing, style = Stroke(width(InnerRingStroke)))
    repeat(4) { spoke(ringDegrees + 90f * it, InnerRing, OuterRing, SpokeStroke) }
    if (!dial) return

    if (filledDegrees > 0f) {
        drawArc(
            color = color.copy(alpha = color.alpha * FillAlpha),
            startAngle = -90f,
            sweepAngle = filledDegrees,
            useCenter = true,
            topLeft = center - Offset(radius * Dial, radius * Dial),
            size = Size(2 * radius * Dial, 2 * radius * Dial),
        )
    }
    drawCircle(color, radius * Dial, style = Stroke(width(DialStroke)))
    drawCircle(color, radius * Hub, style = Stroke(width(HubStroke)))
    spoke(firstHandDegrees, Hub, Dial, SpokeStroke)
    spoke(secondHandDegrees, Hub, Dial, SpokeStroke)
}

// Measured off the artwork, as fractions of the halo's radius. A ring's is to the middle of its line.
private const val OuterRing = 0.954f
private const val OuterRingStroke = 0.092f
private const val InnerRing = 0.805f
private const val InnerRingStroke = 0.038f
private const val Dial = 0.546f
private const val DialStroke = 0.05f
private const val Hub = 0.103f
private const val HubStroke = 0.053f

/** The ties between the two rings and the hands of the dial are the same width. */
private const val SpokeStroke = 0.061f

private const val GlowSpread = 0.06f
private const val GlowAlpha = 0.7f

/** How strongly the dial is filled as progress is made. */
private const val FillAlpha = 0.28f

private const val QuarterTurnMillis = 1400

/**
 * Both themes at once, so the halo can be judged in one look on a device, which shows a preview
 * only in the theme it is in.
 */
@Preview
@Composable
private fun HaloPreview() {
    Column {
        listOf(false, true).forEach { dark ->
            AppTheme(isDark = dark) {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(18, 24, 48, 96, 112).forEach { HaloMark(Modifier.size(it.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            HaloLoadingIndicator()
                            HaloLoadingIndicator(Modifier.size(32.dp))
                            HaloLoadingIndicator(progress = { 0.6f })
                            HaloMark(Modifier.size(96.dp), dial = false)
                            // As over a cover whose details are being looked up.
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HaloLoadingIndicator(glow = true)
                                HaloLoadingIndicator(Modifier.size(32.dp), glow = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
