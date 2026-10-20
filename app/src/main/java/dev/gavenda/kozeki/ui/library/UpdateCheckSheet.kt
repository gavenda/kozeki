package dev.gavenda.kozeki.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.comic.UpdateCheckProgress
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.rememberExpandedSheetState
import dev.gavenda.kozeki.ui.theme.AppTheme

/**
 * How far a pull to refresh has got through the followed comics, with a way to stop it. Sent away,
 * the sheet only hides: the check goes on, and another pull brings the sheet back.
 */
@Composable
fun UpdateCheckSheet(progress: UpdateCheckProgress, onCancel: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        UpdateCheckContent(progress, onCancel)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateCheckContent(progress: UpdateCheckProgress, onCancel: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.update_check_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        // The hands go round once over the whole check; before the comics are counted it is the plain wait.
        HaloLoadingIndicator(
            progress = { if (progress.total == 0) null else progress.checked.toFloat() / progress.total },
            modifier = Modifier.padding(vertical = 8.dp).size(96.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.update_check_count, progress.total, progress.checked, progress.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        progress.current?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Preview
@Composable
private fun UpdateCheckContentPreview() {
    AppTheme {
        Surface {
            UpdateCheckContent(UpdateCheckProgress(checked = 3, total = 12, current = "Solo Leveling"), onCancel = {})
        }
    }
}
