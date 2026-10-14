package dev.gavenda.kozeki.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import dev.gavenda.kozeki.R
import dev.gavenda.kozeki.ui.theme.AppTheme
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import org.koin.core.context.GlobalContext

/**
 * Opens a site's own page in a WebView so the user can solve the captcha it asks for. It shares
 * cookies and the user agent with the requests the sources make, so once the site lets the
 * WebView through, the next request is let through too.
 */
class CaptchaActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) {
            finish()
            return
        }
        val userAgent = GlobalContext.get().get<NetworkHelper>().defaultUserAgentProvider()
        setContent {
            AppTheme {
                val holder = remember { arrayOfNulls<WebView>(1) }
                var cleared by remember { mutableStateOf(false) }
                val done = {
                    setResult(RESULT_OK)
                    finish()
                }
                BackHandler {
                    val web = holder[0]
                    if (web != null && web.canGoBack()) web.goBack() else finish()
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.captcha_title)) },
                            actions = {
                                TextButton(onClick = done) {
                                    Text(stringResource(if (cleared) R.string.captcha_continue else R.string.captcha_done))
                                }
                            },
                        )
                    },
                ) { padding ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        factory = { context ->
                            WebView(context).apply {
                                setDefaultSettings()
                                settings.userAgentString = userAgent
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        // The clearance cookie is the site's word that the challenge was passed.
                                        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                                        if ("cf_clearance=" in cookies) cleared = true
                                    }
                                }
                                holder[0] = this
                                loadUrl(url)
                            }
                        },
                        onRelease = { it.destroy() },
                    )
                }
            }
        }
    }

    /** Asks for the site at the input to be opened, and answers whether the user said it was done. */
    class Contract : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String): Intent =
            Intent(context, CaptchaActivity::class.java).putExtra(EXTRA_URL, input)

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
    }

    private companion object {
        const val EXTRA_URL = "url"
    }
}
