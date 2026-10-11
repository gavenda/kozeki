package dev.gavenda.kozeki.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.ReadingState
import dev.gavenda.kozeki.ui.formatPercent
import dev.gavenda.kozeki.ui.theme.AppTheme

@get:StringRes
val ReadingState.labelRes: Int
    get() = when (this) {
        ReadingState.PLANNED -> R.string.state_planned
        ReadingState.READING -> R.string.state_reading
        ReadingState.COMPLETED -> R.string.state_completed
        ReadingState.DROPPED -> R.string.state_dropped
        ReadingState.PAUSED -> R.string.state_paused
    }

@get:DrawableRes
val ReadingState.icon: Int
    get() = when (this) {
        ReadingState.PLANNED -> R.drawable.schedule_fill_48px
        ReadingState.READING -> R.drawable.menu_book_fill_48px
        ReadingState.COMPLETED -> R.drawable.check_circle_fill_48px
        ReadingState.DROPPED -> R.drawable.stop_circle_fill_48px
        ReadingState.PAUSED -> R.drawable.pause_circle_fill_48px
    }

/** The [icon] as a bare glyph, without the disc around it, light enough to lie over a cover. */
@get:DrawableRes
val ReadingState.outlinedIcon: Int
    get() = when (this) {
        ReadingState.PLANNED -> R.drawable.schedule_48px
        ReadingState.READING -> R.drawable.menu_book_48px
        ReadingState.COMPLETED -> R.drawable.done_all_48px
        ReadingState.DROPPED -> R.drawable.stop_fill_48px
        ReadingState.PAUSED -> R.drawable.pause_fill_48px
    }

/** The order states are offered in: the path a book usually takes, then the ways it can stall. */
val ReadingStateOrder: List<ReadingState> = listOf(
    ReadingState.PLANNED,
    ReadingState.READING,
    ReadingState.COMPLETED,
    ReadingState.PAUSED,
    ReadingState.DROPPED,
)

/** A compact, non-interactive label for a book's state. */
@Composable
fun ReadingStateBadge(state: ReadingState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(state.icon), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(stringResource(state.labelRes), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadingStateBadgePreview() {
    AppTheme {
        Surface {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingStateOrder.forEach { ReadingStateBadge(it) }
            }
        }
    }
}

/** How far into a book the reader is: a ring that fills as they go, around the figure itself. */
@Composable
fun ReadingProgressRing(progression: Double, modifier: Modifier = Modifier) {
    val percent = formatPercent(progression)
    val density = LocalDensity.current
    val stroke = with(density) { Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round) }
    Box(
        // The ring and the figure say the same thing, so it is read out once.
        modifier = modifier.clearAndSetSemantics { contentDescription = percent },
        contentAlignment = Alignment.Center,
    ) {
        // Scaled down from the 48dp default, waves and all, to fit the corner of a cover.
        CircularWavyProgressIndicator(
            progress = { progression.toFloat() },
            modifier = Modifier.size(32.dp),
            stroke = stroke,
            trackStroke = stroke,
            gapSize = 3.dp,
            wavelength = 10.dp,
        )
        Text(
            text = percent,
            // Sized in dp: the ring around it does not grow with the font scale.
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = with(density) { 9.dp.toSp() },
                lineHeight = with(density) { 10.dp.toSp() },
            ),
            maxLines = 1,
        )
    }
}
