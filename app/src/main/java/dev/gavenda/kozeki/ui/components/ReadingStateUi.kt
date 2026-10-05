package dev.gavenda.kozeki.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.ReadingState
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

val ReadingState.icon: ImageVector
    get() = when (this) {
        ReadingState.PLANNED -> Icons.Rounded.Bookmark
        ReadingState.READING -> Icons.AutoMirrored.Rounded.MenuBook
        ReadingState.COMPLETED -> Icons.Rounded.CheckCircle
        ReadingState.DROPPED -> Icons.Rounded.Cancel
        ReadingState.PAUSED -> Icons.Rounded.PauseCircle
    }

/** The order states are offered in: the path a book usually takes, then the ways it can stall. */
val ReadingStateOrder: List<ReadingState> = listOf(
    ReadingState.PLANNED,
    ReadingState.READING,
    ReadingState.PAUSED,
    ReadingState.COMPLETED,
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
            Icon(state.icon, contentDescription = null, modifier = Modifier.size(14.dp))
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
