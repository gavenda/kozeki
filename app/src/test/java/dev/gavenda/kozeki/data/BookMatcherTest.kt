package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.metadata.BookMatcher
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.MetadataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMatcherTest {

    private fun result(
        id: String,
        title: String,
        vararg authors: String,
        subtitle: String? = null,
        coverUrl: String? = null,
        description: String? = null,
    ) = BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = id,
        title = title,
        subtitle = subtitle,
        authors = authors.toList(),
        coverUrl = coverUrl,
        description = description,
    )

    @Test
    fun `an exact title and author is a confident match`() {
        val ranked = BookMatcher.rank(
            title = "The Left Hand of Darkness",
            authors = listOf("Ursula K. Le Guin"),
            candidates = listOf(
                result("a", "The Left Hand of Darkness", "Ursula K. Le Guin"),
                result("b", "The Dispossessed", "Ursula K. Le Guin"),
            ),
        )
        assertEquals("a", BookMatcher.confidentMatch(ranked)?.sourceId)
    }

    @Test
    fun `author name order, punctuation and initials do not matter`() {
        assertTrue(BookMatcher.authorSimilarity("Tolkien, J. R. R.", "J.R.R. Tolkien") > 0.95)
        assertTrue(BookMatcher.authorSimilarity("Ursula Le Guin", "Ursula K. Le Guin") > 0.8)
        assertTrue(BookMatcher.authorSimilarity("Stephen King", "Arkady Martine") < 0.1)
    }

    @Test
    fun `subtitles and diacritics do not prevent a match`() {
        val candidate = result("a", "Les Misérables", "Victor Hugo", subtitle = "A Novel")
        assertTrue(BookMatcher.score("Les Miserables: A Novel", listOf("Victor Hugo"), candidate) >= BookMatcher.CONFIDENT)
    }

    @Test
    fun `several editions of one work still match, picking the most complete`() {
        val ranked = BookMatcher.rank(
            title = "Dune",
            authors = listOf("Frank Herbert"),
            candidates = listOf(
                result("bare", "Dune", "Frank Herbert"),
                result("full", "Dune", "Frank Herbert", coverUrl = "https://example.org/c.jpg", description = "Arrakis."),
            ),
        )
        assertEquals("full", BookMatcher.confidentMatch(ranked)?.sourceId)
    }

    @Test
    fun `two different books scoring alike are left for the user to choose`() {
        val ranked = BookMatcher.rank(
            title = "Foundation",
            authors = emptyList(),
            candidates = listOf(
                result("a", "Foundation", "Isaac Asimov"),
                result("b", "Foundation", "Mercedes Lackey"),
            ),
        )
        assertTrue(ranked.isNotEmpty())
        assertNull(BookMatcher.confidentMatch(ranked))
    }

    @Test
    fun `a title without an author on either side is never confident`() {
        val ranked = BookMatcher.rank("Emma", emptyList(), listOf(result("a", "Emma")))
        assertNotNull(ranked.firstOrNull())
        assertNull(BookMatcher.confidentMatch(ranked))
    }

    @Test
    fun `unrelated results are dropped altogether`() {
        val ranked = BookMatcher.rank(
            title = "Piranesi",
            authors = listOf("Susanna Clarke"),
            candidates = listOf(result("a", "A Brief History of Time", "Stephen Hawking")),
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `a title names its volume in several ways`() {
        assertEquals(2, BookMatcher.volume("Reincarnated as a Sword (Light Novel), Vol. 2"))
        assertEquals(2, BookMatcher.volume("Dune: Book 02"))
        assertEquals(12, BookMatcher.volume("Spice and Wolf, Volume 12"))
        assertEquals(3, BookMatcher.volume("Mistborn 3"))
        assertNull(BookMatcher.volume("The Left Hand of Darkness"))
    }

    @Test
    fun `another volume of the series is not a confident match`() {
        val ranked = BookMatcher.rank(
            title = "Reincarnated as a Sword (Light Novel), Vol. 2",
            authors = listOf("Yuu Tanaka"),
            candidates = listOf(
                result("v1", "Reincarnated as a Sword (Light Novel), Vol. 1", "Yuu Tanaka", coverUrl = "c", description = "d"),
                result("v2", "Reincarnated as a Sword (Light Novel), Vol. 2", "Yuu Tanaka"),
                result("v3", "Reincarnated as a Sword (Light Novel), Vol. 3", "Yuu Tanaka", coverUrl = "c", description = "d"),
            ),
        )
        assertEquals("v2", BookMatcher.confidentMatch(ranked)?.sourceId)
    }

    @Test
    fun `a title without a volume is not matched to one of several volumes`() {
        val ranked = BookMatcher.rank(
            title = "Reincarnated as a Sword",
            authors = listOf("Yuu Tanaka"),
            candidates = listOf(
                result("v1", "Reincarnated as a Sword (Light Novel), Vol. 1", "Yuu Tanaka"),
                result("v2", "Reincarnated as a Sword (Light Novel), Vol. 2", "Yuu Tanaka"),
            ),
        )
        assertNull(BookMatcher.confidentMatch(ranked))
    }
}
