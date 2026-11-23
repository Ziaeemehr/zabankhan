package com.ziaee.zabankhan.watch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer
import org.jetbrains.compose.resources.decodeToImageBitmap

internal class VideoThumbnailLoader(private val http: HttpClient) {
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private val cacheLock = Mutex()

    suspend fun load(videoId: String): ImageBitmap? {
        val id = YoutubeUrl.videoId(videoId) ?: return null
        cacheLock.withLock { cache.remove(id)?.also { cache[id] = it } }?.let { return it }
        val image = loadYoutubeThumbnail(http, id) ?: return null
        cacheLock.withLock {
            cache[id] = image
            if (cache.size > 24) cache.remove(cache.keys.first())
        }
        return image
    }
}

@Composable
internal fun VideoThumbnail(modifier: Modifier, thumbnails: VideoThumbnailLoader, videoId: String) {
    val bitmap by key(thumbnails, videoId) {
        produceState<ImageBitmap?>(null) {
            value = thumbnails.load(videoId)
        }
    }
    Box(
        modifier.aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnail = bitmap
        if (thumbnail != null) {
            Image(thumbnail, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private suspend fun loadYoutubeThumbnail(http: HttpClient, id: String): ImageBitmap? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(5_000) {
        try {
            http.prepareGet("https://i.ytimg.com/vi/$id/mqdefault.jpg").execute { response ->
                if (response.status.value !in 200..299 ||
                    !response.headers[HttpHeaders.ContentType].orEmpty().startsWith("image/", ignoreCase = true)
                ) return@execute null

                val channel = response.bodyAsChannel()
                val buffer = Buffer()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = channel.readAvailable(chunk, 0, chunk.size)
                    if (count == -1) break
                    if (buffer.size + count > 512 * 1024) return@execute null
                    buffer.write(chunk, 0, count)
                }
                buffer.readByteArray().decodeToImageBitmap()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
