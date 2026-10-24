package dev.gavenda.kozeki.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.data.backup.BackupInfo
import dev.gavenda.kozeki.data.backup.RestorePlan
import dev.gavenda.kozeki.data.backup.local.LocalBackup
import dev.gavenda.kozeki.data.backup.local.LocalBackupState
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Failure
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Notice
import dev.gavenda.kozeki.data.backup.local.LocalBackupState.Work
import dev.gavenda.kozeki.ui.DialogPreviews
import dev.gavenda.kozeki.ui.components.ConnectedButtonGroup
import dev.gavenda.kozeki.ui.components.HaloLoadingIndicator
import dev.gavenda.kozeki.ui.components.OptionColor
import dev.gavenda.kozeki.ui.components.toggleButtonColors
import dev.gavenda.kozeki.ui.formatDateTime
import dev.gavenda.kozeki.ui.theme.AppTheme

/** What the local backup settings can ask for. */
class LocalBackupActions(
    /** Opens the system's folder picker. */
    val pickFolder: () -> Unit = {},
    val setInterval: (hours: Int) -> Unit = {},
    val backUp: () -> Unit = {},
    /** Opens the system's file picker, for the backup to restore. */
    val pickFile: () -> Unit = {},
    val restore: () -> Unit = {},
    val dismiss: () -> Unit = {},
)

/** Hours between automatic backups that can be picked, 0 being off. */
private val BACKUP_INTERVALS = listOf(0, 24, 72, 168)

/**
 * Backups to a folder on the device: the folder, how often one is written by itself, the last one
 * written, and the two things to do by hand.
 */
@Composable
internal fun LocalBackupSetting(state: LocalBackupState, actions: LocalBackupActions) {
    val working = state.working
    val folderPicked = state.folder != null

    SettingRow(
        leading = {
            Icon(
                painterResource(R.drawable.folder_fill_48px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        title = stringResource(R.string.settings_local_backup),
        subtitle = when {
            !folderPicked -> stringResource(R.string.settings_local_backup_no_folder)
            state.folderName == null -> stringResource(R.string.settings_local_backup_folder_lost)
            else -> state.folderName
        },
        subtitleColor = if (folderPicked && state.folderName == null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.clickable(
            enabled = working == null,
            onClickLabel = stringResource(R.string.settings_local_backup_choose),
            role = Role.Button,
            onClick = actions.pickFolder,
        ),
    )

    if (!folderPicked) return

    SettingRow(
        icon = painterResource(R.drawable.schedule_fill_48px),
        title = stringResource(R.string.settings_local_backup_auto),
        subtitle = pluralStringResource(R.plurals.settings_local_backup_kept, LocalBackup.BACKUPS_KEPT, LocalBackup.BACKUPS_KEPT),
    )
    // A period kept from elsewhere that is none of the choices shows as the nearest one.
    val selected = BACKUP_INTERVALS.indexOf(state.intervalHours).takeIf { it >= 0 }
        ?: BACKUP_INTERVALS.indices.minBy { kotlin.math.abs(BACKUP_INTERVALS[it] - state.intervalHours) }
    ConnectedButtonGroup(
        options = BACKUP_INTERVALS.map { intervalLabel(it) },
        selectedIndex = selected,
        onSelect = { actions.setInterval(BACKUP_INTERVALS[it]) },
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        optionColors = BACKUP_INTERVALS.map { OptionColor.VIOLET.toggleButtonColors() },
    )

    // Each is a row of its own, tapped like any other setting.
    SettingRow(
        icon = painterResource(R.drawable.save_fill_48px),
        title = stringResource(
            if (working == Work.BACKING_UP) R.string.settings_backup_working else R.string.settings_backup_now,
        ),
        // A lost folder says so on its own row, and no other failure has a cause worth guessing at.
        subtitle = if (state.failure != null) {
            stringResource(R.string.settings_local_backup_failed)
        } else {
            state.latest
                ?.let { stringResource(R.string.settings_backup_latest, formatDateTime(it.createdAt)) }
                ?: stringResource(R.string.settings_backup_none)
        },
        subtitleColor = if (state.failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(enabled = working == null, role = Role.Button, onClick = actions.backUp),
        trailing = if (working == Work.BACKING_UP) {
            { HaloLoadingIndicator(Modifier.size(24.dp)) }
        } else {
            null
        },
    )
    SettingRow(
        icon = painterResource(R.drawable.settings_backup_restore_48px),
        title = stringResource(
            when (working) {
                Work.READING -> R.string.settings_local_backup_reading
                Work.RESTORING -> R.string.settings_backup_restoring
                else -> R.string.settings_backup_restore
            },
        ),
        subtitle = stringResource(R.string.settings_local_backup_restore_description),
        modifier = Modifier.clickable(enabled = working == null, role = Role.Button, onClick = actions.pickFile),
        trailing = if (working == Work.READING || working == Work.RESTORING) {
            { HaloLoadingIndicator(Modifier.size(24.dp)) }
        } else {
            null
        },
    )

    when (val notice = state.notice) {
        is Notice.Restored -> NoticeText(
            text = listOfNotNull(
                stringResource(R.string.settings_backup_restored, bookCount(notice.plan.info.books)),
                notice.plan.booksWithoutFile.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.settings_backup_restored_without_file, it, it) },
            ).joinToString(" "),
            error = false,
            onDismiss = actions.dismiss,
        )

        is Notice.Failed -> NoticeText(
            text = when (notice.failure) {
                Failure.UNREADABLE, Failure.NO_ACCESS -> stringResource(R.string.settings_local_backup_error_unreadable)
                Failure.TOO_NEW -> stringResource(R.string.settings_backup_error_too_new)
                Failure.OTHER -> stringResource(R.string.settings_backup_error_other)
            },
            error = true,
            onDismiss = actions.dismiss,
        )

        null -> Unit
    }

    state.prompt?.let { plan ->
        ConfirmDialog(
            icon = painterResource(R.drawable.history_48px),
            title = stringResource(R.string.settings_backup_restore_title),
            paragraphs = listOfNotNull(
                stringResource(
                    R.string.settings_backup_restore_message,
                    formatDateTime(plan.info.createdAt),
                    deviceName(plan.info),
                    bookCount(plan.info.books),
                ),
                plan.booksRemoved.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.settings_backup_restore_removed, it, it) },
                plan.booksWithoutFile.takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.settings_backup_restore_without_file, it, it) },
            ),
            confirm = stringResource(R.string.settings_backup_restore),
            onConfirm = actions.restore,
            onDismiss = actions.dismiss,
        )
    }
}

@Composable
private fun intervalLabel(hours: Int): String = when (hours) {
    0 -> stringResource(R.string.settings_updates_off)
    24 -> stringResource(R.string.settings_local_backup_daily)
    168 -> stringResource(R.string.settings_local_backup_weekly)
    else -> pluralStringResource(R.plurals.settings_local_backup_days, hours / 24, hours / 24)
}

private val previewInfo = BackupInfo(createdAt = 1_791_234_840_000, device = "Pixel 9", books = 42)

@PreviewLightDark
@Composable
private fun LocalBackupSettingPreview() {
    AppTheme {
        Surface {
            Column {
                LocalPreviewSection(LocalBackupState())
                LocalPreviewSection(
                    LocalBackupState(folder = "content://folder", folderName = "Backups", intervalHours = 24, latest = previewInfo),
                )
                LocalPreviewSection(
                    LocalBackupState(
                        folder = "content://folder",
                        intervalHours = 168,
                        latest = previewInfo,
                        failure = Failure.NO_ACCESS,
                        notice = Notice.Failed(Failure.UNREADABLE),
                    ),
                )
            }
        }
    }
}

/** The local rows as Settings shows them, in their card of the backup section. */
@Composable
private fun LocalPreviewSection(state: LocalBackupState) {
    SplitSection(title = stringResource(R.string.settings_backup_title)) {
        SettingSubgroup(label = stringResource(R.string.settings_backup_local)) {
            LocalBackupSetting(state, LocalBackupActions())
        }
    }
}

@DialogPreviews
@Composable
private fun LocalRestorePromptPreview() {
    AppTheme {
        Surface {
            LocalBackupSetting(
                LocalBackupState(
                    folder = "content://folder",
                    folderName = "Backups",
                    prompt = RestorePlan(previewInfo, booksRemoved = 3, booksWithoutFile = 12),
                ),
                LocalBackupActions(),
            )
        }
    }
}
