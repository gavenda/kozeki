// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's webgl/renderer.js, drawing on an Android Canvas instead of WebGL: each
// attachment is one drawVertices call, tinted by an AGSL shader.
package dev.gavenda.rigby.android

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import dev.gavenda.rigby.Affine
import dev.gavenda.rigby.Atlas
import dev.gavenda.rigby.Attachment
import dev.gavenda.rigby.BlendMode
import dev.gavenda.rigby.ClippingAttachment
import dev.gavenda.rigby.Clipper
import dev.gavenda.rigby.LinkedMeshAttachment
import dev.gavenda.rigby.MeshAttachment
import dev.gavenda.rigby.Pose
import dev.gavenda.rigby.RegionAttachment
import dev.gavenda.rigby.RegionMapping
import kotlin.math.max
import kotlin.math.roundToInt

// Two-color tint: texture color c maps to light * c + dark * (1 - c). The page is sampled
// premultiplied (Android premultiplies on decode), so it is divided by alpha first; a page that
// already held premultiplied color was multiplied twice and is divided twice. Output is
// premultiplied.
private const val TINT_SHADER = """
uniform shader image;
uniform half4 light;
uniform half4 dark;
uniform half pma;
half4 main(float2 coord) {
  half4 t = image.eval(coord);
  if (t.a <= 0.0) return half4(0.0);
  half3 c = t.rgb / t.a;
  if (pma > 0.5) c = c / t.a;
  c = clamp(c, 0.0, 1.0);
  half a = t.a * light.a;
  half3 rgb = c * light.rgb + (1.0 - c) * dark.rgb * dark.a;
  return half4(rgb * a, a);
}
"""

/**
 * The atlas pages as shaders, with every region's place in them. `pages` maps page file names
 * (the first line of each page in the .atlas) to their decoded images.
 *
 * `scale` shrinks the pages before use, for a rig drawn well below its size: about the rig's
 * device pixels per unit. Canvas sampling has no mipmaps, so without it her edges turn jagged.
 */
class AtlasTextures(atlas: Atlas, pages: Map<String, Bitmap>, scale: Float = 1f) {
    internal class Page(val shader: RuntimeShader, val scaleX: Double, val scaleY: Double) {
        // Uniforms last set, so a run of slots with the same tint shares one shader instance.
        val light = FloatArray(4) { Float.NaN }
        val dark = FloatArray(4) { Float.NaN }
    }

    internal class Region(val page: Page, val mapping: RegionMapping)

    private val regions = HashMap<String, Region>()
    private val bitmaps = ArrayList<Bitmap>()

    init {
        for (p in atlas.pages) {
            val source = pages[p.name] ?: continue
            val bitmap = shrink(source, scale.coerceIn(0.01f, 1f))
            if (bitmap !== source) bitmaps += bitmap
            val image = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = if (p.linear) BitmapShader.FILTER_MODE_LINEAR else BitmapShader.FILTER_MODE_NEAREST
            }
            val shader = RuntimeShader(TINT_SHADER).apply {
                setInputShader("image", image)
                setFloatUniform("pma", if (p.pma) 1f else 0f)
            }
            val pw = if (p.width > 0) p.width else source.width
            val ph = if (p.height > 0) p.height else source.height
            val page = Page(shader, bitmap.width.toDouble() / pw, bitmap.height.toDouble() / ph)
            for (r in p.regions) regions[r.key] = Region(page, RegionMapping(r))
        }
    }

    internal fun region(name: String): Region? = regions[name]

    /** Frees the shrunk copies this made (not the pages it was given). */
    fun recycle() {
        bitmaps.forEach(Bitmap::recycle)
        bitmaps.clear()
    }

    private companion object {
        /** Halves the image until it is within twice the wanted size, then scales the rest. */
        fun shrink(source: Bitmap, scale: Float): Bitmap {
            if (scale >= 0.999f) return source
            val w = max(1, (source.width * scale).roundToInt())
            val h = max(1, (source.height * scale).roundToInt())
            var current = source
            while (current.width / 2 >= w && current.height / 2 >= h) {
                val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
                if (current !== source) current.recycle()
                current = next
            }
            if (current.width == w && current.height == h) return current
            val next = Bitmap.createScaledBitmap(current, w, h, true)
            if (current !== source) current.recycle()
            return next
        }
    }
}

/**
 * Where a skeleton is drawn: the world point at the canvas's top-left corner and the canvas
 * pixels per world unit. World Y points up, canvas Y down.
 */
data class Camera(val x: Float, val y: Float, val zoom: Float)

/** Draws posed skeletons on a Canvas. Not thread-safe: use one renderer per drawing thread. */
class SkeletonRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var positions = DoubleArray(256)
    private var uvs = DoubleArray(256)
    private var verts = FloatArray(256)
    private var texs = FloatArray(256)
    private var indices = ShortArray(384)
    private val regionWorld = Affine()

    /**
     * Draws every visible region and mesh of `pose` in draw order, masked by clipping
     * attachments. `tint` (RGBA, 0..1) multiplies every slot's color.
     */
    fun draw(canvas: Canvas, pose: Pose, textures: AtlasTextures, camera: Camera, tint: FloatArray? = null) {
        canvas.save()
        canvas.scale(camera.zoom, -camera.zoom)
        canvas.translate(-camera.x, -camera.y)
        var clipper: Clipper? = null
        var clipEnd: Int? = null
        for (i in pose.drawOrder) {
            val att = pose.slotAttachment(i)
            if (att is ClippingAttachment) {
                val n = pose.worldVertexLength(i, att)
                val polygon = DoubleArray(n)
                pose.worldVertices(i, att, polygon)
                clipper = Clipper(polygon)
                clipEnd = if (att.end == i) null else att.end // ending at its own slot means: to the end
            } else if (att is RegionAttachment || att is MeshAttachment || att is LinkedMeshAttachment) {
                drawAttachment(canvas, pose, i, att, textures, tint, clipper)
            }
            if (clipper != null && clipEnd == i) clipper = null
        }
        canvas.restore()
        paint.shader = null
    }

    private fun drawAttachment(canvas: Canvas, pose: Pose, i: Int, att: Attachment, textures: AtlasTextures, tint: FloatArray?, clipper: Clipper?) {
        val data = pose.data
        val source: Attachment = if (att is RegionAttachment) att else (data.meshSource(i, att, pose.skin) ?: return)
        val entry = textures.region(att.image) ?: return
        val page = entry.page
        val map = entry.mapping
        val count: Int
        val triangles: IntArray
        if (att is RegionAttachment) {
            // The packed (trimmed) part of the image, as corners of the attachment quad.
            val m = pose.attachmentWorld(i, att.offset, regionWorld)
            ensure(8)
            val hw = att.width / 2
            val hh = att.height / 2
            var k = 0
            for (corner in 0 until 4) {
                val u = if (corner == 1 || corner == 2) map.u1 else map.u0
                val v = if (corner >= 2) map.v1 else map.v0
                val lx = -hw + u * att.width
                val ly = hh - v * att.height
                positions[k] = m.applyX(lx, ly)
                positions[k + 1] = m.applyY(lx, ly)
                uvs[k] = map.mapX(u, v) * page.scaleX
                uvs[k + 1] = map.mapY(u, v) * page.scaleY
                k += 2
            }
            count = 8
            triangles = QUAD
        } else {
            val mesh = source as MeshAttachment
            ensure(pose.worldVertexLength(i, att))
            count = pose.worldVertices(i, att, positions)
            var k = 0
            while (k < count) {
                val u = mesh.uvs[k]
                val v = mesh.uvs[k + 1]
                uvs[k] = map.mapX(u, v) * page.scaleX
                uvs[k + 1] = map.mapY(u, v) * page.scaleY
                k += 2
            }
            triangles = mesh.triangles
        }

        val c = pose.color[i]
        val ac = att.color
        val light0 = (c[0] * ac[0] * (tint?.get(0) ?: 1f)).toFloat()
        val light1 = (c[1] * ac[1] * (tint?.get(1) ?: 1f)).toFloat()
        val light2 = (c[2] * ac[2] * (tint?.get(2) ?: 1f)).toFloat()
        val light3 = (c[3] * ac[3] * (tint?.get(3) ?: 1f)).toFloat()
        if (light3 <= 0f) return
        val d = pose.dark[i]
        setUniforms(page, light0, light1, light2, light3, d)
        paint.shader = page.shader
        paint.blendMode = when (data.slots[i].blend) {
            BlendMode.Normal -> android.graphics.BlendMode.SRC_OVER
            BlendMode.Additive -> android.graphics.BlendMode.PLUS
            BlendMode.Multiply -> android.graphics.BlendMode.MULTIPLY
            BlendMode.Screen -> android.graphics.BlendMode.SCREEN
        }

        if (clipper != null) {
            clipper.clip(positions, uvs, triangles, triangles.size) { p, t, tri -> emit(canvas, p, t, p.size, tri, tri.size) }
        } else {
            emit(canvas, positions, uvs, count, triangles, triangles.size)
        }
    }

    private fun setUniforms(page: AtlasTextures.Page, r: Float, g: Float, b: Float, a: Float, dark: DoubleArray?) {
        val l = page.light
        if (l[0] != r || l[1] != g || l[2] != b || l[3] != a) {
            l[0] = r; l[1] = g; l[2] = b; l[3] = a
            page.shader.setFloatUniform("light", r, g, b, a)
        }
        val dr = dark?.get(0)?.toFloat() ?: 0f
        val dg = dark?.get(1)?.toFloat() ?: 0f
        val db = dark?.get(2)?.toFloat() ?: 0f
        val da = if (dark != null) 1f else 0f
        val k = page.dark
        if (k[0] != dr || k[1] != dg || k[2] != db || k[3] != da) {
            k[0] = dr; k[1] = dg; k[2] = db; k[3] = da
            page.shader.setFloatUniform("dark", dr, dg, db, da)
        }
    }

    private fun ensure(floats: Int) {
        if (positions.size < floats) {
            positions = DoubleArray(floats)
            uvs = DoubleArray(floats)
        }
    }

    private fun emit(canvas: Canvas, p: DoubleArray, t: DoubleArray, count: Int, tri: IntArray, indexCount: Int) {
        if (verts.size < count) {
            verts = FloatArray(count)
            texs = FloatArray(count)
        }
        for (k in 0 until count) {
            verts[k] = p[k].toFloat()
            texs[k] = t[k].toFloat()
        }
        if (indices.size < indexCount) indices = ShortArray(indexCount)
        for (k in 0 until indexCount) indices[k] = tri[k].toShort()
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, count, verts, 0, texs, 0, null, 0, indices, 0, indexCount, paint,
        )
    }

    private companion object {
        val QUAD = intArrayOf(0, 1, 2, 0, 2, 3)
    }
}
