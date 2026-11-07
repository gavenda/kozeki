package dev.gavenda.kozeki.data.sync

import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.ReadingState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The rows go over the wire as the server's `shared/tables.ts` reads them. */
class SyncJsonTest {

    @Test
    fun bookKeepsItsBookkeepingNestedAndLeavesNullsOut() {
        val book = BookEntity(id = "b1", title = "Title", sync = SyncStamp(createdAt = 1, updatedAt = 2))
        val json = SyncJson.parseToJsonElement(SyncJson.encodeToString(book)).jsonObject

        assertEquals("2", json["sync"]!!.jsonObject["updatedAt"]!!.jsonPrimitive.content)
        assertFalse("subtitle" in json)
        assertEquals("PLANNED", json["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun aRowFromTheServerFillsWhatItLeftOutWithDefaults() {
        // As the server sends it: no nulls, none of the comic columns, booleans read back from integers.
        val text = """{"id":"b1","title":"Title","acquisition":"PURCHASED","isFavorite":true,"customCover":false,
            "sync":{"createdAt":1,"updatedAt":2,"deletedAt":3}}"""
        val book = SyncJson.decodeFromString<BookEntity>(text)

        assertEquals(Acquisition.PURCHASED, book.acquisition)
        assertEquals(ReadingState.PLANNED, book.state)
        assertEquals(0, book.newChapters)
        assertEquals(emptyList<String>(), book.authors)
        assertEquals(3L, book.sync.deletedAt)
    }

    @Test
    fun aSyncAnswerWithSomeTablesMissingStillReads() {
        val answer = SyncJson.decodeFromString<SyncResponse>("""{"rows":{"notes":[]},"cursor":7,"more":true}""")
        assertEquals(7L, answer.cursor)
        assertEquals(0, answer.rows.size)
    }
}
