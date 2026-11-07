package com.ziaee.zabankhan.dictionary

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.pdf.AppFiles
import com.ziaee.zabankhan.translate.KeyValueCache
import com.ziaee.zabankhan.translate.TranslationRepository
import com.ziaee.zabankhan.translate.translationCacheKey
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

// NFD's Latin accent removal without a JVM normalizer. Ligatures (œ/æ) stay intact.
private val accents = buildMap<Char, Char> {
    listOf("àáâãäåāăąǎ" to 'a', "çćĉċč" to 'c', "ď" to 'd', "èéêëēĕėęě" to 'e',
        "ĝğġģ" to 'g', "ĥ" to 'h', "ìíîïĩīĭįǐ" to 'i', "ĵ" to 'j', "ķ" to 'k',
        "ĺļľ" to 'l', "ñńņň" to 'n', "òóôõöōŏőǒ" to 'o', "ŕŗř" to 'r',
        "śŝşš" to 's', "ţť" to 't', "ùúûüũūŭůűųǔ" to 'u', "ŵẁẃẅ" to 'w',
        "ýÿŷ" to 'y', "źżž" to 'z').forEach { (letters, plain) -> letters.forEach { put(it, plain) } }
}

fun normalizeDictionaryQuery(value: String): String = buildString {
    value.lowercase().forEach { c ->
        if (c.category != CharCategory.NON_SPACING_MARK && c.category != CharCategory.COMBINING_SPACING_MARK &&
            c.category != CharCategory.ENCLOSING_MARK) append(accents[c] ?: if (c == '’') '\'' else c)
    }
}.trim().split(Regex("\\s+")).joinToString(" ")

fun dictionaryShardKey(headword: String): String = normalizeDictionaryQuery(headword)
    .substringBefore(' ').take(2).map { if (it in 'a'..'z') it else '_' }.joinToString("").padEnd(2, '_')

private fun dictionaryWords(value: String): List<String> = normalizeDictionaryQuery(value)
    .map { if (it.isLetterOrDigit() || it in "'_-") it else ' ' }.joinToString("")
    .split(' ').filter { it.any(Char::isLetterOrDigit) }

/** Ordered subsequence matching: every expression word must occur, including repetitions. */
fun dictionaryIdiomMatches(expression: String, query: String): Boolean {
    return orderedIdiomMatch(dictionaryWords(expression), dictionaryWords(query))
}

private fun orderedIdiomMatch(needle: List<String>, query: List<String>): Boolean {
    if (needle.size < 2) return false
    var next = 0
    for (word in query) {
        if (word == needle[next] && ++next == needle.size) return true
    }
    return false
}

@Serializable
data class OwnDictionaryEntry(
    val word: String, val pos: String = "", val rank: Int = Int.MAX_VALUE,
    val ipa: String? = null, val gender: String? = null, val en: List<String>? = null,
    val example: String? = null, val forms: JsonElement? = null, val fa: JsonElement? = null,
) {
    val persian: List<String> get() = fa.dictionaryStrings()
    val formLabels: List<String> get() = forms.dictionaryStrings()
}

private fun JsonElement?.dictionaryStrings(): List<String> = when (this) {
    null, JsonNull -> emptyList()
    is JsonPrimitive -> listOf(content).filter(String::isNotBlank)
    is JsonArray -> flatMap { it.dictionaryStrings() }
    is JsonObject -> entries.flatMap { (key, value) -> value.dictionaryStrings().map { "$key: $it" } }
}

@Serializable
private data class DictionaryIndex(val lang: String, val version: JsonPrimitive, val shards: List<String>)
data class DictionaryMeaning(val entry: OwnDictionaryEntry, val meanings: List<String>, val machineTranslated: Boolean = false, val translations: List<com.ziaee.zabankhan.translate.ExampleTranslation> = emptyList())
data class DictionaryDownload(val completed: Int, val total: Int, val bytes: Long) {
    val complete: Boolean get() = total > 0 && completed == total
}

/** Shards are immutable within an index version. All filesystem/network work runs off the UI thread. */
class OwnDictionary(
    private val http: HttpClient,
    private val cache: KeyValueCache,
    private val root: Path = "${AppFiles.filesDir}/own-dictionary".toPath(),
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private fun directory(lang: LanguageSpec) = root / lang.code
    private fun read(path: Path): String? = if (fs.exists(path)) fs.read(path) { readUtf8() } else null
    private fun write(path: Path, raw: String) {
        fs.createDirectories(requireNotNull(path.parent))
        val temporary = path.parent!! / "${path.name}.tmp"
        fs.write(temporary) { writeUtf8(raw) }
        fs.atomicMove(temporary, path)
    }
    private suspend fun fetch(lang: LanguageSpec, name: String): String {
        val response = http.get("https://ziaeemehr.github.io/zabankhan-dict/${lang.code}/$name.json")
        check(response.status.value in 200..299) { "Dictionary HTTP ${response.status.value}" }
        return response.bodyAsText()
    }
    private suspend fun index(lang: LanguageSpec): DictionaryIndex {
        val file = directory(lang) / "index.json"
        val old = read(file)?.let { runCatching { json.decodeFromString<DictionaryIndex>(it) }.getOrNull() }
        val fresh = try {
            withTimeoutOrNull(5_000) {
                val raw = fetch(lang, "index")
                val parsed = json.decodeFromString<DictionaryIndex>(raw)
                require(parsed.lang == lang.code && parsed.shards.all { it.matches(Regex("[a-z_]{2}")) })
                parsed to raw
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (fresh == null) return old ?: error("Dictionary index unavailable")
        if (old?.version != fresh.first.version) {
            fs.deleteRecursively(directory(lang), mustExist = false)
        }
        write(file, fresh.second)
        return fresh.first
    }
    private suspend fun shard(lang: LanguageSpec, key: String): List<OwnDictionaryEntry> {
        val path = directory(lang) / "$key.json"
        val raw = read(path)
        if (raw != null) runCatching { json.decodeFromString<List<OwnDictionaryEntry>>(raw) }.getOrNull()?.let { return it }
        val downloaded = fetch(lang, key)
        val entries = json.decodeFromString<List<OwnDictionaryEntry>>(downloaded)
        write(path, downloaded)
        return entries
    }
    suspend fun lookup(word: String, lang: LanguageSpec): List<OwnDictionaryEntry> = withContext(Dispatchers.IO) {
        if (!lang.ownDictionaryAvailable || word.isBlank()) return@withContext emptyList()
        mutex.withLock {
            val index = index(lang)
            // An idiom can begin at any word in the selection, not just the first one.
            val normalizedQuery = normalizeDictionaryQuery(word)
            val queryWords = dictionaryWords(word)
            val keys = (queryWords + word).map(::dictionaryShardKey).distinct().filter { it in index.shards }
            val entries = keys.flatMap { key ->
                try { withTimeoutOrNull(15_000) { shard(lang, key) }.orEmpty() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            }
            entries.filter { normalizeDictionaryQuery(it.word) == normalizedQuery ||
                orderedIdiomMatch(dictionaryWords(it.word), queryWords) }.distinctBy { it.word to it.pos }.sortedBy { it.rank }
        }
    }
    suspend fun meaning(entry: OwnDictionaryEntry, lang: LanguageSpec, target: String,
        translations: TranslationRepository): DictionaryMeaning {
        if (target != Languages.PERSIAN.code) return DictionaryMeaning(entry, entry.en.orEmpty())
        if (entry.persian.isNotEmpty()) return DictionaryMeaning(entry, entry.persian)
        // The repository caches each gloss together with its provider. Older dictionary-only
        // caches contain text alone and cannot identify who produced it.
        val translated = entry.en.orEmpty().take(4).mapNotNull { gloss ->
            translations.translateTextWithSource(gloss, Languages.ENGLISH.code, Languages.PERSIAN.code).getOrNull()
                ?.takeIf { it.text.isNotBlank() }
        }
        return DictionaryMeaning(entry, translated.map { it.text }.ifEmpty { entry.en.orEmpty() },
            translated.isNotEmpty(), translated)
    }
    suspend fun downloadAll(lang: LanguageSpec, progress: (DictionaryDownload) -> Unit) = withContext(Dispatchers.IO) {
        require(lang.ownDictionaryAvailable)
        mutex.withLock {
            val index = index(lang)
            var bytes = 0L
            progress(DictionaryDownload(0, index.shards.size, bytes))
            index.shards.forEachIndexed { i, key ->
                checkNotNull(withTimeoutOrNull(15_000) { shard(lang, key) }) { "Dictionary download timed out" }
                bytes += fs.metadata(directory(lang) / "$key.json").size ?: 0L
                progress(DictionaryDownload(i + 1, index.shards.size, bytes))
            }
        }
    }
    suspend fun status(lang: LanguageSpec): DictionaryDownload = withContext(Dispatchers.IO) {
        mutex.withLock {
            val index = read(directory(lang) / "index.json")?.let { runCatching { json.decodeFromString<DictionaryIndex>(it) }.getOrNull() }
            val files = index?.shards.orEmpty().map { directory(lang) / "$it.json" }.filter(fs::exists)
            DictionaryDownload(files.size, index?.shards?.size ?: 0, files.sumOf { fs.metadata(it).size ?: 0L })
        }
    }
    suspend fun delete(lang: LanguageSpec) = withContext(Dispatchers.IO) {
        mutex.withLock { fs.deleteRecursively(directory(lang), mustExist = false) }
    }
}

/** Failed network lookups preserve the existing web/translation fallback; cancellation still propagates. */
suspend fun OwnDictionary.lookupOrEmpty(word: String, lang: LanguageSpec): List<OwnDictionaryEntry> = try {
    lookup(word, lang)
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { emptyList() }

suspend fun OwnDictionary.meaningOrEnglish(entry: OwnDictionaryEntry, lang: LanguageSpec, target: String,
    translations: TranslationRepository): DictionaryMeaning = try {
    meaning(entry, lang, target, translations)
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { DictionaryMeaning(entry, if (target == Languages.PERSIAN.code && entry.persian.isNotEmpty()) entry.persian else entry.en.orEmpty()) }
