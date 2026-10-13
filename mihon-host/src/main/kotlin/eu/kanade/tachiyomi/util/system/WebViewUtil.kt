package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

private const val TAG = "WebViewUtil"

object WebViewUtil {
    const val MINIMUM_WEBVIEW_VERSION = 118

    fun supportsWebView(context: Context): Boolean {
        try {
            // May throw android.webkit.WebViewFactory$MissingWebViewPackageException if WebView
            // is not installed
            CookieManager.getInstance()
        } catch (e: Throwable) {
            Log.e(TAG, "WebView is not available", e)
            return false
        }

        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }
}

/** Devices whose WebView is known to crash while it is being initialised. */
internal object DeviceUtil {
    val isMiui: Boolean by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, "ro.miui.ui.version.name") as? String
        }.getOrNull()?.isNotEmpty() ?: false
    }

    val isSamsung: Boolean by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
}

fun WebView.isOutdated(): Boolean {
    return getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        // Handle popups properly
        setSupportMultipleWindows(true)

        // Allow zooming
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

/**
 * Sets the user agent along with the matching user agent metadata, which Chromium uses to populate
 * the `Sec-CH-UA` client hints. Without this the hints keep advertising the real WebView brand and
 * version, contradicting the spoofed user agent.
 */
fun WebView.setUserAgent(userAgent: String) {
    settings.userAgentString = userAgent

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return

    val versionMatch = CHROME_VERSION_REGEX.find(userAgent) ?: return
    val majorVersion = versionMatch.groupValues[1]
    val fullVersion = majorVersion + versionMatch.groupValues[2].ifEmpty { ".0.0.0" }

    try {
        val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
        val brandVersionList = metadata.brandVersionList.map { brandVersion ->
            val brand = when (brandVersion.brand) {
                WEBVIEW_BRAND -> CHROME_BRAND
                CHROMIUM_BRAND -> CHROMIUM_BRAND
                else -> return@map brandVersion
            }

            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(brand)
                .setMajorVersion(majorVersion)
                .setFullVersion(fullVersion)
                .build()
        }

        WebSettingsCompat.setUserAgentMetadata(
            settings,
            UserAgentMetadata.Builder(metadata)
                .setBrandVersionList(brandVersionList)
                .setFullVersion(fullVersion)
                .build(),
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to set user agent metadata", e)
    }
}

private const val WEBVIEW_BRAND = "Android WebView"
private const val CHROMIUM_BRAND = "Chromium"
private const val CHROME_BRAND = "Google Chrome"
private val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()

private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
}

// Based on https://stackoverflow.com/a/29218966
private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString

    // Next call to getUserAgentString() will get us the default
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString

    // Revert to original UA string
    settings.userAgentString = originalUA

    return defaultUserAgentString
}
