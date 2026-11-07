package dev.gavenda.kozeki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The top of the navigation rail, where the showing tab puts its main action. Only there while
 * the rail is; under a navigation bar it is null and screens keep their own floating button.
 */
@Stable
class RailAction {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)

    /** Whether the rail shows its labels beside the icons; the action then gets its own label too. */
    var expanded: Boolean by mutableStateOf(false)
}

val LocalRailAction = staticCompositionLocalOf<RailAction?> { null }

/** Puts [action] at the top of the rail for as long as this is in the composition. */
@Composable
fun RailActionEffect(action: @Composable () -> Unit) {
    val rail = LocalRailAction.current ?: return
    // Each recomposition hands the rail the new lambda, so what it draws never goes stale.
    val mine = remember { arrayOfNulls<@Composable () -> Unit>(1) }
    SideEffect {
        mine[0] = action
        if (rail.content !== action) rail.content = action
    }
    DisposableEffect(rail) {
        // A tab sliding out goes after the one sliding in has taken the slot.
        onDispose { if (rail.content === mine[0]) rail.content = null }
    }
}
