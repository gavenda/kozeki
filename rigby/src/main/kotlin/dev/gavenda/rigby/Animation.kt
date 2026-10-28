// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/animation.js.
//
// Bone timeline values are relative to the setup pose: rotation, translation and shear are added,
// scale is multiplied.
package dev.gavenda.rigby

import kotlin.math.floor

/** Keys with `width` numbers each, sorted by time. Key `i`'s curve leads to key `i + 1`. */
class NumericKeys(val times: DoubleArray, val values: DoubleArray, val width: Int, val curves: Array<Curve>) {
    val size: Int get() = times.size
    val firstTime: Double get() = times[0]

    fun value(key: Int, component: Int): Double = values[key * width + component]

    /** Samples component `c` at `time` (keys must not be empty). */
    fun sample(time: Double, c: Int): Double {
        if (time <= times[0]) return value(0, c)
        val next = partitionPoint(times, time)
        if (next >= size) return value(size - 1, c)
        val a = next - 1
        val span = times[next] - times[a]
        val va = value(a, c)
        return va + (value(next, c) - va) * curves[a].ease(if (span > 0) (time - times[a]) / span else 1.0)
    }

    /**
     * Samples rotation keys (degrees) at `time`, turning the short way between keys: the change
     * from one key to the next is wrapped into [-180, 180], as Spine does. (Plana patch.)
     */
    fun sampleRotation(time: Double): Double {
        if (time <= times[0]) return value(0, 0)
        val next = partitionPoint(times, time)
        if (next >= size) return value(size - 1, 0)
        val a = next - 1
        val span = times[next] - times[a]
        val va = value(a, 0)
        var delta = value(next, 0) - va
        delta -= jsRound(delta / 360) * 360
        return va + delta * curves[a].ease(if (span > 0) (time - times[a]) / span else 1.0)
    }
}

/** JavaScript's Math.round: halves round up, towards +infinity. */
private fun jsRound(v: Double): Double = floor(v + 0.5)

/** Index of the first key after `time` (keys at `time` count as before it). */
internal fun partitionPoint(times: DoubleArray, time: Double): Int {
    var lo = 0
    var hi = times.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (times[mid] <= time) lo = mid + 1 else hi = mid
    }
    return lo
}

/** Index of the last key at or before `time`, or -1. */
internal fun keyIndexAt(times: DoubleArray, time: Double): Int = partitionPoint(times, time) - 1

class BoneTimeline(
    val bone: Int,
    val rotate: NumericKeys?,
    val translate: NumericKeys?,
    val scale: NumericKeys?,
    val shear: NumericKeys?,
)

class SlotTimeline(
    val slot: Int,
    /** Attachment keys: times and the attachment name shown from each (null hides it). */
    val attachmentTimes: DoubleArray?,
    val attachmentNames: Array<String?>?,
    /** RGBA keys. */
    val color: NumericKeys?,
    /** Two-color keys: light RGBA, then dark RGB. */
    val twoColor: NumericKeys?,
)

/**
 * Mesh deformation of one attachment. Key values are deltas added to the setup vertices from
 * `offsets[i]` on.
 */
class DeformTimeline(
    val skin: String?,
    val slot: Int,
    val attachment: String,
    val times: DoubleArray,
    val offsets: IntArray,
    val vertices: Array<DoubleArray>,
    val curves: Array<Curve>,
) {
    internal val deformKey = "$slot/$attachment"
}

/** Draw order keys: the slot order from each key's time, or null for the setup order. */
class DrawOrderTimeline(val times: DoubleArray, val orders: Array<IntArray?>)

class EventKey(
    val time: Double,
    val name: String,
    val int: Int?,
    val float: Double?,
    val string: String?,
    val volume: Double?,
    val balance: Double?,
)

class IkKey(
    val time: Double,
    val mix: Double,
    val softness: Double,
    val bendPositive: Boolean,
    val compress: Boolean,
    val stretch: Boolean,
)

class IkTimeline(val constraint: Int, val keys: List<IkKey>) {
    internal val times = DoubleArray(keys.size) { keys[it].time }
}

/** Keys of a constraint's raw values, eased the way Rigby2D eases them (see `easeBisect`). */
class ConstraintKeys(val times: DoubleArray, val values: DoubleArray, val width: Int, val curves: Array<Curve>) {
    val size: Int get() = times.size

    /** Samples every value at `time` into `out`. */
    fun sample(time: Double, out: DoubleArray) {
        if (time < times[0]) {
            for (j in 0 until width) out[j] = values[j]
            return
        }
        var i = 0
        while (i + 1 < size && times[i + 1] <= time) i++
        val b = i + 1
        if (b >= size || curves[i] == Curve.Stepped) {
            for (j in 0 until width) out[j] = values[i * width + j]
            return
        }
        val t0 = times[i]
        val t1 = times[b]
        if (t1 <= t0) {
            for (j in 0 until width) out[j] = values[b * width + j]
            return
        }
        val u = curves[i].easeBisect((time - t0) / (t1 - t0))
        for (j in 0 until width) {
            val v0 = values[i * width + j]
            out[j] = v0 + (values[b * width + j] - v0) * u
        }
    }
}

/** Mix keys of a transform constraint: rotate, x, y, scaleX, scaleY, shearY. */
class TransformTimeline(val constraint: Int, val keys: ConstraintKeys)

class PathTimeline(
    val constraint: Int,
    val position: ConstraintKeys?,
    val spacing: ConstraintKeys?,
    /** Rotate, x and y mixes. */
    val mix: ConstraintKeys?,
)

class Animation(
    val name: String,
    val duration: Double,
    val bones: List<BoneTimeline>,
    val slots: List<SlotTimeline>,
    val deform: List<DeformTimeline>,
    val drawOrder: DrawOrderTimeline?,
    val events: List<EventKey>,
    val ik: List<IkTimeline>,
    /** Indexed by `SkeletonData.constraints`. */
    val transform: List<TransformTimeline>,
    val path: List<PathTimeline>,
)

/** Slot order for a draw order key: `offsets` move slots relative to their setup index. */
internal fun applyDrawOrder(count: Int, slots: IntArray, offsets: IntArray): IntArray {
    if (slots.isEmpty()) return IntArray(count) { it }
    val order = IntArray(count) { -1 }
    val unchanged = ArrayList<Int>()
    var original = 0
    val sorted = slots.indices.sortedBy { slots[it] }
    for (k in sorted) {
        val slot = slots[k]
        if (slot >= count) continue
        while (original < slot) unchanged.add(original++)
        val at = original + offsets[k]
        if (at in 0 until count && order[at] == -1) order[at] = original else unchanged.add(original)
        original++
    }
    while (original < count) unchanged.add(original++)
    for (i in count - 1 downTo 0) if (order[i] == -1) order[i] = unchanged.removeAt(unchanged.size - 1)
    return order
}
