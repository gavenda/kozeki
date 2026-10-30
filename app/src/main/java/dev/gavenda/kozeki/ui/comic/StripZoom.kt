package dev.gavenda.kozeki.ui.comic

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How far the strip is zoomed. The list stays as wide as the screen and is drawn larger; it goes on
 * scrolling up and down by itself, so only the sideways place is kept here. A point of the list is on
 * screen at `offsetX + scale * x`, `scale * y`.
 */
@Stable
internal class StripZoom(private val list: LazyListState) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    private var width = 0f

    val zoomed: Boolean get() = scale > 1f

    /**
     * Whether the finger has moved since it went down. A sideways drag is not taken by the list, so
     * nothing else tells a tap or a long press that it has become a drag.
     */
    var moved = false
        private set

    /** Where [screen] is in the list. */
    fun toList(screen: Offset): Offset = Offset((screen.x - offsetX) / scale, screen.y / scale)

    /**
     * Zooms by [zoomChange] about [centroid], where the fingers are now, after they moved by [pan].
     * What was under them stays under them.
     */
    fun transform(zoomChange: Float, pan: Offset, centroid: Offset) {
        val old = scale
        val new = (old * zoomChange).coerceIn(1f, MAX_SCALE)
        val before = toList(centroid - pan)
        offsetX = clampX(centroid.x - new * before.x, new)
        scale = new
        list.dispatchRawDelta(before.y - centroid.y / new)
    }

    fun panX(dx: Float) {
        offsetX = clampX(offsetX + dx, scale)
    }

    /** Double tap: in to [DOUBLE_TAP_SCALE] at [focus], or back out. */
    suspend fun toggle(focus: Offset) {
        val target = if (zoomed) 1f else DOUBLE_TAP_SCALE
        animate(scale, target) { value, _ -> transform(value / scale, Offset.Zero, focus) }
    }

    private fun clampX(x: Float, scale: Float): Float = x.coerceIn(width * (1 - scale), 0f)

    /** Draws the list zoomed. */
    fun Modifier.zoomedLayer(): Modifier = graphicsLayer {
        width = size.width
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = scale
        scaleY = scale
        translationX = offsetX
    }

    /**
     * Pinches, read before the list sees them so it does not scroll with two fingers, and sideways
     * drags while zoomed, which the list has no use for. Goes on something that is not zoomed itself.
     */
    fun Modifier.pinchToZoom(): Modifier = pointerInput(this@StripZoom) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            moved = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!moved) {
                    moved = event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
                }
                if (event.changes.count { it.pressed } >= 2) {
                    transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid())
                    event.changes.forEach { it.consume() }
                } else if (zoomed) {
                    panX(event.calculatePan().x)
                }
            } while (event.changes.any { it.pressed })
        }
    }

    private companion object {
        const val MAX_SCALE = 4f
        const val DOUBLE_TAP_SCALE = 2f
    }
}
