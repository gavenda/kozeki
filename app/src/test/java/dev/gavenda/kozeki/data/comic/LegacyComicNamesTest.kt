package dev.gavenda.kozeki.data.comic

import dev.gavenda.kozeki.data.model.MetadataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Comics were called manga. What was saved under that name is still understood. */
class LegacyComicNamesTest {

    @Test
    fun `a source saved as manga is comics`() {
        assertEquals(MetadataSource.COMICS, MetadataSource.named("MANGA"))
        assertEquals(MetadataSource.COMICS, MetadataSource.named("COMICS"))
        assertEquals(MetadataSource.HARDCOVER, MetadataSource.named("HARDCOVER"))
        assertNull(MetadataSource.named("NOTHING"))
        assertNull(MetadataSource.named(null))
    }

    @Test
    fun `a cover linked under the old name still parses`() {
        val url = ComicCover.url(42L, "https://example.com/a b.jpg")
        val legacy = url.replace("kozeki-comic://", "kozeki-manga://")

        assertEquals(ComicCover.Parsed(42L, "https://example.com/a b.jpg"), ComicCover.parse(url))
        assertEquals(ComicCover.parse(url), ComicCover.parse(legacy))
        assertNull(ComicCover.parse("https://example.com/a.jpg"))
    }
}
