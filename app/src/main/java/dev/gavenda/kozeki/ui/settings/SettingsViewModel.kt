package dev.gavenda.kozeki.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.kozeki.data.metadata.MetadataRepository
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAccount
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.model.MetadataSource
import dev.gavenda.kozeki.data.repository.StatsRepository
import dev.gavenda.kozeki.data.settings.SettingsRepository
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
    val source: MetadataSource = MetadataSource.GOOGLE_BOOKS,
    val googleBooksConfigured: Boolean = false,
    val googleRequestsToday: Int = 0,
    val hardcover: HardcoverAccount = HardcoverAccount.SignedOut,
    val signIn: HardcoverSignIn.Status = HardcoverSignIn.Status.IDLE,
    /** Shown so a mismatch with what is registered on Hardcover is easy to spot. */
    val hardcoverRedirectUri: String = "",
    val dynamicColor: Boolean = true,
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
    private val scheduleMatching: () -> Unit,
    googleBooksConfigured: Boolean,
    versionName: String,
    clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    private data class Sources(
        val source: MetadataSource,
        val requestsToday: Int,
        val hardcover: HardcoverAccount,
        val signIn: HardcoverSignIn.Status,
    )

    private val sources = combine(
        settings.metadataSource,
        settings.googleBooksRequests,
        hardcoverAuth.account,
        hardcoverSignIn.status,
    ) { source, requests, account, signIn ->
        Sources(source, if (requests.day == today.toEpochDay()) requests.count else 0, account, signIn)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        sources,
        settings.dynamicColor,
        settings.dailyGoalMinutes,
        stats.observeYearlyGoal(today.year),
        metadata.observeCacheSize(),
    ) { sources, dynamicColor, dailyGoal, yearlyGoal, cached ->
        SettingsUiState(
            source = sources.source,
            googleBooksConfigured = googleBooksConfigured,
            googleRequestsToday = sources.requestsToday,
            hardcover = sources.hardcover,
            signIn = sources.signIn,
            hardcoverRedirectUri = hardcoverAuth.redirectUri,
            dynamicColor = dynamicColor,
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
            googleBooksConfigured = googleBooksConfigured,
            hardcoverRedirectUri = hardcoverAuth.redirectUri,
            year = today.year,
            versionName = versionName,
        ),
    )

    fun selectSource(source: MetadataSource) {
        viewModelScope.launch {
            settings.setMetadataSource(source)
            // Books still waiting for a lookup may be answerable by the newly selected source.
            scheduleMatching()
        }
    }

    fun signIn() {
        viewModelScope.launch { eventChannel.send(SettingsEvent.OpenBrowser(hardcoverSignIn.begin())) }
    }

    /** The screen is in front again, with or without the browser having redirected back. */
    fun onResumed() = hardcoverSignIn.browserClosed()

    fun dismissSignInError() = hardcoverSignIn.dismissFailure()

    fun signOut() {
        viewModelScope.launch { hardcoverAuth.signOut() }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
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
