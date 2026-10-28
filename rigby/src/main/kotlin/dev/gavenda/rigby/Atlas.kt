// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/atlas.js.
//
// A file is a list of pages separated by blank lines. Each page starts with its image file name,
// followed by "key: value" header lines, then regions: a name line followed by "key: value" lines.
// Both the current format (bounds/offsets) and the legacy one (xy/size/orig/offset) are read.
package dev.gavenda.rigby

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class AtlasRegion(val name: String) {
    var index = -1
    var x = 0
    var y = 0

    /** Unrotated size of the packed image. */
    var width = 0
    var height = 0

    /** Degrees the packed image is turned in the page (90 when stored rotated). */
    var rotate = 0
    var offsetX = 0
    var offsetY = 0
    var originalWidth = -1
    var originalHeight = -1

    /** Name a slot asks for: an indexed region (a frame of a sequence) has its index appended. */
    val key: String get() = if (index >= 0) "$name$index" else name
}

class AtlasPage(val name: String) {
    var width = 0
    var height = 0

    /** Whether filtering is linear (else nearest). */
    var linear = false

    /** Whether the image holds premultiplied alpha. */
    var pma = false
    val regions = ArrayList<AtlasRegion>()
}

class Atlas(val pages: List<AtlasPage>) {
    companion object {
        fun parse(text: String): Atlas {
            val pages = ArrayList<AtlasPage>()
            var page: AtlasPage? = null
            var region: AtlasRegion? = null
            for (raw in text.removePrefix("﻿").lines()) {
                val line = raw.trim()
                if (line.isEmpty()) {
                    page = null
                    region = null
                    continue
                }
                val colon = line.indexOf(':')
                if (colon < 0) {
                    if (page == null) {
                        page = AtlasPage(line).also(pages::add)
                    } else {
                        region = AtlasRegion(line).also(page.regions::add)
                    }
                    continue
                }
                val p = page ?: continue
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                if (region != null) readRegionField(region, key, value) else readPageField(p, key, value)
            }
            for (p in pages) {
                for (r in p.regions) {
                    if (r.originalWidth < 0) r.originalWidth = r.width
                    if (r.originalHeight < 0) r.originalHeight = r.height
                }
            }
            return Atlas(pages)
        }

        private fun ints(v: String): List<Int> = v.split(',').map { it.trim().toDoubleOrNull()?.toInt() ?: 0 }

        private fun readPageField(page: AtlasPage, key: String, value: String) {
            when (key) {
                "size" -> ints(value).let {
                    page.width = it[0]
                    page.height = it.getOrElse(1) { 0 }
                }
                "filter" -> page.linear = !value.split(',')[0].trim().startsWith("Nearest")
                "pma" -> page.pma = value == "true"
            }
        }

        private fun readRegionField(r: AtlasRegion, key: String, value: String) {
            val v = ints(value)
            when (key) {
                "bounds" -> {
                    r.x = v[0]; r.y = v[1]; r.width = v[2]; r.height = v[3]
                }
                "offsets" -> {
                    r.offsetX = v[0]; r.offsetY = v[1]; r.originalWidth = v[2]; r.originalHeight = v[3]
                }
                "xy" -> {
                    r.x = v[0]; r.y = v[1]
                }
                "size" -> {
                    r.width = v[0]; r.height = v[1]
                }
                "orig" -> {
                    r.originalWidth = v[0]; r.originalHeight = v[1]
                }
                "offset" -> {
                    r.offsetX = v[0]; r.offsetY = v[1]
                }
                "rotate" -> r.rotate = when (value) {
                    "true" -> 90
                    "false" -> 0
                    else -> value.toDoubleOrNull()?.toInt() ?: 0
                }
                "index" -> r.index = value.toDoubleOrNull()?.toInt() ?: -1
            }
        }
    }
}

/**
 * Where a region's pixels are in its page. `trim` is the packed part of the image in normalized
 * image coordinates (u right, v down: u0, v0, u1, v1) and `map` turns image coordinates into page
 * pixels, undoing whitespace stripping and rotation.
 */
class RegionMapping(r: AtlasRegion) {
    private val ow = max(1, r.originalWidth).toDouble()
    private val oh = max(1, r.originalHeight).toDouble()
    private val dx = r.offsetX.toDouble()
    private val dy = (r.originalHeight - r.height - r.offsetY).toDouble()
    private val w = r.width.toDouble()
    private val h = r.height.toDouble()
    private val swap = abs(r.rotate % 180) == 90
    private val srcX = r.x.toDouble()
    private val srcY = r.y.toDouble()
    private val cx = (if (swap) r.height else r.width) / 2.0
    private val cy = (if (swap) r.width else r.height) / 2.0

    // The page holds the packed image rotated by -rotate degrees about its center.
    private val cos = floor(cos(-r.rotate * DEG) * 1e6 + 0.5) / 1e6
    private val sin = floor(sin(-r.rotate * DEG) * 1e6 + 0.5) / 1e6

    val u0 = dx / ow
    val v0 = dy / oh
    val u1 = (dx + w) / ow
    val v1 = (dy + h) / oh

    fun mapX(u: Double, v: Double): Double {
        val qx = min(w, max(0.0, u * ow - dx)) - w / 2
        val qy = min(h, max(0.0, v * oh - dy)) - h / 2
        return srcX + cx + qx * cos - qy * sin
    }

    fun mapY(u: Double, v: Double): Double {
        val qx = min(w, max(0.0, u * ow - dx)) - w / 2
        val qy = min(h, max(0.0, v * oh - dy)) - h / 2
        return srcY + cy + qx * sin + qy * cos
    }
}

/**
 * Atlas pixels per skeleton unit, measured on the attachments `pose` shows: the median ratio of
 * an attachment's size in its image to its size in the pose. The canvas pixels per unit a rig is
 * drawn at, divided by this, is how far its pages can be shrunk without losing detail.
 */
fun Atlas.pixelsPerUnit(pose: Pose): Double {
    val regions = HashMap<String, AtlasRegion>()
    for (p in pages) for (r in p.regions) regions[r.key] = r
    val ratios = ArrayList<Double>()
    val buffer = DoubleArray(1 shl 14)
    for (slot in pose.data.slots.indices) {
        val att = pose.slotAttachment(slot) ?: continue
        val r = regions[att.image] ?: continue
        when (att) {
            is RegionAttachment -> if (att.width > 0) ratios += r.originalWidth / (att.width * abs(att.offset.scaleX))
            is MeshAttachment -> {
                val n = pose.worldVertexLength(slot, att)
                if (n > buffer.size) continue
                pose.worldVertices(slot, att, buffer)
                var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
                var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE; var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
                for (k in 0 until n step 2) {
                    minX = min(minX, buffer[k]); maxX = max(maxX, buffer[k])
                    minY = min(minY, buffer[k + 1]); maxY = max(maxY, buffer[k + 1])
                    minU = min(minU, att.uvs[k] * r.originalWidth); maxU = max(maxU, att.uvs[k] * r.originalWidth)
                    minV = min(minV, att.uvs[k + 1] * r.originalHeight); maxV = max(maxV, att.uvs[k + 1] * r.originalHeight)
                }
                val world = kotlin.math.hypot(maxX - minX, maxY - minY)
                if (world > 1e-6) ratios += kotlin.math.hypot(maxU - minU, maxV - minV) / world
            }
            else -> {}
        }
    }
    if (ratios.isEmpty()) return 1.0
    ratios.sort()
    return ratios[ratios.size / 2]
}
