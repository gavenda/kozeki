package dev.gavenda.kozeki.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import dev.gavenda.kozeki.ui.components.CoverAspectRatio
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.db.MangaTrackEntity
import dev.gavenda.kozeki.data.model.TrackStatus
import dev.gavenda.kozeki.data.tracking.anilist.AniListMedia
import dev.gavenda.kozeki.data.tracking.anilist.ScoreFormat
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState

/** What the sheet can ask for. */
class TrackSheetActions(
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onBind: (AniListMedia) -> Unit = {},
    val onEdit: ((MangaTrackEntity) -> MangaTrackEntity) -> Unit = {},
    val onUntrack: (fromList: Boolean) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** Finds the manga on AniList, or, once it is followed, shows and changes its entry there. */
@Composable
fun TrackSheet(state: TrackingUiState, actions: TrackSheetActions) {
    ModalBottomSheet(onDismissRequest = actions.onDismiss, sheetState = rememberExpandedSheetState()) {
        val track = state.track
        if (track == null) {
            TrackSearch(state, actions)
        } else {
            TrackEntry(track, state, actions)
        }
    }
}

@Composable
private fun TrackSearch(state: TrackingUiState, actions: TrackSheetActions) {
    val sheet = state.sheet
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.track_sheet_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        var query by remember { mutableStateOf(sheet.query) }
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                actions.onQueryChange(it)
            },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            leadingIcon = {
                IconButton(onClick = actions.onSearch) {
                    Icon(
                        painterResource(R.drawable.search_48px),
                        contentDescription = stringResource(R.string.action_search),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        when {
            sheet.loading || sheet.working -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                HaloLoadingIndicator()
            }

            sheet.error != null -> SheetNote(stringResource(sheet.error.message))
            sheet.results.isEmpty() -> SheetNote(stringResource(R.string.search_no_results))
            else -> LazyColumn {
                items(sheet.results, key = { it.id }) { media ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actions.onBind(media) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(48.dp)
                                .aspectRatio(CoverAspectRatio)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            media.coverUrl?.let { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(media.title, style = MaterialTheme.typography.bodyLarge)
                            val detail = listOfNotNull(
                                media.format?.replace('_', ' ')?.lowercase()?.replaceFirstChar(Char::uppercase),
                                media.year?.toString(),
                                media.chapters?.let { stringResource(R.string.track_chapters_total, it) },
                                media.entry?.let { stringResource(R.string.track_already_listed) },
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) {
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackEntry(track: MangaTrackEntity, state: TrackingUiState, actions: TrackSheetActions) {
    val sheet = state.sheet
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The statuses of AniList, in the order it lists them. Repeating is something only it sets.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (status in STATUSES) {
                FilterChip(
                    selected = track.status == status,
                    onClick = { actions.onEdit { it.copy(status = status) } },
                    label = { Text(stringResource(status.label)) },
                    enabled = !sheet.working,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.track_progress), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = track.totalChapters?.let { "${track.progress} / $it" } ?: track.progress.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            FilledTonalIconButton(
                onClick = { actions.onEdit { it.copy(progress = (it.progress - 1).coerceAtLeast(0)) } },
                enabled = track.progress > 0 && !sheet.working,
            ) {
                Icon(painterResource(R.drawable.remove_48px), contentDescription = stringResource(R.string.track_progress_less), modifier = Modifier.size(24.dp))
            }
            FilledTonalIconButton(
                onClick = { actions.onEdit { it.copy(progress = it.progress + 1) } },
                enabled = (track.totalChapters == null || track.progress < track.totalChapters) && !sheet.working,
            ) {
                Icon(painterResource(R.drawable.add_48px), contentDescription = stringResource(R.string.track_progress_more), modifier = Modifier.size(24.dp))
            }
        }

        ScoreSlider(track.score, state.scoreFormat, enabled = !sheet.working) { score ->
            actions.onEdit { it.copy(score = score) }
        }

        sheet.error?.let {
            Text(stringResource(it.message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (track.pendingPush) {
            Text(
                stringResource(R.string.track_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { actions.onUntrack(false) }, enabled = !sheet.working, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.track_stop))
            }
            FilledIconButton(
                onClick = { actions.onUntrack(true) },
                enabled = !sheet.working,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(painterResource(R.drawable.delete_fill_48px), contentDescription = stringResource(R.string.track_remove), modifier = Modifier.size(24.dp))
            }
        }
        Box(Modifier.padding(bottom = 16.dp))
    }
}

/** A score on the scale the account uses. It is applied when the slider is let go. */
@Composable
private fun ScoreSlider(score: Int, format: ScoreFormat, enabled: Boolean, onChange: (Int) -> Unit) {
    var position by remember(score, format) { mutableStateOf(format.toPosition(score).toFloat()) }
    val steps = (format.positions - 1).coerceAtLeast(0)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.track_score), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            val shown = position.toInt()
            Text(
                text = when {
                    shown == 0 -> stringResource(R.string.track_score_none)
                    format == ScoreFormat.POINT_10_DECIMAL -> "%.1f".format(shown / 10f)
                    else -> shown.toString()
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = {
                val next = format.toScore(position.toInt())
                if (next != score) onChange(next)
            },
            valueRange = 0f..format.positions.toFloat(),
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
    )
}

private val STATUSES = listOf(
    TrackStatus.CURRENT,
    TrackStatus.PLANNING,
    TrackStatus.COMPLETED,
    TrackStatus.PAUSED,
    TrackStatus.DROPPED,
)

private val TrackStatus.label: Int
    get() = when (this) {
        TrackStatus.CURRENT, TrackStatus.REPEATING -> R.string.track_status_current
        TrackStatus.PLANNING -> R.string.track_status_planning
        TrackStatus.COMPLETED -> R.string.track_status_completed
        TrackStatus.PAUSED -> R.string.track_status_paused
        TrackStatus.DROPPED -> R.string.track_status_dropped
    }
