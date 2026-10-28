// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/clipping.js.
//
// A clipping attachment is a polygon that masks the slots drawn after it, up to its end slot. The
// polygon is split into triangles (it may be concave) and each drawn triangle is clipped against
// each of them, interpolating UVs.
package dev.gavenda.rigby

/** Signed area of a flat x, y polygon (positive when counter-clockwise, Y up). */
private fun signedArea(p: DoubleArray): Double {
    var a = 0.0
    var i = 0
    val n = p.size
    while (i < n) {
        val j = (i + 2) % n
        a += p[i] * p[j + 1] - p[j] * p[i + 1]
        i += 2
    }
    return a / 2
}

/** Ear-clipping triangulation of a simple polygon; returns counter-clockwise triangles. */
internal fun triangulate(polygon: DoubleArray): List<DoubleArray> {
    var p = polygon
    if (signedArea(p) < 0) {
        val r = DoubleArray(p.size)
        var k = 0
        var i = p.size - 2
        while (i >= 0) {
            r[k++] = p[i]
            r[k++] = p[i + 1]
            i -= 2
        }
        p = r
    }
    val idx = ArrayList<Int>()
    for (i in 0 until p.size / 2) idx += i
    fun x(i: Int) = p[2 * i]
    fun y(i: Int) = p[2 * i + 1]
    fun cross(a: Int, b: Int, c: Int) = (x(b) - x(a)) * (y(c) - y(a)) - (y(b) - y(a)) * (x(c) - x(a))
    fun inside(a: Int, b: Int, c: Int, q: Int) = cross(a, b, q) >= 0 && cross(b, c, q) >= 0 && cross(c, a, q) >= 0
    val out = ArrayList<DoubleArray>()
    var guard = 0
    while (idx.size > 3 && guard++ < 10000) {
        var clipped = false
        for (k in idx.indices) {
            val a = idx[(k + idx.size - 1) % idx.size]
            val b = idx[k]
            val c = idx[(k + 1) % idx.size]
            if (cross(a, b, c) <= 0) continue // reflex or degenerate corner
            if (idx.any { q -> q != a && q != b && q != c && inside(a, b, c, q) }) continue
            out += doubleArrayOf(x(a), y(a), x(b), y(b), x(c), y(c))
            idx.removeAt(k)
            clipped = true
            break
        }
        if (!clipped) break // self-intersecting input: keep what we have
    }
    if (idx.size == 3) out += doubleArrayOf(x(idx[0]), y(idx[0]), x(idx[1]), y(idx[1]), x(idx[2]), y(idx[2]))
    return out
}

private class ClipVertex(val x: Double, val y: Double, val u: Double, val v: Double)

/**
 * Clips a convex polygon against a counter-clockwise convex triangle (Sutherland-Hodgman).
 * Returns the clipped polygon (possibly empty).
 */
private fun clipConvex(poly: List<ClipVertex>, tri: DoubleArray): List<ClipVertex> {
    var out = poly
    var e = 0
    while (e < 3 && out.isNotEmpty()) {
        val ax = tri[2 * e]
        val ay = tri[2 * e + 1]
        val bx = tri[(2 * e + 2) % 6]
        val by = tri[(2 * e + 3) % 6]
        fun side(p: ClipVertex) = (bx - ax) * (p.y - ay) - (by - ay) * (p.x - ax)
        val input = out
        val next = ArrayList<ClipVertex>()
        for (i in input.indices) {
            val p = input[i]
            val q = input[(i + 1) % input.size]
            val sp = side(p)
            val sq = side(q)
            if (sp >= 0) next += p
            if ((sp >= 0) != (sq >= 0)) {
                val t = sp / (sp - sq)
                next += ClipVertex(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t, p.u + (q.u - p.u) * t, p.v + (q.v - p.v) * t)
            }
        }
        out = next
        e++
    }
    return out
}

/** An active clipping region: triangles of the clip polygon in world space. */
class Clipper(polygon: DoubleArray) {
    private val triangles = triangulate(polygon)

    /**
     * Clips a triangle mesh given as flat positions, UVs and vertex indices. Calls `emit` with
     * the clipped mesh, if anything is left.
     */
    fun clip(
        positions: DoubleArray,
        uvs: DoubleArray,
        indices: IntArray,
        indexCount: Int,
        emit: (positions: DoubleArray, uvs: DoubleArray, indices: IntArray) -> Unit,
    ) {
        val outP = ArrayList<Double>()
        val outUv = ArrayList<Double>()
        val outT = ArrayList<Int>()
        fun vertex(i: Int) = ClipVertex(positions[2 * i], positions[2 * i + 1], uvs[2 * i], uvs[2 * i + 1])
        var t = 0
        while (t + 2 < indexCount) {
            var tri = listOf(vertex(indices[t]), vertex(indices[t + 1]), vertex(indices[t + 2]))
            // Work in counter-clockwise order so "inside" is on the left of every edge.
            val area = (tri[1].x - tri[0].x) * (tri[2].y - tri[0].y) - (tri[1].y - tri[0].y) * (tri[2].x - tri[0].x)
            if (area < 0) tri = listOf(tri[0], tri[2], tri[1])
            for (clip in triangles) {
                val poly = clipConvex(tri, clip)
                if (poly.size < 3) continue
                val base = outP.size / 2
                for (p in poly) {
                    outP += p.x
                    outP += p.y
                    outUv += p.u
                    outUv += p.v
                }
                for (k in 1 until poly.size - 1) {
                    outT += base
                    outT += base + k
                    outT += base + k + 1
                }
            }
            t += 3
        }
        if (outT.isNotEmpty()) emit(outP.toDoubleArray(), outUv.toDoubleArray(), outT.toIntArray())
    }
}
