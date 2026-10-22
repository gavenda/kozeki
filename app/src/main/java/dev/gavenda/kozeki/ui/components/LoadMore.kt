package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Calls [onLoadMore] when the list is scrolled to within a few items of its end, and again each
 * time [itemCount] grows while it is still there. Once is all it asks per arrival, so a load that
 * failed is not retried until the user scrolls away and back.
 */
@Composable
fun LoadMoreEffect(listState: LazyListState, itemCount: Int, enabled: Boolean, onLoadMore: () -> Unit) {
    val nearEnd = remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            isNearEnd(layout.visibleItemsInfo.lastOrNull()?.index, layout.totalItemsCount)
        }
    }
    LoadMoreOnArrival(nearEnd.value, itemCount, enabled, onLoadMore)
}

/** [LoadMoreEffect] for a grid, counting in items rather than rows. */
@Composable
fun LoadMoreEffect(gridState: LazyGridState, itemCount: Int, enabled: Boolean, onLoadMore: () -> Unit) {
    val nearEnd = remember(gridState) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            isNearEnd(layout.visibleItemsInfo.lastOrNull()?.index, layout.totalItemsCount)
        }
    }
    LoadMoreOnArrival(nearEnd.value, itemCount, enabled, onLoadMore)
}

private fun isNearEnd(lastVisible: Int?, total: Int): Boolean =
    lastVisible != null && lastVisible >= total - 1 - LOAD_MORE_THRESHOLD

@Composable
private fun LoadMoreOnArrival(nearEnd: Boolean, itemCount: Int, enabled: Boolean, onLoadMore: () -> Unit) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(nearEnd, itemCount, enabled) {
        if (nearEnd && enabled) currentOnLoadMore()
    }
}

/** The last row of a list while its next results are on their way. */
fun LazyListScope.loadingMoreItem(visible: Boolean) {
    if (!visible) return
    item(key = "loading-more", contentType = "loading-more") {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            HaloLoadingIndicator()
        }
    }
}

/** The last row of a grid while its next results are on their way, across all its columns. */
fun LazyGridScope.loadingMoreItem(visible: Boolean) {
    if (!visible) return
    item(key = "loading-more", contentType = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            HaloLoadingIndicator()
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 3
