package dev.gavenda.kozeki.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.backup.BackupInfo
import dev.gavenda.kozeki.data.backup.DriveLink
import dev.gavenda.kozeki.data.backup.RestorePlan
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Failure
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Notice
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Prompt
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState.Work
import dev.gavenda.kozeki.ui.DialogPreviews
import dev.gavenda.kozeki.ui.formatDateTime
import dev.gavenda.kozeki.ui.theme.AppTheme
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator

/** What the backup settings can ask for. */
class BackupActions(
    val signIn: () -> Unit = {},
    val signOut: () -> Unit = {},
    /** Backs up, having been told whether a backup made elsewhere may be replaced. */
    val backUp: (replace: Boolean) -> Unit = {},
    /** Gets the backup, to ask whether it should be put back. */
    val fetch: () -> Unit = {},
    val restore: () -> Unit = {},
    /** Closes whatever is being asked or reported. */
    val dismiss: () -> Unit = {},
)

/**
 * The backup on Google Drive: the account it goes to, and once somebody is signed in, when it was
 * last made, with the two things to do about it.
 */
@Composable
internal fun BackupSetting(state: DriveBackupState, actions: BackupActions) {
    val link = state.link
    val working = state.working
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    SettingRow(
        leading = { AccountAvatar(link?.photoUrl) },
        title = stringResource(R.string.settings_drive),
        subtitle = link?.email ?: stringResource(R.string.settings_drive_signed_out),
        modifier = Modifier.clickable(
            enabled = working == null,
            onClickLabel = stringResource(
                if (link == null) R.string.settings_drive_sign_in else R.string.settings_drive_sign_out,
            ),
            role = Role.Button,
        ) {
            if (link == null) actions.signIn() else confirmSignOut = true
        },
        trailing = if (working == Work.SIGNING_IN) {
            { HaloLoadingIndicator(Modifier.size(24.dp)) }
        } else {
            null
        },
    )

    if (link != null) {
        SettingRow(
            icon = painterResource(R.drawable.cloud_upload_fill_48px),
            title = when (working) {
                Work.BACKING_UP -> stringResource(R.string.settings_backup_working)
                Work.FETCHING -> stringResource(R.string.settings_backup_fetching)
                Work.RESTORING -> stringResource(R.string.settings_backup_restoring)
                Work.SIGNING_IN, null -> link.latest
                    ?.let { stringResource(R.string.settings_backup_latest, formatDateTime(it.createdAt)) }
                    ?: stringResource(R.string.settings_backup_none)
            },
            subtitle = stringResource(R.string.settings_backup_contents),
            trailing = if (working != null && working != Work.SIGNING_IN) {
                { HaloLoadingIndicator(Modifier.size(24.dp)) }
            } else {
                null
            },
        )
        // Indented to sit under the row's text rather than its icon.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { actions.backUp(false) },
                enabled = working == null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.settings_backup_now))
            }
            OutlinedButton(onClick = actions.fetch, enabled = working == null, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_backup_restore))
            }
        }
    }

    state.notice?.let { BackupNotice(it, actions.dismiss) }

    when (val prompt = state.prompt) {
        is Prompt.Replace -> ConfirmDialog(
            icon = painterResource(R.drawable.cloud_upload_fill_48px),
            title = stringResource(R.string.settings_backup_replace_title),
            paragraphs = listOf(
                stringResource(
                    R.string.settings_backup_replace_message,
                    bookCount(prompt.existing.books),
                    formatDateTime(prompt.existing.createdAt),
                    deviceName(prompt.existing),
                ),
            ),
            confirm = stringResource(R.string.settings_backup_replace_confirm),
            onConfirm = { actions.backUp(true) },
            onDismiss = actions.dismiss,
        )

        is Prompt.Restore -> ConfirmDialog(
            icon = painterResource(R.drawable.cloud_download_fill_48px),
            title = stringResource(R.string.settings_backup_restore_title),
            paragraphs = listOfNotNull(
                stringResource(
                    R.string.settings_backup_restore_message,
                    formatDateTime(prompt.plan.info.createdAt),
                    deviceName(prompt.plan.info),
                    bookCount(prompt.plan.info.books),
                ),
                prompt.plan.booksRemoved.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.settings_backup_restore_removed, it, it) },
                prompt.plan.booksWithoutFile.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.settings_backup_restore_without_file, it, it) },
            ),
            confirm = stringResource(R.string.settings_backup_restore),
            onConfirm = actions.restore,
            onDismiss = actions.dismiss,
        )

        null -> Unit
    }

    if (confirmSignOut && link != null) {
        ConfirmDialog(
            icon = painterResource(R.drawable.logout_48px),
            title = stringResource(R.string.settings_drive_sign_out_title),
            paragraphs = listOf(stringResource(R.string.settings_drive_sign_out_message)),
            confirm = stringResource(R.string.settings_drive_sign_out),
            onConfirm = {
                confirmSignOut = false
                actions.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** How the last backup or restore ended, under the rows until the user has read it. */
@Composable
private fun BackupNotice(notice: Notice, onDismiss: () -> Unit) {
    val text = when (notice) {
        is Notice.Restored -> listOfNotNull(
            stringResource(R.string.settings_backup_restored, bookCount(notice.plan.info.books)),
            notice.plan.booksWithoutFile.takeIf { it > 0 }
                ?.let { pluralStringResource(R.plurals.settings_backup_restored_without_file, it, it) },
        ).joinToString(" ")

        is Notice.Failed -> when (notice.failure) {
            Failure.OFFLINE -> stringResource(R.string.settings_backup_error_offline)
            Failure.SIGN_IN_REJECTED -> stringResource(R.string.settings_backup_error_sign_in, notice.detail.orEmpty())
            Failure.DRIVE_REFUSED -> stringResource(R.string.settings_backup_error_refused, notice.detail.orEmpty())
            Failure.NO_BACKUP -> stringResource(R.string.settings_backup_error_none)
            Failure.UNREADABLE -> stringResource(R.string.settings_backup_error_unreadable)
            Failure.TOO_NEW -> stringResource(R.string.settings_backup_error_too_new)
            Failure.OTHER -> stringResource(R.string.settings_backup_error_other)
        }
    }
    // Indented to sit under the row's text rather than its icon.
    Column(Modifier.padding(start = 60.dp, end = 20.dp, bottom = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (notice is Notice.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(R.string.action_ok))
        }
    }
}

/** Asks before something that takes the place of something else. */
@Composable
private fun ConfirmDialog(
    icon: Painter,
    title: String,
    paragraphs: List<String>,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // With an icon the dialog centres its headline, as Material 3 lays out a dialog that has one.
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                paragraphs.forEach { Text(it) }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shapes = ButtonDefaults.shapes()) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun bookCount(books: Int): String = pluralStringResource(R.plurals.book_count, books, books)

@Composable
private fun deviceName(info: BackupInfo): String =
    info.device.ifEmpty { stringResource(R.string.settings_backup_unknown_device) }

private val previewLink = DriveLink(
    email = "enda@example.com",
    latest = BackupInfo(createdAt = 1_791_234_840_000, device = "Pixel 9", books = 42),
)

@PreviewLightDark
@Composable
private fun BackupSettingPreview() {
    AppTheme {
        Surface {
            Column {
                Section(title = stringResource(R.string.settings_backup_title)) {
                    BackupSetting(DriveBackupState(), BackupActions())
                }
                Section(title = stringResource(R.string.settings_backup_title)) {
                    BackupSetting(
                        DriveBackupState(
                            link = previewLink,
                            notice = Notice.Restored(RestorePlan(previewLink.latest!!, booksRemoved = 0, booksWithoutFile = 12)),
                        ),
                        BackupActions(),
                    )
                }
                Section(title = stringResource(R.string.settings_backup_title)) {
                    BackupSetting(
                        DriveBackupState(
                            link = previewLink.copy(latest = null),
                            working = Work.BACKING_UP,
                            notice = Notice.Failed(Failure.DRIVE_REFUSED, "403 accessNotConfigured"),
                        ),
                        BackupActions(),
                    )
                }
            }
        }
    }
}

@DialogPreviews
@Composable
private fun RestorePromptPreview() {
    AppTheme {
        Surface {
            BackupSetting(
                DriveBackupState(
                    link = previewLink,
                    prompt = Prompt.Restore(RestorePlan(previewLink.latest!!, booksRemoved = 3, booksWithoutFile = 12)),
                ),
                BackupActions(),
            )
        }
    }
}

@DialogPreviews
@Composable
private fun ReplacePromptPreview() {
    AppTheme {
        Surface {
            BackupSetting(
                DriveBackupState(link = previewLink, prompt = Prompt.Replace(previewLink.latest!!)),
                BackupActions(),
            )
        }
    }
}
