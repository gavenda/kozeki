package dev.gavenda.kozeki.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Previews a whole screen on a phone in both themes and on a tablet, where the rail replaces the bar. */
@Preview(name = "Phone", showBackground = true)
@Preview(name = "Phone, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Tablet", showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
annotation class ScreenPreviews

/**
 * Previews a dialog in both themes. A dialog is drawn in a window of its own and leaves the preview
 * itself empty, which Android Studio sizes to nothing and shows blank, so the whole screen is asked for.
 */
@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class DialogPreviews
