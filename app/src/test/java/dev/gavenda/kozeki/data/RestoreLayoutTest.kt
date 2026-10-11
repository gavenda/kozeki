package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.backup.LibraryBackup
import dev.gavenda.kozeki.data.backup.RestoreLayout
import dev.gavenda.kozeki.data.backup.layOutRestore
import dev.gavenda.kozeki.data.db.BookEntity
import dev.gavenda.kozeki.data.db.SyncStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreLayoutTest {

    private fun book(id: String, hash: String? = null, cover: String? = null, custom: Boolean = false) = BookEntity(
        id = id,
        title = "Book $id",
        fileHash = hash,
        coverFile = cover,
        customCover = custom,
        sync = SyncStamp.created(0),
    )

    /** Lays [incoming] out over a device holding [here], with EPUBs for [epubs] and the pictures [covers]. */
    private fun layOut(
        incoming: List<BookEntity>,
        here: List<BookEntity> = emptyList(),
        epubs: Set<String> = emptySet(),
        covers: Set<String> = emptySet(),
    ): RestoreLayout = layOutRestore(incoming, here, hasEpub = { it in epubs }, hasCover = { it in covers })

    private fun RestoreLayout.plan() =
        plan(LibraryBackup(schemaVersion = 6, createdAt = 1, appVersion = "1.7", device = "Pixel 9", books = books))

    @Test
    fun `a book that is here under its own ID keeps its EPUB`() {
        val layout = layOut(
            incoming = listOf(book("a", hash = "h1")),
            here = listOf(book("a", hash = "h1")),
            epubs = setOf("a"),
        )

        assertEquals(mapOf("a" to "a"), layout.epubs)
        assertEquals(0, layout.plan().booksWithoutFile)
        assertEquals(0, layout.plan().booksRemoved)
    }

    @Test
    fun `an EPUB imported before the restore goes to the book it is the file of`() {
        // A new install: the same file was imported first, and so sits under an ID of its own.
        val layout = layOut(
            incoming = listOf(book("old", hash = "h1")),
            here = listOf(book("new", hash = "h1")),
            epubs = setOf("new"),
        )

        assertEquals(mapOf("new" to "old"), layout.epubs)
        assertEquals(0, layout.plan().booksWithoutFile)
        // The book that held the file is the same book, not one that is lost.
        assertEquals(0, layout.plan().booksRemoved)
    }

    @Test
    fun `a book whose EPUB is nowhere on the device waits for it`() {
        val layout = layOut(
            incoming = listOf(book("a", hash = "h1"), book("b", hash = "h2"), book("wish")),
            here = listOf(book("a", hash = "h1")),
            // The record of a is here and its file is not.
            epubs = emptySet(),
        )

        assertTrue(layout.epubs.isEmpty())
        assertEquals(2, layout.plan().booksWithoutFile)
    }

    @Test
    fun `books that are only here are counted as removed and their EPUBs are not kept`() {
        val layout = layOut(
            incoming = listOf(book("a", hash = "h1")),
            here = listOf(book("a", hash = "h1"), book("b", hash = "h2"), book("wish")),
            epubs = setOf("a", "b"),
        )

        assertEquals(mapOf("a" to "a"), layout.epubs)
        assertEquals(2, layout.plan().booksRemoved)
    }

    @Test
    fun `one EPUB is given to one book only`() {
        val layout = layOut(
            incoming = listOf(book("x", hash = "h1"), book("a", hash = "h1")),
            here = listOf(book("a", hash = "h1")),
            epubs = setOf("a"),
        )

        // The book that already has it comes first, wherever it stands in the backup.
        assertEquals(mapOf("a" to "a"), layout.epubs)
        assertEquals(1, layout.plan().booksWithoutFile)
    }

    @Test
    fun `a file attached to a book since is not taken for the one in the backup`() {
        val layout = layOut(
            incoming = listOf(book("a", hash = "h1")),
            here = listOf(book("a", hash = "other")),
            epubs = setOf("a"),
        )

        assertTrue(layout.epubs.isEmpty())
        assertEquals(1, layout.plan().booksWithoutFile)
    }

    @Test
    fun `a cover whose picture is on the device stays`() {
        val layout = layOut(
            incoming = listOf(book("a", cover = "a-1.jpg", custom = true)),
            here = listOf(book("a", cover = "a-1.jpg", custom = true)),
            covers = setOf("a-1.jpg"),
        )

        assertEquals("a-1.jpg", layout.books.single().coverFile)
        assertTrue(layout.books.single().customCover)
    }

    @Test
    fun `a cover whose picture is not on the device is dropped, chosen or not`() {
        val layout = layOut(incoming = listOf(book("a", cover = "a-1.jpg", custom = true)))

        assertNull(layout.books.single().coverFile)
        // Otherwise the book would offer to remove a cover photo it does not have.
        assertFalse(layout.books.single().customCover)
    }

    @Test
    fun `a book takes the cover of the one it replaces when its own picture is gone`() {
        val layout = layOut(
            incoming = listOf(book("a", cover = "a-1.jpg"), book("old", hash = "h1", cover = "old-1.jpg")),
            here = listOf(book("a", cover = "a-2.jpg", custom = true), book("new", hash = "h1", cover = "new-1.jpg")),
            epubs = setOf("new"),
            covers = setOf("a-2.jpg", "new-1.jpg"),
        )

        val (a, old) = layout.books
        // Changed since the backup was made, and the picture the backup names was deleted then.
        assertEquals("a-2.jpg", a.coverFile)
        assertTrue(a.customCover)
        // Came with the EPUB that was imported under another ID.
        assertEquals("new-1.jpg", old.coverFile)
        assertFalse(old.customCover)
    }
}
