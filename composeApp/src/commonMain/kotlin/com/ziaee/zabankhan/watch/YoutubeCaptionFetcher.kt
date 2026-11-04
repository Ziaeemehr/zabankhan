package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class VideoCaptions(val title: String, val cues: List<SubtitleCue>, val channel: String = "")
data class CaptionTrack(val baseUrl: String, val languageCode: String, val kind: String, val translateTo: String? = null)

fun selectCaptionTrack(response: JsonObject, lang: LanguageSpec): CaptionTrack? {
    require(lang in Languages.learnable)
    val tracks = ((response["captions"] as? JsonObject)?.get("playerCaptionsTracklistRenderer") as? JsonObject)
        ?.get("captionTracks") as? JsonArray ?: return null
    val all = tracks.mapNotNull { element ->
        val track = element as? JsonObject ?: return@mapNotNull null
        val url = track.string("baseUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        CaptionTrack(url, track.string("languageCode"), track.string("kind")) to
            ((track["isTranslatable"] as? JsonPrimitive)?.booleanOrNull == true)
    }
    fun CaptionTrack.sameLanguage() = languageCode.equals(lang.code, true) || languageCode.startsWith("${lang.code}-", true)
    return all.firstOrNull { it.first.sameLanguage() && it.first.kind != "asr" }?.first
        ?: all.firstOrNull { it.first.sameLanguage() && it.first.kind == "asr" }?.first
        ?: all.firstOrNull { it.second }?.first?.copy(translateTo = lang.code)
}

fun playerVideoTitle(response: JsonObject): String = (response["videoDetails"] as? JsonObject)?.string("title").orEmpty()

fun extractPlayerResponse(html: String): JsonObject {
    val marker = Regex("(?:var\\s+)?ytInitialPlayerResponse\\s*=\\s*|[\"']ytInitialPlayerResponse[\"']\\s*:\\s*")
    for (match in marker.findAll(html)) {
        val start = html.indexOf('{', match.range.last + 1)
        if (start < 0) continue
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val ch = html[index]
            if (inString) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') inString = false
            } else when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) {
                    val parsed = runCatching { Json.parseToJsonElement(html.substring(start, index + 1)) as? JsonObject }.getOrNull()
                    if (parsed != null) return parsed
                    break
                }
            }
        }
    }
    error("Missing player response")
}

internal data class InnertubeClient(val name: String, val version: String, val userAgent: String, val extra: JsonObject)
internal val innertubeClients = listOf(
    InnertubeClient("ANDROID", "20.10.38", "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip", buildJsonObject {
        put("androidSdkVersion", 30); put("osName", "Android"); put("osVersion", "11")
    }),
    InnertubeClient("IOS", "20.10.4", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)", buildJsonObject {
        put("deviceMake", "Apple"); put("deviceModel", "iPhone16,2"); put("osName", "iPhone"); put("osVersion", "18.3.2.22D82")
    }),
)
internal fun innertubePlayerRequest(client: InnertubeClient, videoId: String, lang: LanguageSpec): String = buildJsonObject {
    putJsonObject("context") { putJsonObject("client") {
        put("clientName", client.name); put("clientVersion", client.version); put("hl", lang.code)
        client.extra.forEach { (key, value) -> put(key, value) }
    } }
    put("videoId", videoId)
}.toString()

internal fun timedTextUrl(track: CaptionTrack): String = URLBuilder(track.baseUrl).apply {
    parameters.remove("fmt"); parameters.append("fmt", "json3")
    if (track.translateTo != null) { parameters.remove("tlang"); parameters.append("tlang", track.translateTo) }
}.buildString()

class YoutubeCaptionFetcher(private val http: HttpClient) {
    suspend fun fetch(videoId: String, lang: LanguageSpec): VideoCaptions {
        require(YoutubeUrl.videoId(videoId) == videoId)
        require(lang in Languages.learnable)
        var title = ""
        var channel = ""
        suspend fun captions(response: JsonObject, agent: String): VideoCaptions? {
            if (title.isBlank()) title = playerVideoTitle(response)
            if (channel.isBlank()) channel = (response["videoDetails"] as? JsonObject)?.string("author").orEmpty()
            val track = selectCaptionTrack(response, lang) ?: return null
            val cues = YoutubeTimedTextParser.parse(request(timedTextUrl(track), agent, lang), lang)
            return VideoCaptions(title, cues, channel).takeIf { cues.isNotEmpty() }
        }
        for (client in innertubeClients) {
            try {
                val response = Json.parseToJsonElement(request("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", client.userAgent, lang,
                    innertubePlayerRequest(client, videoId, lang))).jsonObject
                captions(response, client.userAgent)?.let { return it }
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Try the next client. */ }
        }
        val agent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
        val response = extractPlayerResponse(request(YoutubeUrl.watchUrl(videoId), agent, lang))
        return try { captions(response, agent) ?: VideoCaptions(title, emptyList(), channel) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { VideoCaptions(title, emptyList(), channel) }
    }

    private suspend fun request(url: String, agent: String, lang: LanguageSpec, body: String? = null): String {
        val response = http.request(url) {
            method = if (body == null) HttpMethod.Get else HttpMethod.Post
            header(HttpHeaders.UserAgent, agent); header(HttpHeaders.AcceptLanguage, lang.code); header(HttpHeaders.Cookie, "SOCS=CAI")
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }
        check(response.status.value in 200..299) { "HTTP ${response.status.value}" }
        return response.bodyAsText()
    }
}
