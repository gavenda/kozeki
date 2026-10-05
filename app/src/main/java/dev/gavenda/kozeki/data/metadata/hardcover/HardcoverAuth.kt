package dev.gavenda.kozeki.data.metadata.hardcover

import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import dev.gavenda.kozeki.data.metadata.MetadataException
import dev.gavenda.kozeki.data.metadata.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Whether somebody is signed in to Hardcover, as shown in Settings. */
sealed interface HardcoverAccount {
    data object SignedOut : HardcoverAccount

    /**
     * @param username null when the app was not granted the scope to read it.
     * @param email null for the same reason.
     * @param avatarUrl null for the same reason, or when the account has no picture.
     */
    data class SignedIn(
        val username: String?,
        val email: String? = null,
        val avatarUrl: String? = null,
    ) : HardcoverAccount
}

/**
 * Hardcover sign-in as an OAuth public client: authorization code with PKCE, no client secret.
 *
 * The flow is started with [beginSignIn], which returns the page to open in a browser. Hardcover
 * then redirects to [redirectUri], which the activity hands to [completeSignIn].
 */
class HardcoverAuth(
    private val client: OkHttpClient,
    private val store: TokenStore,
    private val clock: Clock,
    private val clientId: String,
    val redirectUri: String,
    private val scopes: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Refresh tokens are single-use: two refreshes racing would burn the session, so every read
    // and write of the stored session goes through this lock.
    private val lock = Mutex()
    private var cached: StoredSession? = null

    private val _account = MutableStateFlow<HardcoverAccount>(HardcoverAccount.SignedOut)
    val account: StateFlow<HardcoverAccount> = _account.asStateFlow()

    /** Reads the stored session so [account] reflects it. Call once at startup. */
    suspend fun restore() {
        val restored = lock.withLock { session().also(::publish) }
        // Sessions stored before the profile was kept, or signed in while offline, catch up here.
        if (restored.isSignedIn && (restored.email == null || restored.avatarUrl == null)) {
            runCatching { loadProfile() }
        }
    }

    suspend fun isSignedIn(): Boolean = lock.withLock { session().isSignedIn }

    /** Prepares a sign-in and returns the Hardcover page the user has to approve it on. */
    suspend fun beginSignIn(): Uri = lock.withLock {
        val state = randomUrlSafe(24)
        val verifier = randomUrlSafe(32)
        save(session().copy(pendingState = state, pendingVerifier = verifier))

        AUTHORIZE_ENDPOINT.toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challengeFor(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("scope", scopes)
            .build()
    }

    /** True when [uri] is Hardcover's redirect back into the app. */
    fun isRedirect(uri: Uri): Boolean {
        val expected = redirectUri.toUri()
        return uri.scheme == expected.scheme && uri.host == expected.host && uri.path.orEmpty() == expected.path.orEmpty()
    }

    /**
     * Finishes the sign-in started by [beginSignIn].
     *
     * @throws SignInException when the user declined or the response cannot be trusted.
     */
    suspend fun completeSignIn(redirect: Uri) {
        lock.withLock {
            val pending = session()
            val state = pending.pendingState
            val verifier = pending.pendingVerifier
            // Whatever happens next, this attempt is spent.
            save(pending.copy(pendingState = null, pendingVerifier = null))

            redirect.getQueryParameter("error")?.let { error ->
                throw SignInException(
                    if (error == "access_denied") SignInFailure.DENIED else SignInFailure.REJECTED,
                    redirect.getQueryParameter("error_description") ?: error,
                )
            }
            val code = redirect.getQueryParameter("code")
            // A mismatched state means this redirect does not answer the request this app made.
            if (code == null || state == null || verifier == null || redirect.getQueryParameter("state") != state) {
                throw SignInException(SignInFailure.REJECTED, "Unexpected sign-in response")
            }
            // Guards against a code issued by some other server being redeemed here (RFC 9207).
            if (redirect.getQueryParameter("iss") != ISSUER) {
                throw SignInException(SignInFailure.REJECTED, "Response came from the wrong issuer")
            }

            val tokens = requestTokens(
                FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", verifier)
                    .add("client_id", clientId)
                    .build(),
            ) ?: throw SignInException(SignInFailure.REJECTED, "Hardcover did not issue a token")

            save(tokens.toSession(StoredSession()))
        }
        loadProfile()
    }

    /**
     * A token to call the API with, refreshed first when it is about to expire.
     *
     * @return null when nobody is signed in.
     */
    suspend fun accessToken(): String? = lock.withLock {
        val current = session()
        val token = current.accessToken ?: return@withLock null
        if (clock.millis() < current.expiresAt - REFRESH_MARGIN_MS) token else refreshLocked()
    }

    /** Forces a refresh after the API rejected [rejectedToken]. Returns the new token, or null if signed out. */
    suspend fun refreshAfterRejection(rejectedToken: String): String? = lock.withLock {
        val current = session()
        // Another caller may have refreshed already while this one was waiting for the lock.
        if (current.accessToken != null && current.accessToken != rejectedToken) return@withLock current.accessToken
        refreshLocked()
    }

    suspend fun signOut() {
        val refreshToken = lock.withLock {
            val token = session().refreshToken
            store.clear()
            cached = StoredSession()
            publish(StoredSession())
            token
        } ?: return
        // Best effort: without it Hardcover keeps listing the session until the token expires.
        runCatching {
            val body = FormBody.Builder()
                .add("token", refreshToken)
                .add("token_type_hint", "refresh_token")
                .add("client_id", clientId)
                .build()
            client.newCall(Request.Builder().url(REVOKE_ENDPOINT).post(body).build()).await().close()
        }
    }

    private suspend fun refreshLocked(): String? {
        val current = session()
        val refreshToken = current.refreshToken ?: return null
        // A network failure propagates from here with the refresh token still unspent.
        val tokens = requestTokens(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .build(),
        )
        if (tokens == null) {
            // The refresh token was refused, so the session is over.
            store.clear()
            cached = StoredSession()
            publish(StoredSession())
            return null
        }
        save(tokens.toSession(current))
        return tokens.accessToken
    }

    /** @return null when the server answered but refused the grant. */
    private suspend fun requestTokens(body: FormBody): TokenResponse? {
        val request = Request.Builder().url(TOKEN_ENDPOINT).post(body).build()
        client.newCall(request).await().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                if (response.code >= 500) throw MetadataException.Server(response.code, "Token endpoint unavailable")
                return null
            }
            return runCatching { json.decodeFromString<TokenResponse>(text) }.getOrNull()
        }
    }

    /** Fetches the name and picture shown in Settings. Failing leaves the session as it was. */
    private suspend fun loadProfile() {
        val token = accessToken() ?: return
        // The email sits behind its own scope, and a query touching a field the token may not
        // read is refused whole. So ask for less each time rather than lose what is allowed.
        val me = PROFILE_QUERIES.firstNotNullOfOrNull { queryMe(token, it) } ?: return
        val username = me["username"].stringOrNull()
        val email = me["email"].stringOrNull()
        val avatarUrl = (me["image"] as? JsonObject)?.get("url").stringOrNull()
        lock.withLock {
            val current = session()
            // Signing out while the request was in flight must not bring the account back.
            if (current.isSignedIn) {
                save(
                    current.copy(
                        username = username ?: current.username,
                        email = email ?: current.email,
                        avatarUrl = avatarUrl ?: current.avatarUrl,
                    ),
                )
            }
        }
    }

    /** @return the signed-in user with the given [fields], or null when the query did not succeed. */
    private suspend fun queryMe(token: String, fields: String): JsonObject? = runCatching {
        val query = JsonObject(mapOf("query" to JsonPrimitive("query { me { $fields } }")))
        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .header("Authorization", "Bearer $token")
            .post(query.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).await().use { response ->
            val data = json.parseToJsonElement(response.body.string()).jsonObject["data"] as? JsonObject
            // Hardcover returns the single user as a one-element list.
            when (val me = data?.get("me")) {
                is JsonArray -> me.firstOrNull() as? JsonObject
                is JsonObject -> me
                else -> null
            }
        }
    }.getOrNull()

    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private suspend fun session(): StoredSession =
        cached ?: withContext(Dispatchers.IO) { store.read() }.also { cached = it }

    private suspend fun save(session: StoredSession) {
        withContext(Dispatchers.IO) { store.write(session) }
        cached = session
        publish(session)
    }

    private fun publish(session: StoredSession) {
        _account.value =
            if (session.isSignedIn) {
                HardcoverAccount.SignedIn(session.username, session.email, session.avatarUrl)
            } else {
                HardcoverAccount.SignedOut
            }
    }

    /** The session these tokens start, keeping the profile already known from [previous]. */
    private fun TokenResponse.toSession(previous: StoredSession) = StoredSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = clock.millis() + expiresIn * 1000,
        username = previous.username,
        email = previous.email,
        avatarUrl = previous.avatarUrl,
    )

    private fun randomUrlSafe(bytes: Int): String =
        ByteArray(bytes).also(SecureRandom()::nextBytes).toBase64Url()

    private fun challengeFor(verifier: String): String =
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)).toBase64Url()

    private fun ByteArray.toBase64Url(): String =
        Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long = 0,
    )

    enum class SignInFailure { DENIED, REJECTED }

    class SignInException(val failure: SignInFailure, message: String) : Exception(message)

    companion object {
        const val AUTHORIZE_ENDPOINT = "https://hardcover.app/oauth2/authorize"
        const val TOKEN_ENDPOINT = "https://api.hardcover.app/oauth2/token"
        const val REVOKE_ENDPOINT = "https://api.hardcover.app/oauth2/revoke"
        const val GRAPHQL_ENDPOINT = "https://api.hardcover.app/v1/graphql"
        const val ISSUER = "https://api.hardcover.app"

        internal val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Fields of `me` to ask for, most wanted first. */
        private val PROFILE_QUERIES = listOf("username email image { url }", "username image { url }", "email")

        /** Refresh this long before expiry, so a request never goes out with a token about to lapse. */
        private const val REFRESH_MARGIN_MS = 5 * 60_000L
    }
}
