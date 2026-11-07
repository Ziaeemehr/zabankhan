@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.serialization.json.*
import platform.CoreGraphics.CGRectZero
import platform.Foundation.*
import platform.WebKit.*
import platform.darwin.NSObject

// A named class is required for Kotlin/Native device code generation.
private class DictionaryNavigationDelegate(val onFailed: () -> Unit) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction,
                         decisionHandler: (WKNavigationActionPolicy) -> Unit) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
        val allowed = isAllowedDictionaryUrl(url)
        if (!allowed && decidePolicyForNavigationAction.targetFrame?.mainFrame != false) onFailed()
        decisionHandler(if (allowed) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }

    override fun webView(webView: WKWebView, decidePolicyForNavigationResponse: WKNavigationResponse,
                         decisionHandler: (WKNavigationResponsePolicy) -> Unit) {
        val response = decidePolicyForNavigationResponse.response
        val allowed = isAllowedDictionaryUrl(response.URL?.absoluteString.orEmpty())
        if (decidePolicyForNavigationResponse.forMainFrame &&
            (!allowed || (response as? NSHTTPURLResponse)?.statusCode?.let { it >= 400 } == true)) onFailed()
        decisionHandler(if (allowed) WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow else WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURLErrorCancelled) onFailed()
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURLErrorCancelled) onFailed()
    }
}

// Navigation delegates do not see subresource requests. Content rules apply the
// same host list to ads in frames/scripts/images before the first page is loaded.
private fun dictionaryContentRules(): String = buildJsonArray {
    add(buildJsonObject {
        putJsonObject("trigger") { put("url-filter", "^http:") }
        putJsonObject("action") { put("type", "block") }
    })
    DICTIONARY_AD_BLOCK_HOSTS.forEach { host ->
        add(buildJsonObject {
            putJsonObject("trigger") {
                // Match the resource URL itself; if-domain matches the top document.
                val escapedHost = host.replace(".", "\\.")
                put("url-filter", "^https?://([a-z0-9-]+\\.)*$escapedHost\\.?(:[0-9]+)?/")
            }
            putJsonObject("action") { put("type", "block") }
        })
    }
}.toString()

@Composable
actual fun DictionaryWebView(url: String, modifier: Modifier, onFailed: () -> Unit) {
    val onFailedState = rememberUpdatedState(onFailed)
    val delegate = remember { DictionaryNavigationDelegate { onFailedState.value() } }
    var rules by remember { mutableStateOf<WKContentRuleList?>(null) }
    LaunchedEffect(Unit) {
        val store = WKContentRuleListStore.defaultStore()
        if (store == null) { onFailedState.value(); return@LaunchedEffect }
        store.compileContentRuleListForIdentifier(
            "dictionary-ad-hosts", encodedContentRuleList = dictionaryContentRules()
        ) { result, error ->
            if (error != null || result == null) onFailedState.value() else rules = result
        }
    }
    val contentRules = rules ?: return
    var loadedUrl by remember { mutableStateOf<String?>(null) }
    UIKitView(
        modifier = modifier,
        // The native scroll view handles drags immediately inside its viewport,
        // leaving the Compose sheet handle available for dragging the sheet.
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative),
        factory = {
            val config = WKWebViewConfiguration().apply {
                defaultWebpagePreferences.allowsContentJavaScript = true
                userContentController.addContentRuleList(contentRules)
                userContentController.addUserScript(WKUserScript(
                    source = DICTIONARY_AD_CSS_SCRIPT,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                    forMainFrameOnly = true
                ))
            }
            WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                navigationDelegate = delegate
                allowsBackForwardNavigationGestures = false
            }
        },
        update = { view ->
            if (loadedUrl != url) {
                loadedUrl = url
                val target = NSURL(string = url)
                if (isAllowedDictionaryUrl(url) && target != null) view.loadRequest(NSURLRequest(uRL = target))
                else onFailedState.value()
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.navigationDelegate = null
            view.configuration.userContentController.removeAllUserScripts()
            view.configuration.userContentController.removeAllContentRuleLists()
        }
    )
}
