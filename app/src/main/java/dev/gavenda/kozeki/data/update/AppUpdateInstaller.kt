package dev.gavenda.kozeki.data.update

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where installing a newer version has got to. */
sealed interface AppInstallStep {
    data object Downloading : AppInstallStep
    data object Installing : AppInstallStep
    data class Failed(val error: AppInstallError) : AppInstallStep
}

sealed interface AppInstallError {
    data object Offline : AppInstallError
    data class Refused(val code: Int) : AppInstallError
    /** The APK is not signed with the key the installed app is. */
    data object WrongSignature : AppInstallError
    /** The download is no APK of this app. */
    data object NotAnApk : AppInstallError
    /** The system would not install it. [message] is what it said, if anything. */
    data class System(val message: String?) : AppInstallError
}

/** Installs a newer version of the app over itself, the way extensions are installed. */
class AppUpdateInstaller(
    private val context: Context,
    private val client: OkHttpClient,
    private val apkInstaller: ApkInstaller,
    private val scope: CoroutineScope,
) {
    private val _step = MutableStateFlow<AppInstallStep?>(null)

    /** Installing the newer version, or null when nothing is being installed. */
    val step: StateFlow<AppInstallStep?> = _step.asStateFlow()

    /** Forgets a failed install. */
    fun dismiss() {
        if (_step.value is AppInstallStep.Failed) _step.value = null
    }

    /**
     * Downloads [update] and installs it over this app. It has to be signed with the installed
     * app's key. Once installed the system restarts the app.
     */
    fun install(update: UpdateCheck.Available) {
        val current = _step.value
        if (current != null && current !is AppInstallStep.Failed) return
        _step.value = AppInstallStep.Downloading
        scope.launch {
            val pkg = context.packageName
            val error = try {
                val apk = download(update.url)
                try {
                    val signers = apkInstaller.signers(apk, pkg) ?: throw InstallException(AppInstallError.NotAnApk)
                    if (signers.toSet() != apkInstaller.installedSigners(pkg).toSet()) {
                        throw InstallException(AppInstallError.WrongSignature)
                    }
                    _step.value = AppInstallStep.Installing
                    when (val result = apkInstaller.install(apk, pkg)) {
                        ApkInstallResult.Installed, ApkInstallResult.Declined -> null
                        is ApkInstallResult.Failed -> AppInstallError.System(result.message)
                    }
                } finally {
                    apk.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InstallException) {
                e.error
            } catch (e: IOException) {
                Log.w(TAG, "Could not download the update", e)
                AppInstallError.Offline
            } catch (e: Exception) {
                Log.e(TAG, "Could not install the update", e)
                AppInstallError.System(e.message)
            }
            _step.value = error?.let(AppInstallStep::Failed)
        }
    }

    private suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update_apk").apply { mkdirs() }
        val file = File(dir, "kozeki.apk")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw InstallException(AppInstallError.Refused(response.code))
            file.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
        file
    }

    private class InstallException(val error: AppInstallError) : Exception()

    private companion object {
        const val TAG = "AppUpdateInstaller"
    }
}
