package dev.gavenda.kozeki.ui.components

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.metadata.BookMetadata
import dev.gavenda.kozeki.data.model.Acquisition
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow

/**
 * The results picked in a list of them, to be added together, by source ID. A long press starts
 * picking; while any are picked a tap picks too.
 */
@Stable
class ResultSelection internal constructor(initial: Set<String>) {
    var ids: Set<String> by mutableStateOf(initial)
        private set

    val active: Boolean get() = ids.isNotEmpty()

    fun toggle(result: BookMetadata) {
        ids = if (result.sourceId in ids) ids - result.sourceId else ids + result.sourceId
    }

    fun selectAll(results: List<BookMetadata>) {
        ids = results.mapTo(mutableSetOf()) { it.sourceId }
    }

    fun clear() {
        ids = emptySet()
    }

    /** The picked ones among [results], in their order there. */
    fun of(results: List<BookMetadata>): List<BookMetadata> = results.filter { it.sourceId in ids }
}

/** A [ResultSelection] kept across configuration changes. Back while picking ends it. */
@Composable
fun rememberResultSelection(): ResultSelection {
    val selection = rememberSaveable(saver = SelectionSaver) { ResultSelection(emptySet()) }
    BackHandler(selection.active) { selection.clear() }
    return selection
}

private val SelectionSaver = Saver<ResultSelection, ArrayList<String>>(
    save = { ArrayList(it.ids) },
    restore = { ResultSelection(it.toSet()) },
)

/**
 * What a tap on a result does: picks it while picking, otherwise opens it. A result already among
 * the user's books ([owned]) is never picked; it opens as their own.
 */
fun ResultSelection.onClick(result: BookMetadata, owned: Boolean, open: () -> Unit): () -> Unit = {
    if (active && !owned) toggle(result) else open()
}

/** A long press starts picking, on a result not the user's already. */
fun ResultSelection.onLongClick(result: BookMetadata, owned: Boolean): (() -> Unit)? =
    if (owned) null else ({ toggle(result) })

/**
 * Floats at the foot of [content]'s box while results are picked: how many, picking all of them,
 * and adding them. Books go on the wishlist or in as owned, as from a book's own page; [comics]
 * have the one way in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.AddSelectedToolbar(
    count: Int,
    allSelected: Boolean,
    comics: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onAdd: (Acquisition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countLabel = pluralStringResource(R.plurals.selection_count, count, count)
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.align(Alignment.BottomCenter).padding(bottom = FloatingToolbarDefaults.ScreenOffset),
    ) {
        IconButton(onClick = onClear) {
            Icon(painterResource(R.drawable.close_48px), contentDescription = stringResource(R.string.selection_clear), modifier = Modifier.size(24.dp))
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 4.dp, end = 12.dp)
                .semantics { contentDescription = countLabel },
        )
        IconButton(onClick = onSelectAll, enabled = !allSelected) {
            Icon(painterResource(R.drawable.select_all_48px), contentDescription = stringResource(R.string.selection_select_all), modifier = Modifier.size(24.dp))
        }
        if (comics) {
            IconButton(onClick = { onAdd(Acquisition.DOWNLOADED) }) {
                Icon(painterResource(R.drawable.add_48px), contentDescription = stringResource(R.string.bulk_add_comics), modifier = Modifier.size(24.dp))
            }
        } else {
            IconButton(onClick = { onAdd(Acquisition.WISHLIST) }) {
                Icon(painterResource(R.drawable.kid_star_48px), contentDescription = stringResource(R.string.bulk_add_wishlist), modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = { onAdd(Acquisition.PURCHASED) }) {
                Icon(painterResource(R.drawable.purchased_book_fill_48px), contentDescription = stringResource(R.string.bulk_add_purchased), modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** How far a list scrolls past its last row while the toolbar floats over its foot. */
val SelectionToolbarRoom = 96.dp

/** The line in the snackbar once picked results were added: how many, and as what. */
fun bulkAddedMessage(resources: Resources, added: BulkAdded): String {
    val plural = when (added.acquisition) {
        Acquisition.WISHLIST -> R.plurals.bulk_added_wishlist
        Acquisition.PURCHASED -> R.plurals.bulk_added_purchased
        else -> R.plurals.bulk_added_comics
    }
    return resources.getQuantityString(plural, added.count, added.count)
}

/** Says in [host] what each of [added] was, as it comes. */
@Composable
fun BulkAddedSnackbarEffect(added: Flow<BulkAdded>, host: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(added) {
        added.collect { host.showSnackbar(bulkAddedMessage(resources, it)) }
    }
}

/** [count] picked results were added to the library, as [acquisition]. */
data class BulkAdded(val count: Int, val acquisition: Acquisition)

@PreviewLightDark
@Composable
private fun AddSelectedToolbarPreview() {
    AppTheme {
        Surface {
            Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                AddSelectedToolbar(count = 3, allSelected = false, comics = false, onClear = {}, onSelectAll = {}, onAdd = {})
            }
        }
    }
}
