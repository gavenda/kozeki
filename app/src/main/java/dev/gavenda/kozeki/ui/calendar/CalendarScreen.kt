package dev.gavenda.kozeki.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.CalendarBook
import dev.gavenda.kozeki.data.model.CalendarDay
import dev.gavenda.kozeki.ui.PreviewData
import dev.gavenda.kozeki.ui.ScreenPreviews
import dev.gavenda.kozeki.ui.components.BookCover
import dev.gavenda.kozeki.ui.components.CoverAspectRatio
import dev.gavenda.kozeki.ui.components.RatingBar
import dev.gavenda.kozeki.ui.formatDate
import dev.gavenda.kozeki.ui.formatDuration
import dev.gavenda.kozeki.ui.formatMonth
import dev.gavenda.kozeki.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarScreen(
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onToday = viewModel::goToToday,
        onDayClick = onOpenDay,
        onSettingsClick = onOpenSettings,
    )
}

@Composable
fun CalendarContent(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                subtitle = {
                    Text(
                        if (state.daysRead == 0) {
                            stringResource(R.string.calendar_nothing_read)
                        } else {
                            pluralStringResource(
                                R.plurals.calendar_summary,
                                state.daysRead,
                                state.daysRead,
                                formatDuration(state.durationMs),
                            )
                        },
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonthNavigator(state, onPreviousMonth, onNextMonth, onToday)
                WeekdayHeader(state.firstDayOfWeek)
                MonthGrid(state, onDayClick)
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = formatMonth(state.month),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (state.canGoForward) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.stats_today)) }
        }
        FilledTonalIconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        FilledTonalIconButton(onClick = onNext, enabled = state.canGoForward) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek) {
    val locale = LocalLocale.current.platformLocale
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { offset ->
            Text(
                text = firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The weeks of the month, each day showing the covers of what was read on it. */
@Composable
private fun MonthGrid(state: CalendarUiState, onDayClick: (LocalDate) -> Unit) {
    val first = state.month.atDay(1)
    val gridStart = first.with(TemporalAdjusters.previousOrSame(state.firstDayOfWeek))
    val weeks = generateSequence(gridStart) { it.plusWeeks(1) }
        .takeWhile { it <= state.month.atEndOfMonth() }
        .toList()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        weeks.forEach { weekStart ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { offset ->
                    val date = weekStart.plusDays(offset.toLong())
                    if (YearMonth.from(date) == state.month) {
                        DayCell(
                            date = date,
                            day = state.days[date],
                            isToday = date == state.today,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: CalendarDay?,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val books = day?.books.orEmpty()
    val finished = books.firstOrNull { it.completed }
    val description = if (books.isEmpty()) {
        formatDate(date)
    } else {
        stringResource(R.string.calendar_day_description, formatDate(date), books.joinToString(", ") { it.book.title })
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = books.isNotEmpty(), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = if (isToday) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CoverAspectRatio),
        ) {
            CoverStack(books)
        }
        // The same height on every day, so the rows of the grid stay aligned.
        Box(Modifier.size(width = 40.dp, height = 10.dp), contentAlignment = Alignment.Center) {
            if (finished != null) {
                val rating = finished.book.rating
                if (rating != null) {
                    RatingBar(rating = rating, starSize = 8.dp)
                } else {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}

/** One cover fills the cell; with more, the most-read sits in front and the next peeks out behind. */
@Composable
private fun CoverStack(books: List<CalendarBook>) {
    when (books.size) {
        0 -> Unit
        1 -> BookCover(books[0].book, Modifier.fillMaxSize(), MaterialTheme.shapes.extraSmall, showTitleOnPlaceholder = false)
        else -> Box(Modifier.fillMaxSize()) {
            BookCover(
                book = books[1].book,
                modifier = Modifier
                    .fillMaxSize(0.78f)
                    .align(Alignment.BottomEnd),
                shape = MaterialTheme.shapes.extraSmall,
                showTitleOnPlaceholder = false,
            )
            BookCover(
                book = books[0].book,
                modifier = Modifier
                    .fillMaxSize(0.78f)
                    .align(Alignment.TopStart),
                shape = MaterialTheme.shapes.extraSmall,
                showTitleOnPlaceholder = false,
            )
            if (books.size > 2) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Text(
                        text = stringResource(R.string.calendar_more_books, books.size - 2),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun CalendarContentPreview() {
    AppTheme {
        CalendarContent(
            state = CalendarUiState(
                month = PreviewData.calendarMonth,
                today = PreviewData.dailyStats.date,
                firstDayOfWeek = DayOfWeek.MONDAY,
                days = PreviewData.calendarDays,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onToday = {},
            onDayClick = {},
            onSettingsClick = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun CalendarEmptyPreview() {
    AppTheme {
        CalendarContent(
            state = CalendarUiState(
                month = PreviewData.calendarMonth.minusMonths(2),
                today = PreviewData.dailyStats.date,
                firstDayOfWeek = DayOfWeek.SUNDAY,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onToday = {},
            onDayClick = {},
            onSettingsClick = {},
        )
    }
}
