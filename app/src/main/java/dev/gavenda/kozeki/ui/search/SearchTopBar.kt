package dev.gavenda.kozeki.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.Book
import kotlin.math.roundToInt

private val SearchBarInset = 16.dp
private val SettingsButtonSize = 48.dp
private val SettingsButtonInset = 4.dp

/**
 * The top of a list of books: a large title with the search bar under it. Scrolling takes the
 * title away and leaves the search bar pinned at the top of the screen, with settings beside it.
 */
@Composable
fun SearchTopBar(
    title: String,
    subtitle: String?,
    search: SearchUiState,
    onSearchQueryChange: (String) -> Unit,
    onBookClick: (Book) -> Unit,
    onSettingsClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val barState = scrollBehavior.state
    val containerColor = MaterialTheme.colorScheme.surface
    val scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier
            .drawBehind { drawRect(lerp(containerColor, scrolledContainerColor, barState.collapsedFraction)) }
            .windowInsetsPadding(TopAppBarDefaults.windowInsets),
    ) {
        Column {
            Column(
                modifier = Modifier
                    .clipToBounds()
                    // The title is as tall as the bar can collapse: it slides up out of its own box.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val limit = -placeable.height.toFloat()
                        if (barState.heightOffsetLimit != limit) barState.heightOffsetLimit = limit
                        val height = (placeable.height + barState.heightOffset).roundToInt().coerceAtLeast(0)
                        layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
                    }
                    .graphicsLayer { alpha = 1f - barState.collapsedFraction }
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 20.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Always there, so the title does not jump when the subtitle arrives.
                Text(
                    text = subtitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BookSearchBar(
                state = search,
                onQueryChange = onSearchQueryChange,
                onBookClick = onBookClick,
                modifier = Modifier
                    // The bar gives up its end to the settings button before it slides up beside it.
                    .layout { measurable, constraints ->
                        val making = ((barState.collapsedFraction - 0.4f) / 0.25f).coerceIn(0f, 1f)
                        val start = SearchBarInset.roundToPx()
                        val end = lerp(SearchBarInset, SettingsButtonInset + SettingsButtonSize, making).roundToPx()
                        val placeable = measurable.measure(constraints.offset(horizontal = -(start + end)))
                        layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(start, 0) }
                    }
                    .padding(vertical = 8.dp),
            )
        }
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                // From the title's corner down to the middle of the search bar, a few pixels lower.
                .offset {
                    IntOffset(
                        x = -SettingsButtonInset.roundToPx(),
                        y = lerp(8.dp, 12.dp, barState.collapsedFraction).roundToPx(),
                    )
                },
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
        }
    }
}
