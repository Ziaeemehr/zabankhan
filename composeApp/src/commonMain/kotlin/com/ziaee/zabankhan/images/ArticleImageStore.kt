package com.ziaee.zabankhan.images

import com.ziaee.zabankhan.pdf.AppFiles
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
private const val MAX_IMAGE_WIDTH = 1080
private const val JPEG_QUALITY = 82
internal const val IMAGES_DIR_NAME = "text_images"

/** Downloads, bounds/type-checks, scales, and stores one document's lead
 * image. Testable in isolation via the [ArticleImageStorage] seam --
 * [ArticleImportRepository] never touches Android APIs directly. */
interface ArticleImageStorage {
    /** Downloads [imageUrl], scales it to [MAX_IMAGE_WIDTH], and writes it as
     * a JPEG under `filesDir/text_images/<documentId>.jpg`. Returns the
     * relative path on success, or null (deleting any partial file) if the
     * URL isn't reachable, isn't an image, exceeds the size bound, or fails
     * to decode -- a missing image is never fatal to an import. */
    suspend fun downloadAndStore(documentId: Long, imageUrl: String): String?

    /** Decodes, scales, and stores image bytes at an owned relative path. */
    suspend fun storeBytes(relativePath: String, bytes: ByteArray): String?

    /** Deletes an image at a relative path this store previously returned.
     * Refuses (returns false) any path outside its own images directory. */
    suspend fun delete(relativePath: String): Boolean
}

class ArticleImageStore(private val http: HttpClient) : ArticleImageStorage {
    override suspend fun downloadAndStore(documentId: Long, imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = http.get(imageUrl) { header(HttpHeaders.UserAgent, "Mozilla/5.0 (Zabankhan)") }
            if (response.status.value !in 200..299 || !response.headers[HttpHeaders.ContentType].orEmpty().startsWith("image/", true) ||
                (response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L) > MAX_IMAGE_BYTES) return@withContext null
            val channel = response.bodyAsChannel()
            val buffer = Buffer()
            val chunk = ByteArray(8192)
            try {
                while (true) {
                    val read = channel.readAvailable(chunk, 0, chunk.size)
                    if (read == -1) break
                    if (buffer.size + read > MAX_IMAGE_BYTES) return@withContext null
                    buffer.write(chunk, 0, read)
                }
                storeBytes("$IMAGES_DIR_NAME/$documentId.jpg", buffer.readByteArray())
            } finally { channel.cancel() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    override suspend fun storeBytes(relativePath: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val file = resolveOwned(relativePath) ?: return@withContext null
        if (bytes.size > MAX_IMAGE_BYTES) return@withContext null
        try {
            val jpeg = encodeArticleJpeg(bytes, MAX_IMAGE_WIDTH, JPEG_QUALITY) ?: return@withContext null
            FileSystem.SYSTEM.createDirectories(requireNotNull(file.parent))
            FileSystem.SYSTEM.write(file) { write(jpeg) }
            relativePath
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { FileSystem.SYSTEM.delete(file, mustExist = false); null }
    }

    override suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveOwned(relativePath) ?: return@withContext false
        val exists = FileSystem.SYSTEM.exists(file)
        FileSystem.SYSTEM.delete(file, mustExist = false)
        exists
    }

    private fun resolveOwned(relativePath: String): Path? {
        if (!isOwnedArticleImagePath(relativePath)) return null
        return AppFiles.filesDir.toPath() / relativePath
    }
}

internal fun isOwnedArticleImagePath(path: String): Boolean = path.startsWith("$IMAGES_DIR_NAME/") &&
    path.split('/').none { it == ".." || it == "." || it.isEmpty() } && '\\' !in path

internal expect fun encodeArticleJpeg(bytes: ByteArray, maxWidth: Int, quality: Int): ByteArray?

/** Pure scaling math, split out so it's testable on the plain JVM without
 * Robolectric/instrumentation (unlike the bitmap decode/download above). */
internal fun scaledDimensions(width: Int, height: Int, maxWidth: Int): Pair<Int, Int> {
    if (width <= maxWidth || width <= 0) return width to height
    val scaledHeight = ((height.toLong() * maxWidth) / width).toInt()
    return maxWidth to scaledHeight
}

internal fun sampleSizeFor(sourceWidth: Int, targetWidth: Int): Int {
    if (targetWidth <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
