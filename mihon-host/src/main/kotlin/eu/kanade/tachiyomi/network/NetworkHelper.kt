package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.CoroutineScope
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What extensions make their requests with. The client is the one extensions expect: a cookie jar
 * shared with WebView, a browser's user agent, and Cloudflare challenges solved in a WebView.
 */
class NetworkHelper(
    private val scope: CoroutineScope,
    private val context: Context,
    private val userAgent: String = DEFAULT_USER_AGENT,
) {

    val cookieJar = AndroidCookieJar()

    private fun clientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30.seconds)
        .readTimeout(30.seconds)
        .callTimeout(2.minutes)
        .cache(
            Cache(
                directory = File(context.cacheDir, "network_cache"),
                maxSize = 5L * 1024 * 1024, // 5 MiB
            ),
        )
        // Extensions make their requests with the library's default `Cache-Control: max-age=600`, which some
        // Cloudflare zones answer with a challenge a browser would never be shown. The cache has read it by the
        // time a network interceptor runs, so it only has to stay off the wire.
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val maxAge = request.header("Cache-Control")?.startsWith("max-age=") == true
            chain.proceed(if (maxAge) request.newBuilder().removeHeader("Cache-Control").build() else request)
        }
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))

    val client: OkHttpClient by lazy {
        clientBuilder()
            .addInterceptor(
                CloudflareInterceptor(context, cookieJar, scope, ::defaultUserAgentProvider),
            )
            .build()
    }

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient get() = client

    fun defaultUserAgentProvider() = userAgent.trim()

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/149.0.0.0 Mobile Safari/537.36"
    }
}
