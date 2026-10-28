// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/curve.js.
package dev.gavenda.rigby

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interpolation from one key to the next. Bezier control points are normalized: the endpoints are
 * (0,0) and (1,1), x is the fraction of the time between the two keys and y the fraction of the
 * value change. Spine 3.8 has one curve per key, shared by every value of the key.
 */
sealed interface Curve {
    data object Linear : Curve

    /** Holds the value until the next key. */
    data object Stepped : Curve

    data class Bezier(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : Curve
}

private fun clamp01(t: Double) = min(1.0, max(0.0, t))

/** Maps linear progress `t` in [0, 1] to eased progress. */
internal fun Curve.ease(t: Double): Double {
    val p = clamp01(t)
    return when (this) {
        Curve.Linear -> p
        Curve.Stepped -> 0.0
        is Curve.Bezier -> bezierYForX(x1, y1, x2, y2, p)
    }
}

private fun cubic(p1: Double, p2: Double, t: Double): Double {
    val u = 1 - t
    return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t
}

private fun cubicDerivative(p1: Double, p2: Double, t: Double): Double {
    val u = 1 - t
    return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2)
}

private fun bezierYForX(x1In: Double, y1: Double, x2In: Double, y2: Double, x: Double): Double {
    val x1 = clamp01(x1In)
    val x2 = clamp01(x2In)
    // Newton iterations, falling back to bisection when the slope is too flat.
    var t = x
    for (i in 0 until 8) {
        val err = cubic(x1, x2, t) - x
        if (abs(err) < 1e-5) return cubic(y1, y2, t)
        val d = cubicDerivative(x1, x2, t)
        if (abs(d) < 1e-6) break
        t = clamp01(t - err / d)
    }
    var lo = 0.0
    var hi = 1.0
    t = x
    for (i in 0 until 30) {
        val v = cubic(x1, x2, t)
        if (abs(v - x) < 1e-5) break
        if (v < x) lo = t else hi = t
        t = (lo + hi) * 0.5
    }
    return cubic(y1, y2, t)
}

private fun cubic4(p0: Double, p1: Double, p2: Double, p3: Double, s: Double): Double {
    val u = 1 - s
    return u * u * u * p0 + 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s * p3
}

/**
 * The eased progress Rigby2D's constraint timelines use: plain bisection on the bezier (no Newton
 * steps), so a transform or path constraint key eases a little differently from a bone key.
 */
internal fun Curve.easeBisect(x: Double): Double = when (this) {
    Curve.Linear -> x
    Curve.Stepped -> 0.0
    is Curve.Bezier -> {
        var lo = 0.0
        var hi = 1.0
        var s = x
        for (k in 0 until 40) {
            val v = cubic4(0.0, x1, x2, 1.0, s)
            if (abs(v - x) < 1e-7) break
            if (v < x) lo = s else hi = s
            s = (lo + hi) / 2
        }
        cubic4(0.0, y1, y2, 1.0, s)
    }
}
