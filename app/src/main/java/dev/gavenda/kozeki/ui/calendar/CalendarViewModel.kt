package dev.gavenda.kozeki.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.model.CalendarDay
import dev.gavenda.kozeki.data.repository.StatsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CalendarUiState(
    val month: YearMonth,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    /** Only days on which something was read or finished. */
    val days: Map<LocalDate, CalendarDay> = emptyMap(),
) {
    val canGoForward: Boolean get() = month < YearMonth.from(today)
    /**
     * Every day with a book on it. A book finished without a page entered that day, which is how a
     * physical copy is often marked, has neither time nor pages to show, and was read all the same.
     */
    val daysRead: Int get() = days.values.count { it.books.isNotEmpty() }
    val durationMs: Long get() = days.values.sumOf { day -> day.books.sumOf { it.durationMs } }
}

class CalendarViewModel(
    private val stats: StatsRepository,
    clock: Clock,
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)
    private val month = MutableStateFlow(YearMonth.from(today))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CalendarUiState> = month
        .flatMapLatest { selected ->
            stats.observeMonth(selected).map { CalendarUiState(selected, today, firstDayOfWeek, it) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarUiState(YearMonth.from(today), today, firstDayOfWeek),
        )

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (uiState.value.canGoForward) month.value = month.value.plusMonths(1)
    }

    fun goToToday() {
        month.value = YearMonth.from(today)
    }
}
