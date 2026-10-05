package dev.gavenda.kozeki.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.YearStats
import dev.gavenda.kozeki.ui.components.ChartBar
import dev.gavenda.kozeki.ui.components.ColumnChart
import dev.gavenda.kozeki.ui.components.GoalRing
import dev.gavenda.kozeki.ui.components.RankedBar
import dev.gavenda.kozeki.ui.components.RankedBars
import dev.gavenda.kozeki.ui.components.StatTile
import dev.gavenda.kozeki.ui.formatDuration
import dev.gavenda.kozeki.ui.formatPrice
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import kotlin.math.roundToInt

private const val LONGEST_BOOKS_SHOWN = 8

/** The annual view: the yearly goal, headline numbers, then one chart per question. */
internal fun LazyListScope.yearItems(
    year: YearStats,
    today: LocalDate,
    onBookClick: (Book) -> Unit,
    onSetGoal: () -> Unit,
) {
    item(key = "year-goal") { YearGoal(year, onSetGoal) }
    item(key = "year-tiles") { YearTiles(year, today) }
    item(key = "year-books-chart") {
        MonthlyChart(
            title = stringResource(R.string.stats_books_per_month),
            values = year.completedPerMonth.map { it.toFloat() },
            valueText = { pluralStringResource(R.plurals.book_count, it.roundToInt(), it.roundToInt()) },
            axisValue = { it.roundToInt().toString() },
            wholeNumbers = true,
        )
    }
    item(key = "year-time-chart") {
        val resources = LocalResources.current
        MonthlyChart(
            title = stringResource(R.string.stats_time_per_month),
            values = year.durationPerMonth.map { it / 3_600_000f },
            valueText = { formatDuration((it * 3_600_000f).toLong()) },
            axisValue = { resources.getString(R.string.duration_hours, it.roundToInt()) },
            wholeNumbers = true,
        )
    }
    if (year.ratingAverage != null) {
        item(key = "year-ratings") { RatingsCard(year.ratingAverage, year.ratingCounts) }
    }

    val longest = year.completed
        .distinctBy { it.book.id }
        .mapNotNull { completed -> (completed.book.pageCount ?: completed.book.positionCount)?.let { completed.book to it } }
        .sortedByDescending { it.second }
        .take(LONGEST_BOOKS_SHOWN)
    if (longest.isNotEmpty()) {
        item(key = "year-longest") {
            ChartCard(stringResource(R.string.stats_longest_books)) {
                RankedBars(longest.map { (book, pages) -> RankedBar(book.title, pages.toFloat(), pages.toString()) })
            }
        }
    }
    if (year.spending.isNotEmpty()) {
        item(key = "year-spending") { SpendingCard(year) }
    }
    if (year.completed.isNotEmpty()) {
        item(key = "completed-label") { SectionLabel(stringResource(R.string.stats_finished)) }
        items(year.completed.asReversed(), key = { "completed-${it.book.id}-${it.readThroughNumber}" }) { completed ->
            CompletedRow(completed, onClick = { onBookClick(completed.book) })
        }
    }
}

@Composable
private fun YearGoal(year: YearStats, onSetGoal: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val progress = year.goalProgress
            val goal = year.goalBooks
            if (progress != null && goal != null) {
                GoalRing(progress = progress, label = "${(progress * 100).roundToInt()}%")
                Column {
                    Text(
                        text = pluralStringResource(R.plurals.stats_goal_books, goal, goal),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.stats_books_finished, year.completed.size, year.completed.size),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                    )
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(R.plurals.stats_books_finished, year.completed.size, year.completed.size),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.stats_no_goal, year.year),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onSetGoal) { Text(stringResource(R.string.stats_set_goal)) }
            }
        }
    }
}

@Composable
private fun YearTiles(year: YearStats, today: LocalDate) {
    // Averaging over twelve months would understate a year that is still running.
    val months = if (year.year == today.year) today.monthValue else 12
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stats_monthly_average),
                value = stringResource(R.string.stats_books_decimal, year.completed.size.toFloat() / months),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stats_pages),
                value = year.pages.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        StatTile(
            label = stringResource(R.string.stats_time_read),
            value = formatDuration(year.durationMs),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Twelve columns, January to December. */
@Composable
private fun MonthlyChart(
    title: String,
    values: List<Float>,
    valueText: @Composable (Float) -> String,
    axisValue: (Float) -> String,
    wholeNumbers: Boolean,
) {
    val locale = LocalLocale.current.platformLocale
    val bars = values.mapIndexed { index, value ->
        ChartBar(
            label = Month.of(index + 1).getDisplayName(TextStyle.NARROW, locale),
            value = value,
            valueText = valueText(value),
        )
    }
    val description = title + ": " + values
        .mapIndexed { index, value -> Month.of(index + 1).getDisplayName(TextStyle.SHORT, locale) to value }
        .filter { it.second > 0f }
        .map { (month, value) -> "$month ${valueText(value)}" }
        .joinToString("; ")

    ChartCard(title) {
        ColumnChart(bars = bars, contentDescription = description, axisText = axisValue, wholeNumbers = wholeNumbers)
    }
}

@Composable
private fun RatingsCard(average: Float, counts: List<Int>) {
    ChartCard(stringResource(R.string.stats_ratings)) {
        Text(
            text = stringResource(R.string.stats_rating_average, average),
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        // Five stars first, the way a rating breakdown is usually read.
        RankedBars(
            bars = counts.indices.reversed().map { index ->
                RankedBar(
                    label = pluralStringResource(R.plurals.stats_stars, index + 1, index + 1),
                    value = counts[index].toFloat(),
                    valueText = counts[index].toString(),
                )
            },
            labelWidth = 72.dp,
        )
    }
}

@Composable
private fun SpendingCard(year: YearStats) {
    ChartCard(stringResource(R.string.stats_spending)) {
        year.spending.forEach { (currency, total) ->
            Text(
                text = formatPrice(total, currency.ifEmpty { null }),
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
        }
        val currency = year.spendingCurrency?.ifEmpty { null }
        val locale = LocalLocale.current.platformLocale
        val bars = year.spendingPerMonth.mapIndexed { index, minor ->
            ChartBar(
                label = Month.of(index + 1).getDisplayName(TextStyle.NARROW, locale),
                // Plotted in minor units; only the labels are turned into money.
                value = minor.toFloat(),
                valueText = formatPrice(minor, currency),
            )
        }
        ColumnChart(
            bars = bars,
            contentDescription = stringResource(R.string.stats_spending),
            axisText = { formatPrice(it.toLong(), currency) },
        )
    }
}
