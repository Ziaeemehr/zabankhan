package com.ziaee.zabankhan.ui.reader

import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// WebView setup copied from French Reader's DictionarySheet.kt.
@Composable
actual fun DictionaryWebView(url: String, modifier: Modifier, onFailed: () -> Unit) {
    val onFailedState = rememberUpdatedState(onFailed)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // JS on for the site to render normally, but no
                // file access and no JS bridge back into app
                // code -- the design doc explicitly rules that out.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (!isAllowedDictionaryUrl(request.url.toString())) {
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val blocked = !isAllowedDictionaryUrl(request.url.toString())
                        if (blocked && request.isForMainFrame) onFailedState.value()
                        return blocked
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        // Some dictionary sites (confirmed: Larousse) show
                        // intrusive ads in known placeholder slots -- hide them
                        // once the page finishes loading. Class names verified
                        // against Larousse's real markup; harmless no-op on
                        // sites that don't use them (WordReference/Linguee/
                        // Wiktionary).
                        view.evaluateJavascript(
                            """
                            (function() {
                                var style = document.createElement('style');
                                style.innerHTML = '.pub-top, .pub-bottom, .pub-pave, .pub-gtm, .ads-core-placer { display: none !important; }';
                                document.head.appendChild(style);
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        if (request.isForMainFrame) onFailedState.value()
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame) onFailedState.value()
                    }
                }
                // WebView sits inside ModalBottomSheet's own drag-to-dismiss
                // gesture handling; without this, the sheet's outer touch
                // handling intercepts vertical drags before the WebView's own
                // page scrolling ever receives them, so the dictionary page
                // can only ever show its very top and never scroll.
                setOnTouchListener { view, event ->
                    if (event.action == android.view.MotionEvent.ACTION_MOVE) {
                        view.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
                if (isAllowedDictionaryUrl(url)) loadUrl(url) else onFailedState.value()
            }
        },
        update = { webView ->
            // AndroidView's factory only runs once; without this,
            // switching dictionary-source chips would never
            // reload the WebView. Comparing against the WebView's
            // own current url avoids reloading on every unrelated
            // recomposition (e.g. typing in the meaning field).
            if (webView.url != url) {
                if (isAllowedDictionaryUrl(url)) webView.loadUrl(url) else onFailedState.value()
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    )
}
