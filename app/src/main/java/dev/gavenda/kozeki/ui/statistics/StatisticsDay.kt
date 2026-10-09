package dev.gavenda.kozeki.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import dev.gavenda.kozeki.data.model.DailyStats
import dev.gavenda.kozeki.data.model.DayReading
import dev.gavenda.kozeki.data.model.TimelineEntry
import dev.gavenda.kozeki.ui.components.EmptyState
import dev.gavenda.kozeki.ui.components.GoalRing
import dev.gavenda.kozeki.ui.components.MiniRing
import dev.gavenda.kozeki.ui.formatDate
import dev.gavenda.kozeki.ui.formatDuration
import dev.gavenda.kozeki.ui.formatTime
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** The daily view: the week at a glance, the day against its goal, then what was read and when. */
internal fun LazyListScope.dayItems(
    day: DailyStats,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onBookClick: (Book) -> Unit,
) {
    item(key = "week-strip") { WeekStrip(day.week, day.date, today, day.goalMinutes, onSelectDate) }
    item(key = "day-summary") { DaySummary(day) }

    // Pages of a physical copy take no time on record, so it is the timeline that tells a day with reading.
    if (day.timeline.isEmpty() && day.completed.isEmpty()) {
        item(key = "day-empty") {
            EmptyState(
                icon = Icons.Rounded.AutoStories,
                title = stringResource(R.string.stats_day_empty_title),
                message = stringResource(R.string.stats_day_empty_message),
            )
        }
        return
    }

    if (day.books.isNotEmpty()) {
        item(key = "books-label") { SectionLabel(stringResource(R.string.stats_books)) }
        items(day.books, key = { "book-${it.book.id}" }) { reading ->
            BookReadingCard(reading, onClick = { onBookClick(reading.book) })
        }
    }
    if (day.completed.isNotEmpty()) {
        item(key = "completed-label") { SectionLabel(stringResource(R.string.stats_finished)) }
        items(day.completed, key = { "completed-${it.readThroughId}" }) { completed ->
            CompletedRow(completed, onClick = { onBookClick(completed.book) })
        }
    }
    if (day.timeline.isNotEmpty()) {
        item(key = "timeline-label") { SectionLabel(stringResource(R.string.stats_timeline)) }
        itemsIndexed(day.timeline, key = { index, _ -> "timeline-$index" }) { _, entry -> TimelineRow(entry) }
    }
}

/** Seven days, each a small ring filled by how much of the goal was read. Tapping one opens it. */
@Composable
private fun WeekStrip(
    week: List<DayReading>,
    selected: LocalDate,
    today: LocalDate,
    goalMinutes: Int,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { reading ->
            val isSelected = reading.date == selected
            val isFuture = reading.date > today
            val progress = if (goalMinutes > 0) reading.durationMs / 60_000f / goalMinutes else 0f
            val description = stringResource(
                R.string.stats_day_description,
                formatDate(reading.date),
                formatDuration(reading.durationMs),
            )
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable(enabled = !isFuture, role = Role.Tab) { onSelect(reading.date) }
                    .semantics { contentDescription = description }
                    .alpha(if (isFuture) 0.4f else 1f)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = reading.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MiniRing(progress = progress) {
                    Text(reading.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DaySummary(day: DailyStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoalRing(progress = day.goalProgress, label = "${(day.goalProgress * 100).roundToInt()}%")
            Column {
                Text(
                    text = stringResource(R.string.stats_goal_minutes, day.goalMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val pages = pluralStringResource(R.plurals.stats_pages_read, day.pages, day.pages)
                // The one figure this view leads with. A day read only on paper has no time, so its pages lead.
                val untimed = day.durationMs == 0L && day.pages > 0
                Text(
                    text = if (untimed) pages else formatDuration(day.durationMs),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                )
                if (!untimed) {
                    Text(
                        text = pages,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = formatTime(entry.at),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Column {
            Text(
                text = entry.book.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (entry) {
                    is TimelineEntry.Session ->
                        listOfNotNull(formatDuration(entry.durationMs), entry.chapter).joinToString(" · ")
                    // Nobody timed a physical copy, so it says how far it was read in place of how long.
                    is TimelineEntry.Pages -> listOf(
                        stringResource(R.string.progress_physical),
                        stringResource(R.string.stats_page_span, entry.startPage, entry.endPage),
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
