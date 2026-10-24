package dev.gavenda.kozeki

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.data.files.IncomingEpubs
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.tracking.anilist.AniListAuth
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.data.settings.ThemeMode
import dev.gavenda.kozeki.data.update.AppUpdates
import dev.gavenda.kozeki.data.work.DownloadWorker
import dev.gavenda.kozeki.ui.KozekiApp
import dev.gavenda.kozeki.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val hardcoverAuth: HardcoverAuth by inject()
    private val hardcoverSignIn: HardcoverSignIn by inject()
    private val aniListAuth: AniListAuth by inject()
    private val incomingEpubs: IncomingEpubs by inject()
    private val settings: SettingsRepository by inject()
    private val appUpdates: AppUpdates by inject()

    /** Taps on the download notification, each asking for the download queue. */
    private val openDownloads = Channel<Unit>(Channel.CONFLATED)

    /** Repository URLs from a website's add-repository link, each to be confirmed in the add dialog. */
    private val addRepos = Channel<String>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch brings something new; a recreated activity would replay the old intent.
        if (savedInstanceState == null) {
            handleIntent(intent)
            appUpdates.checkOnLaunch()
        }
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(themeMode) { applyThemeMode(themeMode ?: return@LaunchedEffect) }
            // Light and dark switch in place rather than by recreating the activity, which would
            // flicker. Compose recolours itself; the window and the system bars are redone here.
            val isDark = isSystemInDarkTheme()
            LaunchedEffect(isDark) {
                window.setBackgroundDrawableResource(R.color.window_background)
                enableEdgeToEdge()
            }
            AppTheme {
                KozekiApp(incomingEpubs, openDownloads.receiveAsFlow(), addRepos.receiveAsFlow())
            }
        }
    }

    /**
     * Light or dark is handed to the system rather than forced inside Compose. It remembers the
     * choice and applies it to the whole app: the launch window, the system bars and the reader.
     */
    private fun applyThemeMode(mode: ThemeMode) {
        getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        handleRedirect(intent)
        receiveEpubs(intent)
        if (intent?.action == DownloadWorker.ACTION_OPEN_QUEUE) openDownloads.trySend(Unit)
        receiveRepoLink(intent)
    }

    /** A website's link to add a repository of extensions, as Mihon takes them: `mihon://extension-store?url=…`. */
    private fun receiveRepoLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // The recents screen can replay the link the app was first opened with.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val uri = intent.data ?: return
        if (uri.scheme != "mihon" || uri.host != "extension-store") return
        uri.getQueryParameter("url")?.takeIf { it.isNotBlank() }?.let { addRepos.trySend(it) }
    }

    /** Hardcover sends the browser back here once the user has approved or denied the sign-in. */
    private fun handleRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && hardcoverAuth.isRedirect(uri)) {
            hardcoverSignIn.complete(uri)
        } else if (intent.action == Intent.ACTION_VIEW && aniListAuth.isRedirect(uri)) {
            aniListAuth.complete(uri)
        }
    }

    /**
     * Another app opened an EPUB with Kozeki, or shared some to it. One that was opened is there
     * to be read, while sharing only adds to the library.
     */
    private fun receiveEpubs(intent: Intent?) {
        intent ?: return
        // Coming back through the recents screen can start the app with the intent it was first
        // started with, long after those files were taken in and the permission to read them ended.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val request = when (intent.action) {
            // Anything else the app is opened with is a link, such as the redirect above.
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
                ?.let { IncomingEpubs.Request(listOf(it), open = true) }
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { IncomingEpubs.Request(listOf(it), open = false) }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.takeIf { it.isNotEmpty() }
                ?.let { IncomingEpubs.Request(it, open = false) }
            else -> null
        }
        request?.let(incomingEpubs::add)
    }
}
