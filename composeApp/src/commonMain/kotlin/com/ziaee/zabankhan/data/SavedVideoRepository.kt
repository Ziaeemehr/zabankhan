package com.ziaee.zabankhan.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import okio.ByteString.Companion.encodeUtf8

fun savedVideoStableKey(lang: String, videoId: String, path: String): String {
    require(lang.isNotBlank())
    require(videoId.isNotBlank() || path.isNotBlank())
    val identity = if (path.isNotBlank()) "path:${canonicalSavedVideoPath(path)}" else "video:$videoId"
    return "savedVideo:${"$lang|$identity".encodeUtf8().sha256().hex()}"
}

private fun canonicalSavedVideoPath(path: String): String {
    val managed = path.lastIndexOf("/videos/")
    if (managed >= 0) return path.substring(managed)
    if (path.startsWith("videos/")) return "/$path"
    return path
}

/** iOS reinstalls change the app container path; saved video and subtitle paths are re-anchored here. */
fun rebaseSavedVideoPath(saved: String, filesDir: String): String {
    if (saved.isEmpty()) return saved
    if (!saved.startsWith("/")) return if (saved.startsWith("videos/")) "$filesDir/$saved" else saved
    val at = saved.lastIndexOf("/videos/")
    return if (at < 0) saved else filesDir.trimEnd('/') + saved.substring(at)
}

internal fun isValidYoutubeVideoId(videoId: String) = videoId.length == 11 && videoId.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }

class SavedVideoRepository(private val database: AppDatabase) {
    suspend fun toggleFavorite(
        lang: String,
        videoId: String,
        path: String,
        subtitlePath: String,
        title: String,
        channel: String,
        updatedAtMs: Long = nowMs(),
    ) = update(lang, videoId, path, subtitlePath, title, channel, updatedAtMs) { old -> !(old?.isFavorite ?: false) }

    suspend fun setFavorite(lang: String, videoId: String, path: String, subtitlePath: String, title: String, channel: String, isFavorite: Boolean, updatedAtMs: Long = nowMs()) =
        update(lang, videoId, path, subtitlePath, title, channel, updatedAtMs) { isFavorite }

    private suspend fun update(
        lang: String,
        videoId: String,
        path: String,
        subtitlePath: String,
        title: String,
        channel: String,
        updatedAtMs: Long,
        favorite: (SavedVideoEntity?) -> Boolean,
    ) {
        require(path.isNotBlank() || isValidYoutubeVideoId(videoId)) { "Online saved videos must have a valid YouTube ID" }
        val key = savedVideoStableKey(lang, videoId, path)
        database.useWriterConnection { connection -> connection.immediateTransaction {
            val dao = database.savedVideos()
            val old = dao.get(key)
            dao.upsert(SavedVideoEntity(
                stableKey = key,
                lang = lang,
                videoId = videoId.ifBlank { old?.videoId.orEmpty() },
                path = path.ifBlank { old?.path.orEmpty() },
                subtitlePath = subtitlePath.ifBlank { old?.subtitlePath.orEmpty() },
                title = title.ifBlank { old?.title.orEmpty() },
                channel = channel.ifBlank { old?.channel.orEmpty() },
                isFavorite = favorite(old),
                updatedAtMs = updatedAtMs,
            ))
        } }
    }
}
