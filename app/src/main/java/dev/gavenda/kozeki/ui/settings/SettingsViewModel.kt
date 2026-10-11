package dev.gavenda.kozeki.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.backup.drive.DriveBackup
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAccount
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.repository.StatsRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.data.settings.ThemeMode
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hardcover: HardcoverAccount = HardcoverAccount.SignedOut,
    val signIn: HardcoverSignIn.Status = HardcoverSignIn.Status.IDLE,
    /** Shown so a mismatch with what is registered on Hardcover is easy to spot. */
    val hardcoverRedirectUri: String = "",
    val backup: DriveBackupState = DriveBackupState(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyGoalMinutes: Int = SettingsRepository.DEFAULT_DAILY_GOAL_MINUTES,
    val year: Int = 0,
    val yearlyGoalBooks: Int? = null,
    val cachedLookups: Int = 0,
    val versionName: String = "",
)

sealed interface SettingsEvent {
    /** Send the user to Hardcover to approve the sign-in. */
    data class OpenBrowser(val uri: Uri) : SettingsEvent
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val metadata: MetadataRepository,
    private val stats: StatsRepository,
    private val hardcoverAuth: HardcoverAuth,
    private val hardcoverSignIn: HardcoverSignIn,
    private val driveBackup: DriveBackup,
    versionName: String,
    clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    private val accounts = combine(hardcoverAuth.account, hardcoverSignIn.status, driveBackup.state, ::Triple)

    val uiState: StateFlow<SettingsUiState> = combine(
        accounts,
        settings.themeMode,
        settings.dailyGoalMinutes,
        stats.observeYearlyGoal(today.year),
        metadata.observeCacheSize(),
    ) { (hardcover, signIn, backup), themeMode, dailyGoal, yearlyGoal, cached ->
        SettingsUiState(
            hardcover = hardcover,
            signIn = signIn,
            hardcoverRedirectUri = hardcoverAuth.redirectUri,
            backup = backup,
            themeMode = themeMode,
            dailyGoalMinutes = dailyGoal,
            year = today.year,
            yearlyGoalBooks = yearlyGoal,
            cachedLookups = cached,
            versionName = versionName,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            hardcoverRedirectUri = hardcoverAuth.redirectUri,
            backup = driveBackup.state.value,
            year = today.year,
            versionName = versionName,
        ),
    )

    fun signIn() {
        viewModelScope.launch { eventChannel.send(SettingsEvent.OpenBrowser(hardcoverSignIn.begin())) }
    }

    /** The screen is in front again, with or without the browser having redirected back. */
    fun onResumed() = hardcoverSignIn.browserClosed()

    fun dismissSignInError() = hardcoverSignIn.dismissFailure()

    fun signOut() {
        viewModelScope.launch { hardcoverAuth.signOut() }
    }

    fun signInToDrive() = driveBackup.signIn()

    fun signOutOfDrive() = driveBackup.signOut()

    /** Google's consent screen is being opened. */
    fun onDriveConsentShown() = driveBackup.consentShown()

    /** Google's consent screen closed. [result] is null when the user left it without allowing anything. */
    fun onDriveConsent(result: Intent?) = driveBackup.consentAnswered(result)

    /** @param replace The user has agreed to replace a backup made elsewhere. */
    fun backUp(replace: Boolean = false) = driveBackup.backUp(replace)

    /** Gets the backup, after which the user is asked whether to put it back. */
    fun fetchBackup() = driveBackup.fetchBackup()

    fun restoreBackup() = driveBackup.restore()

    fun dismissBackupNotice() = driveBackup.dismiss()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch { settings.setDailyGoalMinutes(minutes) }
    }

    fun setYearlyGoal(books: Int) {
        viewModelScope.launch { stats.setYearlyGoal(today.year, books) }
    }

    fun clearCache() {
        viewModelScope.launch { metadata.clearCache() }
    }
}
