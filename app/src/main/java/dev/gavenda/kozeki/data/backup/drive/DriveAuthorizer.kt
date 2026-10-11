package dev.gavenda.kozeki.data.backup.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/** What asking for access to the user's Drive came to. */
sealed interface DriveGrant {

    /** Access is given. The token is good for about an hour, and is not kept. */
    data class Token(val value: String) : DriveGrant

    /** The user has to allow it first, on the screen [intent] opens. */
    data class NeedsConsent(val intent: PendingIntent) : DriveGrant
}

/** Google would not give access. [code] is the status Google Play services gave for it. */
class DriveAuthException(val code: Int, cause: Throwable? = null) : Exception("Authorization failed: $code", cause) {
    val offline: Boolean get() = code == CommonStatusCodes.NETWORK_ERROR
    val cancelled: Boolean get() = code == CommonStatusCodes.CANCELED
}

/** Gets the tokens Drive is reached with. */
interface DriveAuthorizer {

    /**
     * @param email The account to ask as, once it is known.
     * @param chooseAccount Has the user pick the account, which is what signing in is.
     * @throws DriveAuthException
     */
    suspend fun authorize(email: String?, chooseAccount: Boolean = false): DriveGrant

    /**
     * The token the user gave on the screen of [DriveGrant.NeedsConsent], out of what it came back with.
     *
     * @throws DriveAuthException
     */
    fun granted(result: Intent?): String

    /** Has the next token asked for be a new one, after Drive refused [token]. */
    suspend fun forget(token: String)
}

/**
 * Asks through Google Play services, which holds the user's Google accounts. The app is known to
 * Google by its package name and signing certificate, as registered in the Google Cloud console,
 * so there is no client ID or secret here, and Play services keeps the grant: the app stores no
 * token of its own for Drive.
 */
class PlayServicesDriveAuthorizer(context: Context) : DriveAuthorizer {

    private val client = Identity.getAuthorizationClient(context.applicationContext)

    override suspend fun authorize(email: String?, chooseAccount: Boolean): DriveGrant {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveApi.SCOPE)))
            .apply {
                if (email != null) setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
                if (chooseAccount) setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            }
            .build()
        val result = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw DriveAuthException(e.statusCode, e)
        }
        result.pendingIntent?.takeIf { result.hasResolution() }?.let { return DriveGrant.NeedsConsent(it) }
        return DriveGrant.Token(result.accessToken ?: throw DriveAuthException(CommonStatusCodes.INTERNAL_ERROR))
    }

    override fun granted(result: Intent?): String = try {
        client.getAuthorizationResultFromIntent(result).accessToken
            ?: throw DriveAuthException(CommonStatusCodes.INTERNAL_ERROR)
    } catch (e: ApiException) {
        throw DriveAuthException(e.statusCode, e)
    }

    override suspend fun forget(token: String) {
        runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
