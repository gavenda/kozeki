package dev.gavenda.rigby

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Compares this port with Rigby2D's JavaScript runtime. Each `<rig>.json` in the folder named by
 * the Gradle property `rigby.parity` comes with `<rig>.expected.json`: a scripted session (which
 * animation goes on which track, when) and the poses the JavaScript runtime reached. This plays
 * the same session and compares bone transforms, slot state and every world vertex.
 *
 *   ./gradlew :rigby:testDebugUnitTest -Prigby.parity=/path/to/folder
 *
 * Without the property the test is skipped: the rigs are game assets and are not checked in.
 */
class ParityTest {
    @Test
    fun matchesTheJavaScriptRuntime() {
        val dir = System.getProperty("rigby.parity").orEmpty()
        assumeTrue("rigby.parity is not set", dir.isNotEmpty())
        val rigs = File(dir).listFiles { f -> f.name.endsWith(".expected.json") }.orEmpty().sortedBy { it.name }
        assumeTrue("no rigs in $dir", rigs.isNotEmpty())
        for (expected in rigs) check(File(dir, expected.name.removeSuffix(".expected.json") + ".json"), expected)
    }

    private fun check(rigFile: File, expectedFile: File) {
        val data = SkeletonJson.read(rigFile.readText(), rigFile.nameWithoutExtension)
        val expected = Json.parseToJsonElement(expectedFile.readText()).jsonObject
        val ops = expected["ops"]!!.jsonArray.map { it.jsonObject }
        val dumps = expected["dumps"]!!.jsonArray.map { it.jsonObject }.associateBy { it["frame"]!!.jsonPrimitive.int }
        val frames = expected["frames"]!!.jsonPrimitive.int
        val pose = Pose(data)
        val state = AnimationState(data)
        val vertices = DoubleArray(1 shl 16)
        var worst = 0.0
        for (frame in 0..frames) {
            for (o in ops) {
                if (o["frame"]!!.jsonPrimitive.int != frame) continue
                val track = o["track"]!!.jsonPrimitive.int
                if (o["op"]!!.jsonPrimitive.content == "set") {
                    state.setAnimation(track, o["name"]!!.jsonPrimitive.content, o["loop"]!!.jsonPrimitive.boolean, o["mix"]!!.jsonPrimitive.double)
                } else {
                    state.clearTrack(track)
                }
            }
            state.update(if (frame == 0) 0.0 else 1.0 / 30)
            state.apply(pose)
            val dump = dumps[frame] ?: continue
            val where = "${rigFile.name} frame $frame"

            val world = dump["world"]!!.jsonArray.doubles()
            for (b in pose.world.indices) {
                val m = pose.world[b]
                val got = doubleArrayOf(m.a, m.b, m.c, m.d, m.tx, m.ty)
                for (k in 0 until 6) worst = max(worst, near(world[b * 6 + k], got[k], "$where bone ${data.bones[b].name}"))
            }
            val color = dump["color"]!!.jsonArray.doubles()
            for (s in pose.color.indices) for (k in 0 until 4) near(color[s * 4 + k], pose.color[s][k], "$where color of ${data.slots[s].name}")
            dump["dark"]!!.jsonArray.forEachIndexed { s, d ->
                if (d is JsonNull) {
                    assertEquals("$where dark of ${data.slots[s].name}", null, pose.dark[s])
                } else {
                    val want = d.jsonArray.doubles()
                    val got = pose.dark[s] ?: fail("$where: no dark tint on ${data.slots[s].name}") as Nothing
                    for (k in 0 until 3) near(want[k], got[k], "$where dark of ${data.slots[s].name}")
                }
            }
            val attachment = dump["attachment"]!!.jsonArray.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            assertEquals("$where attachments", attachment, pose.attachment.toList())
            assertEquals("$where draw order", dump["drawOrder"]!!.jsonArray.map { it.jsonPrimitive.int }, pose.drawOrder.toList())

            val expectedVertices = dump["vertices"]!!.jsonArray
            pose.drawOrder.forEachIndexed { n, slot ->
                val want = expectedVertices[n] as? JsonArray ?: return@forEachIndexed
                val att = pose.slotAttachment(slot) ?: fail("$where: slot ${data.slots[slot].name} shows nothing") as Nothing
                val got: DoubleArray = when (att) {
                    is RegionAttachment -> {
                        val m = pose.attachmentWorld(slot, att.offset)
                        val hw = att.width / 2
                        val hh = att.height / 2
                        doubleArrayOf(-hw, hh, hw, hh, hw, -hh, -hw, -hh).let { c ->
                            DoubleArray(8) { i -> if (i % 2 == 0) m.applyX(c[i], c[i + 1]) else m.applyY(c[i - 1], c[i]) }
                        }
                    }
                    else -> vertices.copyOf(pose.worldVertices(slot, att, vertices))
                }
                assertEquals("$where vertex count of ${data.slots[slot].name}", want.size, got.size)
                for (k in got.indices) worst = max(worst, near(want[k].jsonPrimitive.double, got[k], "$where vertex $k of ${data.slots[slot].name}"))
            }
        }
        println("${rigFile.name}: ${dumps.size} poses match, largest difference $worst")
    }

    private fun JsonArray.doubles() = DoubleArray(size) { this[it].jsonPrimitive.double }

    /** Compares two numbers relative to their size; returns the difference. */
    private fun near(want: Double, got: Double, what: String): Double {
        val diff = abs(want - got)
        if (diff > 1e-6 * max(1.0, abs(want))) fail("$what: expected $want, got $got")
        return diff
    }
}
