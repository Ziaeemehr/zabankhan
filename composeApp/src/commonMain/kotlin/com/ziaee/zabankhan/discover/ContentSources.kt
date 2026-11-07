package com.ziaee.zabankhan.discover

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.percentEncode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

sealed interface ContentSource { val id: String; val name: String }
data class NewsSource(override val id: String, override val name: String, val feedUrl: String, val categories: List<String> = listOf("all")) : ContentSource
data class WikiSource(override val id: String, override val name: String, val host: String) : ContentSource {
    val api: String get() = "https://$host/w/api.php"
    fun articleUrl(title: String): String = "https://$host/wiki/" + percentEncode(title.replace(' ', '_'))
}

/** All language-specific source endpoints live here. */
object ContentSources {
    val byLanguage: Map<String, List<ContentSource>> = mapOf(
        "fr" to listOf(
            NewsSource("rfi_facile", "RFI Français facile", "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627", listOf("all", "culture", "society")),
            NewsSource("france_info", "franceinfo", "https://www.francetvinfo.fr/titres.rss", listOf("all", "world", "europe", "politics", "culture", "science", "society", "economy", "sports")),
            WikiSource("vikidia", "Vikidia", "fr.vikidia.org"),
            WikiSource("wikisource", "Wikisource", "fr.wikisource.org"),
        ),
        "en" to listOf(
            NewsSource("voa", "VOA Learning English", "https://learningenglish.voanews.com/api/zkm-ql-vomx-tpej-rqi", listOf("all", "world", "culture", "science", "society", "economy")),
            NewsSource("bbc", "BBC News — World", "https://feeds.bbci.co.uk/news/world/rss.xml", listOf("all", "world", "europe", "politics", "culture", "science", "society", "economy", "sports")),
            WikiSource("simple_wikipedia", "Simple English Wikipedia", "simple.wikipedia.org"),
            WikiSource("wikisource", "Wikisource", "en.wikisource.org"),
        ),
    )
    fun forLanguage(spec: LanguageSpec): List<ContentSource> = byLanguage[spec.code].orEmpty()
}

internal suspend fun HttpClient.contentGet(url: String): String {
    require(url.startsWith("https://")) { "Content requires HTTPS" }
    val response = get(url) { header("User-Agent", "Zabankhan/1.0 (https://github.com/Ziaeemehr/zabankhan)") }
    check(response.status.value in 200..299) { "Content request failed (${response.status.value})" }
    return response.bodyAsText()
}
