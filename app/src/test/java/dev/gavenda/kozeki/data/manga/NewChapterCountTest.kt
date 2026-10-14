package dev.gavenda.kozeki.data.manga

import dev.gavenda.kozeki.data.db.MangaChapterEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.Test
import org.junit.Assert.assertEquals

class NewChapterCountTest {

    private fun row(url: String) = MangaChapterEntity(bookId = "b", url = url, name = url, sync = SyncStamp.created(1))

    private fun remote(vararg urls: String) = urls.map { url -> SChapter.create().apply { this.url = url; name = url } }

    private fun count(existing: List<MangaChapterEntity>, vararg listed: String) =
        newChapterCount(existing, mergeChapters("b", existing, remote(*listed), now = 2))

    @Test
    fun `a manga being added has no news`() {
        assertEquals(0, count(emptyList(), "1", "2", "3"))
    }

    @Test
    fun `chapters not there before are counted`() {
        assertEquals(2, count(listOf(row("1"), row("2")), "4", "3", "2", "1"))
    }

    @Test
    fun `nothing changed counts nothing`() {
        assertEquals(0, count(listOf(row("1"), row("2")), "2", "1"))
    }

    @Test
    fun `a chapter that left and came back is not news`() {
        val gone = row("1").let { it.copy(sync = it.sync.deleted(1)) }
        assertEquals(0, count(listOf(gone, row("2")), "2", "1"))
    }
}
