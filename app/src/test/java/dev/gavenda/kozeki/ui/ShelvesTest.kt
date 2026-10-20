package dev.gavenda.kozeki.ui

import androidx.compose.ui.state.ToggleableState
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.library.ShelfChange
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.data.model.Shelf
import dev.gavenda.kozeki.ui.library.LibraryFilter
import dev.gavenda.kozeki.ui.library.shelfChange
import dev.gavenda.kozeki.ui.library.shelfGroups
import dev.gavenda.kozeki.ui.library.shelvedIds
import dev.gavenda.kozeki.ui.library.shelfState
import dev.gavenda.kozeki.ui.library.tapped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelvesTest {

    private val one = Shelf("one", "One", 0)
    private val two = Shelf("two", "Two", 0)
    private val reading = Book(id = "r", title = "R", state = ReadingState.READING, acquisition = Acquisition.DOWNLOADED)
    private val done = Book(id = "d", title = "D", state = ReadingState.COMPLETED, acquisition = Acquisition.DOWNLOADED)
    private val planned = Book(id = "p", title = "P", state = ReadingState.PLANNED, acquisition = Acquisition.DOWNLOADED)

    @Test
    fun `each shelf groups its own books by state`() {
        val groups = shelfGroups(
            listOf(one, two),
            mapOf("one" to setOf("d", "r"), "two" to setOf("p")),
            listOf(reading, planned, done),
            LibraryFilter.ALL,
        )

        assertEquals(listOf(one, two), groups.map { it.shelf })
        assertEquals(
            listOf(LibraryFilter.READING, LibraryFilter.COMPLETED),
            groups[0].sections.map { it.heading },
        )
        assertEquals(listOf(planned), groups[1].books)
    }

    @Test
    fun `a book on two shelves is under both`() {
        val groups = shelfGroups(
            listOf(one, two),
            mapOf("one" to setOf("r"), "two" to setOf("r")),
            listOf(reading),
            LibraryFilter.ALL,
        )

        assertEquals(listOf(listOf(reading), listOf(reading)), groups.map { it.books })
    }

    @Test
    fun `a shelf the filter leaves nothing of is dropped`() {
        val groups = shelfGroups(
            listOf(one, two),
            mapOf("one" to setOf("r"), "two" to setOf("d")),
            listOf(reading),
            LibraryFilter.READING,
        )

        assertEquals(listOf(one), groups.map { it.shelf })
        assertEquals(listOf(reading), groups.single().books)
    }

    @Test
    fun `an empty shelf shows under all books only`() {
        assertEquals(listOf(one), shelfGroups(listOf(one), emptyMap(), emptyList(), LibraryFilter.ALL).map { it.shelf })
        assertTrue(shelfGroups(listOf(one), emptyMap(), emptyList(), LibraryFilter.READING).isEmpty())
    }

    @Test
    fun `books on some shelf are the shelved ones`() {
        assertEquals(setOf("a", "b"), shelvedIds(mapOf("x" to setOf("a"), "y" to setOf("a", "b"))))
    }

    @Test
    fun `a shelf is on, off or in between by how many picked books are on it`() {
        assertEquals(ToggleableState.Off, shelfState(onShelf = 0, picked = 3))
        assertEquals(ToggleableState.Indeterminate, shelfState(onShelf = 2, picked = 3))
        assertEquals(ToggleableState.On, shelfState(onShelf = 3, picked = 3))
    }

    @Test
    fun `a tap puts all on a shelf unless all are already there`() {
        assertEquals(ToggleableState.On, ToggleableState.Off.tapped())
        assertEquals(ToggleableState.On, ToggleableState.Indeterminate.tapped())
        assertEquals(ToggleableState.Off, ToggleableState.On.tapped())
    }

    @Test
    fun `only the shelves that changed are added to or removed from`() {
        val initial = mapOf(
            "off" to ToggleableState.Off,
            "on" to ToggleableState.On,
            "some" to ToggleableState.Indeterminate,
            "same" to ToggleableState.On,
        )
        val chosen = mapOf(
            "off" to ToggleableState.On,
            "on" to ToggleableState.Off,
            "some" to ToggleableState.Indeterminate,
            "same" to ToggleableState.On,
        )

        assertEquals(ShelfChange(add = setOf("off"), remove = setOf("on")), shelfChange(initial, chosen))
    }

    @Test
    fun `a shelf with some of the books that is tapped twice is back where it started`() {
        val initial = mapOf("some" to ToggleableState.Indeterminate)
        val tappedOnce = initial.mapValues { it.value.tapped() }
        assertEquals(setOf("some"), shelfChange(initial, tappedOnce).add)

        val tappedTwice = tappedOnce.mapValues { it.value.tapped() }
        assertEquals(setOf("some"), shelfChange(initial, tappedTwice).remove)
    }

    @Test
    fun `nothing changed is an empty change`() {
        assertTrue(shelfChange(mapOf("x" to ToggleableState.On), emptyMap()).isEmpty)
    }
}
