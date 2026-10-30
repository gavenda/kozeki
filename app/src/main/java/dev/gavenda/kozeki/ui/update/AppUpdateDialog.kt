package dev.gavenda.kozeki.ui.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.update.AppInstallError
import dev.gavenda.kozeki.data.update.AppInstallStep
import dev.gavenda.kozeki.data.update.AppUpdateState
import dev.gavenda.kozeki.data.update.AppUpdateInstaller
import dev.gavenda.kozeki.data.update.AppUpdates
import dev.gavenda.kozeki.data.update.UpdateCheck

/**
 * Offers the newer version found when the app opened, and shows it being installed, whether it
 * was started here or from About. A version found by tapping Ui in About is not offered again here:
 * her line already says so.
 */
@Composable
fun AppUpdateDialog(updates: AppUpdates, installer: AppUpdateInstaller) {
    val state by updates.state.collectAsStateWithLifecycle()
    val step by installer.step.collectAsStateWithLifecycle()
    val dismissed by updates.offerDismissed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val current = step) {
        null -> {
            val checked = state as? AppUpdateState.Checked ?: return
            val available = checked.result as? UpdateCheck.Available ?: return
            if (checked.asked || dismissed) return
            UpdateOffer(
                version = available.version,
                onUpdate = {
                    if (available.isApk) {
                        installer.install(available)
                    } else {
                        // No APK on the release; its page is the next best thing.
                        updates.dismissOffer()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.url)))
                        } catch (_: ActivityNotFoundException) {
                            // Nothing on the device opens links.
                        }
                    }
                },
                onLater = updates::dismissOffer,
            )
        }
        is AppInstallStep.Failed -> UpdateFailed(
            current.error,
            onClose = {
                installer.dismiss()
                updates.dismissOffer()
            },
        )
        else -> UpdateWorking(installing = current == AppInstallStep.Installing)
    }
}

@Composable
private fun UpdateOffer(version: String, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.app_update_title)) },
        text = { Text(stringResource(R.string.app_update_message, version)) },
        confirmButton = { TextButton(onClick = onUpdate) { Text(stringResource(R.string.app_update_install)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.app_update_later)) } },
    )
}

/** Stays up until the system has its prompt up or the app restarts into the new version. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateWorking(installing: Boolean) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.app_update_working_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (installing) R.string.app_update_installing else R.string.app_update_downloading))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun UpdateFailed(error: AppInstallError, onClose: () -> Unit) {
    val message = when (error) {
        AppInstallError.Offline -> stringResource(R.string.app_update_offline)
        is AppInstallError.Refused -> stringResource(R.string.app_update_refused, error.code)
        AppInstallError.WrongSignature -> stringResource(R.string.app_update_signature)
        AppInstallError.NotAnApk -> stringResource(R.string.app_update_not_apk)
        is AppInstallError.System -> error.message?.let { stringResource(R.string.app_update_system, it) }
            ?: stringResource(R.string.app_update_system_unknown)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.app_update_failed_title)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
}
