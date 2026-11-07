package dev.gavenda.kozeki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The top of the navigation rail, where the showing tab puts its main action. Only there while
 * the rail is; under a navigation bar it is null and screens keep their own floating button.
 */
@Stable
class RailAction {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

val LocalRailAction = staticCompositionLocalOf<RailAction?> { null }

/** Puts [action] at the top of the rail for as long as this is in the composition. */
@Composable
fun RailActionEffect(action: @Composable () -> Unit) {
    val rail = LocalRailAction.current ?: return
    val current by rememberUpdatedState(action)
    DisposableEffect(rail) {
        val content: @Composable () -> Unit = { current() }
        rail.content = content
        // A tab sliding out goes after the one sliding in has taken the slot.
        onDispose { if (rail.content === content) rail.content = null }
    }
}
