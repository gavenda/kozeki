package dev.gavenda.kozeki.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(StringList, value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        runCatching { Json.decodeFromString(StringList, value) }.getOrDefault(emptyList())

    private companion object {
        val StringList = ListSerializer(String.serializer())
    }
}
