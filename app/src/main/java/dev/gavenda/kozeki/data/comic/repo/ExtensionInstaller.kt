package dev.gavenda.kozeki.data.comic.repo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import dev.gavenda.kozeki.data.comic.ComicSources
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes

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
    private val comicSources: ComicSources,
    private val scope: CoroutineScope,
) {
    private val _steps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    private val installing = Mutex()
    private val sessions = mutableMapOf<Int, CompletableDeferred<Int>>()

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
        val info = context.packageManager.getPackageArchiveInfo(
            apk.path,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        if (info == null || info.packageName != pkg) throw InstallException(InstallError.NotAnApk)
        val signers = info.signingInfo?.apkContentsSigners.orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
        val fingerprints = signers.map { signer -> digest.digest(signer.toByteArray()).joinToString("") { "%02x".format(it) } }
        if (signingKey !in fingerprints) throw InstallException(InstallError.WrongSignature)
    }

    /** Hands [apk] to the system and waits for it to be installed. Null when it was, or the user said no. */
    private suspend fun commit(apk: File, pkg: String): InstallError? {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Updates of an extension this app installed need no prompt.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = withContext(Dispatchers.IO) { installer.createSession(params) }
        val result = CompletableDeferred<Int>()
        synchronized(sessions) { sessions[sessionId] = result }
        try {
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("extension.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(context, ExtensionInstallReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        // The system fills in the status, so it has to be mutable.
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pending.intentSender)
                }
            }
            // A prompt left unanswered would hold up every install after it.
            val status = withTimeoutOrNull(PROMPT_TIMEOUT) { result.await() }
            return when (status) {
                PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    lastMessage.remove(sessionId)
                    null
                }
                null -> {
                    installer.abandonSession(sessionId)
                    null
                }
                else -> InstallError.System(lastMessage.remove(sessionId))
            }
        } finally {
            synchronized(sessions) { sessions.remove(sessionId) }
        }
    }

    private val lastMessage = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** The system's word on a session, through [ExtensionInstallReceiver]. */
    internal fun onStatus(intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.e(TAG, "Could not show the install prompt", e)
                    e.message?.let { lastMessage[sessionId] = it }
                    synchronized(sessions) { sessions[sessionId] }?.complete(PackageInstaller.STATUS_FAILURE)
                }
            }
            else -> {
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { lastMessage[sessionId] = it }
                synchronized(sessions) { sessions[sessionId] }?.complete(status)
            }
        }
    }

    private class InstallException(val error: InstallError) : Exception()

    private companion object {
        const val TAG = "ExtensionInstaller"
        val PROMPT_TIMEOUT = 5.minutes
    }
}

/** Passes the package installer's results on to [ExtensionInstaller]. */
class ExtensionInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GlobalContext.get().get<ExtensionInstaller>().onStatus(intent)
    }
}
