package dev.gavenda.kozeki.ui.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the app alive when the WebView renderer process dies.
 *
 * Readium's Compose navigators draw pages in WebViews they create themselves, and their client
 * does not answer `onRenderProcessGone`. Android treats an unanswered renderer death as fatal and
 * kills the whole app, whether the renderer crashed or was reclaimed for memory. Since the WebViews
 * cannot be configured from outside, this finds them in the view tree as they appear and wraps
 * their client with one that does answer, then asks the reader to rebuild the page.
 */
@Composable
fun RendererGuard(onRendererGone: () -> Unit) {
    val view = LocalView.current
    val currentOnRendererGone by rememberUpdatedState(onRendererGone)
    val guard = remember { RendererGuardInstaller { currentOnRendererGone() } }

    DisposableEffect(view, guard) {
        // Pages are created lazily as they scroll into range, and each one triggers a layout pass.
        val listener = ViewTreeObserver.OnGlobalLayoutListener { guard.protect(view.rootView) }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        guard.protect(view.rootView)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
}

private class RendererGuardInstaller(private val onRendererGone: () -> Unit) {

    // Every WebView shares the one renderer, so they all report its death at once. The reader
    // only needs telling once per generation of WebViews.
    private var reported = false

    fun protect(view: View) {
        when (view) {
            is WebView -> {
                val client = view.webViewClient
                if (client !is GuardedClient) {
                    view.webViewClient = GuardedClient(client, ::report)
                    // A WebView that was not seen before belongs to a rebuilt page with a new renderer.
                    reported = false
                }
            }
            is ViewGroup -> for (index in 0 until view.childCount) protect(view.getChildAt(index))
        }
    }

    private fun report() {
        if (reported) return
        reported = true
        onRendererGone()
    }
}

/** Behaves exactly like [delegate], except that it survives the renderer going away. */
// Lint mistakes the superclass constructor call for a bare WebViewClient; the callback is overridden below.
@SuppressLint("MissingOnRenderProcessGone")
private class GuardedClient(
    private val delegate: WebViewClient,
    private val onRendererGone: () -> Unit,
) : WebViewClient() {

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // A WebView whose renderer is gone must not be used again. The reader drops it from the
        // composition; once it is off screen it can be destroyed.
        view?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                (v as? WebView)?.destroy()
            }
        })
        // Posted rather than called, so the reader is rebuilt after this callback has returned.
        if (view != null) view.post(onRendererGone) else onRendererGone()
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        delegate.shouldInterceptRequest(view, request)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        delegate.shouldOverrideUrlLoading(view, request)

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) =
        delegate.onPageStarted(view, url, favicon)

    override fun onPageFinished(view: WebView, url: String?) = delegate.onPageFinished(view, url)

    override fun onPageCommitVisible(view: WebView, url: String?) = delegate.onPageCommitVisible(view, url)

    override fun onLoadResource(view: WebView, url: String?) = delegate.onLoadResource(view, url)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) =
        delegate.onReceivedError(view, request, error)

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) =
        delegate.onReceivedHttpError(view, request, errorResponse)

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) =
        delegate.onReceivedSslError(view, handler, error)

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) =
        delegate.onReceivedClientCertRequest(view, request)

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String?, realm: String?) =
        delegate.onReceivedHttpAuthRequest(view, handler, host, realm)

    override fun onReceivedLoginRequest(view: WebView, realm: String?, account: String?, args: String?) =
        delegate.onReceivedLoginRequest(view, realm, account, args)

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) =
        delegate.onFormResubmission(view, dontResend, resend)

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
        delegate.doUpdateVisitedHistory(view, url, isReload)

    override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean =
        delegate.shouldOverrideKeyEvent(view, event)

    override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) = delegate.onUnhandledKeyEvent(view, event)

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) =
        delegate.onScaleChanged(view, oldScale, newScale)

    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) = delegate.onSafeBrowsingHit(view, request, threatType, callback)
}
