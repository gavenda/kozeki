package dev.gavenda.kozeki.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.ui.theme.AppTheme

internal val MetadataSource.nameRes: Int
    get() = when (this) {
        MetadataSource.HARDCOVER -> R.string.source_hardcover
        MetadataSource.GOOGLE_BOOKS -> R.string.source_google_books
    }

/** The choice of which catalogue an online search goes to, for under that search's field. */
@Composable
fun SourcePicker(source: MetadataSource, onSourceChange: (MetadataSource) -> Unit, modifier: Modifier = Modifier) {
    val sources = MetadataSource.entries
    ConnectedButtonGroup(
        options = sources.map { stringResource(it.nameRes) },
        selectedIndex = sources.indexOf(source),
        onSelect = { onSourceChange(sources[it]) },
        modifier = modifier,
    )
}

/**
 * The first row of a list of results from [source], crediting it. Only Google's terms ask for that,
 * wherever its book results are shown, so the results of any other source get no such row. It
 * leads the list because a list that keeps loading has no end to put it at.
 */
fun LazyListScope.sourceAttributionItem(source: MetadataSource) {
    if (source != MetadataSource.GOOGLE_BOOKS) return
    item(key = "attribution", contentType = "attribution") {
        Text(
            text = stringResource(R.string.attribution_google),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun SourcePickerPreview() {
    AppTheme {
        Surface {
            var source by remember { mutableStateOf(MetadataSource.HARDCOVER) }
            SourcePicker(
                source = source,
                onSourceChange = { source = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}
