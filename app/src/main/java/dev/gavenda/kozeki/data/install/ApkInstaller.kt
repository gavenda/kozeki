package dev.gavenda.kozeki.data.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes

/** How handing an APK to the system went. */
sealed interface ApkInstallResult {
    data object Installed : ApkInstallResult

    /** The user said no to the system's prompt, or left it unanswered. */
    data object Declined : ApkInstallResult

    /** The system would not install it. [message] is what it said, if anything. */
    data class Failed(val message: String?) : ApkInstallResult
}

/**
 * Installs APKs through the system's package installer: extensions, and the app's own updates.
 * Updates of a package this app installed go through without a prompt.
 */
class ApkInstaller(private val context: Context) {

    private val sessions = ConcurrentHashMap<Int, CompletableDeferred<Int>>()
    private val lastMessage = ConcurrentHashMap<Int, String>()

    /** The SHA-256 fingerprints of the keys [apk] is signed with, or null when it is no APK of [pkg]. */
    fun signers(apk: File, pkg: String): List<String>? {
        val info = context.packageManager.getPackageArchiveInfo(
            apk.path,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        if (info == null || info.packageName != pkg) return null
        return info.signingInfo?.apkContentsSigners.orEmpty().map(::fingerprint)
    }

    /** The fingerprints of the keys the installed [pkg] is signed with. */
    fun installedSigners(pkg: String): List<String> =
        context.packageManager
            .getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            .signingInfo?.apkContentsSigners.orEmpty().map(::fingerprint)

    /** Hands [apk] to the system as [pkg] and waits for it to be installed. */
    suspend fun install(apk: File, pkg: String): ApkInstallResult {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = withContext(Dispatchers.IO) { installer.createSession(params) }
        val result = CompletableDeferred<Int>()
        sessions[sessionId] = result
        try {
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("package.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(context, ApkInstallReceiver::class.java)
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
            val message = lastMessage.remove(sessionId)
            return when (status) {
                PackageInstaller.STATUS_SUCCESS -> ApkInstallResult.Installed
                PackageInstaller.STATUS_FAILURE_ABORTED -> ApkInstallResult.Declined
                null -> {
                    installer.abandonSession(sessionId)
                    ApkInstallResult.Declined
                }
                else -> ApkInstallResult.Failed(message)
            }
        } finally {
            sessions.remove(sessionId)
        }
    }

    /** The system's word on a session, through [ApkInstallReceiver]. */
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
                    sessions[sessionId]?.complete(PackageInstaller.STATUS_FAILURE)
                }
            }
            else -> {
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { lastMessage[sessionId] = it }
                sessions[sessionId]?.complete(status)
            }
        }
    }

    private fun fingerprint(signer: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "ApkInstaller"
        val PROMPT_TIMEOUT = 5.minutes
    }
}

/** Passes the package installer's results on to [ApkInstaller]. */
class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GlobalContext.get().get<ApkInstaller>().onStatus(intent)
    }
}
