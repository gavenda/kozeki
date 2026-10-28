// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/pose.js, with the Plana patches (marked) that make it play Spine 3.8
// rigs as Spine does.
package dev.gavenda.rigby

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal class IkState(
    var mix: Double = 1.0,
    var softness: Double = 0.0,
    var bendPositive: Boolean = true,
    var compress: Boolean = false,
    var stretch: Boolean = false,
)

/** Animatable state of a transform constraint (`mix`) or a path constraint (the rest). */
internal class ConstraintState(
    val mix: DoubleArray = DoubleArray(6),
    var position: Double = 0.0,
    var spacing: Double = 0.0,
    var mixRotate: Double = 0.0,
    var mixX: Double = 0.0,
    var mixY: Double = 0.0,
)

private fun angleOf(x: Double, y: Double) = atan2(y, x) / DEG

/**
 * A mutable pose of a skeleton: apply animations (or let an `AnimationState` do it), then call
 * `updateWorld` before reading world transforms or drawing.
 *
 * `local` holds the animated local transforms, which may be changed by hand before `updateWorld`;
 * `applied` holds them after constraints and is what `world` is computed from.
 */
class Pose(val data: SkeletonData) {
    /** Skin whose attachments come before the default skin's; null for the default skin only. */
    var skin: String? = null

    private val boneCount = data.bones.size
    private val slotCount = data.slots.size

    val local: Array<Transform> = Array(boneCount) { data.bones[it].setup.copy() }
    val applied: Array<Transform> = Array(boneCount) { Transform() }
    val world: Array<Affine> = Array(boneCount) { Affine() }
    internal val inherit: Array<Inherit> = Array(boneCount) { data.bones[it].inherit }

    /** Name of the attachment each slot shows, or null. */
    val attachment: Array<String?> = arrayOfNulls(slotCount)
    val color: Array<DoubleArray> = Array(slotCount) { DoubleArray(4) }
    val dark: Array<DoubleArray?> = arrayOfNulls(slotCount)

    private val setupOrder = IntArray(slotCount) { it }

    /** Slot indices in draw order. Don't change it in place: it may be shared with an animation. */
    var drawOrder: IntArray = setupOrder
        private set

    /** Deform deltas by deform key; only the keys in `deformActive` apply this frame. */
    private val deform = HashMap<String, DoubleArray>()
    private val deformActive = HashSet<String>()
    private var deformFrom = DoubleArray(0)
    private var deformTo = DoubleArray(0)

    internal val ik = Array(data.ik.size) { IkState() }
    internal val constraintState = Array(data.constraints.size) { ConstraintState() }

    private val scratch = Affine()
    private val raw = DoubleArray(6)
    private var dirty = BooleanArray(boneCount)

    init {
        setToSetup()
    }

    /** Resets everything to the setup pose and recomputes world transforms. */
    fun setToSetup() {
        reset()
        updateWorld()
    }

    /**
     * Resets local state to the setup pose without computing world transforms (call
     * `updateWorld` after applying animations).
     */
    fun reset() {
        for (i in 0 until boneCount) {
            local[i].set(data.bones[i].setup)
            inherit[i] = data.bones[i].inherit
        }
        for (i in 0 until slotCount) {
            val s = data.slots[i]
            attachment[i] = s.attachment
            s.color.copyInto(color[i])
            val d = s.dark
            dark[i] = if (d == null) null else (dark[i] ?: DoubleArray(3)).also { d.copyInto(it) }
        }
        drawOrder = setupOrder
        deformActive.clear()
        for ((i, c) in data.ik.withIndex()) {
            val s = ik[i]
            s.mix = c.mix
            s.softness = c.softness
            s.bendPositive = c.bendPositive
            s.compress = c.compress
            s.stretch = c.stretch
        }
        for ((i, c) in data.constraints.withIndex()) {
            val s = constraintState[i]
            when (c) {
                is TransformConstraintData -> c.mix.copyInto(s.mix)
                is PathConstraintData -> {
                    s.position = c.position
                    s.spacing = c.spacing
                    s.mixRotate = c.mixRotate
                    s.mixX = c.mixX
                    s.mixY = c.mixY
                }
            }
        }
    }

    /** Applies `anim` at `time` on top of the current pose, blended by `alpha` (1 = fully). */
    fun apply(anim: Animation, time: Double, alpha: Double) {
        val hold = alpha >= 0.5
        // Before a timeline's first key it does nothing, as in Spine. (Plana patch.)
        fun has(keys: NumericKeys?) = keys != null && keys.size > 0 && time >= keys.firstTime

        for (t in anim.bones) {
            val setup = data.bones[t.bone].setup
            val l = local[t.bone]
            var x = l.x
            var y = l.y
            var rotation = l.rotation
            var sx = l.scaleX
            var sy = l.scaleY
            var hx = l.shearX
            var hy = l.shearY
            if (has(t.rotate)) rotation = setup.rotation + t.rotate!!.sampleRotation(time)
            if (has(t.translate)) {
                x = setup.x + t.translate!!.sample(time, 0)
                y = setup.y + t.translate.sample(time, 1)
            }
            if (has(t.scale)) {
                sx = setup.scaleX * t.scale!!.sample(time, 0)
                sy = setup.scaleY * t.scale.sample(time, 1)
            }
            if (has(t.shear)) {
                hx = setup.shearX + t.shear!!.sample(time, 0)
                hy = setup.shearY + t.shear.sample(time, 1)
            }
            if (alpha >= 1) {
                l.x = x
                l.y = y
                l.rotation = rotation
                l.scaleX = sx
                l.scaleY = sy
                l.shearX = hx
                l.shearY = hy
            } else {
                l.x = lerp(l.x, x, alpha)
                l.y = lerp(l.y, y, alpha)
                l.rotation = lerp(l.rotation, rotation, alpha)
                l.scaleX = lerp(l.scaleX, sx, alpha)
                l.scaleY = lerp(l.scaleY, sy, alpha)
                l.shearX = lerp(l.shearX, hx, alpha)
                l.shearY = lerp(l.shearY, hy, alpha)
            }
        }

        for (t in anim.slots) {
            val i = t.slot
            if (i >= slotCount) continue
            val times = t.attachmentTimes
            if (hold && times != null && time >= times[0]) attachment[i] = t.attachmentNames!![discreteIndex(times, time)]
            val current = color[i]
            val c0 = current[0]
            val c1 = current[1]
            val c2 = current[2]
            val c3 = current[3]
            var r = c0
            var g = c1
            var b = c2
            var a = c3
            var darkR = 0.0
            var darkG = 0.0
            var darkB = 0.0
            var hasDark = false
            dark[i]?.let {
                darkR = it[0]
                darkG = it[1]
                darkB = it[2]
                hasDark = true
            }
            val color4 = t.color
            if (has(color4)) {
                r = color4!!.sample(time, 0)
                g = color4.sample(time, 1)
                b = color4.sample(time, 2)
                a = color4.sample(time, 3)
            }
            val two = t.twoColor
            if (has(two)) {
                r = two!!.sample(time, 0)
                g = two.sample(time, 1)
                b = two.sample(time, 2)
                a = two.sample(time, 3)
                darkR = two.sample(time, 4)
                darkG = two.sample(time, 5)
                darkB = two.sample(time, 6)
                hasDark = true
            }
            current[0] = lerp(c0, r, alpha)
            current[1] = lerp(c1, g, alpha)
            current[2] = lerp(c2, b, alpha)
            current[3] = lerp(c3, a, alpha)
            if (hasDark) {
                val d = dark[i] ?: doubleArrayOf(darkR, darkG, darkB).also { dark[i] = it }
                d[0] = lerp(d[0], darkR, alpha)
                d[1] = lerp(d[1], darkG, alpha)
                d[2] = lerp(d[2], darkB, alpha)
            }
        }

        for (t in anim.deform) {
            if (time < t.times[0]) continue // Plana patch: nothing before the first key.
            if (t.slot >= slotCount) continue
            val att = data.attachment(t.slot, t.attachment, t.skin ?: skin) ?: continue
            val source = data.meshSource(t.slot, att, skin) ?: att
            val n = (source as? VertexAttachment)?.deformLength ?: 0
            val deltas = sampleDeform(t, time, n)
            val buffer = deform[t.deformKey]?.takeIf { it.size == n } ?: DoubleArray(n).also { deform[t.deformKey] = it }
            if (alpha < 1 && t.deformKey in deformActive) {
                for (j in 0 until n) buffer[j] = lerp(buffer[j], deltas[j], alpha)
            } else {
                deltas.copyInto(buffer, 0, 0, n)
            }
            deformActive += t.deformKey
        }

        val order = anim.drawOrder
        if (hold && order != null) {
            val i = keyIndexAt(order.times, time)
            if (i >= 0) drawOrder = order.orders[i] ?: setupOrder
        }

        for (t in anim.transform) {
            val keys = t.keys
            if (time < keys.times[0]) continue // Plana patch: nothing before the first key, as in Spine.
            keys.sample(time, raw)
            val mix = constraintState[t.constraint].mix
            for (j in 0 until 6) mix[j] += (raw[j] - mix[j]) * alpha
        }
        for (t in anim.path) {
            val s = constraintState[t.constraint]
            t.position?.let {
                it.sample(time, raw)
                s.position += (raw[0] - s.position) * alpha
            }
            t.spacing?.let {
                it.sample(time, raw)
                s.spacing += (raw[0] - s.spacing) * alpha
            }
            t.mix?.let {
                it.sample(time, raw)
                s.mixRotate += (raw[0] - s.mixRotate) * alpha
                s.mixX += (raw[1] - s.mixX) * alpha
                s.mixY += (raw[2] - s.mixY) * alpha
            }
        }

        for (t in anim.ik) {
            val state = ik.getOrNull(t.constraint) ?: continue
            val i = keyIndexAt(t.times, time)
            if (i < 0) continue
            val a = t.keys[i]
            val b = t.keys.getOrNull(i + 1)
            // Rigby2D eases IK keys linearly, whatever their curve.
            val frac = if (b != null && b.time > a.time) (time - a.time) / (b.time - a.time) else 0.0
            state.mix = lerp(state.mix, lerp(a.mix, b?.mix ?: a.mix, frac), alpha)
            state.softness = lerp(state.softness, lerp(a.softness, b?.softness ?: a.softness, frac), alpha)
            if (hold) {
                state.bendPositive = a.bendPositive
                state.compress = a.compress
                state.stretch = a.stretch
            }
        }
    }

    /** Index of the key at `time` for keys that hold their value (attachments). */
    private fun discreteIndex(times: DoubleArray, time: Double): Int {
        if (time <= times[0]) return 0
        return min(partitionPoint(times, time), times.size) - 1
    }

    /** Deform deltas at `time`: each key's sparse values expanded, interpolated along its curve. */
    private fun sampleDeform(t: DeformTimeline, time: Double, n: Int): DoubleArray {
        if (deformFrom.size < n) {
            deformFrom = DoubleArray(n)
            deformTo = DoubleArray(n)
        }
        val i = max(0, keyIndexAt(t.times, time))
        expand(t, i, n, deformFrom)
        val b = i + 1
        if (b >= t.times.size || time <= t.times[i] || t.curves[i] == Curve.Stepped) return deformFrom
        expand(t, b, n, deformTo)
        val e = t.curves[i].ease((time - t.times[i]) / (t.times[b] - t.times[i]))
        for (j in 0 until n) deformFrom[j] += (deformTo[j] - deformFrom[j]) * e
        return deformFrom
    }

    private fun expand(t: DeformTimeline, key: Int, n: Int, out: DoubleArray) {
        out.fill(0.0, 0, n)
        val offset = t.offsets[key]
        val v = t.vertices[key]
        var j = 0
        while (j < v.size && offset + j < n) {
            out[offset + j] = v[j]
            j++
        }
    }

    /** Recomputes world transforms from local transforms, then applies constraints in order. */
    fun updateWorld() {
        for (i in 0 until boneCount) applied[i].set(local[i])
        computeWorld()
        for (step in data.updateOrder) {
            if (step >= 0) {
                val c = data.ik[step]
                if (c.skinRequired && activeSkin()?.ik?.contains(step) != true) continue
                solveIk(c, ik[step])
                continue
            }
            val index = -step - 1
            val c = data.constraints[index]
            if (c.skinRequired && activeSkin()?.constraints?.contains(c.name) != true) continue
            when (c) {
                is TransformConstraintData -> solveTransform(this, c, constraintState[index])
                is PathConstraintData -> solvePath(this, c, constraintState[index])
            }
        }
    }

    private fun activeSkin(): Skin? = skin?.let(data::findSkin)

    internal fun computeWorld() {
        for (i in 0 until boneCount) updateBone(i)
    }

    /**
     * Sets the world transforms of some bones, updating their applied local transforms to match
     * and moving their descendants along.
     */
    internal fun setWorlds(desired: Map<Int, Affine>) {
        val dirty = dirtyFlags()
        var first = boneCount
        for (b in desired.keys) first = min(first, b)
        for (i in first until boneCount) {
            val p = data.bones[i].parent
            val want = desired[i]
            if (want != null) {
                applied[i].set(localFromWorld(if (p == null) null else world[p], want, inherit[i]))
                world[i].set(want.a, want.b, want.c, want.d, want.tx, want.ty)
                dirty[i] = true
            } else if (p != null && dirty[p]) {
                updateBone(i)
                dirty[i] = true
            }
        }
    }

    private fun dirtyFlags(): BooleanArray {
        dirty.fill(false)
        return dirty
    }

    /** Recomputes the world transform of `root` and its descendants. */
    internal fun updateBranch(root: Int) {
        val dirty = dirtyFlags()
        dirty[root] = true
        updateBone(root)
        for (i in root + 1 until boneCount) {
            val p = data.bones[i].parent
            if (p != null && dirty[p]) {
                dirty[i] = true
                updateBone(i)
            }
        }
    }

    private fun updateBone(i: Int) {
        val p = data.bones[i].parent
        if (p == null) {
            applied[i].toAffine(world[i])
        } else {
            inheritWorld(world[p], applied[i].toAffine(scratch), inherit[i], world[i])
        }
    }

    private fun parentSign(b: Int): Double {
        val p = data.bones[b].parent
        return if (p != null && world[p].determinant() < 0) -1.0 else 1.0
    }

    /** Rotates bone `b` so the direction from `(fx, fy)` to `(tipX, tipY)` points at the target. */
    private fun aim(b: Int, fx: Double, fy: Double, tipX: Double, tipY: Double, tx: Double, ty: Double, mix: Double) {
        val delta = wrapDegrees(angleOf(tx - fx, ty - fy) - angleOf(tipX - fx, tipY - fy))
        applied[b].rotation += delta * mix * parentSign(b)
    }

    /** Rotates (and optionally stretches) the constrained bones so the chain reaches the target. */
    private fun solveIk(c: IkData, state: IkState) {
        val mix = state.mix
        if (mix <= 0 || c.bones.isEmpty()) return
        val t = world[c.target]
        val tx = t.tx
        val ty = t.ty
        if (c.bones.size == 1) {
            val b = c.bones[0]
            val w = world[b]
            val ox = w.tx
            val oy = w.ty
            aim(b, ox, oy, w.applyX(1.0, 0.0), w.applyY(1.0, 0.0), tx, ty, mix)
            val boneLen = data.bones[b].length * hypot(w.a, w.b)
            val dist = hypot(tx - ox, ty - oy)
            if (boneLen > 1e-4 && ((state.compress && dist < boneLen) || (state.stretch && dist > boneLen))) {
                val s = (dist / boneLen - 1) * mix + 1
                applied[b].scaleX *= s
                if (c.uniform) applied[b].scaleY *= s
            }
            updateBranch(b)
            return
        }
        val p = c.bones[0]
        val ch = c.bones[1]
        val px = world[p].tx
        val py = world[p].ty
        val cx = world[ch].tx
        val cy = world[ch].ty
        val chLen = data.bones[ch].length
        val tipX = world[ch].applyX(chLen, 0.0)
        val tipY = world[ch].applyY(chLen, 0.0)
        val a = hypot(cx - px, cy - py)
        val bLen = hypot(tipX - cx, tipY - cy)
        if (a < 1e-4 || bLen < 1e-4) {
            if (cx != px || cy != py) aim(p, px, py, cx, cy, tx, ty, mix)
            else aim(p, px, py, world[p].applyX(1.0, 0.0), world[p].applyY(1.0, 0.0), tx, ty, mix)
            updateBranch(p)
            return
        }
        var d = hypot(tx - px, ty - py)
        if (state.stretch && d > a + bLen) {
            val s = (d / (a + bLen) - 1) * mix + 1
            applied[p].scaleX *= s
            if (c.uniform) applied[p].scaleY *= s
            updateBranch(p)
            finishTwoBone(ch, tx, ty, mix)
            return
        }
        val soft = state.softness
        if (soft > 0 && d > a + bLen - soft) d = a + bLen - soft * exp(-(d - (a + bLen - soft)) / soft)
        d = min(max(d, abs(a - bLen) + 1e-6), a + bLen - 1e-6)
        val cosA = min(1.0, max(-1.0, (a * a + d * d - bLen * bLen) / (2 * a * d)))
        val angle = acos(cosA) / DEG
        // A mirrored chain bends the other way on screen.
        val bend = (if (state.bendPositive) 1 else -1) * (if (world[p].determinant() < 0) -1 else 1)
        val desired = angleOf(tx - px, ty - py) - bend * angle
        val delta = wrapDegrees(desired - angleOf(cx - px, cy - py))
        applied[p].rotation += delta * mix * parentSign(p)
        updateBranch(p)
        finishTwoBone(ch, tx, ty, mix)
    }

    private fun finishTwoBone(ch: Int, tx: Double, ty: Double, mix: Double) {
        val w = world[ch]
        val len = max(data.bones[ch].length, 1e-3)
        aim(ch, w.tx, w.ty, w.applyX(len, 0.0), w.applyY(len, 0.0), tx, ty, mix)
        updateBranch(ch)
    }

    /** The attachment a slot shows now, resolved through the skin. */
    fun slotAttachment(slot: Int): Attachment? = data.attachment(slot, attachment[slot], skin)

    /** World transform of a region or point attachment, written into `out`. */
    fun attachmentWorld(slot: Int, offset: Transform, out: Affine = Affine()): Affine =
        Affine.multiply(world[data.slots[slot].bone], offset.toAffine(out), out)

    /** Floats `worldVertices` writes for an attachment. */
    fun worldVertexLength(slot: Int, att: Attachment): Int = when (att) {
        is VertexAttachment -> att.vertexCount * 2
        is LinkedMeshAttachment -> (data.meshSource(slot, att, skin)?.vertexCount ?: 0) * 2
        else -> 0
    }

    /**
     * World positions of a mesh-like attachment's vertices, written to `out` as x, y pairs.
     * Returns how many floats were written.
     */
    fun worldVertices(slot: Int, att: Attachment, out: DoubleArray): Int {
        val source: VertexAttachment = when (att) {
            is VertexAttachment -> att
            is LinkedMeshAttachment -> data.meshSource(slot, att, skin) ?: return 0
            else -> return 0
        }
        val key = if (att is LinkedMeshAttachment) att.deformKey else source.deformKey
        val deform = if (key in deformActive) deform[key] else null
        val weights = source.weights
        var o = 0
        if (weights != null) {
            var k = 0
            var w = 0
            for (count in weights.counts) {
                var x = 0.0
                var y = 0.0
                repeat(count) {
                    val bx = weights.values[w * 3] + (deform?.get(k) ?: 0.0)
                    val by = weights.values[w * 3 + 1] + (deform?.get(k + 1) ?: 0.0)
                    val weight = weights.values[w * 3 + 2]
                    k += 2
                    val m = world[weights.bones[w]]
                    x += (m.a * bx + m.c * by + m.tx) * weight
                    y += (m.b * bx + m.d * by + m.ty) * weight
                    w++
                }
                out[o++] = x
                out[o++] = y
            }
        } else {
            val m = world[data.slots[slot].bone]
            val v = source.vertices ?: return 0
            var i = 0
            while (i + 1 < v.size) {
                val vx = v[i] + (deform?.get(i) ?: 0.0)
                val vy = v[i + 1] + (deform?.get(i + 1) ?: 0.0)
                out[o++] = m.a * vx + m.c * vy + m.tx
                out[o++] = m.b * vx + m.d * vy + m.ty
                i += 2
            }
        }
        return o
    }
}

/** The local transform that gives world transform `world` under `parent` and inherit `mode`. */
private fun localFromWorld(parent: Affine?, world: Affine, mode: Inherit): Transform {
    if (parent == null) return world.decompose()
    // Translation always goes through the full parent transform; the linear part through the part
    // of the parent the inherit mode keeps.
    val inv = parent.inverse()
    val x = inv.applyX(world.tx, world.ty)
    val y = inv.applyY(world.tx, world.ty)
    val keep = inheritWorld(parent, Affine(), mode, Affine())
    keep.tx = 0.0
    keep.ty = 0.0
    val linear = keep.inverse() * Affine(world.a, world.b, world.c, world.d, 0.0, 0.0)
    linear.tx = x
    linear.ty = y
    return linear.decompose()
}

/**
 * Child world transform for the given inherit mode, written into `out` (which may be `local`).
 * Translation always goes through the full parent transform; the linear part through the part
 * of the parent that the mode keeps.
 */
internal fun inheritWorld(parent: Affine, local: Affine, mode: Inherit, out: Affine): Affine {
    if (mode == Inherit.Normal) return Affine.multiply(parent, local, out)
    val tx = parent.a * local.tx + parent.c * local.ty + parent.tx
    val ty = parent.b * local.tx + parent.d * local.ty + parent.ty
    val sx = hypot(parent.a, parent.b)
    val det = parent.determinant()
    val pa: Double
    val pb: Double
    val pc: Double
    val pd: Double
    when (mode) {
        Inherit.OnlyTranslation -> {
            pa = 1.0; pb = 0.0; pc = 0.0; pd = 1.0
        }
        Inherit.NoRotationOrReflection -> {
            pa = sx; pb = 0.0; pc = 0.0; pd = if (sx > 1e-12) abs(det) / sx else 0.0
        }
        else -> {
            val ux = if (sx > 1e-12) parent.a / sx else 1.0
            val uy = if (sx > 1e-12) parent.b / sx else 0.0
            val flip = if (mode == Inherit.NoScale && det < 0) -1.0 else 1.0
            pa = ux; pb = uy; pc = -uy * flip; pd = ux * flip
        }
    }
    return out.set(
        pa * local.a + pc * local.b,
        pb * local.a + pd * local.b,
        pa * local.c + pc * local.d,
        pb * local.c + pd * local.d,
        tx,
        ty,
    )
}
