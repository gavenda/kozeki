package dev.gavenda.kozeki.ui.statistics

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.CompletedBook
import dev.gavenda.kozeki.data.model.YearStats
import dev.gavenda.kozeki.ui.components.ChartBar
import dev.gavenda.kozeki.ui.components.ColumnChart
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.GoalRing
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.Portrait
import dev.gavenda.kozeki.ui.components.RankedBar
import dev.gavenda.kozeki.ui.components.RankedBars
import dev.gavenda.kozeki.ui.components.SpeechBubble
import dev.gavenda.kozeki.ui.components.StatTile
import dev.gavenda.kozeki.ui.components.toggleButtonColors
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
        )
    }
    item(key = "year-time-chart") {
        val resources = LocalResources.current
        MonthlyChart(
            title = stringResource(R.string.stats_time_per_month),
            values = year.durationPerMonth.map { it / 3_600_000f },
            valueText = { formatDuration((it * 3_600_000f).toLong()) },
            axisValue = { resources.getString(R.string.duration_hours, it.roundToInt()) },
        )
    }
    if (year.ratingAverage != null) {
        item(key = "year-ratings") { RatingsCard(year.ratingAverage, year.ratingCounts) }
    }
    longestBooks(year.completed)
    if (year.spending.isNotEmpty()) {
        item(key = "year-spending") {
            SpendingCard(year.spending, year.spendingCurrency, monthInitials(), year.spendingPerMonth)
        }
    }
    if (year.completed.isNotEmpty()) {
        item(key = "completed-label") { SectionLabel(stringResource(R.string.stats_finished)) }
        items(year.completed.asReversed(), key = { "completed-${it.readThroughId}" }) { completed ->
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
                Button(
                    onClick = onSetGoal,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text(stringResource(R.string.stats_set_goal))
                }
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
        val time = stringResource(R.string.stats_time_read)
        if (year.chapters > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(label = time, value = formatDuration(year.durationMs), modifier = Modifier.weight(1f))
                StatTile(
                    label = stringResource(R.string.stats_chapters),
                    value = year.chapters.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            StatTile(label = time, value = formatDuration(year.durationMs), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Twelve columns, January to December. */
@Composable
private fun MonthlyChart(
    title: String,
    values: List<Float>,
    valueText: @Composable (Float) -> String,
    axisValue: (Float) -> String,
) {
    val locale = LocalLocale.current.platformLocale
    ColumnChartCard(
        title = title,
        labels = monthInitials(),
        names = Month.entries.map { it.getDisplayName(TextStyle.SHORT, locale) },
        values = values,
        valueText = valueText,
        axisValue = axisValue,
        wholeNumbers = true,
    )
}

/** The twelve months as they go under a year's columns, January first. */
@Composable
private fun monthInitials(): List<String> {
    val locale = LocalLocale.current.platformLocale
    return Month.entries.map { it.getDisplayName(TextStyle.NARROW, locale) }
}

// ---- Pieces shared with the all-time view --------------------------------------------------

/**
 * A titled chart with one column per stretch of time. [labels] go under the columns, an empty one
 * leaving its column bare; [names] are what the spoken description calls them.
 */
@Composable
internal fun ColumnChartCard(
    title: String,
    labels: List<String>,
    names: List<String>,
    values: List<Float>,
    valueText: @Composable (Float) -> String,
    axisValue: (Float) -> String,
    wholeNumbers: Boolean,
) {
    val bars = values.mapIndexed { index, value ->
        ChartBar(label = labels[index], value = value, valueText = valueText(value))
    }
    val named = values
        .mapIndexed { index, value -> names[index] to value }
        .filter { it.second > 0f }
        .map { (name, value) -> "$name ${valueText(value)}" }
    val description = "$title: ${named.joinToString("; ")}"

    ChartCard(title) {
        ColumnChart(bars = bars, contentDescription = description, axisText = axisValue, wholeNumbers = wholeNumbers)
    }
}

@Composable
internal fun RatingsCard(average: Float, counts: List<Int>) {
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

/** The longest of the [completed] books, in a card that is left out when no book's length is known. */
internal fun LazyListScope.longestBooks(completed: List<CompletedBook>) {
    val longest = LongestKind.entries.associateWith { longestFinished(completed, it) }
    if (longest.values.any { it.isNotEmpty() }) {
        item(key = "longest-books") { LongestBooksCard(longest) }
    }
}

/** The kinds the longest books are ranked by, each behind its own tab. */
internal enum class LongestKind(
    @param:StringRes val label: Int,
    @param:StringRes val empty: Int,
    @param:DrawableRes val icon: Int,
    val color: OptionColor,
) {
    // Leather for paper and violet for EPUB, as on a book's own page. The EPUB's is the icon of the library's imported badge.
    PHYSICAL(R.string.stats_longest_physical, R.string.stats_longest_physical_empty, R.drawable.auto_stories_48px, OptionColor.LEATHER),
    EPUB(R.string.stats_longest_epub, R.string.stats_longest_epub_empty, R.drawable.library_books_fill_48px, OptionColor.VIOLET),
    COMIC(R.string.stats_longest_comic, R.string.stats_longest_comic_empty, R.drawable.photo_library_48px, OptionColor.GOLD),
}

/**
 * The longest of the [completed] books with their length, longest first, of one [kind]: the
 * physical ones, which are the books without an EPUB, the ones with an EPUB, or the comics. A
 * physical copy is as long as the user says it is; an EPUB without a page count goes by its
 * positions; a comic is as long as its chapters. A book finished more than once is listed once, and
 * one whose length is not known not at all.
 */
internal fun longestFinished(
    completed: List<CompletedBook>,
    kind: LongestKind,
    limit: Int = LONGEST_BOOKS_SHOWN,
): List<Pair<Book, Int>> = completed
    .asSequence()
    .map { it.book }
    .distinctBy { it.id }
    .filter {
        when (kind) {
            LongestKind.PHYSICAL -> !it.inLibrary && !it.isComic
            LongestKind.EPUB -> it.inLibrary && !it.isComic
            LongestKind.COMIC -> it.isComic
        }
    }
    .mapNotNull { book ->
        val length = when (kind) {
            LongestKind.PHYSICAL -> book.physicalPageCount ?: book.pageCount
            LongestKind.EPUB -> book.pageCount ?: book.positionCount
            LongestKind.COMIC -> book.chapterCount
        }
        length?.takeIf { it > 0 }?.let { book to it }
    }
    .sortedByDescending { it.second }
    .take(limit)
    .toList()

/** The kinds of [longestFinished] behind a tab each, the physical books on the left and shown first. */
@Composable
private fun LongestBooksCard(longest: Map<LongestKind, List<Pair<Book, Int>>>) {
    // Until a tab is picked, the card opens on the first kind that has any.
    var picked by rememberSaveable { mutableStateOf<LongestKind?>(null) }
    val selected = picked ?: LongestKind.entries.first { longest[it].orEmpty().isNotEmpty() }

    ChartCard(stringResource(R.string.stats_longest_books)) {
        ConnectedButtonGroup(
            options = LongestKind.entries.map { stringResource(it.label) },
            selectedIndex = selected.ordinal,
            onSelect = { picked = LongestKind.entries[it] },
            modifier = Modifier.fillMaxWidth(),
            icons = LongestKind.entries.map { it.icon },
            optionColors = LongestKind.entries.map { it.color.toggleButtonColors() },
        )
        val books = longest[selected].orEmpty()
        if (books.isEmpty()) {
            // As the library ends: her face, and the bubble kept to the width of an empty state's text.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Portrait(Expression.NEUTRAL, Modifier.width(96.dp))
                SpeechBubble(stringResource(selected.empty), Modifier.weight(1f, fill = false).widthIn(max = 360.dp))
            }
        } else {
            val unit = if (selected == LongestKind.COMIC) R.string.stats_length_chapters else R.string.stats_length_pages
            RankedBars(
                books.map { (book, length) -> RankedBar(book.title, length.toFloat(), stringResource(unit, length)) },
            )
        }
    }
}

/**
 * The total spent in each currency of [spending], over a column per entry of [amounts]. Those are
 * in [chartCurrency] alone and go with [labels].
 */
@Composable
internal fun SpendingCard(
    spending: Map<String, Long>,
    chartCurrency: String?,
    labels: List<String>,
    amounts: List<Long>,
) {
    ChartCard(stringResource(R.string.stats_spending)) {
        spending.forEach { (currency, total) ->
            Text(
                text = formatPrice(total, currency.ifEmpty { null }),
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
        }
        val currency = chartCurrency?.ifEmpty { null }
        val bars = amounts.mapIndexed { index, minor ->
            ChartBar(
                label = labels[index],
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
