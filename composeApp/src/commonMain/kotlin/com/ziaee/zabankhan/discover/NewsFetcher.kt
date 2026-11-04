package com.ziaee.zabankhan.discover

import com.ziaee.zabankhan.content.decodeEntities
import com.ziaee.zabankhan.content.htmlToParagraphs
import com.ziaee.zabankhan.content.markupTags
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

private val xmlOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
data class NewsItem(val title: String, val snippet: String, val link: String, val guid: String, val publishedAtMs: Long?, val imageUrl: String? = null)

class NewsFetcher(private val http: HttpClient) {
    suspend fun fetchItems(feedUrl: String, limit: Int = 25): List<NewsItem> = parseItems(http.contentGet(feedUrl), limit)
    companion object {
        fun parseItems(xml: String, limit: Int = 25): List<NewsItem> {
            if (limit <= 0) return emptyList()
            return Regex("<(?:[\\w.-]+:)?(item|entry)\\b[^>]*>(.*?)</(?:[\\w.-]+:)?\\1\\s*>", xmlOptions)
                .findAll(xml).mapNotNull { match ->
                    val item = match.groupValues[2]
                    val title = field(item, "title")?.let(::plain).orEmpty()
                    val snippet = (field(item, "content:encoded") ?: field(item, "content") ?: field(item, "description") ?: field(item, "summary"))?.let(::plain).orEmpty()
                    val link = markupTags(item).firstOrNull { it.name == "link" && !it.closing && it.attributes["href"] != null && it.attributes["rel"].let { rel -> rel == null || rel == "alternate" } }?.attributes?.get("href")
                        ?: field(item, "link")?.let(::plain).orEmpty()
                    val url = secureUrl(link) ?: return@mapNotNull null
                    if (title.isBlank() || snippet.isBlank()) return@mapNotNull null
                    val image = markupTags(item).firstNotNullOfOrNull { tag ->
                        val attrs = tag.attributes
                        when {
                            tag.name == "thumbnail" -> attrs["url"]?.let(::secureUrl)
                            tag.name in setOf("content", "enclosure") && (attrs["type"].orEmpty().startsWith("image/") || attrs["medium"] == "image" || attrs["url"].orEmpty().substringBefore('?').lowercase().matches(Regex(".*\\.(jpg|jpeg|png|webp|gif|avif)$"))) -> attrs["url"]?.let(::secureUrl)
                            else -> null
                        }
                    } ?: markupTags(unCdata(field(item, "description").orEmpty())).firstOrNull { it.name == "img" }?.attributes?.get("src")?.let(::secureUrl)
                    NewsItem(title, snippet, url, (field(item, "guid") ?: field(item, "id"))?.let(::plain)?.ifBlank { url } ?: url,
                        (field(item, "pubDate") ?: field(item, "published") ?: field(item, "updated"))?.let(::parseFeedDate), image)
                }.take(limit).toList()
        }
    }
}
private fun field(xml: String, name: String): String? = Regex("<$name(?:\\s[^>]*)?>(.*?)</$name\\s*>", xmlOptions).find(xml)?.groupValues?.get(1)
private fun unCdata(value: String): String = value.replace("<![CDATA[", "").replace("]]>", "")
private fun plain(value: String): String = htmlToParagraphs(decodeEntities(unCdata(value))).replace(Regex("\\s+"), " ").trim()
private fun secureUrl(value: String): String? = runCatching {
    val url = Url(value.trim().replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://"))
    url.toString().takeIf { url.protocol.name == "https" && url.host.isNotBlank() }
}.getOrNull()
fun normalizeArticleUrl(value: String): String = runCatching {
    val url = Url(secureUrl(value) ?: value)
    "https://${url.host.lowercase()}${if (url.port == 443) "" else ":${url.port}"}${url.encodedPath.trimEnd('/')}${url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()}"
}.getOrElse { value.substringBefore('#').trimEnd('/') }
fun mergeHeadlines(vararg sources: List<NewsItem>, limit: Int): List<NewsItem> {
    if (limit <= 0) return emptyList()
    val merged = linkedMapOf<String, NewsItem>()
    sources.flatMap { it }.forEach { item ->
        val key = normalizeArticleUrl(item.link)
        val existing = merged[key]
        if (existing == null || existing.imageUrl == null && item.imageUrl != null) merged[key] = item
    }
    return merged.values.sortedWith(compareByDescending<NewsItem> { it.publishedAtMs }).take(limit)
}
internal fun parseFeedDate(raw: String): Long? {
    runCatching { Instant.parse(raw.trim()).toEpochMilliseconds() }.getOrNull()?.let { return it }
    val parts = Regex("(?:[A-Za-z]+,?\\s+)?(\\d{1,2})\\s+([A-Za-z]{3})\\s+(\\d{4})\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?\\s+([+-]\\d{4}|GMT|UTC|UT|Z)", RegexOption.IGNORE_CASE).matchEntire(raw.trim()) ?: return null
    return runCatching {
        val p = parts.groupValues
        val month = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(p[2].lowercase()) + 1
        val offset = p[7].takeIf { it.startsWith('+') || it.startsWith('-') }?.let { (it.substring(1, 3).toInt() * 60 + it.substring(3).toInt()) * if (it[0] == '-') -1 else 1 } ?: 0
        LocalDateTime(p[3].toInt(), month, p[1].toInt(), p[4].toInt(), p[5].toInt(), p[6].ifEmpty { "0" }.toInt()).toInstant(TimeZone.UTC).toEpochMilliseconds() - offset * 60_000L
    }.getOrNull()
}
