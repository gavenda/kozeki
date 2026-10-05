package dev.gavenda.kozeki.ui

import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.library.LibrarySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {

    private val wished = Book(id = "wished", title = "Piranesi", acquisition = Acquisition.WISHLIST)
    private val bought = Book(
        id = "bought",
        title = "The Dispossessed",
        state = ReadingState.READING,
        acquisition = Acquisition.PURCHASED,
        isFavorite = true,
    )
    private val downloaded = Book(
        id = "downloaded",
        title = "Solaris",
        state = ReadingState.READING,
        acquisition = Acquisition.DOWNLOADED,
        inLibrary = true,
    )

    @Test
    fun `a wishlisted book sits out favorites and reading states`() {
        val selection = LibrarySelection(listOf(wished, bought))
        assertEquals(listOf(bought), selection.favoritable)
        assertEquals(listOf(bought), selection.stateChangeable)
        assertEquals(ReadingState.READING, selection.sharedState)
    }

    @Test
    fun `favorites are only all set when every eligible book is one`() {
        assertTrue(LibrarySelection(listOf(wished, bought)).allFavorite)
        assertFalse(LibrarySelection(listOf(bought, downloaded)).allFavorite)
        assertFalse(LibrarySelection(listOf(wished)).allFavorite)
    }

    @Test
    fun `books in different states share none`() {
        val selection = LibrarySelection(listOf(bought, downloaded.copy(state = ReadingState.PAUSED)))
        assertNull(selection.sharedState)
    }

    @Test
    fun `purchases are told apart from wishes and downloads`() {
        val selection = LibrarySelection(listOf(wished, bought, downloaded))
        assertEquals(listOf(bought), selection.purchased)
        assertEquals(listOf(wished, downloaded), selection.notPurchased)
        assertEquals(setOf("wished", "bought", "downloaded"), selection.ids)
    }
}
