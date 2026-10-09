package dev.gavenda.kozeki.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.BookReading
import dev.gavenda.kozeki.data.model.CompletedBook
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.RatingBar
import dev.gavenda.kozeki.ui.formatDate
import dev.gavenda.kozeki.ui.formatDuration
import dev.gavenda.kozeki.ui.formatMonth
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @param initialDate When set, the screen opens on that day and behaves as a detail page with a
 *   back arrow instead of a top-level destination.
 */
@Composable
fun StatisticsScreen(
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    initialDate: LocalDate? = null,
    onBack: (() -> Unit)? = null,
    viewModel: StatisticsViewModel = koinViewModel(key = "statistics-$initialDate") { parametersOf(initialDate) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsContent(
        state = state,
        onSelectRange = viewModel::selectRange,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onSelectDate = viewModel::selectDate,
        onBookClick = { onOpenBook(it.id) },
        onSettingsClick = onOpenSettings,
        onBack = onBack,
    )
}

@Composable
fun StatisticsContent(
    state: StatisticsUiState,
    onSelectRange: (StatsRange) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onBookClick: (Book) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    // Each bar gets a behavior of its kind. A collapsing one with no bar to collapse would take
    // every drag upwards for itself and leave the list unable to scroll.
    val scrollBehavior = if (onBack == null) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (onBack == null) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.nav_statistics)) },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_statistics)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // One row of filters above everything they scope.
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val ranges = StatsRange.entries
                        ConnectedButtonGroup(
                            options = ranges.map { stringResource(it.label) },
                            selectedIndex = ranges.indexOf(state.range),
                            onSelect = { onSelectRange(ranges[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        periodLabel(state)?.let { PeriodNavigator(it, state.canGoForward, onPrevious, onNext) }
                    }
                }
                when (state.range) {
                    StatsRange.DAY -> state.day?.let { dayItems(it, state.today, onSelectDate, onBookClick) }
                    StatsRange.WEEK, StatsRange.MONTH -> state.period?.let { periodItems(it, state.range, onBookClick) }
                    StatsRange.YEAR -> state.year?.let { yearItems(it, state.today, onBookClick, onSettingsClick) }
                    StatsRange.ALL -> state.allTime?.let { allTimeItems(it, onBookClick) }
                }
            }
        }
    }
}

/** What the period being shown is called, or null for all time, which is not one of several. */
@Composable
private fun periodLabel(state: StatisticsUiState): String? = when (state.range) {
    StatsRange.DAY -> if (state.anchor == state.today) stringResource(R.string.stats_today) else formatDate(state.anchor)
    StatsRange.WEEK -> {
        val formatter = DateTimeFormatter.ofPattern("MMM d")
        stringResource(
            R.string.stats_date_range,
            state.weekStart.format(formatter),
            state.weekStart.plusDays(6).format(formatter),
        )
    }
    StatsRange.MONTH -> formatMonth(YearMonth.from(state.anchor))
    StatsRange.YEAR -> state.anchor.year.toString()
    StatsRange.ALL -> null
}

@Composable
private fun PeriodNavigator(label: String, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.stats_previous),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.stats_next))
        }
    }
}

// ---- Pieces shared by the day, period, year and all-time views -----------------------------

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMediumEmphasized, modifier = modifier)
}

@Composable
internal fun ChartCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/**
 * What was read of one book: where from and to, for how long, and how fast. A physical copy was not
 * timed, so its card leads with the pages read in place of the time.
 */
@Composable
internal fun BookReadingCard(reading: BookReading, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookCover(reading.book, Modifier.width(64.dp), showTitleOnPlaceholder = false)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = reading.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (reading.physical) {
                        pluralStringResource(R.plurals.stats_pages_read, reading.pages, reading.pages)
                    } else {
                        formatDuration(reading.durationMs)
                    },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                val details = buildList {
                    val from = reading.startPosition
                    val to = reading.endPosition
                    val span = if (from != null && to != null) stringResource(R.string.stats_page_span, from, to) else null
                    if (reading.physical) {
                        add(listOfNotNull(stringResource(R.string.progress_physical), span).joinToString(" · "))
                    } else {
                        span?.let { add(it) }
                        if (reading.pages > 0) {
                            add(
                                pluralStringResource(
                                    R.plurals.stats_pages_gained,
                                    reading.pages,
                                    reading.pages,
                                    (reading.progressGained * 100).roundToInt(),
                                ),
                            )
                        }
                        reading.pagesPerHour?.let { add(stringResource(R.string.stats_pages_per_hour, it)) }
                    }
                }
                details.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A book that was finished, with the day and the rating it got. */
@Composable
internal fun CompletedRow(completed: CompletedBook, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(completed.book, Modifier.width(40.dp), showTitleOnPlaceholder = false)
            Column(Modifier.weight(1f)) {
                Text(
                    text = completed.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatDate(completed.finishedOn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            completed.book.rating?.let { RatingBar(rating = it, starSize = 14.dp) }
        }
    }
}

internal val PreviewFirstDayOfWeek = DayOfWeek.MONDAY

@ScreenPreviews
@Composable
private fun StatisticsDayPreview() {
    AppTheme {
        StatisticsContent(
            state = StatisticsUiState(
                range = StatsRange.DAY,
                anchor = PreviewData.dailyStats.date,
                today = PreviewData.dailyStats.date,
                firstDayOfWeek = PreviewFirstDayOfWeek,
                day = PreviewData.dailyStats,
            ),
            onSelectRange = {},
            onPrevious = {},
            onNext = {},
            onSelectDate = {},
            onBookClick = {},
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun StatisticsWeekPreview() {
    AppTheme {
        StatisticsContent(
            state = StatisticsUiState(
                range = StatsRange.WEEK,
                anchor = PreviewData.weekStats.endInclusive,
                today = PreviewData.dailyStats.date,
                firstDayOfWeek = PreviewFirstDayOfWeek,
                period = PreviewData.weekStats,
            ),
            onSelectRange = {},
            onPrevious = {},
            onNext = {},
            onSelectDate = {},
            onBookClick = {},
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun StatisticsMonthPreview() {
    AppTheme {
        StatisticsContent(
            state = StatisticsUiState(
                range = StatsRange.MONTH,
                anchor = PreviewData.dailyStats.date,
                today = PreviewData.dailyStats.date,
                firstDayOfWeek = PreviewFirstDayOfWeek,
                period = PreviewData.monthStats,
            ),
            onSelectRange = {},
            onPrevious = {},
            onNext = {},
            onSelectDate = {},
            onBookClick = {},
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun StatisticsYearPreview() {
    AppTheme {
        StatisticsContent(
            state = StatisticsUiState(
                range = StatsRange.YEAR,
                anchor = PreviewData.dailyStats.date,
                today = PreviewData.dailyStats.date,
                firstDayOfWeek = PreviewFirstDayOfWeek,
                year = PreviewData.yearStats,
            ),
            onSelectRange = {},
            onPrevious = {},
            onNext = {},
            onSelectDate = {},
            onBookClick = {},
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun StatisticsAllTimePreview() {
    AppTheme {
        StatisticsContent(
            state = StatisticsUiState(
                range = StatsRange.ALL,
                anchor = PreviewData.dailyStats.date,
                today = PreviewData.dailyStats.date,
                firstDayOfWeek = PreviewFirstDayOfWeek,
                allTime = PreviewData.allTimeStats,
            ),
            onSelectRange = {},
            onPrevious = {},
            onNext = {},
            onSelectDate = {},
            onBookClick = {},
            onSettingsClick = {},
        )
    }
}
