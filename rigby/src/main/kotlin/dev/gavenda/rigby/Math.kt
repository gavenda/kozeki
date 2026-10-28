// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/math.js.
//
// Y points up and rotations are in degrees, counter-clockwise. Shear is in degrees and tilts the
// X and Y axes independently (0 = no shear).
package dev.gavenda.rigby

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val DEG = PI / 180

internal fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

/** Wraps an angle in degrees to (-180, 180]. */
internal fun wrapDegrees(degrees: Double): Double {
    val d = (((degrees + 180) % 360) + 360) % 360 - 180
    return if (d == -180.0) 180.0 else d
}

/** A bone's local transform. */
class Transform(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var rotation: Double = 0.0,
    var scaleX: Double = 1.0,
    var scaleY: Double = 1.0,
    var shearX: Double = 0.0,
    var shearY: Double = 0.0,
) {
    fun copy(): Transform = Transform(x, y, rotation, scaleX, scaleY, shearX, shearY)

    fun set(o: Transform): Transform {
        x = o.x
        y = o.y
        rotation = o.rotation
        scaleX = o.scaleX
        scaleY = o.scaleY
        shearX = o.shearX
        shearY = o.shearY
        return this
    }

    /** The X axis points along rotation + shearX; the Y axis along rotation + 90 + shearY. */
    fun toAffine(out: Affine = Affine()): Affine {
        val rx = (rotation + shearX) * DEG
        val ry = (rotation + 90 + shearY) * DEG
        return out.set(cos(rx) * scaleX, sin(rx) * scaleX, cos(ry) * scaleY, sin(ry) * scaleY, x, y)
    }
}

/** 2D affine matrix mapping (x, y) to (a*x + c*y + tx, b*x + d*y + ty). */
class Affine(
    @JvmField var a: Double = 1.0,
    @JvmField var b: Double = 0.0,
    @JvmField var c: Double = 0.0,
    @JvmField var d: Double = 1.0,
    @JvmField var tx: Double = 0.0,
    @JvmField var ty: Double = 0.0,
) {
    fun copy(): Affine = Affine(a, b, c, d, tx, ty)

    fun set(a: Double, b: Double, c: Double, d: Double, tx: Double, ty: Double): Affine {
        this.a = a
        this.b = b
        this.c = c
        this.d = d
        this.tx = tx
        this.ty = ty
        return this
    }

    /** `this * r`: applies `r` first, then `this`. */
    operator fun times(r: Affine): Affine = multiply(this, r, Affine())

    fun determinant(): Double = a * d - b * c

    fun inverse(): Affine {
        val det = determinant()
        if (abs(det) < 1e-12) return Affine()
        val inv = 1 / det
        val na = d * inv
        val nb = -b * inv
        val nc = -c * inv
        val nd = a * inv
        return Affine(na, nb, nc, nd, -(na * tx + nc * ty), -(nb * tx + nd * ty))
    }

    fun applyX(x: Double, y: Double): Double = a * x + c * y + tx

    fun applyY(x: Double, y: Double): Double = b * x + d * y + ty

    /** World rotation of the X axis, in degrees. */
    fun rotation(): Double = atan2(b, a) / DEG

    /**
     * Decomposes into translation, rotation, scale and Y shear (shearX is always 0). Reflection is
     * expressed as a negative Y scale.
     */
    fun decompose(): Transform {
        val sx = hypot(a, b)
        if (sx < 1e-12) return Transform(tx, ty, 0.0, 0.0, 0.0)
        val rotation = rotation()
        val sy = hypot(c, d) * if (determinant() < 0) -1 else 1
        val yAngle = if (sy == 0.0) rotation + 90 else atan2(d / sy, c / sy) / DEG
        val shearY = wrapDegrees(yAngle - rotation - 90)
        return Transform(tx, ty, rotation, sx, sy, 0.0, if (abs(shearY) < 1e-9) 0.0 else shearY)
    }

    companion object {
        /** Writes `l * r` into `out` (which may be `l` or `r`) and returns it. */
        fun multiply(l: Affine, r: Affine, out: Affine): Affine = out.set(
            l.a * r.a + l.c * r.b,
            l.b * r.a + l.d * r.b,
            l.a * r.c + l.c * r.d,
            l.b * r.c + l.d * r.d,
            l.a * r.tx + l.c * r.ty + l.tx,
            l.b * r.tx + l.d * r.ty + l.ty,
        )
    }
}
