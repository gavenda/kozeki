package dev.gavenda.kozeki.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.AllTimeStats
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.StatTile
import dev.gavenda.kozeki.ui.formatDate
import dev.gavenda.kozeki.ui.formatDuration
import kotlin.math.roundToInt

/** As many years as a phone has room to name under a chart. */
private const val YEAR_LABELS_SHOWN = 6

/** The all-time view: what has added up since the start, then the year view's charts with a column per year. */
internal fun LazyListScope.allTimeItems(stats: AllTimeStats, onBookClick: (Book) -> Unit) {
    if (stats.isEmpty) {
        item(key = "all-time-empty") {
            EmptyState(
                icon = Icons.Rounded.AutoStories,
                title = stringResource(R.string.stats_all_time_empty_title),
                message = stringResource(R.string.stats_all_time_empty_message),
            )
        }
        return
    }

    item(key = "all-time-summary") { AllTimeSummary(stats) }
    item(key = "all-time-tiles") { AllTimeTiles(stats) }
    if (stats.years.isNotEmpty()) {
        val years = stats.years.map { it.year }
        item(key = "all-time-books-chart") {
            YearlyChart(
                title = stringResource(R.string.stats_books_per_year),
                years = years,
                values = stats.years.map { it.completed.toFloat() },
                valueText = { pluralStringResource(R.plurals.book_count, it.roundToInt(), it.roundToInt()) },
                axisValue = { it.roundToInt().toString() },
            )
        }
        item(key = "all-time-time-chart") {
            val resources = LocalResources.current
            YearlyChart(
                title = stringResource(R.string.stats_time_per_year),
                years = years,
                values = stats.years.map { it.durationMs / 3_600_000f },
                valueText = { formatDuration((it * 3_600_000f).toLong()) },
                axisValue = { resources.getString(R.string.duration_hours, it.roundToInt()) },
            )
        }
    }
    if (stats.ratingAverage != null) {
        item(key = "all-time-ratings") { RatingsCard(stats.ratingAverage, stats.ratingCounts) }
    }
    longestBooks(stats.completed)
    if (stats.spending.isNotEmpty()) {
        item(key = "all-time-spending") {
            SpendingCard(
                spending = stats.spending,
                chartCurrency = stats.spendingCurrency,
                labels = yearLabels(stats.spendingPerYear.keys.toList()),
                amounts = stats.spendingPerYear.values.toList(),
            )
        }
    }
    if (stats.completed.isNotEmpty()) {
        item(key = "completed-label") { SectionLabel(stringResource(R.string.stats_finished)) }
        items(stats.completed.asReversed(), key = { "completed-${it.readThroughId}" }) { completed ->
            CompletedRow(completed, onClick = { onBookClick(completed.book) })
        }
    }
}

@Composable
private fun AllTimeSummary(stats: AllTimeStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Only purchases may be on record, and those say nothing about when reading began.
            stats.since?.let { since ->
                Text(
                    text = stringResource(R.string.stats_since, formatDate(since)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.stats_books_finished, stats.completed.size, stats.completed.size),
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
        }
    }
}

@Composable
private fun AllTimeTiles(stats: AllTimeStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stats_time_read),
                value = formatDuration(stats.durationMs),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stats_pages),
                value = stats.pages.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stats_days_read),
                value = stats.daysRead.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stats_monthly_average),
                value = stringResource(R.string.stats_books_decimal, stats.booksPerMonth),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One column per year, the current one last. */
@Composable
private fun YearlyChart(
    title: String,
    years: List<Int>,
    values: List<Float>,
    valueText: @Composable (Float) -> String,
    axisValue: (Float) -> String,
) {
    ColumnChartCard(
        title = title,
        labels = yearLabels(years),
        names = years.map { it.toString() },
        values = values,
        valueText = valueText,
        axisValue = axisValue,
        wholeNumbers = true,
    )
}

/**
 * What goes under a column per year. A long run of years would collide, so past a handful only
 * every few are named, counted back from the last so that the current year always is.
 */
internal fun yearLabels(years: List<Int>): List<String> {
    val step = ((years.size + YEAR_LABELS_SHOWN - 1) / YEAR_LABELS_SHOWN).coerceAtLeast(1)
    return years.mapIndexed { index, year -> if ((years.lastIndex - index) % step == 0) year.toString() else "" }
}
