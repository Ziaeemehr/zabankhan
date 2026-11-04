package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.translate.KeyValueCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CaptionEnvelope(val title: String, val cues: List<SubtitleCue>, val channel: String = "", val version: Int? = null, val source: String? = null)

class CaptionCache(private val cache: KeyValueCache) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(videoId: String, lang: LanguageSpec): String {
        require(lang in Languages.learnable)
        return "watch:captions:${lang.code}:$videoId"
    }
    internal suspend fun read(videoId: String, lang: LanguageSpec): CachedCaptions? {
        val raw = cache.get(key(videoId, lang)) ?: return null
        return runCatching {
            val value = json.decodeFromString<CaptionEnvelope>(raw)
            val captions = VideoCaptions(value.title, normalizeCues(value.cues), value.channel)
            CachedCaptions(captions, value.version, value.source).takeIf { captions.cues.isNotEmpty() }
        }.getOrNull()
    }
    suspend fun write(videoId: String, lang: LanguageSpec, captions: VideoCaptions, source: String) {
        require(source in listOf("youtube", "file"))
        cache.put(key(videoId, lang), json.encodeToString(CaptionEnvelope(captions.title, normalizeCues(captions.cues), captions.channel, 2, source)))
    }
    suspend fun load(videoId: String, lang: LanguageSpec, fetcher: YoutubeCaptionFetcher): VideoCaptions {
        val cached = read(videoId, lang)
        val fetched = if (shouldFetchCaptions(cached)) fetcher.fetch(videoId, lang) else null
        val decision = chooseCaptions(cached, fetched) ?: return VideoCaptions("", emptyList())
        if (decision.write) write(videoId, lang, decision.captions, decision.source)
        return decision.captions
    }
}
