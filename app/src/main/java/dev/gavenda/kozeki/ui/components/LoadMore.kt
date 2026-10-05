package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LoadingIndicator
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
    val nearEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= layout.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
        }
    }
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
            LoadingIndicator()
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 3
