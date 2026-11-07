package com.ziaee.zabankhan.discover

import com.ziaee.zabankhan.content.htmlToParagraphs
import com.ziaee.zabankhan.lang.percentEncode
import io.ktor.client.HttpClient
import kotlinx.serialization.json.*
import kotlin.time.Instant

data class WikiSearchResult(val pageId: Int, val title: String, val snippet: String, val wordCount: Int)
data class WikiArticle(val title: String, val text: String, val publishedAtMs: Long?)
class MediaWikiClient(private val http: HttpClient, val source: WikiSource) {
    suspend fun search(query: String, limit: Int = 10): List<WikiSearchResult> = parseSearchResults(http.contentGet("${source.api}?action=query&list=search&format=json&srlimit=${limit.coerceIn(1, 50)}&srprop=snippet%7Cwordcount&srsearch=${percentEncode(query)}"))
    suspend fun fetchArticle(pageId: Int): WikiArticle? {
        if (source.id != "wikisource") return parseArticle(http.contentGet("${source.api}?action=query&prop=extracts%7Crevisions&explaintext=1&rvprop=timestamp&format=json&pageids=$pageId"), pageId)
        val meta = parseArticle(http.contentGet("${source.api}?action=query&prop=extracts%7Crevisions&explaintext=1&rvprop=timestamp&format=json&pageids=$pageId"), pageId) ?: return null
        val page = Json.parseToJsonElement(http.contentGet("${source.api}?action=parse&format=json&prop=text&page=${percentEncode(meta.title)}")).jsonObject["parse"] as? JsonObject ?: return null
        val html = ((page["text"] as? JsonObject)?.get("*") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val cleaned = html
            .replace(Regex("<(div|span|table)\\b[^>]*class=[\\\"'][^\\\"']*(ws-noexport|headertemplate|pagenum|ws-pagenum)[^\\\"']*[\\\"'][^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<table\\b[^>]*>[\\s\\S]*?</table\\s*>", RegexOption.IGNORE_CASE), "")
        val text = htmlToParagraphs(cleaned).takeIf { it.isNotBlank() } ?: return null
        return WikiArticle(meta.title, text, meta.publishedAtMs)
    }
    companion object {
        fun parseSearchResults(json: String): List<WikiSearchResult> {
            val query = Json.parseToJsonElement(json).jsonObject["query"] as? JsonObject ?: return emptyList()
            return (query["search"] as? JsonArray).orEmpty().mapNotNull { raw ->
                val obj = raw as? JsonObject ?: return@mapNotNull null
                val id = (obj["pageid"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WikiSearchResult(id, title, htmlToParagraphs((obj["snippet"] as? JsonPrimitive)?.contentOrNull.orEmpty()), (obj["wordcount"] as? JsonPrimitive)?.intOrNull ?: 0)
            }
        }
        fun parseArticle(json: String, pageId: Int): WikiArticle? {
            val query = Json.parseToJsonElement(json).jsonObject["query"] as? JsonObject ?: return null
            val page = (query["pages"] as? JsonObject)?.get(pageId.toString()) as? JsonObject ?: return null
            if ("missing" in page) return null
            val title = (page["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            val text = (page["extract"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val heading = Regex("^(={2,6})\\s*(.+?)\\s*\\1$")
            val converted = text.lines().joinToString("\n") { line -> heading.find(line.trim())?.let { "#".repeat(it.groupValues[1].length) + " " + it.groupValues[2] } ?: line }
            val timestamp = ((page["revisions"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("timestamp") as? JsonPrimitive
            return WikiArticle(title, converted, timestamp?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() })
        }
    }
}
