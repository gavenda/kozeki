package dev.gavenda.kozeki.ui.statistics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.DailyStats
import dev.gavenda.kozeki.data.model.PeriodStats
import dev.gavenda.kozeki.data.model.YearStats
import dev.gavenda.kozeki.data.repository.StatsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class StatsRange(@param:StringRes val label: Int) {
    DAY(R.string.stats_range_day),
    WEEK(R.string.stats_range_week),
    MONTH(R.string.stats_range_month),
    YEAR(R.string.stats_range_year),
}

data class StatisticsUiState(
    val range: StatsRange,
    /** A date inside the period being shown. */
    val anchor: LocalDate,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val day: DailyStats? = null,
    val period: PeriodStats? = null,
    val year: YearStats? = null,
) {
    val weekStart: LocalDate get() = anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    /** There is nothing to show beyond the present. */
    val canGoForward: Boolean
        get() = when (range) {
            StatsRange.DAY -> anchor < today
            StatsRange.WEEK -> weekStart.plusDays(7) <= today
            StatsRange.MONTH -> YearMonth.from(anchor) < YearMonth.from(today)
            StatsRange.YEAR -> anchor.year < today.year
        }
}

/**
 * @param initialDate Opens on the daily view of this date, which is how the calendar links here.
 */
class StatisticsViewModel(
    private val stats: StatsRepository,
    clock: Clock,
    initialDate: LocalDate? = null,
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)
    private val range = MutableStateFlow(StatsRange.DAY)
    private val anchor = MutableStateFlow(initialDate ?: today)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatisticsUiState> = combine(range, anchor, ::Pair)
        .flatMapLatest { (range, anchor) ->
            val base = StatisticsUiState(range, anchor, today, firstDayOfWeek)
            when (range) {
                StatsRange.DAY -> stats.observeDay(anchor, firstDayOfWeek).map { base.copy(day = it) }
                StatsRange.WEEK -> stats.observePeriod(base.weekStart, base.weekStart.plusDays(6))
                    .map { base.copy(period = it) }
                StatsRange.MONTH -> YearMonth.from(anchor).let { month ->
                    stats.observePeriod(month.atDay(1), month.atEndOfMonth()).map { base.copy(period = it) }
                }
                StatsRange.YEAR -> stats.observeYear(anchor.year).map { base.copy(year = it) }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatisticsUiState(StatsRange.DAY, initialDate ?: today, today, firstDayOfWeek),
        )

    fun selectRange(selected: StatsRange) {
        range.value = selected
    }

    fun selectDate(date: LocalDate) {
        if (date <= today) anchor.value = date
    }

    fun previous() = shift(-1)

    fun next() {
        if (uiState.value.canGoForward) shift(1)
    }

    private fun shift(direction: Long) {
        val moved = when (range.value) {
            StatsRange.DAY -> anchor.value.plusDays(direction)
            StatsRange.WEEK -> anchor.value.plusWeeks(direction)
            StatsRange.MONTH -> anchor.value.plusMonths(direction)
            StatsRange.YEAR -> anchor.value.plusYears(direction)
        }
        // Stepping forward from mid-period can overshoot today; the period itself is still valid.
        anchor.value = minOf(moved, today)
    }
}
