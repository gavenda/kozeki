package dev.gavenda.kozeki.data.metadata.hardcover

import android.net.Uri
import dev.gavenda.kozeki.data.metadata.MetadataException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Carries a Hardcover sign-in across the trip to the browser and back. The redirect arrives at the
 * activity, possibly in a fresh process, so the outcome is held here rather than in a screen.
 */
class HardcoverSignIn(
    private val auth: HardcoverAuth,
    private val scope: CoroutineScope,
    private val onSignedIn: () -> Unit,
) {
    enum class Status {
        IDLE,

        /** The browser is open and Hardcover has not redirected back yet. */
        AWAITING_REDIRECT,

        /** The redirect arrived and its code is being exchanged for tokens. */
        EXCHANGING,

        /** The user pressed Deny on Hardcover. */
        DENIED,

        /** Hardcover refused the request, most often because the redirect URI is not registered. */
        REJECTED,
        OFFLINE,
    }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Returns the page to open in the browser. */
    suspend fun begin(): Uri {
        _status.value = Status.AWAITING_REDIRECT
        return auth.beginSignIn()
    }

    /** Handles Hardcover's redirect back into the app. */
    fun complete(redirect: Uri) {
        _status.value = Status.EXCHANGING
        scope.launch {
            _status.value = try {
                auth.completeSignIn(redirect)
                onSignedIn()
                Status.IDLE
            } catch (e: CancellationException) {
                throw e
            } catch (e: HardcoverAuth.SignInException) {
                if (e.failure == HardcoverAuth.SignInFailure.DENIED) Status.DENIED else Status.REJECTED
            } catch (e: MetadataException.Network) {
                Status.OFFLINE
            } catch (e: Exception) {
                Status.REJECTED
            }
        }
    }

    /**
     * The app is in front again. If no redirect came with it, the user closed the browser without
     * finishing. A redirect is always delivered before the app resumes, so by then the status has
     * already moved on to [Status.EXCHANGING].
     */
    fun browserClosed() {
        if (_status.value == Status.AWAITING_REDIRECT) _status.value = Status.IDLE
    }

    /** Clears a failure once the user has seen it. */
    fun dismissFailure() {
        if (_status.value !in WORKING) _status.value = Status.IDLE
    }

    companion object {
        val WORKING = setOf(Status.AWAITING_REDIRECT, Status.EXCHANGING)
    }
}
