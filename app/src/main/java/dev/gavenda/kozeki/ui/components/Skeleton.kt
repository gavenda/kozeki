package dev.gavenda.kozeki.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * Stands in for something still loading: a block of the theme's container colour with a soft band
 * of light sweeping across it. The band's place comes from the frame clock, so every skeleton on
 * screen sweeps in step however late it appeared.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameNanos { now -> phase = (now % SWEEP_NANOS).toFloat() / SWEEP_NANOS }
        }
    }
    Box(
        modifier.drawBehind {
            drawRect(base)
            // A band as wide as the block, from wholly off its left edge to wholly off its right.
            val band = size.width
            val start = -band + (size.width + band) * phase
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + band, size.height * 0.25f),
                ),
            )
        },
    )
}

private const val SWEEP_NANOS = 1_300_000_000L

@PreviewLightDark
@Composable
private fun SkeletonPreview() {
    AppTheme {
        Surface {
            Skeleton(Modifier.size(width = 112.dp, height = 168.dp))
        }
    }
}
