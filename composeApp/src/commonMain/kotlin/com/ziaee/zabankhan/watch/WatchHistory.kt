package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.translate.KeyValueCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecentVideo(val videoId: String, val title: String, val channel: String, val positionMs: Long, val path: String? = null, val subs: String? = null)

fun updateRecentVideos(old: List<RecentVideo>, entry: RecentVideo): List<RecentVideo> {
    val previous = old.firstOrNull { it.videoId == entry.videoId && it.path == entry.path }
    val merged = entry.copy(title = entry.title.ifBlank { previous?.title.orEmpty() }, channel = entry.channel.ifBlank { previous?.channel.orEmpty() },
        positionMs = entry.positionMs.coerceAtLeast(0), subs = entry.subs ?: previous?.subs)
    return (listOf(merged) + old.filterNot { it.videoId == entry.videoId && it.path == entry.path }).distinctBy { it.path ?: it.videoId }.take(20)
}

class WatchHistory(private val cache: KeyValueCache) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(lang: LanguageSpec): String { require(lang in Languages.learnable); return "watch:history:${lang.code}" }
    suspend fun read(lang: LanguageSpec): List<RecentVideo> {
        val raw = cache.get(key(lang)) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentVideo>>(raw).take(20) }.getOrDefault(emptyList())
    }
    suspend fun record(lang: LanguageSpec, entry: RecentVideo) = mutex.withLock {
        cache.put(key(lang), json.encodeToString(updateRecentVideos(read(lang), entry)))
    }
    suspend fun remove(lang: LanguageSpec, entry: RecentVideo): List<RecentVideo> = mutex.withLock {
        read(lang).filterNot { it.videoId == entry.videoId && it.path == entry.path }.also { cache.put(key(lang), json.encodeToString(it)) }
    }
}
