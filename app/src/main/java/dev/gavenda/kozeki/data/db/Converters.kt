package dev.gavenda.kozeki.data.db

import androidx.room.TypeConverter
import dev.gavenda.kozeki.data.metadata.AuthorRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(StringList, value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        runCatching { Json.decodeFromString(StringList, value) }.getOrDefault(emptyList())

    @TypeConverter
    fun authorRefsToJson(value: List<AuthorRef>): String = Json.encodeToString(AuthorRefList, value)

    @TypeConverter
    fun jsonToAuthorRefs(value: String): List<AuthorRef> =
        runCatching { Json.decodeFromString(AuthorRefList, value) }.getOrDefault(emptyList())

    private companion object {
        val StringList = ListSerializer(String.serializer())
        val AuthorRefList: KSerializer<List<AuthorRef>> = ListSerializer(AuthorRef.serializer())
    }
}
