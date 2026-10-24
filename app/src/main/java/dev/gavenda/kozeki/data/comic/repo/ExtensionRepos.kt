package dev.gavenda.kozeki.data.comic.repo

import android.util.Log
import dev.gavenda.kozeki.data.settings.SettingsRepository
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Why a repository's index could not be read. */
sealed interface RepoError {
    data object Offline : RepoError
    data class Refused(val code: Int) : RepoError
    /** Not a protobuf index of Mihon extensions. */
    data object Unreadable : RepoError
}

/** A repository the user added, and what is known of it so far. */
sealed interface RepoState {
    val url: String

    data class Loading(override val url: String) : RepoState
    data class Loaded(override val url: String, val store: ExtensionStore) : RepoState
    data class Failed(override val url: String, val error: RepoError) : RepoState
}

/** What came of adding a repository. */
sealed interface AddRepoResult {
    data object Added : AddRepoResult
    data object AlreadyAdded : AddRepoResult
    data object InvalidUrl : AddRepoResult
    data class Failed(val error: RepoError) : AddRepoResult
}

/**
 * The repositories of Mihon extensions the user added, each read from its protobuf index (`index.pb`,
 * gzipped or not). Only the URLs are kept; the indexes are read again each time the app runs.
 */
@OptIn(ExperimentalSerializationApi::class)
class ExtensionRepos(
    private val client: OkHttpClient,
    private val protoBuf: ProtoBuf,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val fetched = MutableStateFlow<Map<String, RepoState>>(emptyMap())

    /** Every repository added, in the order they were added. */
    val repos: StateFlow<List<RepoState>> =
        combine(settings.extensionRepos, fetched) { urls, states ->
            urls.map { states[it] ?: RepoState.Loading(it) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Reads every repository not read yet, or every one with [force]. */
    fun refresh(force: Boolean = false) {
        scope.launch {
            val urls = settings.extensionRepos.first()
            urls.filter { force || fetched.value[it] !is RepoState.Loaded }
                .map { url -> async { load(url) } }
                .awaitAll()
        }
    }

    /** Reads the index at [input] and keeps it once it reads as one. */
    suspend fun add(input: String): AddRepoResult {
        val url = input.trim().toHttpUrlOrNull()?.toString() ?: return AddRepoResult.InvalidUrl
        if (url in settings.extensionRepos.first()) return AddRepoResult.AlreadyAdded
        val state = load(url)
        if (state is RepoState.Failed) {
            fetched.update { it - url }
            return AddRepoResult.Failed(state.error)
        }
        settings.addExtensionRepo(url)
        return AddRepoResult.Added
    }

    fun remove(url: String) {
        scope.launch {
            settings.removeExtensionRepo(url)
            fetched.update { it - url }
        }
    }

    private suspend fun load(url: String): RepoState {
        fetched.update { it + (url to RepoState.Loading(url)) }
        val state = try {
            RepoState.Loaded(url, fetch(url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RepoException) {
            RepoState.Failed(url, e.error)
        } catch (e: IOException) {
            Log.w(TAG, "Could not reach $url", e)
            RepoState.Failed(url, RepoError.Offline)
        } catch (e: SerializationException) {
            Log.w(TAG, "Could not read $url", e)
            RepoState.Failed(url, RepoError.Unreadable)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not read $url", e)
            RepoState.Failed(url, RepoError.Unreadable)
        }
        fetched.update { it + (url to state) }
        return state
    }

    private suspend fun fetch(url: String): ExtensionStore {
        val index = protoBuf.decodeFromByteArray<NetworkExtensionStore>(download(url))
        val list = index.extensionList
            ?: index.extensionListUrl?.let { protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(download(it)) }
            ?: throw RepoException(RepoError.Unreadable)
        return ExtensionStore(
            indexUrl = url,
            name = index.name,
            signingKey = index.signingKey.normalizedFingerprint(),
            website = index.contact?.website?.ifBlank { null },
            extensions = list.toAvailable(),
        )
    }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw RepoException(RepoError.Refused(response.code))
            val bytes = response.body.bytes()
            // The index may be served gzipped without saying so.
            val gzipped = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            if (gzipped) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        }
    }

    private class RepoException(val error: RepoError) : Exception()

    private companion object {
        const val TAG = "ExtensionRepos"
    }
}
