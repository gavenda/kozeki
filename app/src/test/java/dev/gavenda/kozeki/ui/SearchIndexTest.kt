package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.search.SearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    private fun book(
        id: String,
        title: String,
        vararg authors: String,
        subtitle: String? = null,
        inLibrary: Boolean = false,
        acquisition: Acquisition = Acquisition.WISHLIST,
    ) = Book(
        id = id,
        title = title,
        subtitle = subtitle,
        authors = authors.toList(),
        inLibrary = inLibrary,
        acquisition = acquisition,
    )

    // An imported EPUB is a download until a purchase is recorded; either way it stays in the library.
    private fun imported(
        id: String,
        title: String,
        vararg authors: String,
        acquisition: Acquisition = Acquisition.DOWNLOADED,
    ) = book(id, title, *authors, inLibrary = true, acquisition = acquisition)

    private val index = SearchIndex(
        listOf(
            imported("ender", "Ender's Game", "Orson Scott Card"),
            book("miserables", "Les Misérables", "Victor Hugo"),
            book("dispossessed", "The Dispossessed", "Ursula K. Le Guin", subtitle = "An Ambiguous Utopia"),
            book("lathe", "The Lathe of Heaven", "Ursula K. Le Guin", acquisition = Acquisition.PURCHASED),
            imported("darkness", "The Left Hand of Darkness", "Ursula K. Le Guin", acquisition = Acquisition.PURCHASED),
        ),
    )

    private fun found(query: String): List<String> =
        index.search(query).run { library + wishlist + purchased }.map { it.id }

    @Test
    fun `results are split by where each book lives`() {
        val results = index.search("le guin")
        assertEquals(listOf("darkness"), results.library.map { it.id })
        assertEquals(listOf("dispossessed"), results.wishlist.map { it.id })
        assertEquals(listOf("lathe"), results.purchased.map { it.id })
    }

    @Test
    fun `every word has to appear, in the title, the subtitle or an author`() {
        assertEquals(listOf("darkness"), found("guin hand"))
        assertEquals(listOf("dispossessed"), found("ursula utopia"))
        assertEquals(emptyList<String>(), found("ursula hugo"))
    }

    @Test
    fun `case, accents and punctuation make no difference`() {
        assertEquals(listOf("miserables"), found("MISERABLES"))
        assertEquals(listOf("miserables"), found("misérables, hugo"))
        assertEquals(listOf("ender"), found("enders game"))
        assertEquals(listOf("ender"), found("Ender’s"))
    }

    @Test
    fun `part of a word is enough, and each group keeps the order its books came in`() {
        assertEquals(listOf("dispossessed"), found("posses"))

        val results = index.search("e")
        assertEquals(listOf("ender", "darkness"), results.library.map { it.id })
        assertEquals(listOf("miserables", "dispossessed"), results.wishlist.map { it.id })
        assertEquals(listOf("lathe"), results.purchased.map { it.id })
    }

    @Test
    fun `a query without a word in it finds nothing`() {
        assertTrue(index.search("").isEmpty)
        assertTrue(index.search("   ").isEmpty)
        assertTrue(index.search("?!").isEmpty)
        assertEquals("", index.search("   ").query)
    }
}
