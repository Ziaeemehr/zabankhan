package com.ziaee.zabankhan.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ziaee.zabankhan.lang.DictionaryLink
import com.ziaee.zabankhan.lang.LanguageSpec
import io.ktor.http.Url

internal fun dictionaryProviders(lang: LanguageSpec, meaningLanguage: String): List<DictionaryLink> =
    lang.dictionaryLinks.filter { it.supportsMeaningLanguage(meaningLanguage) }

internal fun selectedDictionaryProvider(providers: List<DictionaryLink>, last: String?): DictionaryLink? =
    if (last == "own") null else providers.firstOrNull { it.name == last } ?: providers.firstOrNull()

internal fun restoredDictionarySource(providers: List<DictionaryLink>, last: String?): String =
    when {
        last == null || last == "own" -> "own"
        else -> selectedDictionaryProvider(providers, last)?.name ?: "own"
    }

// Copied from French Reader's DictionarySheet.kt; shared by both embedded browsers.
internal val DICTIONARY_AD_BLOCK_HOSTS = setOf(
    "smartadserver.com", "themoneytizer.com",
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adnxs.com", "criteo.com", "criteo.net", "outbrain.com", "taboola.com",
    "pubmatic.com", "rubiconproject.com", "casalemedia.com", "adform.net",
    "amazon-adsystem.com", "adsafeprotected.com", "moatads.com", "scorecardresearch.com"
)

internal fun isDictionaryAdHost(host: String): Boolean {
    val normalized = host.lowercase().trimEnd('.')
    return DICTIONARY_AD_BLOCK_HOSTS.any { normalized == it || normalized.endsWith(".$it") }
}

internal fun isAllowedDictionaryUrl(url: String): Boolean = runCatching {
    val parsed = Url(url)
    parsed.protocol.name == "https" && parsed.host.isNotEmpty() && !isDictionaryAdHost(parsed.host)
}.getOrDefault(false)

// Larousse's known ad placeholders, copied from the reference WebView setup.
internal const val DICTIONARY_AD_CSS_SCRIPT = """
(function() {
    var style = document.createElement('style');
    style.innerHTML = '.pub-top, .pub-bottom, .pub-pave, .pub-gtm, .ads-core-placer { display: none !important; }';
    document.head.appendChild(style);
})();
"""

@Composable
expect fun DictionaryWebView(url: String, modifier: Modifier = Modifier, onFailed: () -> Unit)
