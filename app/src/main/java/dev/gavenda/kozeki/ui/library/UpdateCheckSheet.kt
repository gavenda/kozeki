package dev.gavenda.kozeki.ui.library

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.UpdateCheckProgress
import dev.gavenda.kozeki.ui.components.Expression
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.Portrait
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the sheet follows: a pull to refresh through the followed comics for new chapters or the books
 * for their details, or the lookup of books just imported.
 */
enum class UpdateCheckKind(
    @param:StringRes val title: Int,
    @param:PluralsRes val count: Int,
    @param:PluralsRes val skipped: Int,
    /** Said when every one was skipped and none asked; only the comics' check can end that way. */
    @param:StringRes val upToDate: Int = R.string.update_check_done,
) {
    CHAPTERS(
        R.string.update_check_title,
        R.plurals.update_check_count,
        R.plurals.update_check_skipped,
        R.string.update_check_up_to_date,
    ),
    METADATA(R.string.metadata_refresh_title, R.plurals.metadata_refresh_count, R.plurals.metadata_refresh_skipped),

    /** The books just imported, being looked up for the first time. Nothing is skipped. */
    LOOKUP(R.string.lookup_title, R.plurals.metadata_refresh_count, R.plurals.metadata_refresh_skipped),
}

/**
 * How far a pull to refresh has got through the followed comics, or the books for [kind] METADATA, with a way to stop it. [progress]
 * is null once the check is over. A check that got through every comic, [finished], is marked done
 * with a tick for a moment before the sheet slides away; a cancelled one slides away at once.
 *
 * Sent away by hand, the sheet only hides: the check goes on, and another pull brings it back.
 * [hidden] keeps it away until then.
 */
@Composable
fun UpdateCheckSheet(
    progress: UpdateCheckProgress?,
    kind: UpdateCheckKind,
    finished: Boolean,
    hidden: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    upToDate: Int = 0,
    onUpToDateShown: () -> Unit = {},
) {
    // The last of the check seen, kept after it ends so the sheet can say it is done. It waits for
    // the comics to be counted, so a library with none to ask does not flash one up. When every
    // comic was left for later, [upToDate] of them, it says they are all up to date instead.
    var last by remember { mutableStateOf<UpdateCheckProgress?>(null) }
    LaunchedEffect(progress, hidden, upToDate) {
        when {
            hidden -> last = null
            progress != null && progress.total > 0 -> last = progress
            progress == null && upToDate > 0 -> {
                last = UpdateCheckProgress(checked = 0, total = 0, current = null, skipped = upToDate)
                onUpToDateShown()
            }
        }
    }
    val seen = last ?: return
    val running = progress != null
    val done = !running && finished
    // The last count heard may be one short: the one saying all were checked can come too late.
    val shown = if (done) seen.copy(checked = seen.total, current = null) else seen

    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch {
            sheetState.hide()
            last = null
        }
    }
    LaunchedEffect(running, done) {
        when {
            running -> Unit
            done -> {
                delay(DONE_HOLD_MS)
                sheetState.hide()
                last = null
            }
            // Cancelled: nothing to show off.
            else -> {
                sheetState.hide()
                last = null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            last = null
            if (running) onDismiss()
        },
        sheetState = sheetState,
    ) {
        UpdateCheckContent(shown, kind, done, onCancel = onCancel, onClose = close)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateCheckContent(
    progress: UpdateCheckProgress,
    kind: UpdateCheckKind,
    finished: Boolean,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    // Nothing was asked: every one was left for later.
    val noneDue = progress.total == 0
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        AnimatedContent(targetState = finished, transitionSpec = { crossfade() }, label = "title") { done ->
            Text(
                text = stringResource(
                    when {
                        noneDue -> kind.upToDate
                        done -> R.string.update_check_done
                        else -> kind.title
                    },
                ),
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Nothing to wait on: she is pleased the shelves are in order, where the tick would be.
        if (noneDue) {
            Portrait(Expression.HAPPY, Modifier.padding(vertical = 8.dp).width(96.dp))
        } else AnimatedContent(
            targetState = finished,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) + scaleIn(spring(Spring.DampingRatioMediumBouncy), 0.6f))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(targetScale = 0.8f))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp).size(96.dp),
            label = "indicator",
        ) { done ->
            if (done) {
                DoneMark(Modifier.size(96.dp))
            } else {
                // The hands go round once over the whole check.
                HaloLoadingIndicator(
                    progress = { progress.checked.toFloat() / progress.total },
                    modifier = Modifier.size(96.dp),
                )
            }
        }
        if (!noneDue) {
            Text(
                text = pluralStringResource(kind.count, progress.total, progress.checked, progress.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress.skipped > 0) {
                Text(
                    text = pluralStringResource(kind.skipped, progress.skipped, progress.skipped),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The comic or book being asked; the line keeps its place once done, so the sheet does not jump.
        // With none asked there is nothing to keep it for.
        if (!noneDue) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = progress.current ?: " ",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedContent(
            targetState = finished,
            transitionSpec = { crossfade() },
            modifier = Modifier.padding(top = 4.dp),
            label = "action",
        ) { done ->
            // One width for both, so the swap from Cancel to Close does not shift it.
            OutlinedButton(onClick = if (done) onClose else onCancel, modifier = Modifier.width(200.dp)) {
                Text(stringResource(if (done) R.string.action_close else R.string.action_cancel))
            }
        }
    }
}

private fun crossfade() = fadeIn(tween(220, delayMillis = 90)).togetherWith(fadeOut(tween(90)))

/** A tick in a filled circle, the circle growing in and the tick then drawn as by a pen. */
@Composable
private fun DoneMark(modifier: Modifier = Modifier) {
    val circle = MaterialTheme.colorScheme.primaryContainer
    val tick = MaterialTheme.colorScheme.onPrimaryContainer
    val drawn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        drawn.animateTo(1f, tween(320))
    }
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        drawCircle(circle)
        val w = size.width
        path.reset()
        path.moveTo(w * 0.29f, w * 0.52f)
        path.lineTo(w * 0.44f, w * 0.66f)
        path.lineTo(w * 0.72f, w * 0.37f)
        measure.setPath(path, false)
        segment.reset()
        measure.getSegment(0f, measure.length * drawn.value, segment, true)
        drawPath(
            segment,
            tick,
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** How long the tick is shown before the sheet goes. */
private const val DONE_HOLD_MS = 2_500L

@Preview
@Composable
private fun UpdateCheckContentPreview() {
    AppTheme {
        Surface {
            UpdateCheckContent(
                UpdateCheckProgress(checked = 3, total = 12, current = "Solo Leveling", skipped = 8),
                UpdateCheckKind.CHAPTERS,
                finished = false,
                onCancel = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun MetadataRefreshContentPreview() {
    AppTheme {
        Surface {
            UpdateCheckContent(
                UpdateCheckProgress(checked = 41, total = 120, current = "The Left Hand of Darkness", skipped = 3),
                UpdateCheckKind.METADATA,
                finished = false,
                onCancel = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun UpdateCheckDonePreview() {
    AppTheme {
        Surface {
            UpdateCheckContent(
                UpdateCheckProgress(checked = 12, total = 12, current = null, skipped = 8),
                UpdateCheckKind.CHAPTERS,
                finished = true,
                onCancel = {},
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun UpdateCheckUpToDatePreview() {
    AppTheme {
        Surface {
            UpdateCheckContent(
                UpdateCheckProgress(checked = 0, total = 0, current = null, skipped = 8),
                UpdateCheckKind.CHAPTERS,
                finished = true,
                onCancel = {},
                onClose = {},
            )
        }
    }
}
