// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/interop.js (reading only), which was written clean-room from the
// format's public documentation. Reads Spine 3.8 skeleton JSON.
package dev.gavenda.rigby

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.max

class SkeletonJsonException(message: String) : IllegalArgumentException(message)

object SkeletonJson {
    /** Reads skeleton JSON text. `name` becomes the skeleton's name (the format has none). */
    fun read(text: String, name: String = "skeleton"): SkeletonData {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: fail("not a skeleton JSON file")
        return Reader(root, name).read()
    }
}

private fun fail(message: String): Nothing = throw SkeletonJsonException(message)

private val WHITE = doubleArrayOf(1.0, 1.0, 1.0, 1.0)
private val BLACK = doubleArrayOf(0.0, 0.0, 0.0)
private val HEX = Regex("^[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")

/** "RRGGBB" or "RRGGBBAA" as 0..1 components; `def` when missing or malformed. */
internal fun hexToColor(hex: String?, def: DoubleArray): DoubleArray {
    if (hex == null || !HEX.matches(hex)) return def.copyOf()
    val c = DoubleArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) / 255.0 }
    return if (c.size == 3 && def.size == 4) doubleArrayOf(c[0], c[1], c[2], 1.0) else c
}

private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
private val JsonElement?.arr: JsonArray? get() = this as? JsonArray
private val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private val JsonElement?.num: Double? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
private val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
private fun JsonObject.num(key: String, def: Double): Double = this[key].num ?: def
private fun JsonObject.bool(key: String, def: Boolean): Boolean = this[key].bool ?: def
private fun JsonObject.str(key: String): String? = this[key].str
private fun JsonArray?.doubles(): DoubleArray = this?.let { a -> DoubleArray(a.size) { a[it].num ?: 0.0 } } ?: DoubleArray(0)
private fun JsonArray?.ints(): IntArray = this?.let { a -> IntArray(a.size) { (a[it] as? JsonPrimitive)?.intOrNull ?: 0 } } ?: IntArray(0)
private fun JsonObject?.entries(): List<Pair<String, JsonElement>> = this?.entries?.map { it.key to it.value } ?: emptyList()
private fun JsonElement?.objects(): List<JsonObject> = arr?.mapNotNull { it.obj } ?: emptyList()

/** A key's curve: "stepped", a number with c2..c4 (3.8) or four numbers; linear otherwise. */
private fun readCurve(key: JsonObject): Curve {
    val c = key["curve"] ?: return Curve.Linear
    if (c is JsonNull) return Curve.Linear
    if (c.str == "stepped") return Curve.Stepped
    c.num?.let { return Curve.Bezier(it, key.num("c2", 0.0), key.num("c3", 1.0), key.num("c4", 1.0)) }
    val a = c.arr
    if (a != null && a.size == 4) return Curve.Bezier(a[0].num ?: 0.0, a[1].num ?: 0.0, a[2].num ?: 0.0, a[3].num ?: 0.0)
    return Curve.Linear
}

private fun time(k: JsonObject) = k.num("time", 0.0)

private fun numericKeys(keys: List<JsonObject>, width: Int, values: (JsonObject) -> DoubleArray): NumericKeys? {
    if (keys.isEmpty()) return null
    val out = DoubleArray(keys.size * width)
    keys.forEachIndexed { i, k -> values(k).copyInto(out, i * width, 0, width) }
    return NumericKeys(DoubleArray(keys.size) { time(keys[it]) }, out, width, Array(keys.size) { readCurve(keys[it]) })
}

private fun constraintKeys(keys: List<JsonObject>, width: Int, values: (JsonObject) -> DoubleArray): ConstraintKeys? =
    numericKeys(keys, width, values)?.let { ConstraintKeys(it.times, it.values, it.width, it.curves) }

private class Reader(val root: JsonObject, val name: String) {
    val boneIndex = HashMap<String, Int>()
    val slotIndex = HashMap<String, Int>()
    var maxTime = 0.0

    fun bone(n: String?): Int = boneIndex[n] ?: fail("unknown bone '$n'")

    fun slot(n: String?): Int = slotIndex[n] ?: fail("unknown slot '$n'")

    fun read(): SkeletonData {
        val header = root["skeleton"].obj ?: fail("not a skeleton JSON file")
        val bonesJson = root["bones"].arr ?: fail("not a skeleton JSON file")

        val bones = bonesJson.mapIndexed { i, e ->
            val b = e.obj ?: fail("bone $i is not an object")
            val boneName = b.str("name") ?: fail("bone $i has no name")
            boneIndex[boneName] = i
            val parent = b.str("parent")?.let { p -> boneIndex[p] ?: fail("bone '$boneName' references unknown parent '$p'") }
            BoneData(
                index = i,
                name = boneName,
                parent = parent,
                length = b.num("length", 0.0),
                setup = Transform(
                    b.num("x", 0.0), b.num("y", 0.0), b.num("rotation", 0.0),
                    b.num("scaleX", 1.0), b.num("scaleY", 1.0), b.num("shearX", 0.0), b.num("shearY", 0.0),
                ),
                inherit = inheritOf(b.str("inherit") ?: b.str("transform")),
            )
        }

        val slots = root["slots"].objects().mapIndexed { i, s ->
            val slotName = s.str("name") ?: fail("slot $i has no name")
            slotIndex[slotName] = i
            SlotData(
                index = i,
                name = slotName,
                bone = bone(s.str("bone")),
                attachment = s.str("attachment"),
                color = hexToColor(s.str("color"), WHITE),
                dark = s.str("dark")?.let { hexToColor(it, BLACK) },
                blend = when (s.str("blend")) {
                    "additive" -> BlendMode.Additive
                    "multiply" -> BlendMode.Multiply
                    "screen" -> BlendMode.Screen
                    else -> BlendMode.Normal
                },
            )
        }

        val ik = root["ik"].objects().mapIndexed { i, c ->
            IkData(
                name = c.str("name") ?: "ik$i",
                order = c.num("order", i.toDouble()).toInt(),
                skinRequired = c.bool("skin", false),
                bones = c["bones"].arr?.map { bone(it.str) }?.toIntArray() ?: IntArray(0),
                target = bone(c.str("target")),
                mix = c.num("mix", 1.0),
                softness = c.num("softness", 0.0),
                bendPositive = c.bool("bendPositive", true),
                compress = c.bool("compress", false),
                stretch = c.bool("stretch", false),
                uniform = c.bool("uniform", false),
            )
        }
        val ikIndex = ik.withIndex().associate { it.value.name to it.index }

        // Transform constraints, then path constraints, as Rigby2D lists them. A constraint whose
        // bones are missing is left out.
        val constraints = ArrayList<ConstraintData>()
        root["transform"].objects().forEachIndexed { i, d ->
            val bones = d["bones"].arr?.map { boneIndex[it.str] } ?: return@forEachIndexed
            if (bones.isEmpty() || bones.any { it == null }) return@forEachIndexed
            val source = boneIndex[d.str("target")] ?: return@forEachIndexed
            val t = d.num("translateMix", 1.0)
            val s = d.num("scaleMix", 1.0)
            constraints += TransformConstraintData(
                name = d.str("name") ?: "transform$i",
                order = d.num("order", i.toDouble()).toInt(),
                skinRequired = d.bool("skin", false),
                bones = bones.map { it!! }.toIntArray(),
                source = source,
                offsets = doubleArrayOf(
                    d.num("rotation", 0.0), d.num("x", 0.0), d.num("y", 0.0),
                    d.num("scaleX", 0.0), d.num("scaleY", 0.0), d.num("shearY", 0.0),
                ),
                mix = doubleArrayOf(d.num("rotateMix", 1.0), t, t, s, s, d.num("shearMix", 1.0)),
                local = d.bool("local", false),
                relative = d.bool("relative", false),
            )
        }
        root["path"].objects().forEachIndexed { i, d ->
            val bones = d["bones"].arr?.map { boneIndex[it.str] } ?: return@forEachIndexed
            if (bones.isEmpty() || bones.any { it == null }) return@forEachIndexed
            val slot = slotIndex[d.str("target")] ?: return@forEachIndexed
            val t = d.num("translateMix", 1.0)
            constraints += PathConstraintData(
                name = d.str("name") ?: "path$i",
                order = d.num("order", i.toDouble()).toInt(),
                skinRequired = d.bool("skin", false),
                bones = bones.map { it!! }.toIntArray(),
                slot = slot,
                positionMode = if (d.str("positionMode") == "fixed") PositionMode.Fixed else PositionMode.Percent,
                spacingMode = when (d.str("spacingMode")) {
                    "fixed" -> SpacingMode.Fixed
                    "percent" -> SpacingMode.Percent
                    else -> SpacingMode.Length
                },
                rotateMode = when (d.str("rotateMode")) {
                    "chain" -> RotateMode.Chain
                    "chainScale" -> RotateMode.ChainScale
                    else -> RotateMode.Tangent
                },
                offsetRotation = d.num("rotation", 0.0),
                position = d.num("position", 0.0),
                spacing = d.num("spacing", 0.0),
                mixRotate = d.num("rotateMix", 1.0),
                mixX = t,
                mixY = t,
            )
        }
        val constraintIndex = constraints.withIndex().associate { it.value.name to it.index }

        // Skins: an array since 3.8, a map of skin name to slots before that.
        val skinsJson: List<Pair<String, JsonObject>> = when (val s = root["skins"]) {
            is JsonArray -> s.objects().map { (it.str("name") ?: "") to it }
            is JsonObject -> s.entries().map { (k, v) -> k to JsonObject(mapOf("attachments" to v)) }
            else -> emptyList()
        }
        var defaultSkin = Skin("default", emptyMap(), emptyList(), emptyList(), emptySet())
        val skins = ArrayList<Skin>()
        for ((skinName, skin) in skinsJson) {
            val attachments = LinkedHashMap<Int, Map<String, Attachment>>()
            for ((slotName, atts) in skin["attachments"].obj.entries()) {
                val s = slot(slotName)
                attachments[s] = atts.obj.entries().associate { (key, a) -> key to readAttachment(s, key, a.obj ?: JsonObject(emptyMap())) }
            }
            val names = (skin["transform"].arr.orEmpty() + skin["path"].arr.orEmpty()).mapNotNull { it.str }.toSet()
            val built = Skin(
                name = skinName,
                attachments = attachments,
                bones = skin["bones"].arr?.map { bone(it.str) } ?: emptyList(),
                ik = skin["ik"].arr?.mapNotNull { ikIndex[it.str] } ?: emptyList(),
                constraints = names,
            )
            if (skinName == "default") defaultSkin = built else skins += built
        }

        val events = root["events"].obj.entries().map { (eventName, e) ->
            val o = e.obj ?: JsonObject(emptyMap())
            EventData(
                name = eventName,
                int = o.num("int", 0.0).toInt(),
                float = o.num("float", 0.0),
                string = o.str("string"),
                audio = o.str("audio"),
                volume = o.num("volume", 1.0),
                balance = o.num("balance", 0.0),
            )
        }

        val animations = root["animations"].obj.entries().map { (animName, a) ->
            readAnimation(animName, a.obj ?: JsonObject(emptyMap()), slots.size, ikIndex, constraintIndex, constraints)
        }

        val bounds = listOf("x", "y", "width", "height").map { header[it].num }
        return SkeletonData(
            name = name,
            bones = bones,
            slots = slots,
            defaultSkin = defaultSkin,
            skins = skins,
            ik = ik,
            constraints = constraints,
            events = events,
            animations = animations,
            bounds = if (bounds.all { it != null }) bounds.map { it!! }.toDoubleArray() else null,
        )
    }

    fun inheritOf(mode: String?): Inherit = when (mode) {
        "onlyTranslation" -> Inherit.OnlyTranslation
        "noRotationOrReflection" -> Inherit.NoRotationOrReflection
        "noScale" -> Inherit.NoScale
        "noScaleOrReflection" -> Inherit.NoScaleOrReflection
        else -> Inherit.Normal
    }

    /** Splits a packed vertex array into unweighted floats or per-vertex weights. */
    fun decodeVertices(json: JsonArray?, count: Int): Pair<DoubleArray?, Weights?> {
        val v = json.doubles()
        if (json == null) return DoubleArray(0) to null
        if (v.size <= count * 2) return v to null
        val counts = IntArray(count)
        val bones = ArrayList<Int>()
        val values = ArrayList<Double>()
        var i = 0
        var vertex = 0
        while (i < v.size && vertex < count) {
            val n = v[i++].toInt()
            counts[vertex++] = n
            repeat(n) {
                bones += v[i++].toInt()
                values += v[i++]
                values += v[i++]
                values += v[i++]
            }
        }
        return null to Weights(counts, bones.toIntArray(), values.toDoubleArray())
    }

    fun readAttachment(slot: Int, key: String, a: JsonObject): Attachment {
        val image = a.str("path") ?: a.str("name") ?: key
        val color = hexToColor(a.str("color"), WHITE)
        val ownKey = "$slot/$key"
        fun offset() = Transform(a.num("x", 0.0), a.num("y", 0.0), a.num("rotation", 0.0), a.num("scaleX", 1.0), a.num("scaleY", 1.0))
        return when (val type = a.str("type") ?: "region") {
            "region" -> RegionAttachment(key, image, color, offset(), a.num("width", 0.0), a.num("height", 0.0))
            "point" -> PointAttachment(key, image, color, offset())
            "mesh" -> {
                val uvs = a["uvs"].arr.doubles()
                val count = uvs.size / 2
                val (vertices, weights) = decodeVertices(a["vertices"].arr, count)
                MeshAttachment(
                    key, image, color, count, vertices, weights, ownKey,
                    uvs = uvs,
                    triangles = a["triangles"].arr.ints(),
                    hull = a.num("hull", 0.0).toInt(),
                    width = a.num("width", 0.0),
                    height = a.num("height", 0.0),
                )
            }
            "linkedmesh" -> {
                val parent = a.str("parent") ?: fail("linked mesh '$key' has no parent")
                val deform = a.bool("deform", true)
                LinkedMeshAttachment(key, image, color, parent, a.str("skin"), deform, if (deform) "$slot/$parent" else ownKey)
            }
            "boundingbox", "path", "clipping" -> {
                val count = a.num("vertexCount", 0.0).toInt()
                val (vertices, weights) = decodeVertices(a["vertices"].arr, count)
                when (type) {
                    "boundingbox" -> BoundingBoxAttachment(key, image, color, count, vertices, weights, ownKey)
                    "clipping" -> ClippingAttachment(key, image, color, count, vertices, weights, ownKey, a.str("end")?.let { slot(it) })
                    else -> PathAttachment(
                        key, image, color, count, vertices, weights, ownKey,
                        closed = a.bool("closed", false),
                        constantSpeed = a.bool("constantSpeed", true),
                        lengths = a["lengths"].arr.doubles(),
                    )
                }
            }
            else -> RegionAttachment(key, image, color, offset(), a.num("width", 0.0), a.num("height", 0.0))
        }
    }

    fun track(keys: List<JsonObject>): List<JsonObject> {
        for (k in keys) maxTime = max(maxTime, time(k))
        return keys
    }

    fun readAnimation(
        animName: String,
        a: JsonObject,
        slotCount: Int,
        ikIndex: Map<String, Int>,
        constraintIndex: Map<String, Int>,
        constraints: List<ConstraintData>,
    ): Animation {
        maxTime = 0.0

        val bones = a["bones"].obj.entries().map { (boneName, timelines) ->
            val t = timelines.obj
            fun xy(kind: String, def: Double) = numericKeys(track(t?.get(kind).objects()), 2) { k -> doubleArrayOf(k.num("x", def), k.num("y", def)) }
            BoneTimeline(
                bone = bone(boneName),
                rotate = numericKeys(track(t?.get("rotate").objects()), 1) { k -> doubleArrayOf(k["value"].num ?: k["angle"].num ?: 0.0) },
                translate = xy("translate", 0.0),
                scale = xy("scale", 1.0),
                shear = xy("shear", 0.0),
            )
        }

        val slots = a["slots"].obj.entries().map { (slotName, timelines) ->
            val t = timelines.obj
            val attachment = track(t?.get("attachment").objects())
            SlotTimeline(
                slot = slot(slotName),
                attachmentTimes = attachment.takeIf { it.isNotEmpty() }?.let { keys -> DoubleArray(keys.size) { time(keys[it]) } },
                attachmentNames = attachment.takeIf { it.isNotEmpty() }?.map { it.str("name") }?.toTypedArray(),
                color = numericKeys(track(t?.get("color").objects()), 4) { k -> hexToColor(k.str("color"), WHITE) },
                twoColor = numericKeys(track(t?.get("twoColor").objects()), 7) { k ->
                    hexToColor(k.str("light"), WHITE) + hexToColor(k.str("dark"), BLACK)
                },
            )
        }

        val deform = ArrayList<DeformTimeline>()
        for ((skinName, slotsJson) in a["deform"].obj.entries()) {
            for ((slotName, atts) in slotsJson.obj.entries()) {
                for ((attName, keysJson) in atts.obj.entries()) {
                    val keys = track(keysJson.objects())
                    if (keys.isEmpty()) continue
                    deform += DeformTimeline(
                        skin = skinName.takeIf { it != "default" },
                        slot = slot(slotName),
                        attachment = attName,
                        times = DoubleArray(keys.size) { time(keys[it]) },
                        offsets = IntArray(keys.size) { keys[it].num("offset", 0.0).toInt() },
                        vertices = Array(keys.size) { keys[it]["vertices"].arr.doubles() },
                        curves = Array(keys.size) { readCurve(keys[it]) },
                    )
                }
            }
        }

        val drawOrderKeys = track((a["drawOrder"] ?: a["draworder"]).objects())
        val drawOrder = drawOrderKeys.takeIf { it.isNotEmpty() }?.let { keys ->
            DrawOrderTimeline(
                times = DoubleArray(keys.size) { time(keys[it]) },
                orders = Array(keys.size) { i ->
                    keys[i]["offsets"].arr?.let { offsets ->
                        val o = offsets.objects()
                        applyDrawOrder(slotCount, IntArray(o.size) { slot(o[it].str("slot")) }, IntArray(o.size) { o[it].num("offset", 0.0).toInt() })
                    }
                },
            )
        }

        val events = track(a["events"].objects()).map { e ->
            EventKey(
                time = time(e),
                name = e.str("name") ?: "",
                int = e["int"].num?.toInt(),
                float = e["float"].num,
                string = e.str("string"),
                volume = e["volume"].num,
                balance = e["balance"].num,
            )
        }

        val ik = a["ik"].obj.entries().mapNotNull { (ikName, keysJson) ->
            val constraint = ikIndex[ikName] ?: return@mapNotNull null
            IkTimeline(
                constraint,
                track(keysJson.objects()).map { k ->
                    IkKey(
                        time = time(k),
                        mix = k.num("mix", 1.0),
                        softness = k.num("softness", 0.0),
                        bendPositive = k.bool("bendPositive", true),
                        compress = k.bool("compress", false),
                        stretch = k.bool("stretch", false),
                    )
                },
            )
        }

        val transform = a["transform"].obj.entries().mapNotNull { (cName, keysJson) ->
            val index = constraintIndex[cName] ?: return@mapNotNull null
            if (constraints[index] !is TransformConstraintData) return@mapNotNull null
            val keys = constraintKeys(track(keysJson.objects()), 6) { k ->
                val t = k.num("translateMix", 1.0)
                val s = k.num("scaleMix", 1.0)
                doubleArrayOf(k.num("rotateMix", 1.0), t, t, s, s, k.num("shearMix", 1.0))
            } ?: return@mapNotNull null
            TransformTimeline(index, keys)
        }

        val path = a["path"].obj.entries().mapNotNull { (cName, timelines) ->
            val index = constraintIndex[cName] ?: return@mapNotNull null
            if (constraints[index] !is PathConstraintData) return@mapNotNull null
            val t = timelines.obj
            PathTimeline(
                constraint = index,
                position = constraintKeys(track(t?.get("position").objects()), 1) { k -> doubleArrayOf(k["value"].num ?: k["position"].num ?: 0.0) },
                spacing = constraintKeys(track(t?.get("spacing").objects()), 1) { k -> doubleArrayOf(k["value"].num ?: k["spacing"].num ?: 0.0) },
                mix = constraintKeys(track(t?.get("mix").objects()), 3) { k ->
                    val x = k.num("translateMix", 1.0)
                    doubleArrayOf(k.num("rotateMix", 1.0), x, x)
                },
            )
        }

        return Animation(animName, maxTime, bones, slots, deform, drawOrder, events, ik, transform, path)
    }
}
