package dev.gavenda.kozeki.data.comic.repo

import android.content.Context
import android.util.Log
import dev.gavenda.kozeki.data.comic.ComicSources
import dev.gavenda.kozeki.data.install.ApkInstallResult
import dev.gavenda.kozeki.data.install.ApkInstaller
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where the install of one extension has got to. Once it is installed it has no step any more. */
sealed interface InstallStep {
    data object Waiting : InstallStep
    data object Downloading : InstallStep
    data object Installing : InstallStep
    data class Failed(val error: InstallError) : InstallStep
}

sealed interface InstallError {
    data object Offline : InstallError
    data class Refused(val code: Int) : InstallError
    /** The APK is not signed with the key its repository names. */
    data object WrongSignature : InstallError
    /** The download is no APK of the package it was meant to be. */
    data object NotAnApk : InstallError
    /** The system would not install it. [message] is what it said, if anything. */
    data class System(val message: String?) : InstallError
}

/**
 * Installs extensions from a repository through the system's package installer, so they are
 * ordinary apps that [ComicSources] finds like any other. One install runs at a time, so the
 * system's prompts come one after another.
 */
class ExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
    private val apkInstaller: ApkInstaller,
    private val comicSources: ComicSources,
    private val scope: CoroutineScope,
) {
    private val _steps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    private val installing = Mutex()

    /** The extensions being installed or that failed to, by package name. */
    val steps: StateFlow<Map<String, InstallStep>> = _steps.asStateFlow()

    /** Downloads [extension], checks it is signed with [signingKey], and installs it. */
    fun install(extension: AvailableExtension, signingKey: String) {
        val pkg = extension.packageName
        val current = _steps.value[pkg]
        if (current != null && current !is InstallStep.Failed) return
        setStep(pkg, InstallStep.Waiting)
        scope.launch {
            installing.withLock {
                val error = try {
                    setStep(pkg, InstallStep.Downloading)
                    val apk = download(extension)
                    try {
                        verify(apk, pkg, signingKey)
                        setStep(pkg, InstallStep.Installing)
                        commit(apk, pkg)
                    } finally {
                        apk.delete()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: InstallException) {
                    e.error
                } catch (e: IOException) {
                    Log.w(TAG, "Could not download $pkg", e)
                    InstallError.Offline
                } catch (e: Exception) {
                    Log.e(TAG, "Could not install $pkg", e)
                    InstallError.System(e.message)
                }
                if (error != null) setStep(pkg, InstallStep.Failed(error)) else setStep(pkg, null)
            }
            // The package broadcast reloads them too, but only once they have been looked for.
            comicSources.refresh()
        }
    }

    /** Forgets a failed install. */
    fun dismiss(packageName: String) {
        if (_steps.value[packageName] is InstallStep.Failed) setStep(packageName, null)
    }

    private fun setStep(pkg: String, step: InstallStep?) =
        _steps.update { if (step == null) it - pkg else it + (pkg to step) }

    private suspend fun download(extension: AvailableExtension): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "extension_apks").apply { mkdirs() }
        val file = File(dir, "${extension.packageName}.apk")
        client.newCall(Request.Builder().url(extension.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw InstallException(InstallError.Refused(response.code))
            file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
        file
    }

    private fun verify(apk: File, pkg: String, signingKey: String) {
        val fingerprints = apkInstaller.signers(apk, pkg) ?: throw InstallException(InstallError.NotAnApk)
        if (signingKey !in fingerprints) throw InstallException(InstallError.WrongSignature)
    }

    /** Hands [apk] to the system and waits for it to be installed. Null when it was, or the user said no. */
    private suspend fun commit(apk: File, pkg: String): InstallError? =
        when (val result = apkInstaller.install(apk, pkg)) {
            ApkInstallResult.Installed, ApkInstallResult.Declined -> null
            is ApkInstallResult.Failed -> InstallError.System(result.message)
        }

    private class InstallException(val error: InstallError) : Exception()

    private companion object {
        const val TAG = "ExtensionInstaller"
    }
}
