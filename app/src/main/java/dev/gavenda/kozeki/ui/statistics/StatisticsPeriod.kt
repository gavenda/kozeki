package dev.gavenda.kozeki.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.PeriodStats
import dev.gavenda.kozeki.ui.components.ChartBar
import dev.gavenda.kozeki.ui.components.ChartReference
import dev.gavenda.kozeki.ui.components.ColumnChart
import dev.gavenda.kozeki.ui.components.StatTile
import dev.gavenda.kozeki.ui.formatDate
import dev.gavenda.kozeki.ui.formatDuration
import java.time.format.TextStyle

/** The weekly and monthly views: headline numbers, reading time per day, then books. */
internal fun LazyListScope.periodItems(
    period: PeriodStats,
    range: StatsRange,
    onBookClick: (Book) -> Unit,
) {
    item(key = "period-tiles") { PeriodTiles(period) }
    item(key = "period-chart") { ReadingTimeChart(period, range) }

    if (period.books.isNotEmpty()) {
        item(key = "books-label") { SectionLabel(stringResource(R.string.stats_books)) }
        items(period.books, key = { "book-${it.book.id}" }) { reading ->
            BookReadingCard(reading, onClick = { onBookClick(reading.book) })
        }
    }
    if (period.completed.isNotEmpty()) {
        item(key = "completed-label") { SectionLabel(stringResource(R.string.stats_finished)) }
        items(period.completed, key = { "completed-${it.readThroughId}" }) { completed ->
            CompletedRow(completed, onClick = { onBookClick(completed.book) })
        }
    }
}

@Composable
private fun PeriodTiles(period: PeriodStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stats_time_read),
                value = formatDuration(period.durationMs),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stats_pages),
                value = period.pages.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stats_days_read),
                value = stringResource(R.string.x_of_y, period.daysRead, period.days.size),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stats_goal_reached),
                value = pluralStringResource(R.plurals.stats_days, period.goalDaysMet, period.goalDaysMet),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReadingTimeChart(period: PeriodStats, range: StatsRange) {
    val locale = LocalLocale.current.platformLocale
    val resources = LocalResources.current
    val bars = period.days.map { day ->
        ChartBar(
            label = when {
                range == StatsRange.WEEK -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                // A month of labels would collide, so only one a week is drawn.
                (day.date.dayOfMonth - 1) % 7 == 0 -> day.date.dayOfMonth.toString()
                else -> ""
            },
            value = day.durationMs / 60_000f,
            valueText = formatDuration(day.durationMs),
        )
    }
    val description = stringResource(R.string.stats_time_per_day) + ": " + period.days
        .filter { it.durationMs > 0 }
        .map { stringResource(R.string.stats_day_description, formatDate(it.date), formatDuration(it.durationMs)) }
        .joinToString("; ")
        .ifEmpty { stringResource(R.string.stats_day_empty_title) }

    ChartCard(stringResource(R.string.stats_time_per_day)) {
        ColumnChart(
            bars = bars,
            contentDescription = description,
            axisText = { minutes -> resources.getString(R.string.duration_minutes, minutes.toInt()) },
            reference = ChartReference(period.goalMinutes.toFloat(), stringResource(R.string.stats_goal)),
        )
    }
}
