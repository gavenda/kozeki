// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/constraints.js: transform and path constraints, solved on a pose's
// world transforms. Spine 3.8 maps each transform property onto itself; the 4.x property
// mapping, physics and slider constraints are not ported.
package dev.gavenda.rigby

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

private const val EPSILON = 1e-5

private fun axisAngle(x: Double, y: Double) = atan2(y, x) / DEG

/** Rotates the linear part of `m` by `deg` degrees. */
private fun rotateLinear(m: Affine, deg: Double) {
    if (deg == 0.0) return
    val cos = cos(deg * DEG)
    val sin = sin(deg * DEG)
    val a = m.a
    val b = m.b
    val c = m.c
    val d = m.d
    m.a = cos * a - sin * b
    m.b = sin * a + cos * b
    m.c = cos * c - sin * d
    m.d = sin * c + cos * d
}

/** Target values of rotate, x, y, scaleX, scaleY and shearY, offsets included. */
private fun targetValues(pose: Pose, c: TransformConstraintData): DoubleArray {
    val v = DoubleArray(6)
    if (c.local) {
        val t = pose.applied[c.source]
        v[ROTATE] = t.rotation
        v[X] = t.x
        v[Y] = t.y
        v[SCALE_X] = t.scaleX
        v[SCALE_Y] = t.scaleY
        v[SHEAR_Y] = t.shearY
    } else {
        val s = pose.world[c.source]
        val xAngle = axisAngle(s.a, s.b)
        v[ROTATE] = xAngle
        v[X] = s.applyX(c.offsets[X], c.offsets[Y])
        v[Y] = s.applyY(c.offsets[X], c.offsets[Y])
        v[SCALE_X] = hypot(s.a, s.b)
        v[SCALE_Y] = hypot(s.c, s.d)
        v[SHEAR_Y] = wrapDegrees(axisAngle(s.c, s.d) - xAngle - 90)
    }
    val reflected = !c.local && pose.world[c.source].determinant() < 0
    for (p in 0 until 6) {
        // World x/y offsets are already applied through the source bone.
        var off = if (!c.local && (p == X || p == Y)) 0.0 else c.offsets[p]
        if (reflected && (p == ROTATE || p == SHEAR_Y)) off = -off
        v[p] += off
    }
    return v
}

internal fun solveTransform(pose: Pose, c: TransformConstraintData, state: ConstraintState) {
    val mix = state.mix
    if (mix.all { it == 0.0 }) return
    val v = targetValues(pose, c)
    if (c.local) {
        for (b in c.bones) {
            val t = pose.applied[b]
            if (c.relative) {
                if (mix[ROTATE] != 0.0) t.rotation += v[ROTATE] * mix[ROTATE]
                if (mix[X] != 0.0) t.x += v[X] * mix[X]
                if (mix[Y] != 0.0) t.y += v[Y] * mix[Y]
                if (mix[SCALE_X] != 0.0) t.scaleX *= (v[SCALE_X] - 1) * mix[SCALE_X] + 1
                if (mix[SCALE_Y] != 0.0) t.scaleY *= (v[SCALE_Y] - 1) * mix[SCALE_Y] + 1
                if (mix[SHEAR_Y] != 0.0) t.shearY += v[SHEAR_Y] * mix[SHEAR_Y]
            } else {
                if (mix[ROTATE] != 0.0) t.rotation += wrapDegrees(v[ROTATE] - t.rotation) * mix[ROTATE]
                if (mix[X] != 0.0) t.x += (v[X] - t.x) * mix[X]
                if (mix[Y] != 0.0) t.y += (v[Y] - t.y) * mix[Y]
                if (mix[SCALE_X] != 0.0) t.scaleX += (v[SCALE_X] - t.scaleX) * mix[SCALE_X]
                if (mix[SCALE_Y] != 0.0) t.scaleY += (v[SCALE_Y] - t.scaleY) * mix[SCALE_Y]
                if (mix[SHEAR_Y] != 0.0) t.shearY += wrapDegrees(v[SHEAR_Y] - t.shearY) * mix[SHEAR_Y]
            }
        }
        for (b in c.bones) pose.updateBranch(b)
        return
    }
    val desired = LinkedHashMap<Int, Affine>()
    for (b in c.bones) {
        val m = pose.world[b].copy()
        if (mix[ROTATE] != 0.0) {
            val r = if (c.relative) v[ROTATE] else wrapDegrees(v[ROTATE] - axisAngle(m.a, m.b))
            rotateLinear(m, r * mix[ROTATE])
        }
        if (mix[X] != 0.0) m.tx += if (c.relative) v[X] * mix[X] else (v[X] - m.tx) * mix[X]
        if (mix[Y] != 0.0) m.ty += if (c.relative) v[Y] * mix[Y] else (v[Y] - m.ty) * mix[Y]
        if (mix[SCALE_X] != 0.0) {
            val s = hypot(m.a, m.b)
            if (s >= EPSILON) {
                val f = if (c.relative) (v[SCALE_X] - 1) * mix[SCALE_X] + 1 else (s + (v[SCALE_X] - s) * mix[SCALE_X]) / s
                m.a *= f
                m.b *= f
            }
        }
        if (mix[SCALE_Y] != 0.0) {
            val s = hypot(m.c, m.d)
            if (s >= EPSILON) {
                val f = if (c.relative) (v[SCALE_Y] - 1) * mix[SCALE_Y] + 1 else (s + (v[SCALE_Y] - s) * mix[SCALE_Y]) / s
                m.c *= f
                m.d *= f
            }
        }
        if (mix[SHEAR_Y] != 0.0) {
            val xAngle = axisAngle(m.a, m.b)
            val gap = wrapDegrees(axisAngle(m.c, m.d) - xAngle)
            val want = if (c.relative) gap + v[SHEAR_Y] * mix[SHEAR_Y] else gap + wrapDegrees(90 + v[SHEAR_Y] - gap) * mix[SHEAR_Y]
            val len = hypot(m.c, m.d)
            m.c = cos((xAngle + want) * DEG) * len
            m.d = sin((xAngle + want) * DEG) * len
        }
        desired[b] = m
    }
    pose.setWorlds(desired)
}

internal fun solvePath(pose: Pose, c: PathConstraintData, state: ConstraintState) {
    val mixRotate = state.mixRotate
    val mixX = state.mixX
    val mixY = state.mixY
    if (mixRotate == 0.0 && mixX == 0.0 && mixY == 0.0) return
    val att = pose.slotAttachment(c.slot) as? PathAttachment ?: return
    val verts = DoubleArray(pose.worldVertexLength(c.slot, att))
    pose.worldVertices(c.slot, att, verts)
    val path = Path.build(verts, att) ?: return

    val data = pose.data
    val n = c.bones.size
    val tangents = c.rotateMode == RotateMode.Tangent
    val chainScale = c.rotateMode == RotateMode.ChainScale
    val count = if (tangents) n else n + 1
    val lengths = DoubleArray(n) { data.bones[c.bones[it]].length * hypot(pose.world[c.bones[it]].a, pose.world[c.bones[it]].b) }
    val spaces = DoubleArray(count)
    val percentSpacing = c.spacingMode == SpacingMode.Percent
    for (i in 0 until count - 1) {
        val len = lengths[i]
        spaces[i + 1] = when (c.spacingMode) {
            SpacingMode.Percent, SpacingMode.Fixed -> state.spacing
            SpacingMode.Length -> if (len < EPSILON) 0.0 else len + state.spacing
        }
    }
    var position = state.position
    if (c.positionMode == PositionMode.Percent) position *= path.length
    val points = ArrayList<DoubleArray>(count)
    var p = position
    for (i in 0 until count) {
        p += if (percentSpacing) spaces[i] * path.length else spaces[i]
        points += path.at(p)
    }

    val desired = LinkedHashMap<Int, Affine>()
    for (i in 0 until n) {
        val b = c.bones[i]
        val m = pose.world[b].copy()
        val (x, y, angle) = points[i].let { Triple(it[0], it[1], it[2]) }
        m.tx += (x - m.tx) * mixX
        m.ty += (y - m.ty) * mixY
        if (mixRotate > 0) {
            var r: Double
            if (tangents) {
                r = angle
            } else {
                val next = points[i + 1]
                val dx = next[0] - m.tx
                val dy = next[1] - m.ty
                if (chainScale && lengths[i] >= EPSILON) {
                    val s = (hypot(dx, dy) / lengths[i] - 1) * mixRotate + 1
                    m.a *= s
                    m.b *= s
                }
                r = if (hypot(dx, dy) > EPSILON) axisAngle(dx, dy) else angle
            }
            val reflect = if (m.determinant() < 0) -1 else 1
            r = wrapDegrees(r - axisAngle(m.a, m.b) + c.offsetRotation * reflect)
            rotateLinear(m, r * mixRotate)
        }
        desired[b] = m
    }
    pose.setWorlds(desired)
}

private fun cubic(p0: Double, p1: Double, p2: Double, p3: Double, s: Double): Double {
    val u = 1 - s
    return u * u * u * p0 + 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s * p3
}

/** A path from world vertices laid out as in-handle, point, out-handle per path point. */
private class Path(
    val curves: List<DoubleArray>,
    val tables: List<DoubleArray>,
    val ends: DoubleArray,
    val length: Double,
    val closed: Boolean,
    val constantSpeed: Boolean,
) {
    private fun evaluate(i: Int, u: Double): DoubleArray {
        val cv = curves[i]
        val x = cubic(cv[0], cv[2], cv[4], cv[6], u)
        val y = cubic(cv[1], cv[3], cv[5], cv[7], u)
        fun d(p0: Double, p1: Double, p2: Double, p3: Double) =
            3 * (1 - u) * (1 - u) * (p1 - p0) + 6 * (1 - u) * u * (p2 - p1) + 3 * u * u * (p3 - p2)
        var dx = d(cv[0], cv[2], cv[4], cv[6])
        var dy = d(cv[1], cv[3], cv[5], cv[7])
        if (hypot(dx, dy) < EPSILON) {
            dx = cv[6] - cv[0]
            dy = cv[7] - cv[1]
        }
        return doubleArrayOf(x, y, axisAngle(dx, dy))
    }

    /** Point and tangent (degrees) at `distance` along the path. */
    fun at(distance: Double): DoubleArray {
        var dist = distance
        if (closed) {
            dist %= length
            if (dist < 0) dist += length
        } else if (dist < 0) {
            val cv = curves[0]
            val ang = axisAngle(cv[2] - cv[0], cv[3] - cv[1])
            return doubleArrayOf(cv[0] + cos(ang * DEG) * dist, cv[1] + sin(ang * DEG) * dist, ang)
        } else if (dist > length) {
            val cv = curves.last()
            val ang = axisAngle(cv[6] - cv[4], cv[7] - cv[5])
            val over = dist - length
            return doubleArrayOf(cv[6] + cos(ang * DEG) * over, cv[7] + sin(ang * DEG) * over, ang)
        }
        var i = 0
        while (i < ends.size - 1 && dist > ends[i]) i++
        val start = if (i > 0) ends[i - 1] else 0.0
        val span = ends[i] - start
        val f = if (span > EPSILON) (dist - start) / span else 0.0
        if (!constantSpeed) return evaluate(i, f)
        // Constant speed: invert the arc length table.
        val t = tables[i]
        val target = f * t[SEGMENTS]
        var s = 1
        while (s < SEGMENTS && t[s] < target) s++
        val segLen = t[s] - t[s - 1]
        val u = (s - 1 + if (segLen > EPSILON) (target - t[s - 1]) / segLen else 0.0) / SEGMENTS
        return evaluate(i, u)
    }

    companion object {
        const val SEGMENTS = 16

        fun build(v: DoubleArray, att: PathAttachment): Path? {
            val pointCount = floor(v.size / 6.0).toInt()
            if (pointCount < 2 && !(att.closed && pointCount >= 1)) return null
            val curveCount = if (att.closed) pointCount else pointCount - 1
            val curves = (0 until curveCount).map { i ->
                val o = 6 * i
                val next = if (att.closed && i == pointCount - 1) 0 else o + 6
                doubleArrayOf(v[o + 2], v[o + 3], v[o + 4], v[o + 5], v[next], v[next + 1], v[next + 2], v[next + 3])
            }
            // Cumulative arc length table per curve.
            val tables = curves.map { cv ->
                val t = DoubleArray(SEGMENTS + 1)
                var px = cv[0]
                var py = cv[1]
                for (s in 1..SEGMENTS) {
                    val u = s.toDouble() / SEGMENTS
                    val x = cubic(cv[0], cv[2], cv[4], cv[6], u)
                    val y = cubic(cv[1], cv[3], cv[5], cv[7], u)
                    t[s] = t[s - 1] + hypot(x - px, y - py)
                    px = x
                    py = y
                }
                t
            }
            val constantSpeed = att.constantSpeed || att.lengths.isEmpty()
            val ends = DoubleArray(curveCount)
            if (constantSpeed) {
                var sum = 0.0
                for (i in 0 until curveCount) {
                    sum += tables[i][SEGMENTS]
                    ends[i] = sum
                }
            } else {
                for (i in 0 until curveCount) ends[i] = att.lengths.getOrNull(i) ?: if (i > 0) ends[i - 1] else 0.0
            }
            val length = if (curveCount > 0) ends[curveCount - 1] else 0.0
            if (length < EPSILON) return null
            return Path(curves, tables, ends, length, att.closed, constantSpeed)
        }
    }
}
