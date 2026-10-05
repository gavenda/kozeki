package dev.gavenda.kozeki.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.settings.ReaderFont
import dev.gavenda.kozeki.data.settings.ReaderPreferences
import dev.gavenda.kozeki.data.settings.ReaderTheme
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlin.math.roundToInt
import org.readium.r2.shared.util.Url

private const val MIN_FONT_SCALE = 0.7
private const val MAX_FONT_SCALE = 2.5
private const val FONT_SCALE_STEP = 0.1

/** The table of contents. Opens scrolled to the chapter being read. */
@Composable
fun ContentsSheet(
    entries: List<TocEntry>,
    currentIndex: Int?,
    onSelect: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        ContentsSheetContent(entries, currentIndex, onSelect)
    }
}

@Composable
private fun ContentsSheetContent(entries: List<TocEntry>, currentIndex: Int?, onSelect: (TocEntry) -> Unit) {
    Column {
        SheetTitle(stringResource(R.string.reader_contents))
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.reader_contents_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex ?: 0).coerceAtLeast(0))
            LazyColumn(state = listState) {
                itemsIndexed(entries) { index, entry ->
                    ListItem(
                        selected = index == currentIndex,
                        onClick = { onSelect(entry) },
                        // Nested entries are indented rather than collapsed, so nothing is hidden.
                        modifier = Modifier.padding(start = (16 * entry.level.coerceAtMost(4)).dp),
                    ) {
                        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Theme, text size, typeface and layout of the page. */
@Composable
fun AppearanceSheet(
    preferences: ReaderPreferences,
    textSettingsApply: Boolean,
    onChange: (ReaderPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        AppearanceSheetContent(preferences, textSettingsApply, onChange)
    }
}

@Composable
private fun AppearanceSheetContent(
    preferences: ReaderPreferences,
    textSettingsApply: Boolean,
    onChange: (ReaderPreferences) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.reader_appearance), style = MaterialTheme.typography.titleLargeEmphasized)

        if (!textSettingsApply) {
            Text(
                text = stringResource(R.string.reader_fixed_layout_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Setting(stringResource(R.string.reader_theme)) {
            val themes = ReaderTheme.entries
            ConnectedButtonGroup(
                options = themes.map { stringResource(it.labelRes) },
                selectedIndex = themes.indexOf(preferences.theme),
                onSelect = { onChange(preferences.copy(theme = themes[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Setting(stringResource(R.string.reader_text_size)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = { onChange(preferences.copy(fontScale = stepped(preferences.fontScale, -1))) },
                    enabled = preferences.fontScale > MIN_FONT_SCALE + 0.001,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.reader_text_smaller))
                }
                Text(
                    text = "${(preferences.fontScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(72.dp),
                )
                FilledTonalIconButton(
                    onClick = { onChange(preferences.copy(fontScale = stepped(preferences.fontScale, 1))) },
                    enabled = preferences.fontScale < MAX_FONT_SCALE - 0.001,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.reader_text_larger))
                }
            }
        }

        Setting(stringResource(R.string.reader_font)) {
            val fonts = ReaderFont.entries
            ConnectedButtonGroup(
                options = fonts.map { stringResource(it.labelRes) },
                selectedIndex = fonts.indexOf(preferences.font),
                onSelect = { onChange(preferences.copy(font = fonts[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Setting(stringResource(R.string.reader_layout)) {
            ConnectedButtonGroup(
                options = listOf(stringResource(R.string.reader_layout_pages), stringResource(R.string.reader_layout_scroll)),
                selectedIndex = if (preferences.scroll) 1 else 0,
                onSelect = { onChange(preferences.copy(scroll = it == 1)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.reader_justify),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = preferences.justify, onCheckedChange = { onChange(preferences.copy(justify = it)) })
        }
    }
}

private fun stepped(scale: Double, direction: Int): Double =
    ((scale + direction * FONT_SCALE_STEP) * 10).roundToInt().div(10.0).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

private val ReaderTheme.labelRes: Int
    get() = when (this) {
        ReaderTheme.SYSTEM -> R.string.reader_theme_system
        ReaderTheme.LIGHT -> R.string.reader_theme_light
        ReaderTheme.SEPIA -> R.string.reader_theme_sepia
        ReaderTheme.DARK -> R.string.reader_theme_dark
    }

private val ReaderFont.labelRes: Int
    get() = when (this) {
        ReaderFont.PUBLISHER -> R.string.reader_font_publisher
        ReaderFont.SERIF -> R.string.reader_font_serif
        ReaderFont.SANS_SERIF -> R.string.reader_font_sans
        ReaderFont.MONOSPACE -> R.string.reader_font_mono
    }

@Composable
private fun Setting(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@PreviewLightDark
@Composable
private fun ContentsSheetContentPreview() {
    val url = Url("chapter.xhtml")!!
    AppTheme {
        Surface {
            ContentsSheetContent(
                entries = listOf(
                    TocEntry("A Parade in Erhenrang", url, 0, 1),
                    TocEntry("The Place Inside the Blizzard", url, 0, 2),
                    TocEntry("The Mad King", url, 1, 3),
                    TocEntry("The Nineteenth Day", url, 1, 4),
                    TocEntry("Estraven the Traitor", url, 0, 5),
                ),
                currentIndex = 2,
                onSelect = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AppearanceSheetContentPreview() {
    AppTheme {
        Surface { AppearanceSheetContent(ReaderPreferences(fontScale = 1.2), textSettingsApply = true, onChange = {}) }
    }
}
