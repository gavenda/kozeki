package dev.gavenda.kozeki.data.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** What a check for a newer version found. */
sealed interface UpdateCheck {
    /** The installed version is the newest released. */
    data object UpToDate : UpdateCheck

    /** [version] is newer than the installed one. [url] downloads its APK, or opens its release page. */
    data class Available(val version: String, val url: String) : UpdateCheck

    sealed interface Failed : UpdateCheck
    data object Offline : Failed
    data object RateLimited : Failed
    data class Refused(val code: Int) : Failed
    data object Unreadable : Failed
}

/** Asks GitHub for the app's latest release and compares it with [installedVersion]. */
class UpdateChecker(
    private val client: OkHttpClient,
    private val installedVersion: String,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> read(response.body.string())
                    // GitHub answers 403 as well as 429 when too many requests came without a token.
                    response.code == 403 || response.code == 429 -> UpdateCheck.RateLimited
                    else -> UpdateCheck.Refused(response.code)
                }
            }
        } catch (_: IOException) {
            UpdateCheck.Offline
        }
    }

    private fun read(body: String): UpdateCheck {
        val release = try {
            json.decodeFromString<Release>(body)
        } catch (_: SerializationException) {
            return UpdateCheck.Unreadable
        } catch (_: IllegalArgumentException) {
            return UpdateCheck.Unreadable
        }
        val latest = release.tagName.removePrefix("v")
        if (!isNewer(latest, installedVersion)) return UpdateCheck.UpToDate
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl
        return UpdateCheck.Available(latest, apk ?: release.htmlUrl)
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/gavenda/kozeki/releases/latest"
    }
}

/**
 * Whether version [candidate] comes after [installed], comparing the dotted numbers in turn.
 * A missing part counts as 0, and anything after a number ("2.6.0-debug") is ignored.
 */
internal fun isNewer(candidate: String, installed: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun versionParts(version: String): List<Int> =
    version.removePrefix("v").split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
