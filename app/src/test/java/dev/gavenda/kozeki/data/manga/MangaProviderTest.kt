package dev.gavenda.kozeki.data.manga

import dev.gavenda.kozeki.data.manga.MangaProvider.Companion.toMetadata
import dev.gavenda.kozeki.data.model.MetadataSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaProviderTest {

    private val source = object : CatalogueSource {
        override val id = 42L
        override val name = "Fake"
        override val lang = "en"
        override val supportsLatest = false
        override fun getFilterList() = FilterList()
        override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
            MangasPage(emptyList(), false)
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ) = SMangaUpdate(manga, chapters)
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    @Test
    fun `source id round trips and keeps colons of the url`() {
        val id = MangaProvider.sourceId(42L, "/manga/a:b")
        assertEquals(42L to "/manga/a:b", MangaProvider.parseSourceId(id))
    }

    @Test
    fun `source id of another catalogue is not a manga`() {
        assertNull(MangaProvider.parseSourceId("OL123W"))
        assertNull(MangaProvider.parseSourceId("42:"))
    }

    @Test
    fun `creators merge author and artist without repeats`() {
        val manga = SManga.create().apply {
            author = "Oda, Someone"
            artist = "Oda"
        }
        assertEquals(listOf("Oda", "Someone"), MangaProvider.creators(manga))
    }

    @Test
    fun `a manga becomes book metadata of the manga source`() {
        val manga = SManga.create().apply {
            url = "/m/1"
            title = "One Book"
            author = "Writer"
            genre = "Action, Drama,"
            description = "  "
            thumbnail_url = "https://example.com/c.jpg"
        }
        val metadata = manga.toMetadata(source)
        assertEquals(MetadataSource.MANGA, metadata.source)
        assertEquals("42:/m/1", metadata.sourceId)
        assertEquals(listOf("Writer"), metadata.authors)
        assertEquals(listOf("Action", "Drama"), metadata.categories)
        assertNull(metadata.description)
        assertEquals("en", metadata.language)
        assertEquals(MangaCover.Parsed(42L, "https://example.com/c.jpg"), MangaCover.parse(metadata.coverUrl!!))
    }

    @Test
    fun `a cover url that names no source is left alone`() {
        assertNull(MangaCover.parse("https://example.com/c.jpg"))
    }

    @Test
    fun `cover url round trips an image url with a query`() {
        val url = "https://example.com/a b.jpg?x=1&y=2"
        assertEquals(MangaCover.Parsed(7L, url), MangaCover.parse(MangaCover.url(7L, url)))
    }
}
