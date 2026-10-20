package dev.gavenda.kozeki.data

import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.metadata.sortedBySeries
import dev.gavenda.kozeki.data.model.MetadataSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesSortTest {

    private fun book(id: String, series: String? = null, number: Double? = null) = BookMetadata(
        source = MetadataSource.HARDCOVER,
        sourceId = id,
        title = id,
        series = series,
        seriesNumber = number,
    )

    @Test
    fun `groups each series by name and orders it by number, books in no series last`() {
        val results = listOf(
            book("loose"),
            book("tearmoon 3", "Tearmoon Empire", 3.0),
            book("dune 2", "dune", 2.0),
            book("tearmoon side", "Tearmoon Empire"),
            book("tearmoon 1", "Tearmoon Empire", 1.0),
            book("dune 1.5", "Dune", 1.5),
            book("another loose"),
        )

        assertEquals(
            listOf("dune 1.5", "dune 2", "tearmoon 1", "tearmoon 3", "tearmoon side", "loose", "another loose"),
            results.sortedBySeries().map { it.sourceId },
        )
    }
}
