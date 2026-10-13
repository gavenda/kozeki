package dev.gavenda.kozeki.data.manga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A source can ask the app to keep a small JSON object with a manga or a chapter and hand it back
 * on later calls. It is kept as the text of that object, and as nothing when it is empty.
 */
internal object MangaMemo {

    fun encode(memo: JsonObject): String? = memo.takeIf { it.isNotEmpty() }?.toString()

    fun decode(text: String?): JsonObject =
        text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: JsonObject(emptyMap())
}
