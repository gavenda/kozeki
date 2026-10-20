package dev.gavenda.kozeki.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.backup.drive.DriveBackup
import dev.gavenda.kozeki.data.backup.drive.DriveBackupState
import dev.gavenda.kozeki.data.comic.ComicExtension
import dev.gavenda.kozeki.data.comic.ComicSources
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAccount
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.repository.StatsRepository
import dev.gavenda.kozeki.data.tracking.anilist.AniListAccount
import dev.gavenda.kozeki.data.tracking.anilist.AniListAuth
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.data.settings.ThemeMode
import dev.gavenda.kozeki.ui.isUiBirthday
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
    val aniList: AniListAccount = AniListAccount.SignedOut,
    val aniListSignIn: AniListAuth.Status = AniListAuth.Status.IDLE,
    val backup: DriveBackupState = DriveBackupState(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyGoalMinutes: Int = SettingsRepository.DEFAULT_DAILY_GOAL_MINUTES,
    val year: Int = 0,
    val yearlyGoalBooks: Int? = null,
    val cachedLookups: Int = 0,
    /** The Mihon extensions installed on the device, or null until they have been looked for. */
    val extensions: List<ComicExtension>? = null,
    /** Hours between the checks of followed comics for new chapters, 0 being never. */
    val updateIntervalHours: Int = SettingsRepository.DEFAULT_UPDATE_INTERVAL_HOURS,
    val versionName: String = "",
    /** Today is Ui's birthday. */
    val birthday: Boolean = false,
)

sealed interface SettingsEvent {
    /** Send the user to Hardcover to approve the sign-in. */
    data class OpenBrowser(val uri: Uri) : SettingsEvent
}

/** What the screen reads from the sources of comics and the tracker, held together to be combined. */
private data class LibrarySettings(
    val extensions: List<ComicExtension>?,
    val updateIntervalHours: Int,
    val aniList: AniListAccount,
    val aniListSignIn: AniListAuth.Status,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val metadata: MetadataRepository,
    private val stats: StatsRepository,
    private val hardcoverAuth: HardcoverAuth,
    private val hardcoverSignIn: HardcoverSignIn,
    private val aniListAuth: AniListAuth,
    private val driveBackup: DriveBackup,
    private val comicSources: ComicSources,
    versionName: String,
    clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    private val accounts = combine(hardcoverAuth.account, hardcoverSignIn.status, driveBackup.state, ::Triple)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            accounts,
            combine(
                comicSources.extensions,
                settings.updateIntervalHours,
                aniListAuth.account,
                aniListAuth.status,
                ::LibrarySettings,
            ),
            ::Pair,
        ),
        settings.themeMode,
        settings.dailyGoalMinutes,
        stats.observeYearlyGoal(today.year),
        metadata.observeCacheSize(),
    ) { (accountsState, comicState), themeMode, dailyGoal, yearlyGoal, cached ->
        val (hardcover, signIn, backup) = accountsState
        val (extensions, updateIntervalHours, aniList, aniListSignIn) = comicState
        SettingsUiState(
            hardcover = hardcover,
            signIn = signIn,
            hardcoverRedirectUri = hardcoverAuth.redirectUri,
            aniList = aniList,
            aniListSignIn = aniListSignIn,
            backup = backup,
            themeMode = themeMode,
            dailyGoalMinutes = dailyGoal,
            year = today.year,
            yearlyGoalBooks = yearlyGoal,
            cachedLookups = cached,
            extensions = extensions,
            updateIntervalHours = updateIntervalHours,
            versionName = versionName,
            birthday = isUiBirthday(today),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            hardcoverRedirectUri = hardcoverAuth.redirectUri,
            backup = driveBackup.state.value,
            year = today.year,
            versionName = versionName,
            birthday = isUiBirthday(today),
        ),
    )

    init {
        // The list is what the section shows, so it is not left for the first time something searches.
        viewModelScope.launch { comicSources.ensureLoaded() }
    }

    /** Looks for the installed extensions again, for after one was installed or removed. */
    fun refreshExtensions() {
        viewModelScope.launch { comicSources.refresh() }
    }

    fun signIn() {
        viewModelScope.launch { eventChannel.send(SettingsEvent.OpenBrowser(hardcoverSignIn.begin())) }
    }

    /** The screen is in front again, with or without the browser having redirected back. */
    fun onResumed() {
        hardcoverSignIn.browserClosed()
        aniListAuth.browserClosed()
    }

    fun dismissSignInError() = hardcoverSignIn.dismissFailure()

    fun signOut() {
        viewModelScope.launch { hardcoverAuth.signOut() }
    }

    fun signInToAniList() {
        eventChannel.trySend(SettingsEvent.OpenBrowser(aniListAuth.beginSignIn()))
    }

    fun signOutOfAniList() {
        viewModelScope.launch { aniListAuth.signOut() }
    }

    fun dismissAniListError() = aniListAuth.dismissFailure()

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

    fun setUpdateInterval(hours: Int) {
        viewModelScope.launch { settings.setUpdateIntervalHours(hours) }
    }

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
