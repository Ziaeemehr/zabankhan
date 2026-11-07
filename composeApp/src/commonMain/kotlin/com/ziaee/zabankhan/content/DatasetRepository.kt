package com.ziaee.zabankhan.content

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.pdf.AppFiles
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
    private val meaningLanguage: String
) {
    private var manifest: DatasetManifest? = null
    suspend fun loadManifest(): DatasetManifest = withContext(Dispatchers.IO) {
        manifest?.let { return@withContext it }
        // A missing language pack is an empty catalog; never reuse another language's content.
        val value = try { parseDatasetManifest(asset("manifest.json")) }
        catch (_: org.jetbrains.compose.resources.MissingResourceException) { DatasetManifest(0, emptyList(), emptyList()) }
        manifest = value
        value
    }

    suspend fun installDeck(pack: DatasetDeckPack): DatasetInstallResult = withContext(Dispatchers.IO) {
        val rows = parseDatasetDeck(asset(pack.file))
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
        val rowsByPack = manifest.decks.associateWith { parseDatasetDeck(asset(it.file)) }
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

    suspend fun installStories(pack: DatasetStoryPack): DatasetInstallResult = withContext(Dispatchers.IO) {
        val rows = parseDatasetStories(asset(pack.file))
        db.useWriterConnection { connection -> connection.immediateTransaction {
            val folders = db.libraryOrganizer()
            val metadata = loadManifest()
            val root = folders.findFolderByName(language.code, metadata.gradedStoriesFolder)?.id
                ?: folders.insertFolder(LibraryFolder(lang = language.code, name = metadata.gradedStoriesFolder))
            val level = folders.findFolderByName(language.code, pack.level, root)?.id
                ?: folders.insertFolder(LibraryFolder(lang = language.code, name = pack.level, parentId = root))
            var added = 0
            rows.forEach { story ->
                if (db.texts().getByExternalKey(externalKey(story.key)) == null) {
                    val stored = story.image?.let { image ->
                        val bytes = assetBytes(image)
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
                            externalKey = externalKey(story.key),
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
                pack.id to DatasetPackStatus(installed, pack.count)
            },
            stories = manifest.stories.associate { pack ->
                val installed = parseDatasetStories(asset(pack.file)).count { externalKey(it.key) in storyKeys }
                pack.id to DatasetPackStatus(installed, pack.count)
            }
        )
    }

    suspend fun installAll(manifestOrNull: DatasetManifest? = null): DatasetInstallResult {
        val manifest = manifestOrNull ?: loadManifest()
        return installPacks(manifest.decks, manifest.stories)
    }

    suspend fun installStarter(manifestOrNull: DatasetManifest? = null): DatasetInstallResult {
        val manifest = manifestOrNull ?: loadManifest()
        return installPacks(
            manifest.decks.filter { it.id in manifest.starterDeckIds },
            manifest.stories.filter { it.id in manifest.starterStoryIds }
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

    private fun externalKey(key: String): String = "dataset:${language.code}:$key"
    private suspend fun assetBytes(path: String): ByteArray = Res.readBytes("files/${language.code}/dataset/$path")
    private suspend fun asset(path: String): String = assetBytes(path).decodeToString()
}
