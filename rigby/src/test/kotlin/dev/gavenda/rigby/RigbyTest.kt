package dev.gavenda.rigby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RigbyTest {
    /** A root, an arm turned 90° with a hand that keeps only its parent's translation. */
    private val rig = """
        {
          "skeleton": { "spine": "3.8.99", "x": -10, "y": -10, "width": 20, "height": 30 },
          "bones": [
            { "name": "root" },
            { "name": "arm", "parent": "root", "rotation": 90, "length": 10 },
            { "name": "hand", "parent": "arm", "x": 10, "transform": "onlyTranslation" }
          ],
          "slots": [
            { "name": "back", "bone": "root", "attachment": "a" },
            { "name": "front", "bone": "hand", "attachment": "b", "color": "ff000080" }
          ],
          "skins": [
            { "name": "default", "attachments": {
              "back": { "a": { "width": 4, "height": 2 }, "c": { "width": 1, "height": 1 } },
              "front": { "b": { "type": "mesh", "uvs": [0, 0, 1, 0, 1, 1], "triangles": [0, 1, 2],
                                "vertices": [0, 0, 2, 0, 2, 2], "hull": 3 } }
            } }
          ],
          "animations": {
            "turn": {
              "bones": { "arm": { "rotate": [
                { "time": 0, "angle": 170 },
                { "time": 1, "angle": -170 }
              ] } },
              "slots": {
                "back": { "attachment": [ { "time": 0.5, "name": "c" }, { "time": 0.75, "name": null } ] },
                "front": { "color": [ { "time": 0, "color": "ffffffff", "curve": "stepped" }, { "time": 1, "color": "00000000" } ] }
              },
              "deform": { "default": { "front": { "b": [
                { "time": 0 },
                { "time": 1, "offset": 2, "vertices": [4, 0] }
              ] } } },
              "drawOrder": [ { "time": 0.5, "offsets": [ { "slot": "back", "offset": 1 } ] } ]
            }
          }
        }
    """.trimIndent()

    private fun near(want: Double, got: Double) = assertEquals(want, got, 1e-9)

    @Test
    fun setupPose() {
        val data = SkeletonJson.read(rig)
        val pose = Pose(data)
        // The arm points up; the hand sits at its tip and keeps the root's orientation.
        near(10.0, pose.world[2].ty)
        near(0.0, pose.world[2].tx)
        near(1.0, pose.world[2].a)
        near(1.0, pose.world[2].d)
        assertArrayEquals(doubleArrayOf(1.0, 0.0, 0.0, 128 / 255.0), pose.color[1], 1e-9)
        assertArrayEquals(doubleArrayOf(-10.0, -10.0, 20.0, 30.0), data.bounds, 0.0)
        val out = DoubleArray(6)
        assertEquals(6, pose.worldVertices(1, pose.slotAttachment(1)!!, out))
        assertArrayEquals(doubleArrayOf(0.0, 10.0, 2.0, 10.0, 2.0, 12.0), out, 1e-9)
    }

    @Test
    fun rotationTurnsTheShortWay() {
        val data = SkeletonJson.read(rig)
        val pose = Pose(data)
        pose.apply(data.findAnimation("turn")!!, 0.5, 1.0)
        // From 170° to -170° goes through 180°, not through 0°.
        near(90.0 + 180.0, pose.local[1].rotation)
    }

    @Test
    fun slotsDeformAndDrawOrder() {
        val data = SkeletonJson.read(rig)
        val pose = Pose(data)
        val turn = data.findAnimation("turn")!!
        assertEquals(1.0, turn.duration, 0.0)

        pose.apply(turn, 0.6, 1.0)
        pose.updateWorld()
        assertEquals("c", pose.attachment[0])
        assertEquals(listOf(1, 0), pose.drawOrder.toList())
        // Stepped: the color holds until the next key.
        assertArrayEquals(doubleArrayOf(1.0, 1.0, 1.0, 1.0), pose.color[1], 1e-9)
        // Deform moves the second vertex 60% of the way to +4 x.
        val out = DoubleArray(6)
        val world = pose.world[2]
        pose.worldVertices(1, pose.slotAttachment(1)!!, out)
        near(world.tx + 2 + 0.6 * 4, out[2])

        pose.reset()
        pose.apply(turn, 0.8, 1.0)
        assertNull(pose.attachment[0])

        pose.reset()
        assertEquals("a", pose.attachment[0])
        assertEquals(listOf(0, 1), pose.drawOrder.toList())
    }

    @Test
    fun aOneShotHoldsItsLastFrame() {
        val data = SkeletonJson.read(rig)
        val pose = Pose(data)
        val state = AnimationState(data)
        val ended = ArrayList<String>()
        state.addListener(object : AnimationStateListener {
            override fun complete(entry: TrackEntry) {
                ended += entry.name
            }
        })
        state.setAnimation(0, "turn", false)
        state.update(1.5)
        state.apply(pose)
        assertEquals(listOf("turn"), ended)
        // Past its end, an animation that doesn't loop holds its last key.
        near(90.0 - 170.0, pose.local[1].rotation)
    }

    @Test
    fun drawOrderOffsets() {
        assertEquals(listOf(1, 2, 0, 3), applyDrawOrder(4, intArrayOf(0), intArrayOf(2)).toList())
        assertEquals(listOf(0, 3, 1, 2), applyDrawOrder(4, intArrayOf(3), intArrayOf(-2)).toList())
    }

    @Test
    fun curves() {
        near(0.25, Curve.Linear.ease(0.25))
        near(0.0, Curve.Stepped.ease(0.9))
        val ease = Curve.Bezier(0.25, 0.0, 0.75, 1.0)
        near(0.5, ease.ease(0.5))
        assert(ease.ease(0.1) < 0.1)
        assertEquals(ease.ease(0.3), ease.easeBisect(0.3), 1e-5)
    }

    @Test
    fun rotatedAtlasRegion() {
        val atlas = Atlas.parse(
            """
            page.png
            size: 64, 64
            filter: Linear, Linear
            face
              rotate: true
              xy: 10, 20
              size: 8, 4
              orig: 10, 4
              offset: 1, 0
              index: -1
            """.trimIndent(),
        )
        val r = atlas.pages[0].regions[0]
        assertEquals("face", r.key)
        assertEquals(true, atlas.pages[0].linear)
        val map = RegionMapping(r)
        near(0.1, map.u0)
        near(0.9, map.u1)
        // Stored turned 90° counter-clockwise in a 4x8 footprint at (10, 20): the packed image's
        // top-left corner is the footprint's bottom-left, its top-right the top-left.
        near(10.0, map.mapX(map.u0, map.v0))
        near(28.0, map.mapY(map.u0, map.v0))
        near(10.0, map.mapX(map.u1, map.v0))
        near(20.0, map.mapY(map.u1, map.v0))
        near(14.0, map.mapX(map.u0, map.v1))
    }

    @Test
    fun clipping() {
        val square = doubleArrayOf(0.0, 0.0, 2.0, 0.0, 2.0, 2.0, 0.0, 2.0)
        assertEquals(2, triangulate(square).size)
        var area = 0.0
        Clipper(square).clip(
            doubleArrayOf(1.0, 1.0, 5.0, 1.0, 1.0, 5.0), DoubleArray(6), intArrayOf(0, 1, 2), 3,
        ) { p, _, t ->
            for (k in t.indices step 3) {
                val (a, b, c) = Triple(t[k], t[k + 1], t[k + 2])
                area += ((p[2 * b] - p[2 * a]) * (p[2 * c + 1] - p[2 * a + 1]) - (p[2 * b + 1] - p[2 * a + 1]) * (p[2 * c] - p[2 * a])) / 2
            }
        }
        // The triangle covers the square's top-right unit cell.
        near(1.0, area)
    }
}
