package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R

/** How a run of books is laid out, in the library or the results of a search. */
enum class BookDisplay {
    /** A grid of covers. */
    COVERS,

    /** One row per book, with a small cover and more of its details in words. */
    LIST;

    companion object {
        /** The display saved as [name], or [default] when there is none or it is not known. */
        fun named(name: String?, default: BookDisplay): BookDisplay = entries.firstOrNull { it.name == name } ?: default
    }
}

/** Flips between covers and a list. The button shows the layout a tap leads to. */
@Composable
fun DisplayToggle(
    display: BookDisplay,
    onDisplaySelected: (BookDisplay) -> Unit,
    modifier: Modifier = Modifier,
    inAppBar: Boolean = false,
) {
    val other = if (display == BookDisplay.LIST) BookDisplay.COVERS else BookDisplay.LIST
    val icon: @Composable () -> Unit = {
        Icon(
            painter = painterResource(if (other == BookDisplay.LIST) R.drawable.view_list_fill_48px else R.drawable.grid_view_fill_48px),
            contentDescription = stringResource(
                if (other == BookDisplay.LIST) R.string.display_as_list else R.string.display_as_covers,
            ),
            modifier = Modifier.size(24.dp),
        )
    }
    // In an app bar it takes the bar's pill, as the other actions there do.
    if (inAppBar) {
        FilledTonalIconButton(
            onClick = { onDisplaySelected(other) },
            shapes = IconButtonDefaults.shapes(),
            colors = appBarIconButtonColors(),
            modifier = modifier.narrowIconButton(),
            content = icon,
        )
    } else {
        IconButton(onClick = { onDisplaySelected(other) }, modifier = modifier, content = icon)
    }
}
