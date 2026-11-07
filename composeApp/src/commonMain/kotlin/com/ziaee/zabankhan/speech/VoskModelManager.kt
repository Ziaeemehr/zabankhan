package com.ziaee.zabankhan.speech

// Port of FrenchReader shadowing/VoskModelManager.kt; Ktor + okio replace URL/File/ZipInputStream.
import com.ziaee.zabankhan.content.ZipArchive
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.pdf.AppFiles
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.coroutines.coroutineContext

private const val MARKER = ".installed"
private const val MAX_MODEL_BYTES = 300L * 1024 * 1024
private val modelOperations = Mutex()

/** Strip the model's enclosing directory; ZipArchive validates paths, bounds and checksums. */
internal fun voskModelEntries(zip: ByteArray): Map<String, ByteArray> {
    val entries = ZipArchive.read(zip, MAX_MODEL_BYTES)
    val roots = entries.keys.map { it.substringBefore('/') }.toSet()
    require(roots.size == 1 && entries.keys.all { '/' in it }) { "Not a Vosk model" }
    val files = entries.mapKeys { (name, _) -> name.substringAfter('/') }
    require(files["am/final.mdl"]?.isNotEmpty() == true && "conf/model.conf" in files) { "Not a Vosk model" }
    return files
}

class VoskModelManager(val language: LanguageSpec, private val fs: FileSystem = FileSystem.SYSTEM) {
    val modelDir = "${AppFiles.filesDir}/vosk/${language.code}-small".toPath()
    fun isInstalled(): Boolean = fs.exists(modelDir / MARKER)
    fun sizeBytes(): Long = if (fs.exists(modelDir)) fs.listRecursively(modelDir).sumOf { fs.metadataOrNull(it)?.takeIf { meta -> meta.isRegularFile }?.size ?: 0 } else 0

    suspend fun download(http: HttpClient, onProgress: (Float) -> Unit) = modelOperations.withLock {
        withContext(Dispatchers.IO) {
            val model = requireNotNull(language.offlineSpeechModel)
            fs.createDirectories(requireNotNull(modelDir.parent))
            val zip = modelDir.parent!! / "${language.code}.zip.part"
            try {
                http.prepareGet(model.url).execute { response ->
                    check(response.status.value == 200) { "HTTP ${response.status.value}" }
                    val total = response.contentLength()
                    require(total == null || total <= MAX_MODEL_BYTES) { "Model archive too large" }
                    var done = 0L
                    val channel = response.bodyAsChannel()
                    fs.write(zip) {
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = channel.readAvailable(buffer)
                            if (n < 0) break
                            write(buffer, 0, n); done += n
                            require(done <= MAX_MODEL_BYTES) { "Model archive too large" }
                            if (total != null && total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    check(total == null || done == total) { "Incomplete download" }
                }
                install(fs.read(zip) { readByteArray() })
            } finally { fs.delete(zip, mustExist = false) }
        }
    }

    suspend fun installFromZip(path: String) = modelOperations.withLock {
        withContext(Dispatchers.IO) {
            val zip = path.toPath()
            require((fs.metadata(zip).size ?: 0) <= MAX_MODEL_BYTES) { "Model archive too large" }
            install(fs.read(zip) { readByteArray() })
        }
    }
    private suspend fun install(bytes: ByteArray) {
        val files = voskModelEntries(bytes)
        val tmp = modelDir.resolveSibling("${modelDir.name}.tmp")
        val old = modelDir.resolveSibling("${modelDir.name}.old")
        fs.deleteRecursively(tmp, mustExist = false)
        try {
            fs.createDirectories(tmp)
            for ((relative, bytes) in files) {
                coroutineContext.ensureActive()
                val out = tmp / relative
                fs.createDirectories(requireNotNull(out.parent))
                fs.write(out) { write(bytes) }
            }
            fs.write(tmp / MARKER) { writeUtf8("ok") }
            // The recognizer's model lease prevents a deletion/replacement while native code uses it.
            changeOfflineModel(modelDir.toString()) {
                fs.deleteRecursively(old, mustExist = false)
                if (fs.exists(modelDir)) fs.atomicMove(modelDir, old)
                try { fs.atomicMove(tmp, modelDir) }
                catch (error: Throwable) { if (fs.exists(old)) fs.atomicMove(old, modelDir); throw error }
                fs.deleteRecursively(old, mustExist = false)
            }
        } finally { fs.deleteRecursively(tmp, mustExist = false) }
    }
    suspend fun deleteAndUnload() = modelOperations.withLock {
        withContext(Dispatchers.IO) {
            changeOfflineModel(modelDir.toString()) { fs.deleteRecursively(modelDir, mustExist = false) }
        }
    }
}
private fun Path.resolveSibling(name: String): Path = requireNotNull(parent) / name
