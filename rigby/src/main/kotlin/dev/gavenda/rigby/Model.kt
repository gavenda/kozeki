// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/model.js (the parts a player needs). Bones, slots and constraints
// reference each other by index; read a rig with `SkeletonJson.read`.
package dev.gavenda.rigby

/** How a bone inherits its parent's transform. */
enum class Inherit { Normal, OnlyTranslation, NoRotationOrReflection, NoScale, NoScaleOrReflection }

enum class BlendMode { Normal, Additive, Multiply, Screen }

class BoneData(
    val index: Int,
    val name: String,
    /** Index of the parent bone; null for the root. Parents always come before their children. */
    val parent: Int?,
    val length: Double,
    val setup: Transform,
    val inherit: Inherit,
)

class SlotData(
    val index: Int,
    val name: String,
    val bone: Int,
    /** Attachment shown in the setup pose. */
    val attachment: String?,
    /** RGBA tint, 0..1. */
    val color: DoubleArray,
    /** RGB dark tint of two-color tinting, or null. */
    val dark: DoubleArray?,
    val blend: BlendMode,
)

/**
 * Bone weights of a weighted vertex attachment. For each vertex, `counts` holds how many bones
 * move it; per bone, `bones` holds the bone index and `values` its x, y and weight.
 */
class Weights(val counts: IntArray, val bones: IntArray, val values: DoubleArray) {
    /** Floats a deform key covers: one x/y pair per bone per vertex. */
    val deformLength: Int get() = bones.size * 2
}

sealed class Attachment(
    val name: String,
    /** Atlas region the attachment draws. */
    val image: String,
    /** RGBA tint, 0..1. */
    val color: DoubleArray,
)

class RegionAttachment(
    name: String,
    image: String,
    color: DoubleArray,
    val offset: Transform,
    val width: Double,
    val height: Double,
) : Attachment(name, image, color)

class PointAttachment(name: String, image: String, color: DoubleArray, val offset: Transform) :
    Attachment(name, image, color)

/** An attachment made of vertices: bone-relative (`vertices`) or weighted (`weights`). */
sealed class VertexAttachment(
    name: String,
    image: String,
    color: DoubleArray,
    val vertexCount: Int,
    val vertices: DoubleArray?,
    val weights: Weights?,
    /** Key of this attachment's deform deltas in a pose. */
    internal val deformKey: String,
) : Attachment(name, image, color) {
    internal val deformLength: Int get() = weights?.deformLength ?: vertices?.size ?: (vertexCount * 2)
}

class MeshAttachment(
    name: String,
    image: String,
    color: DoubleArray,
    vertexCount: Int,
    vertices: DoubleArray?,
    weights: Weights?,
    deformKey: String,
    /** Normalized image coordinates per vertex (u right, v down). */
    val uvs: DoubleArray,
    val triangles: IntArray,
    val hull: Int,
    val width: Double,
    val height: Double,
) : VertexAttachment(name, image, color, vertexCount, vertices, weights, deformKey)

/** A mesh that shows its own image over the geometry of another mesh (`parent`). */
class LinkedMeshAttachment(
    name: String,
    image: String,
    color: DoubleArray,
    val parent: String,
    val skin: String?,
    /** Whether the parent's deform keys move this mesh too. */
    val deform: Boolean,
    /** Key of the deform deltas this mesh follows. */
    internal val deformKey: String,
) : Attachment(name, image, color)

class BoundingBoxAttachment(
    name: String, image: String, color: DoubleArray, vertexCount: Int, vertices: DoubleArray?, weights: Weights?, deformKey: String,
) : VertexAttachment(name, image, color, vertexCount, vertices, weights, deformKey)

/** A polygon that masks the slots drawn after it, up to `end` (null: to the end). */
class ClippingAttachment(
    name: String, image: String, color: DoubleArray, vertexCount: Int, vertices: DoubleArray?, weights: Weights?, deformKey: String,
    val end: Int?,
) : VertexAttachment(name, image, color, vertexCount, vertices, weights, deformKey)

class PathAttachment(
    name: String, image: String, color: DoubleArray, vertexCount: Int, vertices: DoubleArray?, weights: Weights?, deformKey: String,
    val closed: Boolean,
    val constantSpeed: Boolean,
    val lengths: DoubleArray,
) : VertexAttachment(name, image, color, vertexCount, vertices, weights, deformKey)

class Skin(
    val name: String,
    /** Attachments by slot index, then by name. */
    val attachments: Map<Int, Map<String, Attachment>>,
    val bones: List<Int>,
    val ik: List<Int>,
    /** Names of the transform and path constraints the skin turns on. */
    val constraints: Set<String>,
)

class IkData(
    val name: String,
    val order: Int,
    val skinRequired: Boolean,
    val bones: IntArray,
    val target: Int,
    val mix: Double,
    val softness: Double,
    val bendPositive: Boolean,
    val compress: Boolean,
    val stretch: Boolean,
    val uniform: Boolean,
)

/** A transform or path constraint. */
sealed class ConstraintData(val name: String, val order: Int, val skinRequired: Boolean, val bones: IntArray)

/** Properties in the order transform constraint offsets and mixes are stored. */
internal const val ROTATE = 0
internal const val X = 1
internal const val Y = 2
internal const val SCALE_X = 3
internal const val SCALE_Y = 4
internal const val SHEAR_Y = 5

class TransformConstraintData(
    name: String,
    order: Int,
    skinRequired: Boolean,
    bones: IntArray,
    val source: Int,
    /** Offsets of rotate, x, y, scaleX, scaleY and shearY. */
    val offsets: DoubleArray,
    /** Setup mixes of the same properties. */
    val mix: DoubleArray,
    val local: Boolean,
    val relative: Boolean,
) : ConstraintData(name, order, skinRequired, bones)

enum class PositionMode { Fixed, Percent }

enum class SpacingMode { Length, Fixed, Percent }

enum class RotateMode { Tangent, Chain, ChainScale }

class PathConstraintData(
    name: String,
    order: Int,
    skinRequired: Boolean,
    bones: IntArray,
    val slot: Int,
    val positionMode: PositionMode,
    val spacingMode: SpacingMode,
    val rotateMode: RotateMode,
    val offsetRotation: Double,
    val position: Double,
    val spacing: Double,
    val mixRotate: Double,
    val mixX: Double,
    val mixY: Double,
) : ConstraintData(name, order, skinRequired, bones)

class EventData(
    val name: String,
    val int: Int,
    val float: Double,
    val string: String?,
    val audio: String?,
    val volume: Double,
    val balance: Double,
)

/** A loaded rig: bones, slots, skins, constraints, events and animations. */
class SkeletonData(
    val name: String,
    /** Bones ordered so that every parent comes before its children. Bone 0 is the root. */
    val bones: List<BoneData>,
    /** Slots in setup draw order (first is drawn at the back). */
    val slots: List<SlotData>,
    val defaultSkin: Skin,
    val skins: List<Skin>,
    val ik: List<IkData>,
    val constraints: List<ConstraintData>,
    val events: List<EventData>,
    val animations: List<Animation>,
    /** Bounds of the setup pose from the file header (x, y, width, height), if it has them. */
    val bounds: DoubleArray?,
) {
    private val boneIndex = bones.associate { it.name to it.index }
    private val slotIndex = slots.associate { it.name to it.index }
    private val animationIndex = animations.withIndex().associate { it.value.name to it.index }

    /**
     * IK, transform and path constraints in the order they are applied: by `order`, and IK first
     * among equals, as Rigby2D sorts them. An IK constraint is its index in `ik`; another
     * constraint `j` of `constraints` is `-(j + 1)`.
     */
    internal val updateOrder: IntArray =
        (ik.indices.map { ik[it].order to it } + constraints.indices.map { constraints[it].order to -(it + 1) })
            .sortedBy { it.first }
            .map { it.second }
            .toIntArray()

    fun findBone(name: String): Int? = boneIndex[name]

    fun findSlot(name: String): Int? = slotIndex[name]

    fun findSkin(name: String): Skin? = if (name == "default") defaultSkin else skins.find { it.name == name }

    fun findAnimation(name: String): Animation? = animationIndex[name]?.let(animations::get)

    internal fun animationIndexOf(name: String): Int = animationIndex[name] ?: -1

    /** Attachments a skin defines for a slot (the default skin when `skin` is null). */
    internal fun skinAttachments(slot: Int, skin: String?): Map<String, Attachment>? =
        if (skin == null || skin == "default") defaultSkin.attachments[slot] else findSkin(skin)?.attachments?.get(slot)

    /** Resolves an attachment by slot and name: `skin` first, then the default skin. */
    fun attachment(slot: Int, name: String?, skin: String?): Attachment? {
        if (name == null) return null
        if (skin != null) skinAttachments(slot, skin)?.get(name)?.let { return it }
        return defaultSkin.attachments[slot]?.get(name)
    }

    /** The mesh that holds geometry for `att` (itself, or the parent of a linked mesh). */
    internal fun meshSource(slot: Int, att: Attachment, skin: String?): MeshAttachment? = when (att) {
        is MeshAttachment -> att
        is LinkedMeshAttachment -> (skinAttachments(slot, att.skin)?.get(att.parent) ?: attachment(slot, att.parent, skin)) as? MeshAttachment
        else -> null
    }
}
