package com.ziaee.zabankhan.content

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.pdf.AppFiles
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import okio.Path
import okio.Buffer
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

data class DatasetPackStatus(val installed: Int, val total: Int) {
    val complete get() = installed >= total && total > 0
}

data class DatasetStatus(
    val decks: Map<String, DatasetPackStatus>,
    val stories: Map<String, DatasetPackStatus>
)

data class DatasetInstallResult(val added: Int, val skipped: Int)

class DatasetRepository(
    private val db: AppDatabase,
    val language: LanguageSpec,
    private val meaningLanguage: String,
    private val http: HttpClient = HttpClient()
) {
    private var manifest: DatasetManifest? = null
    private val bundledSource = DatasetSources.bundled(language.code)
    private val remoteSource = DatasetSource.Remote(language.code)
    private val downloadedSizes = mutableMapOf<String, Long>()

    suspend fun loadManifest(): DatasetManifest = withContext(Dispatchers.IO) {
        manifest?.let { return@withContext it }
        val bundled = bundledSource?.let { source ->
            try { parseDatasetManifest(asset(source, "manifest.json"), source) }
            catch (_: org.jetbrains.compose.resources.MissingResourceException) { null }
        }
        val remote = fetchRemoteManifest()
        val value = mergeDatasetManifests(bundled, remote)
        manifest = value
        value
    }

    private suspend fun fetchRemoteManifest(): DatasetManifest? {
        val cachePath = datasetRoot(remoteSource) / "manifest.json"
        val cached = runCatching { parseDatasetManifest(FileSystem.SYSTEM.read(cachePath) { readUtf8() }, remoteSource) }.getOrNull()
        return try {
            val response = http.get(manifestUrl(remoteSource))
            if (response.status == HttpStatusCode.NotFound) {
                if (FileSystem.SYSTEM.exists(cachePath)) FileSystem.SYSTEM.delete(cachePath)
                null
            } else if (response.status.value in 200..299) {
                val body = response.bodyAsBytes().decodeToString()
                val parsed = parseDatasetManifest(body, remoteSource)
                FileSystem.SYSTEM.createDirectories(requireNotNull(cachePath.parent))
                FileSystem.SYSTEM.write(cachePath) { writeUtf8(body) }
                parsed
            } else cached
        } catch (_: Exception) { cached }
    }

    suspend fun installDeck(pack: DatasetDeckPack, progress: (Long, Long?) -> Unit = { _, _ -> }): DatasetInstallResult = withContext(Dispatchers.IO) {
        download(pack, progress)
        val rows = parseDatasetDeck(asset(pack.source, pack.file))
        val metadata = loadManifest()
        val now = nowMs()
        db.useWriterConnection { connection -> connection.immediateTransaction {
            val lists = db.vocab().observeLists(language.code).first()
            val listId = lists.firstOrNull { it.name.equals(pack.name, true) }?.id
                ?: db.vocab().insertList(VocabList(lang = language.code, name = pack.name))
            val existing = db.vocab().getAll(language.code)
                .filter { it.listId == listId }
                .map { it.word.lowercase() }
                .toMutableSet()
            var added = 0
            rows.forEach { row ->
                if (existing.add(row.word.lowercase())) {
                    db.vocab().insert(
                        VocabEntry(
                            lang = language.code,
                            meaningLang = metadata.meaningLanguage.ifBlank { meaningLanguage },
                            word = row.word,
                            sentence = row.sentence,
                            textId = 0,
                            dictionaryUrl = language.dictionaryLinks.firstOrNull { it.supportsMeaningLanguage(meaningLanguage) }?.urlFor(row.word),
                            meaning = datasetMeaning(row.meaning, row.lesson, metadata.lessonLabel),
                            listId = listId,
                            createdAtMs = now + added,
                            nextReviewAtMs = now,
                            leitnerBox = 1,
                            lastReviewedAtMs = null
                        )
                    )
                    added++
                }
            }
            DatasetInstallResult(added, rows.size - added)
        } }
    }

    suspend fun removeOrphanedDatasetCards(): Int = withContext(Dispatchers.IO) {
        val manifest = loadManifest()
        val rowsByPack = manifest.decks.mapNotNull { pack ->
            runCatching { pack to parseDatasetDeck(asset(pack.source, pack.file)) }.getOrNull()
        }.toMap()
        db.useWriterConnection { connection -> connection.immediateTransaction {
            val lists = db.vocab().observeLists(language.code).first()
            val packs = rowsByPack.map { (pack, rows) ->
                rows to lists.firstOrNull { it.name.equals(pack.name, ignoreCase = true) }?.id
            }
            val plan = planDatasetCardCleanup(packs, db.vocab().getAll(language.code), manifest.lessonLabel)
            plan.deleteIds.chunked(500).forEach { db.vocab().deleteIds(it) }
            plan.moveToList.entries.groupBy({ it.value }, { it.key }).forEach { (listId, ids) ->
                ids.chunked(500).forEach { db.vocab().moveToList(it, listId) }
            }
            plan.removed
        } }
    }

    suspend fun installStories(pack: DatasetStoryPack, progress: (Long, Long?) -> Unit = { _, _ -> }): DatasetInstallResult = withContext(Dispatchers.IO) {
        download(pack, progress)
        val rows = parseDatasetStories(asset(pack.sourceRef, pack.file))
        db.useWriterConnection { connection -> connection.immediateTransaction {
            val folders = db.libraryOrganizer()
            val metadata = loadManifest()
            val root = folders.findFolderByName(language.code, metadata.gradedStoriesFolder)?.id
                ?: folders.insertFolder(LibraryFolder(lang = language.code, name = metadata.gradedStoriesFolder))
            val level = folders.findFolderByName(language.code, pack.level, root)?.id
                ?: folders.insertFolder(LibraryFolder(lang = language.code, name = pack.level, parentId = root))
            var added = 0
            rows.forEach { story ->
                if (db.texts().getByExternalKey(externalKey(story.key, pack.sourceRef)) == null) {
                    val stored = story.image?.let { image ->
                        val bytes = assetBytes(pack.sourceRef, image)
                        val path = "${AppFiles.filesDir}/text_images/${language.code}/dataset/${image.substringAfterLast('/')}".toPath()
                        FileSystem.SYSTEM.createDirectories(requireNotNull(path.parent))
                        FileSystem.SYSTEM.write(path) { write(bytes) }
                        path.toString()
                    }
                    val body = if (stored != null) {
                        // Brackets in the alt text would break the reader's image-line pattern.
                        val alt = story.title.replace(Regex("[\\[\\]]"), "")
                        "![$alt](epubimg:$stored)\n\n${story.body}"
                    } else {
                        story.body
                    }
                    db.texts().insert(
                        TextDocument(
                            lang = language.code,
                            title = story.title,
                            body = body,
                            sourceName = pack.source,
                            sourceUrl = story.sourceUrl,
                            externalKey = externalKey(story.key, pack.sourceRef),
                            folderId = level,
                            level = pack.level.takeIf { it in setOf("A1", "A2", "B1", "B2", "C1", "C2") },
                            imagePath = stored
                        )
                    )
                    added++
                }
            }
            DatasetInstallResult(added, rows.size - added)
        } }
    }

    suspend fun status(manifestOrNull: DatasetManifest? = null): DatasetStatus = withContext(Dispatchers.IO) {
        val manifest = manifestOrNull ?: loadManifest()
        val lists = db.vocab().observeLists(language.code).first()
        val entries = db.vocab().getAll(language.code)
        val storyKeys = db.texts().observeAll(language.code).first().mapNotNull { it.externalKey }.toSet()
        DatasetStatus(
            decks = manifest.decks.associate { pack ->
                val installed = lists.firstOrNull { it.name.equals(pack.name, true) }
                    ?.let { list -> entries.count { it.listId == list.id } }
                    ?: 0
                pack.key to DatasetPackStatus(installed, pack.count)
            },
            stories = manifest.stories.associate { pack ->
                val installed = runCatching { parseDatasetStories(asset(pack.sourceRef, pack.file)).count { externalKey(it.key, pack.sourceRef) in storyKeys } }.getOrDefault(0)
                pack.key to DatasetPackStatus(installed, pack.count)
            }
        )
    }

    suspend fun installAll(manifestOrNull: DatasetManifest? = null): DatasetInstallResult {
        val manifest = manifestOrNull ?: loadManifest()
        return installPacks(manifest.decks, manifest.stories)
    }

    suspend fun downloadSize(pack: DatasetDeckPack): Long? = pack.sizeBytes ?: downloadedSizes[pack.key] ?: remoteFileSize(pack.source, pack.file)
    suspend fun downloadSize(pack: DatasetStoryPack): Long? = pack.sizeBytes ?: downloadedSizes[pack.key] ?: remoteFileSize(pack.sourceRef, pack.file)

    private suspend fun remoteFileSize(source: DatasetSource, file: String): Long? = try {
        if (source is DatasetSource.Bundled) assetBytes(source, file).size.toLong()
        else {
            val local = datasetRoot(source) / file
            if (FileSystem.SYSTEM.exists(local)) FileSystem.SYSTEM.metadata(local).size
            else http.head(fileUrl(source, file)).takeIf { it.status.value in 200..299 }?.headers?.get("Content-Length")?.toLongOrNull()
        }
    } catch (_: Exception) { null }

    suspend fun download(pack: DatasetDeckPack, progress: (Long, Long?) -> Unit = { _, _ -> }) {
        downloadFile(pack.source, pack.file, pack.key, progress)
    }

    suspend fun download(pack: DatasetStoryPack, progress: (Long, Long?) -> Unit = { _, _ -> }) {
        val bytes = downloadFile(pack.sourceRef, pack.file, pack.key, progress)
        val stories = parseDatasetStories(bytes.decodeToString())
        stories.mapNotNull { it.image }.distinct().forEach { image -> downloadFile(pack.sourceRef, image, pack.key, progress) }
    }

    private suspend fun downloadFile(source: DatasetSource, file: String, key: String, progress: (Long, Long?) -> Unit): ByteArray {
        validateDatasetFile(file)
        if (source is DatasetSource.Bundled) return assetBytes(source, file)
        val path = datasetRoot(source) / file
        if (FileSystem.SYSTEM.exists(path)) return FileSystem.SYSTEM.read(path) { readByteArray() }
        val response = http.get(fileUrl(source, file))
        check(response.status.value in 200..299) { "Dataset download failed (${response.status.value})" }
        val total = response.headers["Content-Length"]?.toLongOrNull()
        val sink = Buffer()
        val chunk = ByteArray(16 * 1024)
        var received = 0L
        val channel = response.bodyAsChannel()
        while (true) {
            val count = channel.readAvailable(chunk, 0, chunk.size)
            if (count < 0) break
            if (count == 0) continue
            sink.write(chunk, 0, count)
            received += count
            progress(received, total)
        }
        val bytes = sink.readByteArray()
        FileSystem.SYSTEM.createDirectories(requireNotNull(path.parent))
        FileSystem.SYSTEM.write(path) { write(bytes) }
        downloadedSizes[key] = (downloadedSizes[key] ?: 0L) + bytes.size
        return bytes
    }

    suspend fun installStarter(manifestOrNull: DatasetManifest? = null): DatasetInstallResult {
        val manifest = manifestOrNull ?: loadManifest()
        return installPacks(
            manifest.decks.filter { it.starter || (it.source is DatasetSource.Bundled && it.id in manifest.starterDeckIds) },
            manifest.stories.filter { it.starter || (it.sourceRef is DatasetSource.Bundled && it.id in manifest.starterStoryIds) }
        )
    }

    private suspend fun installPacks(
        decks: List<DatasetDeckPack>,
        stories: List<DatasetStoryPack>
    ): DatasetInstallResult {
        var added = 0
        var skipped = 0
        decks.forEach { pack ->
            installDeck(pack).also { result ->
                added += result.added
                skipped += result.skipped
            }
        }
        stories.forEach { pack ->
            installStories(pack).also { result ->
                added += result.added
                skipped += result.skipped
            }
        }
        return DatasetInstallResult(added, skipped)
    }

    private fun externalKey(key: String, source: DatasetSource): String = when (source) {
        is DatasetSource.Bundled -> "dataset:${language.code}:$key"
        is DatasetSource.Remote -> "dataset:${language.code}:remote:$key"
    }
    private suspend fun assetBytes(source: DatasetSource, path: String): ByteArray {
        validateDatasetFile(path)
        return when (source) {
        is DatasetSource.Bundled -> Res.readBytes("files/${source.language}/dataset/$path")
        is DatasetSource.Remote -> {
            val file = datasetRoot(source) / path
            FileSystem.SYSTEM.read(file) { readByteArray() }
        }
        }
    }
    private suspend fun asset(source: DatasetSource, path: String): String = assetBytes(source, path).decodeToString()

    private fun datasetRoot(source: DatasetSource): Path = "${AppFiles.filesDir}/dataset_packs/${source.language}".toPath()
    private fun manifestUrl(source: DatasetSource) = "https://ziaeemehr.github.io/zabankhan-dict/packs/${source.language}/dataset/manifest.json"
    private fun fileUrl(source: DatasetSource, file: String) = "https://ziaeemehr.github.io/zabankhan-dict/packs/${source.language}/dataset/$file"
    private fun validateDatasetFile(file: String) { require(!file.startsWith('/') && file.split('/').none { it == ".." }) }
}

/** Bundled assets are registered only for languages that ship copyrighted/local datasets. */
internal object DatasetSources {
    private val bundledLanguages = setOf("fr")
    fun bundled(language: String): DatasetSource.Bundled? = language.takeIf { it in bundledLanguages }?.let { DatasetSource.Bundled(it) }
}
