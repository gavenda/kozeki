package dev.gavenda.kozeki.data.account

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Who is signed in to Kozeki's account, from the ID token Auth0 issued at sign-in. */
@Serializable
data class Account(
    /** The Auth0 subject, which the server keeps the library under. */
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val picture: String? = null,
)

sealed interface AccountState {
    /** The stored session is still being read. */
    data object Loading : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val account: Account) : AccountState
}

/** Why a sign-in did not happen. */
enum class SignInFailure { CANCELLED, OFFLINE, REJECTED }

/**
 * Kozeki's account on Auth0, which the library syncs through.
 *
 * The session survives a restart without the network: a stored refresh token is enough to count
 * as signed in, and who it belongs to is kept from the ID token, so the library opens offline. A
 * fresh access token is only needed when the sync talks to the server, and is asked for then.
 */
class AccountAuth(
    private val context: Context,
    clientId: String,
    domain: String,
    private val audience: String,
    private val scheme: String,
) {
    private val auth0 = Auth0.getInstance(clientId, domain)
    private val credentials by lazy {
        SecureCredentialsManager(AuthenticationAPIClient(auth0), context, SharedPreferencesStorage(context))
    }
    private val profile = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<AccountState>(AccountState.Loading)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Reads the stored session. Needs no network. */
    fun restore() {
        val account = profile.getString(KEY_ACCOUNT, null)?.let { runCatching { json.decodeFromString<Account>(it) }.getOrNull() }
        _state.value = if (account != null && credentials.hasValidCredentials()) {
            AccountState.SignedIn(account)
        } else {
            AccountState.SignedOut
        }
    }

    /** Opens the Auth0 sign-in in a browser tab. */
    suspend fun signIn(activity: Activity): SignInFailure? = suspendCancellableCoroutine { continuation ->
        WebAuthProvider.login(auth0)
            .withScheme(scheme)
            // offline_access asks for the refresh token that keeps the session across restarts.
            .withScope("openid profile email offline_access")
            .withAudience(audience)
            .start(
                activity,
                object : Callback<Credentials, AuthenticationException> {
                    override fun onSuccess(result: Credentials) {
                        val account = accountOf(result)
                        if (account == null) {
                            continuation.resume(SignInFailure.REJECTED)
                            return
                        }
                        credentials.saveCredentials(result)
                        profile.edit().putString(KEY_ACCOUNT, json.encodeToString(account)).apply()
                        _state.value = AccountState.SignedIn(account)
                        continuation.resume(null)
                    }

                    override fun onFailure(error: AuthenticationException) {
                        continuation.resume(
                            when {
                                error.isCanceled -> SignInFailure.CANCELLED
                                error.isNetworkError -> SignInFailure.OFFLINE
                                else -> SignInFailure.REJECTED
                            },
                        )
                    }
                },
            )
    }

    /** Ends the session here and on Auth0. Signing out locally does not wait on the browser. */
    fun signOut(activity: Activity) {
        clear()
        WebAuthProvider.logout(auth0).withScheme(scheme).start(
            activity,
            object : Callback<Void?, AuthenticationException> {
                override fun onSuccess(result: Void?) = Unit
                override fun onFailure(error: AuthenticationException) = Unit
            },
        )
    }

    /** Forgets the session without asking Auth0, for when the server refused it for good. */
    fun clear() {
        credentials.clearCredentials()
        profile.edit().remove(KEY_ACCOUNT).apply()
        _state.value = AccountState.SignedOut
    }

    /** An access token for the API, refreshed first when it is about to expire, or null when there is none to be had. */
    suspend fun accessToken(): String? {
        if (!credentials.hasValidCredentials()) return null
        return suspendCancellableCoroutine { continuation ->
            credentials.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
                override fun onSuccess(result: Credentials) = continuation.resume(result.accessToken)
                override fun onFailure(error: CredentialsManagerException) = continuation.resume(null)
            })
        }
    }

    private fun accountOf(result: Credentials): Account? {
        val payload = result.idToken.split(".").getOrNull(1) ?: return null
        return runCatching {
            val claims = json.decodeFromString<IdTokenClaims>(
                String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8),
            )
            Account(claims.sub, claims.name, claims.email, claims.picture)
        }.getOrNull()
    }

    @Serializable
    private data class IdTokenClaims(val sub: String, val name: String? = null, val email: String? = null, val picture: String? = null)

    private companion object {
        const val KEY_ACCOUNT = "account"
    }
}
