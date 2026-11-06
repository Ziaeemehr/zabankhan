package com.ziaee.zabankhan.speech.edge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.ByteString.Companion.encodeUtf8

/** A lease keeps both the playing file and the prefetched file out of eviction. */
data class CachedSpeech(val key: String, val path: String, val boundaries: List<Boundary>)
class TtsCache(
    directory: String,
    private val maxBytes: Long = 200L * 1024 * 1024,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    private val dir = directory.toPath()
    private val mutex = Mutex()
    private val leases = mutableMapOf<String, Int>()
    private fun path(key: String, extension: String) = dir / "$key.$extension"
    fun key(text: String, voice: String, rate: Int): String =
        Json.encodeToString(listOf(voice, rate.toString(), text)).encodeUtf8().sha256().hex()
    suspend fun acquire(text: String, voice: String, rate: Int, synthesize: suspend () -> EdgeSpeech): CachedSpeech {
        val key = key(text, voice, rate)
        // This mutex covers synthesis too: never race two writers of a cache entry.
        var acquired: CachedSpeech? = null
        try {
            return mutex.withLock {
            withContext(Dispatchers.IO) {
                fs.createDirectories(dir)
                val audio = path(key, "mp3")
                val meta = path(key, "json")
                val cached = try {
                    if (fs.metadataOrNull(audio)?.size?.let { it > 0 } == true && fs.exists(meta))
                        Json.decodeFromString<List<Boundary>>(fs.read(meta) { readUtf8() }).takeIf { it.isNotEmpty() }
                    else null
                } catch (_: Exception) { null }
                val boundaries = cached ?: synthesize().let { result ->
                    require(result.mp3.isNotEmpty() && result.boundaries.isNotEmpty())
                    val pending = path(key, "tmp")
                    try {
                        fs.write(pending) { write(result.mp3) }
                        fs.atomicMove(pending, audio)
                    } finally { fs.delete(pending, mustExist = false) }
                    result.boundaries
                }
                // Metadata is the commit marker and the persistent LRU access timestamp.
                val pendingMeta = path(key, "json.tmp")
                try {
                    fs.write(pendingMeta) { writeUtf8(Json.encodeToString(boundaries)) }
                    fs.atomicMove(pendingMeta, meta)
                } finally { fs.delete(pendingMeta, mustExist = false) }
                leases[key] = (leases[key] ?: 0) + 1
                prune()
                CachedSpeech(key, audio.toString(), boundaries).also { acquired = it }
            }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { acquired?.let { release(it) } }
            throw e
        }
    }
    suspend fun release(entry: CachedSpeech) = mutex.withLock {
        val count = (leases[entry.key] ?: 1) - 1
        if (count <= 0) leases.remove(entry.key) else leases[entry.key] = count
        withContext(Dispatchers.IO) { prune() }
    }
    /** Several documents can share the same synthesized clip; each owns its own pin. */
    suspend fun setPinned(entry: CachedSpeech, pinned: Boolean, owner: String = "speech") = mutex.withLock {
        withContext(Dispatchers.IO) {
            updatePin(path(entry.key, "pin"), owner, pinned)
            prune()
        }
    }
    suspend fun unpinDocument(owner: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (fs.exists(dir)) fs.list(dir).filter { it.name.endsWith(".pin") }.forEach { updatePin(it, owner, false) }
            if (fs.exists(dir)) prune()
        }
    }
    private fun updatePin(file: Path, owner: String, pinned: Boolean) {
        require(owner.isNotBlank() && '\n' !in owner && '\r' !in owner)
        val owners = if (fs.exists(file)) fs.read(file) { readUtf8() }.lineSequence().filter { it.isNotBlank() }.toSet() else emptySet()
        val updated = if (pinned) owners + owner else owners - owner
        if (updated.isEmpty()) fs.delete(file, mustExist = false)
        else fs.write(file) { writeUtf8(updated.sorted().joinToString("\n")) }
    }
    private fun prune() {
        val files = fs.list(dir)
        var total = files.sumOf { fs.metadataOrNull(it)?.size ?: 0 }
        val groups = files.groupBy { it.name.substringBefore('.') }
        for ((key, paths) in groups.entries.sortedBy { (_, paths) ->
            paths.firstOrNull { it.name.endsWith(".json") }?.let { fs.metadataOrNull(it)?.lastModifiedAtMillis } ?: 0
        }) {
            if (total <= maxBytes) break
            if (key in leases || paths.any { it.name.endsWith(".pin") }) continue
            for (file in paths) {
                total -= fs.metadataOrNull(file)?.size ?: 0
                fs.delete(file, mustExist = false)
            }
        }
    }
}
