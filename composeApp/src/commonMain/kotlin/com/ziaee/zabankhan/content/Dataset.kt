package com.ziaee.zabankhan.content

import com.ziaee.zabankhan.data.VocabEntry
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8

data class DatasetDeckPack(
    val id: String,
    val name: String,
    val level: String,
    val kind: String,
    val file: String,
    val count: Int,
    val starter: Boolean = false,
    val source: DatasetSource = DatasetSource.Bundled(""),
    val sizeBytes: Long? = null
) { val key: String get() = "${source.key}:$id" }

data class DatasetStoryPack(
    val id: String,
    val name: String,
    val level: String,
    val source: String,
    val file: String,
    val count: Int,
    val starter: Boolean = false,
    val sourceRef: DatasetSource = DatasetSource.Bundled(""),
    val sizeBytes: Long? = null
) { val key: String get() = "${sourceRef.key}:$id" }

sealed interface DatasetSource {
    val language: String
    val key: String
    data class Bundled(override val language: String) : DatasetSource { override val key = "bundled:$language" }
    data class Remote(override val language: String) : DatasetSource { override val key = "remote:$language" }
}

data class DatasetManifest(
    val version: Int,
    val decks: List<DatasetDeckPack>,
    val stories: List<DatasetStoryPack>,
    val sentenceTranslations: DatasetSentenceTranslationMetadata? = null,
    val meaningLanguage: String = "",
    val gradedStoriesFolder: String = "Graded stories",
    val lessonLabel: String = "Lesson",
    val starterDeckIds: Set<String> = emptySet(),
    val starterStoryIds: Set<String> = emptySet()
)

data class DatasetSentenceTranslationMetadata(val model: String, val promptRevision: String)

data class DatasetDeckRow(
    val word: String,
    val meaning: String,
    val sentence: String,
    val lesson: String?,
    val sentenceTranslations: Map<String, String> = emptyMap()
)

data class DatasetStoryRow(
    val key: String,
    val title: String,
    val body: String,
    val sourceUrl: String?,
    val image: String?
)

internal fun parseDatasetManifest(json: String): DatasetManifest {
    return parseDatasetManifest(json, DatasetSource.Bundled(""))
}

internal fun parseDatasetManifest(json: String, source: DatasetSource): DatasetManifest {
    val root = Json.parseToJsonElement(json).jsonObject
    val decks = root.getValue("decks").jsonArray.map { value ->
        val deck = value.jsonObject
        DatasetDeckPack(deck.string("id"), deck.string("name"), deck.string("level"), deck.string("kind"), deck.string("file"), deck.getValue("count").jsonPrimitive.int,
            (deck["starter"] as? JsonPrimitive)?.booleanOrNull ?: false, source,
            (deck["sizeBytes"] as? JsonPrimitive)?.longOrNull)
    }
    val stories = root.getValue("stories").jsonArray.map { value ->
        val story = value.jsonObject
        DatasetStoryPack(story.string("id"), story.string("name"), story.string("level"), story.string("source"), story.string("file"), story.getValue("count").jsonPrimitive.int,
            (story["starter"] as? JsonPrimitive)?.booleanOrNull ?: false, source,
            (story["sizeBytes"] as? JsonPrimitive)?.longOrNull)
    }
    val metadata = (root["sentenceTranslations"] as? JsonObject)?.let {
        DatasetSentenceTranslationMetadata(it.string("model"), it.string("promptRevision"))
    }
    fun ids(key: String) = (root[key] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
    return DatasetManifest(root.getValue("version").jsonPrimitive.int, decks, stories, metadata,
        root.string("meaningLanguage"), root.string("gradedStoriesFolder"), root.string("lessonLabel"), ids("starterDeckIds"), ids("starterStoryIds"))
}

internal fun mergeDatasetManifests(bundled: DatasetManifest?, remote: DatasetManifest?): DatasetManifest {
    val base = bundled ?: remote ?: return DatasetManifest(0, emptyList(), emptyList())
    if (bundled == null) return base
    if (remote == null) return bundled
    return bundled.copy(
        version = maxOf(bundled.version, remote.version),
        decks = bundled.decks + remote.decks,
        stories = bundled.stories + remote.stories,
        starterDeckIds = bundled.starterDeckIds + remote.starterDeckIds,
        starterStoryIds = bundled.starterStoryIds + remote.starterStoryIds
    )
}

internal fun parseDatasetDeck(json: String): List<DatasetDeckRow> =
    Json.parseToJsonElement(json).jsonArray.map { value ->
        val row = value.jsonObject
        val sentence = row.string("s")
        val sourceHash by lazy { sentence.encodeUtf8().sha256().hex() }
        DatasetDeckRow(
            word = row.string("w"),
            meaning = row.string("m"),
            sentence = sentence,
            lesson = row.string("l").takeIf(String::isNotBlank),
            sentenceTranslations = (row["st"] as? JsonObject)?.mapNotNull { (lang, translation) ->
                val source = ((row["stMeta"] as? JsonObject)?.get(lang) as? JsonObject)?.get("source")?.jsonPrimitive?.contentOrNull
                if (source != null && source != sourceHash) null
                else translation.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)?.let { lang to it }
            }?.toMap().orEmpty()
        )
    }

internal fun parseDatasetStories(json: String): List<DatasetStoryRow> =
    Json.parseToJsonElement(json).jsonArray.map { value ->
        val row = value.jsonObject
        DatasetStoryRow(row.string("key"), row.string("title"), row.string("body"),
            row.string("sourceUrl").takeIf(String::isNotBlank), row.string("image").takeIf(String::isNotBlank))
    }

private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun datasetMeaning(meaning: String, lesson: String?, lessonLabel: String = "Lesson"): String =
    lesson?.takeIf { it.isNotBlank() && meaning.isNotBlank() }?.let { "$meaning — $lessonLabel $it" } ?: meaning

/** Uncategorized dataset cards to delete, and ones to move back into their (re-added) deck. */
internal data class DatasetCardCleanup(val deleteIds: Set<Long>, val moveToList: Map<Long, Long>) {
    val removed get() = deleteIds.size
}

/**
 * Uncategorized ([VocabEntry.listId] null, textId 0) copies of dataset deck cards, left behind when a
 * deck list was deleted. [packs] pairs each deck's rows with its current list id (null = list gone).
 * - list gone: the copy is deleted.
 * - list exists: the copy is a duplicate. If it has review progress and the deck's copy has none, it
 *   replaces the deck's copy (keeping progress); otherwise it is deleted.
 * - the legacy swapped form of the old gram-dial-b1-phrases deck (French word, Persian meaning) is
 *   treated the same way, but only merges into a deck that still holds that legacy card; otherwise
 *   (list gone, or the deck was re-added Persian-first) it is deleted.
 */
internal fun planDatasetCardCleanup(
    packs: List<Pair<List<DatasetDeckRow>, Long?>>,
    entries: List<VocabEntry>,
    lessonLabel: String = "Lesson"
): DatasetCardCleanup {
    val deleteIds = mutableSetOf<Long>()
    val moves = mutableMapOf<Long, Long>()
    val loose = entries.filter { it.listId == null && (it.textId == 0L || it.textId == null) }
    packs.forEach { (rows, listId) ->
        val current = HashSet<Pair<String, String>>()
        val legacy = HashSet<Pair<String, String>>()
        rows.forEach { row ->
            current += row.word.lowercase() to datasetMeaning(row.meaning, row.lesson, lessonLabel)
            if (row.sentence.isNotBlank()) legacy += row.sentence.lowercase() to datasetMeaning(row.word, row.lesson, lessonLabel)
        }
        val inDeck = listId?.let { id ->
            entries.filter { it.listId == id }.associateByTo(mutableMapOf()) { it.word.lowercase() }
        }
        loose.forEach { entry ->
            if (entry.id in deleteIds || entry.id in moves) return@forEach
            val key = entry.word.lowercase() to entry.meaning
            val isLegacy = key in legacy
            when {
                !isLegacy && key !in current -> Unit
                listId == null || inDeck == null -> deleteIds += entry.id
                isLegacy && key.first !in inDeck -> deleteIds += entry.id
                else -> {
                    val occupant = inDeck[key.first]
                    if (occupant == null || (entry.hasProgress() && !occupant.hasProgress())) {
                        occupant?.let { deleteIds += it.id }
                        moves[entry.id] = listId
                        inDeck[key.first] = entry
                    } else {
                        deleteIds += entry.id
                    }
                }
            }
        }
    }
    return DatasetCardCleanup(deleteIds, moves)
}

private fun VocabEntry.hasProgress() = lastReviewedAtMs != null || leitnerBox > 1
