package dev.gavenda.kozeki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverAuth
import dev.gavenda.kozeki.data.metadata.hardcover.HardcoverSignIn
import dev.gavenda.kozeki.data.settings.SettingsRepository
import dev.gavenda.kozeki.ui.KozekiApp
import dev.gavenda.kozeki.ui.theme.AppTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val hardcoverAuth: HardcoverAuth by inject()
    private val hardcoverSignIn: HardcoverSignIn by inject()
    private val settings: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch can be a redirect; a recreated activity would replay the old intent.
        if (savedInstanceState == null) handleRedirect(intent)
        setContent {
            // Nothing is drawn until the stored choice is read, so the other palette never flashes.
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = null)
            AppTheme(dynamicColor = dynamicColor ?: return@setContent) {
                KozekiApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    /** Hardcover sends the browser back here once the user has approved or denied the sign-in. */
    private fun handleRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && hardcoverAuth.isRedirect(uri)) {
            hardcoverSignIn.complete(uri)
        }
    }
}
