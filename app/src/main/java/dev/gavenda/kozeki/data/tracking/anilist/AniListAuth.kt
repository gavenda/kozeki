package dev.gavenda.kozeki.data.tracking.anilist

import android.net.Uri
import androidx.core.net.toUri
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.hardcover.StoredSession
import dev.gavenda.kozeki.data.metadata.hardcover.TokenStore
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether somebody is signed in to AniList, as shown in Settings. */
sealed interface AniListAccount {
    data object SignedOut : AniListAccount

    data class SignedIn(
        val username: String,
        val avatarUrl: String?,
        val scoreFormat: ScoreFormat,
    ) : AniListAccount
}

/**
 * AniList sign-in. AniList has no PKCE and no client secret for an app like this one, so it uses
 * the implicit grant: the browser comes back to [redirectUri] with the token in the fragment. The
 * token lasts a year and cannot be refreshed; once it lapses the user signs in again.
 */
class AniListAuth(
    private val api: AniListApi,
    private val store: TokenStore,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val clientId: String,
    val redirectUri: String,
) {
    enum class Status {
        IDLE,

        /** The browser is open and AniList has not redirected back yet. */
        AWAITING_REDIRECT,

        /** The redirect arrived and the token is being checked. */
        EXCHANGING,

        /** The user pressed Deny, or no token came back. */
        DENIED,
        REJECTED,
        OFFLINE,
    }

    private val lock = Mutex()
    private val _account = MutableStateFlow<AniListAccount>(AniListAccount.SignedOut)
    val account: StateFlow<AniListAccount> = _account.asStateFlow()

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Shows who is signed in, from what the last launch kept. */
    suspend fun restore() {
        lock.withLock { publish(store.read()) }
    }

    /** The token to make a call with, or null when nobody is signed in or the token has lapsed. */
    suspend fun accessToken(): String? = lock.withLock {
        val session = store.read()
        val token = session.accessToken ?: return@withLock null
        if (clock.millis() >= session.expiresAt) {
            store.clear()
            publish(StoredSession())
            return@withLock null
        }
        token
    }

    /** The page to open in a browser for the user to approve the sign-in. */
    fun beginSignIn(): Uri {
        _status.value = Status.AWAITING_REDIRECT
        return AUTHORIZE.toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "token")
            .build()
    }

    /** True when [uri] is AniList's redirect back into the app. */
    fun isRedirect(uri: Uri): Boolean {
        val expected = redirectUri.toUri()
        return uri.scheme == expected.scheme && uri.host == expected.host && uri.path == expected.path
    }

    /** Handles the redirect: the token is in its fragment, or an error is. */
    fun complete(redirect: Uri) {
        _status.value = Status.EXCHANGING
        scope.launch {
            _status.value = try {
                val fragment = redirect.fragment.orEmpty()
                val params = fragment.split('&').mapNotNull {
                    val (key, value) = it.split('=', limit = 2).takeIf { pair -> pair.size == 2 } ?: return@mapNotNull null
                    key to Uri.decode(value)
                }.toMap()
                val token = params["access_token"]
                if (token == null) {
                    if (params["error"] == "access_denied") Status.DENIED else Status.REJECTED
                } else {
                    val lifetime = params["expires_in"]?.toLongOrNull() ?: DEFAULT_LIFETIME_SECONDS
                    val viewer = api.viewer(token)
                    lock.withLock {
                        val session = StoredSession(
                            accessToken = token,
                            expiresAt = clock.millis() + lifetime * 1000,
                            username = viewer.name,
                            avatarUrl = viewer.avatarUrl,
                            scoreFormat = viewer.scoreFormat.name,
                        )
                        store.write(session)
                        publish(session)
                    }
                    Status.IDLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: MetadataException.Network) {
                Status.OFFLINE
            } catch (_: Exception) {
                Status.REJECTED
            }
        }
    }

    /**
     * The app is in front again. If no redirect came with it the user closed the browser without
     * finishing; a redirect is always delivered first, which has already moved the status on.
     */
    fun browserClosed() {
        if (_status.value == Status.AWAITING_REDIRECT) _status.value = Status.IDLE
    }

    /** Clears a failure once the user has seen it. */
    fun dismissFailure() {
        if (_status.value !in WORKING) _status.value = Status.IDLE
    }

    /** Forgets the token, which AniList is not told of: there is no call that revokes it. */
    suspend fun signOut() {
        lock.withLock {
            store.clear()
            publish(StoredSession())
        }
    }

    /** The token was refused, so it is no good however long it has left. */
    suspend fun invalidate() = signOut()

    private fun publish(session: StoredSession) {
        _account.value = if (session.accessToken != null && session.username != null) {
            AniListAccount.SignedIn(
                username = session.username,
                avatarUrl = session.avatarUrl,
                scoreFormat = ScoreFormat.parse(session.scoreFormat),
            )
        } else {
            AniListAccount.SignedOut
        }
    }

    companion object {
        val WORKING = setOf(Status.AWAITING_REDIRECT, Status.EXCHANGING)
        private const val AUTHORIZE = "https://anilist.co/api/v2/oauth/authorize"
        private const val DEFAULT_LIFETIME_SECONDS = 365L * 24 * 60 * 60
    }
}
