package dev.gavenda.kozeki.ui.addbook

import dev.gavenda.kozeki.ui.addbook.AddBookViewModel.Companion.mangaSourceLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaSourceLabelsTest {

    @Test
    fun `a name that is alone is shown as it is`() {
        assertEquals(listOf("Asura Scans", "Comix"), mangaSourceLabels(listOf("Asura Scans" to "en", "Comix" to "en")))
    }

    @Test
    fun `sources with the same name are told apart by language`() {
        assertEquals(
            listOf("MangaDex · EN", "MangaDex · JA", "Comix"),
            mangaSourceLabels(listOf("MangaDex" to "en", "MangaDex" to "ja", "Comix" to "en")),
        )
    }

    @Test
    fun `a clash with no language to tell it by stays as the name`() {
        assertEquals(listOf("Site", "Site · EN"), mangaSourceLabels(listOf("Site" to "all", "Site" to "en")))
    }
}
