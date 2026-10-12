package dev.gavenda.kozeki.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A scrollbar down the end edge of the grid of [state] that can be taken hold of. It comes up while
 * the grid moves and fades once it is left alone; while it shows, dragging its thumb goes through
 * the whole grid in the length of the track. Fill the height the thumb may travel with it.
 */
@Composable
fun DraggableScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    val indicator = state.scrollIndicatorState ?: return
    // Where the finger holds the thumb, as a fraction of the way down. The size of what is not on
    // screen is only estimated, so while it is held the thumb follows the finger, not the estimate.
    var held by remember { mutableStateOf<Float?>(null) }
    var travel by remember { mutableFloatStateOf(0f) }
    val dragging = held != null

    val active = state.isScrollInProgress || dragging
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            alpha.snapTo(1f)
        } else {
            delay(FadeDelayMillis.milliseconds)
            alpha.animateTo(0f, tween(FadeMillis))
        }
    }
    // Out of sight it is out of the way too: the edge of the books under it takes the touches.
    val shown by remember { derivedStateOf { alpha.value > 0f && indicator.contentSize > indicator.viewportSize } }

    val thickness by animateDpAsState(if (dragging) 8.dp else 4.dp)
    val color by animateColorAsState(
        if (dragging) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
    )

    val drag = rememberDraggableState { delta ->
        if (travel <= 0f) return@rememberDraggableState
        val to = ((held ?: indicator.fraction()) + delta / travel).coerceIn(0f, 1f)
        held = to
        val range = indicator.contentSize - indicator.viewportSize
        // Aimed anew from where the grid says it is each time, so a changing estimate is caught up
        // with. At either end it overshoots, which the grid stops at its real first or last row.
        val target = when (to) {
            0f -> -indicator.viewportSize.toFloat()
            1f -> range + indicator.viewportSize.toFloat()
            else -> to * range
        }
        state.dispatchRawDelta(target - indicator.scrollOffset)
    }

    Box(
        modifier
            .layout { measurable, constraints ->
                val track = constraints.maxHeight
                val width = TouchWidth.roundToPx()
                val content = indicator.contentSize
                val length = if (content > 0) {
                    (track.toFloat() * indicator.viewportSize / content).roundToInt()
                        .coerceIn(ThumbMinLength.roundToPx().coerceAtMost(track), track)
                } else {
                    track
                }
                travel = (track - length).toFloat()
                val thumb = measurable.measure(Constraints.fixed(width, length))
                layout(width, track) {
                    thumb.place(0, ((held ?: indicator.fraction()) * travel).roundToInt())
                }
            }
            .systemGestureExclusion()
            .draggable(
                state = drag,
                orientation = Orientation.Vertical,
                enabled = shown,
                startDragImmediately = true,
                onDragStarted = { held = indicator.fraction() },
                onDragStopped = {
                    val at = held
                    held = null
                    val last = state.layoutInfo.totalItemsCount - 1
                    if (at == 0f) state.scrollToItem(0) else if (at == 1f && last >= 0) state.scrollToItem(last)
                },
            )
            .drawBehind {
                if (indicator.contentSize <= indicator.viewportSize || alpha.value == 0f) return@drawBehind
                val wide = thickness.toPx()
                val inset = EdgeInset.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        x = if (layoutDirection == LayoutDirection.Ltr) size.width - wide - inset else inset,
                        y = 0f,
                    ),
                    size = Size(wide, size.height),
                    cornerRadius = CornerRadius(wide / 2),
                    alpha = alpha.value,
                )
            },
    )
}

/** How far down the content the viewport is, from none of the way to all of it. */
private fun ScrollIndicatorState.fraction(): Float {
    val range = contentSize - viewportSize
    return if (range <= 0) 0f else (scrollOffset.toFloat() / range).coerceIn(0f, 1f)
}

/** Wider than the thumb is drawn, so that a finger finds it. */
private val TouchWidth = 28.dp
private val ThumbMinLength = 48.dp
private val EdgeInset = 2.dp
private const val FadeDelayMillis = 1500L
private const val FadeMillis = 300
