package dev.gavenda.kozeki.data.update

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The check for a newer version of the app, as far as it has got. */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    /** `asked` when the user asked for the check, rather than the app opening. */
    data class Checked(val result: UpdateCheck, val asked: Boolean = false) : AppUpdateState
}

/**
 * Checks for a newer version once when the app is freshly opened, and again whenever the user
 * asks. Lives as long as the process, so the result is still there when Settings opens.
 */
class AppUpdates(private val checker: UpdateChecker, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _offerDismissed = MutableStateFlow(false)

    /** Whether the user put off the newer version found on launch. It is not offered again this run. */
    val offerDismissed: StateFlow<Boolean> = _offerDismissed.asStateFlow()

    private val launchChecked = AtomicBoolean(false)

    private companion object {
        val MIN_ASKED_CHECK = 1.seconds
    }

    /**
     * The check made on a fresh open. It runs once per process, so coming back to the app or
     * turning the screen does not check again. A failure here goes unmentioned, as nobody asked.
     */
    fun checkOnLaunch() {
        if (!launchChecked.compareAndSet(false, true)) return
        run(quiet = true)
    }

    /** The check the user asked for. */
    fun check() = run(quiet = false)

    /**
     * Forgets how an asked-for check went, so Settings opens with Ui's own line. A newer version
     * found when the app opened is kept: that is the launch check's only news.
     */
    fun clearAsked() {
        _state.update { if (it is AppUpdateState.Checked && it.asked) AppUpdateState.Idle else it }
    }

    /** Puts off the newer version until the next fresh open. */
    fun dismissOffer() {
        _offerDismissed.value = true
    }

    private fun run(quiet: Boolean) {
        if (_state.value == AppUpdateState.Checking) return
        val before = _state.value
        _state.value = AppUpdateState.Checking
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            val result = checker.check()
            // GitHub often answers faster than Ui's face can change, so an asked-for check lasts
            // long enough for her checking face to be seen.
            if (!quiet) delay(MIN_ASKED_CHECK - started.elapsedNow())
            _state.value = if (quiet && result is UpdateCheck.Failed) before else AppUpdateState.Checked(result, asked = !quiet)
        }
    }
}
