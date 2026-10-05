package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * As many columns as fit at [minCellWidth], but never fewer than [minColumns]. On a phone that is
 * the minimum; wider windows get more columns instead of bigger covers.
 */
class AdaptiveColumns(
    private val minColumns: Int,
    private val minCellWidth: Dp,
) : GridCells {

    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val fitting = (availableSize + spacing) / (minCellWidth.roundToPx() + spacing)
        val count = maxOf(fitting, minColumns)
        val usable = availableSize - spacing * (count - 1)
        val size = usable / count
        val remainder = usable % count
        return List(count) { index -> size + if (index < remainder) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean =
        other is AdaptiveColumns && other.minColumns == minColumns && other.minCellWidth == minCellWidth

    override fun hashCode(): Int = 31 * minColumns + minCellWidth.hashCode()
}
