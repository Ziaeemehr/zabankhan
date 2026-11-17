package com.ziaee.zabankhan.dictionary

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.lang.LemmaLexicon
import com.ziaee.zabankhan.lang.normalizeForMatch
import com.ziaee.zabankhan.datapack.LanguageDataPacks
import com.ziaee.zabankhan.pdf.AppFiles
import com.ziaee.zabankhan.translate.ExampleSource
import com.ziaee.zabankhan.translate.ExampleTranslation
import com.ziaee.zabankhan.translate.TranslationRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

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
}.trim().split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ")

/** Exact query first, followed by headword candidates with known leading articles removed. */
fun dictionaryLookupCandidates(value: String, lang: LanguageSpec): List<String> {
    val normalized = normalizeDictionaryQuery(value)
    if (normalized.isEmpty()) return emptyList()
    val candidates = linkedSetOf(normalized)
    var rest = normalized
    val articles = lang.dictionaryLeadingArticles.map(::normalizeDictionaryQuery).sortedByDescending { it.length }
    while (rest.isNotEmpty()) {
        val article = articles.firstOrNull { token ->
            if (token.endsWith("'")) rest.startsWith(token)
            else rest.startsWith("$token ")
        } ?: break
        rest = if (article.endsWith("'")) rest.removePrefix(article) else rest.removePrefix("$article ")
        rest = rest.trim()
        if (rest.isNotEmpty()) candidates += rest
    }
    return candidates.toList()
}

fun dictionaryShardKey(word: String, split: Set<String> = emptySet()): String {
    val first = normalizeDictionaryQuery(word).substringBefore(' ')
    var width = 2
    fun key(size: Int) = first.take(size).map { if (it in 'a'..'z') it else '_' }.joinToString("").padEnd(size, '_')
    var result = key(width)
    while (result in split) { width += 1; result = key(width) }
    return result
}

private fun dictionaryWords(value: String): List<String> = normalizeDictionaryQuery(value)
    .map { if (it.isLetterOrDigit() || it in "'_-" ) it else ' ' }.joinToString("")
    .split(' ').filter { it.any(Char::isLetterOrDigit) }

fun dictionaryIdiomMatches(expression: String, query: String): Boolean = orderedIdiomMatch(dictionaryWords(expression), dictionaryWords(query))
private fun orderedIdiomMatch(needle: List<String>, query: List<String>): Boolean {
    if (needle.size < 2) return false
    var next = 0
    for (word in query) if (word == needle[next] && ++next == needle.size) return true
    return false
}

data class Example(val text: String, val en: String, val fa: String)
data class MonolingualSense(val definition: String, val tags: List<String>, val example: String = "")
data class MonolingualEntry(val word: String, val pos: String, val gender: String, val senses: List<MonolingualSense>)
data class LearnerSense(val definition: String, val example: String = "", val register: String = "", val synonyms: List<String> = emptyList(), val antonyms: List<String> = emptyList())
data class LearnerPhrase(val phrase: String, val definition: String)
data class LearnerEntry(val word: String, val pos: String, val senses: List<LearnerSense>, val more: List<String>, val phrases: List<LearnerPhrase>, val pattern: String, val note: String, val family: List<String>)
data class Sense(val en: String, val fa: String, val tags: List<String>, val examples: List<Example>, val synonyms: List<String>)
data class Entry(
    val id: Long, val word: String, val pos: String, val freq: Int, val ipa: String, val gender: String,
    val audio: String, val senses: List<Sense>, val forms: Map<String, List<String>>,
    val expressions: List<String>, val moreExamples: List<String>, val monolingualDefinitions: List<MonolingualSense> = emptyList(),
    val learner: LearnerEntry? = null,
) {
    val rank: Int get() = if (freq > 0) 6 - freq else Int.MAX_VALUE
    val en: List<String> get() = senses.map { it.en }.filter(String::isNotBlank)
    val persian: List<String> get() = senses.map { it.fa }.filter(String::isNotBlank)
    val formLabels: List<String> get() = forms.flatMap { (name, values) -> values.map { "$name: $it" } }
    val example: String? get() = senses.flatMap { it.examples }.firstOrNull()?.let { listOf(it.text, it.en, it.fa).filter(String::isNotBlank).joinToString("\t") }
}
typealias OwnDictionaryEntry = Entry
data class DictionaryRedirect(val form: String, val lemma: String, val pos: String, val labels: List<String>, val entryId: Long)
data class DictionaryLookup(val query: String, val entries: List<Entry>, val via: List<DictionaryRedirect>, val idioms: List<Entry>)
data class SenseMeaning(val text: String, val translation: ExampleTranslation? = null)
data class DictionaryMeaning(val entry: Entry, val meanings: List<String>, val machineTranslated: Boolean = false, val translations: List<ExampleTranslation> = emptyList())
data class DictionaryDownload(val completed: Int, val total: Int, val bytes: Long) { val complete get() = total > 0 && completed == total }

data class DictionaryIndex(
    val lang: String, val format: Int, val version: JsonElement, val entries: Int, val persian: Int,
    val shards: List<String>, val split: Map<String, JsonElement>, val audio_prefixes: List<String>,
)
data class DictionaryShard(val entries: List<Entry>, val redirects: Map<String, List<DictionaryRedirect>>, val targets: Map<String, Pair<String, String>>, val formLabels: List<String>)

private fun JsonElement?.stringOrEmpty() = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonElement?.intOrZero() = (this as? JsonPrimitive)?.intOrNull ?: 0
private fun JsonElement.longOrZero() = (this as? JsonPrimitive)?.longOrNull ?: 0L
private fun JsonElement?.stringList(): List<String> = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private fun JsonArray.at(index: Int): JsonElement = getOrNull(index) ?: JsonNull
private fun JsonElement.asStringList(): List<String> = when (this) {
    is JsonPrimitive -> contentOrNull?.let(::listOf).orEmpty()
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}

fun decodeDictionaryEntry(element: JsonElement, audioPrefixes: List<String> = emptyList()): Entry {
    val row = element as? JsonArray ?: JsonArray(emptyList())
    val audioRaw = row.at(6)
    val audio = when (audioRaw) {
        is JsonArray -> audioPrefixes.getOrNull(audioRaw.getOrNull(0)?.intOrZero() ?: 0).orEmpty() + audioRaw.getOrNull(1).stringOrEmpty()
        is JsonPrimitive -> audioRaw.contentOrNull.orEmpty()
        else -> ""
    }
    val senses = (row.at(7) as? JsonArray).orEmpty().mapNotNull { senseRaw ->
        val s = senseRaw as? JsonArray ?: return@mapNotNull null
        val examples = (s.getOrNull(4) as? JsonArray).orEmpty().mapNotNull { exampleRaw ->
            val e = exampleRaw as? JsonArray ?: return@mapNotNull null
            Example(e.at(0).stringOrEmpty(), e.at(1).stringOrEmpty(), e.at(2).stringOrEmpty())
        }
        Sense(s.at(0).stringOrEmpty(), s.at(1).stringOrEmpty(), s.at(2).stringList(), examples, s.at(3).stringList())
    }
    val forms = (row.at(8) as? JsonObject).orEmpty().mapValues { (_, value) -> value.asStringList() }
    val monolingual = (row.at(11) as? JsonArray).orEmpty().mapNotNull { raw ->
        when (raw) {
            is JsonPrimitive -> raw.contentOrNull?.takeIf(String::isNotBlank)?.let { MonolingualSense(it, emptyList()) }
            is JsonObject -> raw["definition"].stringOrEmpty().takeIf(String::isNotBlank)?.let {
                MonolingualSense(it, raw["tags"].stringList(), raw["example"].stringOrEmpty())
            }
            else -> null
        }
    }
    return Entry(row.at(0).longOrZero(), row.at(1).stringOrEmpty(), row.at(2).stringOrEmpty(), row.at(3).intOrZero(),
        row.at(4).stringOrEmpty(), row.at(5).stringOrEmpty(), audio, senses, forms, row.at(9).stringList(), row.at(10).stringList(), monolingual)
}

fun decodeDictionaryShard(element: JsonElement, audioPrefixes: List<String> = emptyList()): DictionaryShard {
    val root = element as? JsonObject ?: JsonObject(emptyMap())
    val labels = root["form_labels"].stringList()
    val targets = (root["targets"] as? JsonObject).orEmpty().mapNotNull { (id, target) ->
        val pair = target as? JsonArray ?: return@mapNotNull null
        id to (pair.at(0).stringOrEmpty() to pair.at(1).stringOrEmpty())
    }.toMap()
    val redirects = (root["redirects"] as? JsonObject).orEmpty().mapValues { (form, list) ->
        (list as? JsonArray).orEmpty().mapNotNull { raw ->
            val r = raw as? JsonArray ?: return@mapNotNull null
            val id = r.at(0).longOrZero()
            val target = targets[id.toString()] ?: return@mapNotNull null
            val labelCodes = labels.getOrNull(r.at(1).intOrZero())?.split(';')?.filter(String::isNotBlank).orEmpty()
            DictionaryRedirect(form, target.first, target.second, labelCodes, id)
        }
    }
    val entries = (root["entries"] as? JsonArray).orEmpty().map { decodeDictionaryEntry(it, audioPrefixes) }
    return DictionaryShard(entries, redirects, targets, labels)
}

fun decodeMonolingualShard(element: JsonElement): List<MonolingualEntry> =
    (element.jsonObject["entries"] as? JsonArray).orEmpty().mapNotNull { raw ->
        val row = raw as? JsonObject ?: return@mapNotNull null
        val senses = (row["senses"] as? JsonArray).orEmpty().mapNotNull { value ->
            val sense = value as? JsonObject ?: return@mapNotNull null
            val definition = sense["definition"].stringOrEmpty()
            if (definition.isBlank()) null else MonolingualSense(definition, sense["tags"].stringList(), sense["example"].stringOrEmpty())
        }
        if (senses.isEmpty()) null else MonolingualEntry(row["word"].stringOrEmpty(), row["pos"].stringOrEmpty(), row["gender"].stringOrEmpty(), senses)
    }

fun decodeLearnerShard(element: JsonElement): List<LearnerEntry> =
    ((element as? JsonObject)?.get("entries") as? JsonArray).orEmpty().mapNotNull { raw ->
        val row = raw as? JsonObject ?: return@mapNotNull null
        val senses = (row["senses"] as? JsonArray).orEmpty().mapNotNull { value ->
            val sense = value as? JsonObject ?: return@mapNotNull null
            val definition = sense["d"].stringOrEmpty()
            if (definition.isBlank()) null else LearnerSense(definition, sense["ex"].stringOrEmpty(), sense["reg"].stringOrEmpty(), sense["syn"].stringList(), sense["ant"].stringList())
        }
        if (senses.isEmpty()) null else LearnerEntry(row["word"].stringOrEmpty(), row["pos"].stringOrEmpty(), senses,
            row["more"].stringList(), (row["phrases"] as? JsonArray).orEmpty().mapNotNull { phrase ->
                val p = phrase as? JsonObject ?: return@mapNotNull null
                LearnerPhrase(p["p"].stringOrEmpty(), p["d"].stringOrEmpty())
            }, row["pattern"].stringOrEmpty(), row["note"].stringOrEmpty(), row["family"].stringList())
    }

fun lookupMonolingualDefinitions(word: String, lexicon: LemmaLexicon, entries: List<MonolingualEntry>, lang: LanguageSpec): List<MonolingualEntry> {
    val normalized = normalizeForMatch(word, lang)
    val lemmas = lexicon.lemmas(normalized).map { normalizeForMatch(it, lang) }.toSet() + normalized
    return entries.filter { normalizeForMatch(it.word, lang) in lemmas }
}

fun resolveDictionaryRedirects(shard: DictionaryShard, lemmaShard: DictionaryShard?): List<Entry> = shard.redirects.values.flatten().mapNotNull { redirect ->
    lemmaShard?.entries?.firstOrNull { it.id == redirect.entryId && it.word == redirect.lemma && it.pos == redirect.pos }
}.distinctBy { it.id }

fun formLabelTokens(code: String): List<List<String>> = code.split(';').filter(String::isNotBlank).map { analysis ->
    analysis.split('.').flatMap { part ->
        if (part.contains(',')) {
            val bits = part.split(',')
            listOf(bits.first()) + bits.drop(1)
        } else listOf(part)
    }
}
private val formNames = mapOf("ind" to "indicative", "subj" to "subjunctive", "cond" to "conditional", "imp" to "imperative",
    "pres" to "present", "impf" to "imperfect", "fut" to "future", "past" to "past", "pp" to "past participle", "ppr" to "present participle", "inf" to "infinitive",
    "1s" to "1st singular", "2s" to "2nd singular", "3s" to "3rd singular", "1p" to "1st plural", "2p" to "2nd plural", "3p" to "3rd plural",
    "1" to "1st person", "2" to "2nd person", "3" to "3rd person", "m" to "masculine", "f" to "feminine", "sg" to "singular", "pl" to "plural", "refl" to "reflexive", "form" to "form")
fun formLabelText(code: String, nameOf: (String) -> String = { formNames[it] ?: it }): String = formLabelTokens(code).joinToString("; ") { it.joinToString(" ") { token -> nameOf(token) } }

private fun shardKeyInShards(index: DictionaryIndex, key: String) = key in index.shards

class OwnDictionary(private val http: HttpClient, @Suppress("UNUSED_PARAMETER") cache: com.ziaee.zabankhan.translate.KeyValueCache,
    private val root: Path = "${AppFiles.filesDir}/own-dictionary".toPath(), private val fs: FileSystem = FileSystem.SYSTEM) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val checkedPacks = mutableSetOf<String>()
    private fun directory(lang: LanguageSpec) = root / lang.code / "v2"
    private fun packDirectory(lang: LanguageSpec, pack: String) = root / lang.code / pack.substringAfter('/') / "v1"
    private fun read(path: Path): String? = if (fs.exists(path)) fs.read(path) { readUtf8() } else null
    private fun write(path: Path, raw: String) { fs.createDirectories(requireNotNull(path.parent)); val tmp = path.parent!! / "${path.name}.tmp"; fs.write(tmp) { writeUtf8(raw) }; fs.atomicMove(tmp, path) }
    private suspend fun fetch(lang: LanguageSpec, name: String): String {
        val response = http.get("https://ziaeemehr.github.io/zabankhan-dict/${lang.code}/v2/$name.json")
        check(response.status.value in 200..299) { "Dictionary HTTP ${response.status.value}" }
        return response.bodyAsText()
    }
    private suspend fun index(lang: LanguageSpec): DictionaryIndex {
        val file = directory(lang) / "index.json"
        val oldRaw = read(file)
        val freshRaw = try { fetch(lang, "index") } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (freshRaw == null) return oldRaw?.let { raw ->
            val obj = json.parseToJsonElement(raw).jsonObject
            DictionaryIndex(obj["lang"].stringOrEmpty(), obj["format"].intOrZero(), obj["version"] ?: JsonNull,
                obj["entries"]?.intOrZero() ?: 0, obj["persian"]?.intOrZero() ?: 0,
                obj["shards"].stringList(),
                (obj["split"] as? JsonObject).orEmpty(), obj["audio_prefixes"].stringList())
        } ?: error("Dictionary index unavailable")
        val freshObject = json.parseToJsonElement(freshRaw).jsonObject
        val fresh = DictionaryIndex(
            lang = freshObject["lang"].stringOrEmpty(),
            format = freshObject["format"].intOrZero(),
            version = freshObject["version"] ?: JsonNull,
            entries = freshObject["entries"].intOrZero(),
            persian = freshObject["persian"].intOrZero(),
            shards = freshObject["shards"].stringList(),
            split = (freshObject["split"] as? JsonObject).orEmpty(),
        audio_prefixes = freshObject["audio_prefixes"].stringList(),
        )
        require(fresh.lang == lang.code && fresh.format == 2)
        val oldVersion = oldRaw?.let { raw -> runCatching { json.parseToJsonElement(raw).jsonObject["version"] }.getOrNull() }
        if (oldVersion != fresh.version) fs.deleteRecursively(directory(lang), mustExist = false)
        write(directory(lang) / "index.json", freshRaw)
        return fresh
    }
    private suspend fun shard(lang: LanguageSpec, index: DictionaryIndex, key: String): DictionaryShard {
        require(shardKeyInShards(index, key))
        val path = directory(lang) / "$key.json"
        read(path)?.let { cached -> runCatching { decodeDictionaryShard(json.parseToJsonElement(cached), index.audio_prefixes) }.getOrNull()?.let { return it } }
        val raw = fetch(lang, key)
        val result = decodeDictionaryShard(json.parseToJsonElement(raw), index.audio_prefixes)
        write(path, raw)
        return result
    }
    private suspend fun safeShard(lang: LanguageSpec, index: DictionaryIndex, key: String): DictionaryShard? = try { shard(lang, index, key) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }

    private suspend fun packIndex(lang: LanguageSpec, pack: String): List<String>? {
        val base = packDirectory(lang, pack)
        val indexFile = base / "index.json"
        val oldIndex = read(indexFile)
        // One version check per pack per session; later lookups read the cached index without network.
        val freshIndex = if (oldIndex != null && pack in checkedPacks) null else try {
            val response = http.get("https://ziaeemehr.github.io/zabankhan-dict/$pack/v1/index.json")
            if (response.status.value in 200..299) response.bodyAsText() else null
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        val indexRaw = freshIndex ?: oldIndex ?: return null
        checkedPacks += pack  // also when offline: do not retry the network on every lookup
        if (freshIndex != null) {
            val previousVersion = oldIndex?.let { runCatching { json.parseToJsonElement(it).jsonObject["version"] }.getOrNull() }
            val currentVersion = runCatching { json.parseToJsonElement(freshIndex).jsonObject["version"] }.getOrNull()
            if (previousVersion != null && previousVersion != currentVersion) {
                fs.list(base).filter { it.name != "index.json" }.forEach { fs.delete(it) }
            }
            write(indexFile, freshIndex)
        }
        val index = runCatching { json.parseToJsonElement(indexRaw).jsonObject }.getOrNull() ?: return null
        if (index["lang"].stringOrEmpty() != lang.code || index["format"].intOrZero() != 1) return null
        return index["shards"].stringList()
    }

    private suspend fun packShard(lang: LanguageSpec, pack: String, key: String): String? {
        val path = packDirectory(lang, pack) / "$key.json"
        val cached = read(path)
        if (cached != null) {
            val valid = runCatching { json.parseToJsonElement(cached).jsonObject["entries"] as? JsonArray }.getOrNull()
            if (valid != null) return cached
        }
        val response = http.get("https://ziaeemehr.github.io/zabankhan-dict/$pack/v1/$key.json")
        if (response.status.value !in 200..299) return null
        return response.bodyAsText().also { write(path, it) }
    }

    private suspend fun packEntries(lang: LanguageSpec, pack: String?, word: String): List<MonolingualEntry> {
        pack ?: return emptyList()
        val lexicon = LanguageDataPacks.load(lang.code)?.lemmas ?: return emptyList()
        val available = packIndex(lang, pack)?.toSet() ?: return emptyList()
        val candidates = (lexicon.lemmas(normalizeForMatch(word, lang)) + normalizeForMatch(word, lang)).distinct()
        val entries = candidates.flatMap { candidate ->
            val key = dictionaryShardKey(candidate)
            if (key !in available) return@flatMap emptyList()
            val raw = try { packShard(lang, pack, key) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } ?: return@flatMap emptyList()
            runCatching { decodeMonolingualShard(json.parseToJsonElement(raw)) }.getOrDefault(emptyList())
        }
        return lookupMonolingualDefinitions(word, lexicon, entries, lang)
    }

    // Looks up by the resolved headwords, not the tapped form: "abandonne" must find "abandonner".
    private suspend fun learnerEntries(lang: LanguageSpec, headwords: List<String>): List<LearnerEntry> {
        val pack = lang.learnerPackId ?: return emptyList()
        if (headwords.isEmpty()) return emptyList()
        val available = packIndex(lang, pack)?.toSet() ?: return emptyList()
        val wanted = headwords.map(::normalizeDictionaryQuery).toSet()
        val keys = headwords.map(::dictionaryShardKey).distinct().filter { it in available }
        return keys.flatMap { key ->
            val raw = try { packShard(lang, pack, key) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } ?: return@flatMap emptyList()
            runCatching { decodeLearnerShard(json.parseToJsonElement(raw)) }.getOrDefault(emptyList())
        }.filter { normalizeDictionaryQuery(it.word) in wanted }
    }

    suspend fun lookup(word: String, lang: LanguageSpec, sentence: String? = null): DictionaryLookup = withContext(Dispatchers.IO) {
        if (!lang.ownDictionaryAvailable || word.isBlank()) return@withContext DictionaryLookup(word, emptyList(), emptyList(), emptyList())
        mutex.withLock {
            val index = index(lang)
            val splitParents = index.split.keys
            val candidates = dictionaryLookupCandidates(word, lang)
            val exact = mutableListOf<Entry>()
            val redirects = mutableListOf<DictionaryRedirect>()
            candidates.forEach { query ->
                val key = dictionaryShardKey(query, splitParents)
                val queryShard = if (key in index.shards) shard(lang, index, key) else null
                exact += queryShard?.entries.orEmpty().filter { normalizeDictionaryQuery(it.word) == query }
                redirects += queryShard?.redirects?.entries?.firstOrNull { normalizeDictionaryQuery(it.key) == query }?.value.orEmpty()
            }
            val resolved = redirects.distinctBy { it.entryId to it.pos }.mapNotNull { redirect ->
                val key = dictionaryShardKey(redirect.lemma, splitParents)
                val targetShard = if (key in index.shards) shard(lang, index, key) else null
                targetShard?.entries?.firstOrNull { it.id == redirect.entryId && it.pos == redirect.pos }
            }.distinctBy { it.id }
            // Same headword, several POS: the richer entry leads (e.g. "contre" preposition, not its one-sense bridge noun "double").
            val bases = (exact.sortedByDescending { it.senses.size } + resolved).distinctBy { it.id }
            val idiomWords = sentence?.let(::dictionaryWords).orEmpty()
            val idiomNames = bases.flatMap { it.expressions }.filter { dictionaryIdiomMatches(it, sentence.orEmpty()) }.distinct()
            val idioms = idiomNames.mapNotNull { expression ->
                val key = dictionaryShardKey(expression, splitParents)
                if (key !in index.shards) null else safeShard(lang, index, key)?.entries?.firstOrNull { normalizeDictionaryQuery(it.word) == normalizeDictionaryQuery(expression) }
            }.distinctBy { it.id }.filter { entry -> idiomWords.isNotEmpty() && dictionaryIdiomMatches(entry.word, sentence.orEmpty()) }
            val definitions = try { packEntries(lang, lang.monolingualPackId, word) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
            val learnerEntries = try { learnerEntries(lang, bases.map { it.word }.distinct()) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
            val enriched = bases.map { entry ->
                val mono = definitions.filter { normalizeDictionaryQuery(it.word) == normalizeDictionaryQuery(entry.word) }
                val headwordMatches = learnerEntries.filter { normalizeDictionaryQuery(it.word) == normalizeDictionaryQuery(entry.word) }
                val learner = headwordMatches.firstOrNull { it.pos == entry.pos }
                entry.copy(gender = entry.gender.ifBlank { mono.firstOrNull()?.gender.orEmpty() }, monolingualDefinitions = mono.flatMap { it.senses }, learner = learner)
            }
            DictionaryLookup(word, enriched, redirects, idioms)
        }
    }
    suspend fun search(prefix: String, lang: LanguageSpec, limit: Int = 30): List<Entry> = withContext(Dispatchers.IO) {
        if (!lang.ownDictionaryAvailable || prefix.isBlank() || limit <= 0) return@withContext emptyList()
        mutex.withLock {
            val index = index(lang)
            val results = dictionaryLookupCandidates(prefix, lang).flatMap { query ->
                val key = dictionaryShardKey(query, index.split.keys)
                if (key !in index.shards) return@flatMap emptyList()
                val shard = shard(lang, index, key)
                val exact = shard.entries.filter { normalizeDictionaryQuery(it.word) == query }
                val redirectEntries = shard.redirects.filterKeys { normalizeDictionaryQuery(it) == query }.values.flatten().mapNotNull { redirect ->
                    val lemmaKey = dictionaryShardKey(redirect.lemma, index.split.keys)
                    if (lemmaKey !in index.shards) null else shard(lang, index, lemmaKey).entries.firstOrNull { it.id == redirect.entryId && it.pos == redirect.pos }
                }
                val redirects = shard.redirects.filterKeys { normalizeDictionaryQuery(it).startsWith(query) }.values.flatten().mapNotNull { redirect ->
                    val lemmaKey = dictionaryShardKey(redirect.lemma, index.split.keys)
                    if (lemmaKey !in index.shards) null else shard(lang, index, lemmaKey).entries.firstOrNull { it.id == redirect.entryId && it.pos == redirect.pos }
                }
                exact + redirectEntries + redirects + shard.entries.filter { normalizeDictionaryQuery(it.word).startsWith(query) }
            }
            results.distinctBy { it.id }.sortedBy { it.freq.takeIf { f -> f > 0 }?.let { 6 - it } ?: Int.MAX_VALUE }.take(limit)
        }
    }
    suspend fun senseMeanings(entry: Entry, target: String, translations: TranslationRepository): List<SenseMeaning> = entry.senses.map { sense ->
        if (target != Languages.PERSIAN.code) SenseMeaning(sense.en)
        // Pack Persian meanings are generated automatically, without human review.
        else if (sense.fa.isNotBlank()) SenseMeaning(sense.fa, ExampleTranslation(sense.fa, ExampleSource.MODEL))
        else if (sense.en.isNotBlank()) {
            val translated = translations.translateTextWithSource(sense.en, Languages.ENGLISH.code, target).getOrNull()?.takeIf { it.text.isNotBlank() }
            SenseMeaning(translated?.text ?: sense.en, translated)
        } else SenseMeaning("")
    }
    suspend fun meaning(entry: Entry, lang: LanguageSpec, target: String, translations: TranslationRepository): DictionaryMeaning {
        val meanings = senseMeanings(entry, target, translations)
        return DictionaryMeaning(entry, meanings.map { it.text }.filter(String::isNotBlank), meanings.any { it.translation?.source in setOf(ExampleSource.MODEL, ExampleSource.GOOGLE) }, meanings.mapNotNull { it.translation })
    }
    suspend fun downloadAll(lang: LanguageSpec, onProgress: (done: Int, total: Int) -> Unit) = withContext(Dispatchers.IO) {
        require(lang.ownDictionaryAvailable)
        val idx = mutex.withLock { index(lang) }
        val progressMutex = Mutex()
        var done = 0
        var failed = 0
        onProgress(0, idx.shards.size)
        coroutineScope {
            repeat(4) { worker ->
                launch {
                    for (i in worker until idx.shards.size step 4) {
                        var succeeded = false
                        for (attempt in 0..1) {
                            try {
                                shard(lang, idx, idx.shards[i]) // different keys, atomic writes: safe in parallel
                                succeeded = true
                                break
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Retry once, then continue so other shards can still be cached.
                            }
                        }
                        progressMutex.withLock {
                            if (!succeeded) failed++
                            onProgress(++done, idx.shards.size)
                        }
                    }
                }
            }
        }
        check(failed == 0) { "Dictionary download failed: $failed shards unavailable" }
    }

    suspend fun downloadAllMonolingual(lang: LanguageSpec, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        require(lang.monolingualPackId != null)
        val packs = listOfNotNull(lang.monolingualPackId, lang.learnerPackId)
        val keys = mutex.withLock { packs.associateWith { pack -> packIndex(lang, pack) ?: error("Dictionary index unavailable: $pack") } }
        val total = keys.values.sumOf { it.size }
        val progressMutex = Mutex()
        var done = 0
        var failed = 0
        onProgress(0, total)
        coroutineScope {
            val jobs = packs.flatMap { pack -> keys.getValue(pack).map { pack to it } }
            repeat(4) { worker -> launch {
                for (i in worker until jobs.size step 4) {
                    var succeeded = false
                    for (attempt in 0..1) try {
                        if (packShard(lang, jobs[i].first, jobs[i].second) != null) { succeeded = true; break }
                    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                    progressMutex.withLock { if (!succeeded) failed++; onProgress(++done, total) }
                }
            } }
        }
        check(failed == 0) { "Dictionary download failed: $failed shards unavailable" }
    }


    suspend fun status(lang: LanguageSpec): DictionaryDownload = withContext(Dispatchers.IO) { mutex.withLock {
        val idx = read(directory(lang) / "index.json")?.let { raw -> runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            DictionaryIndex(obj["lang"].stringOrEmpty(), obj["format"].intOrZero(), obj["version"] ?: JsonNull,
                obj["entries"].intOrZero(), obj["persian"].intOrZero(),
                obj["shards"].stringList(),
                (obj["split"] as? JsonObject).orEmpty(), obj["audio_prefixes"].stringList())
        }.getOrNull() }
        val files = idx?.shards.orEmpty().map { directory(lang) / "$it.json" }.filter(fs::exists)
        DictionaryDownload(files.size, idx?.shards?.size ?: 0, files.sumOf { fs.metadata(it).size ?: 0L })
    } }
    suspend fun monolingualStatus(lang: LanguageSpec): DictionaryDownload = withContext(Dispatchers.IO) { mutex.withLock {
        val packs = listOfNotNull(lang.monolingualPackId, lang.learnerPackId)
        val keysByPack = packs.associateWith { pack ->
            val base = packDirectory(lang, pack)
            read(base / "index.json")?.let { raw -> runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            if (obj["lang"].stringOrEmpty() == lang.code && obj["format"].intOrZero() == 1) obj["shards"].stringList() else emptyList()
            }.getOrNull() }.orEmpty()
        }
        val files = keysByPack.flatMap { (pack, keys) -> keys.map { packDirectory(lang, pack) / "$it.json" } }.filter(fs::exists)
        val total = keysByPack.values.sumOf { it.size }
        DictionaryDownload(files.size, total, files.sumOf { fs.metadata(it).size ?: 0L })
    } }
    suspend fun deleteMonolingual(lang: LanguageSpec) = withContext(Dispatchers.IO) { mutex.withLock { listOfNotNull(lang.monolingualPackId, lang.learnerPackId).forEach { fs.deleteRecursively(packDirectory(lang, it), mustExist = false) } } }
    suspend fun delete(lang: LanguageSpec) = withContext(Dispatchers.IO) { mutex.withLock { fs.deleteRecursively(directory(lang), mustExist = false) } }
}

suspend fun OwnDictionary.lookupOrEmpty(word: String, lang: LanguageSpec, sentence: String? = null): DictionaryLookup = try { lookup(word, lang, sentence) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { DictionaryLookup(word, emptyList(), emptyList(), emptyList()) }
suspend fun OwnDictionary.meaningOrEnglish(entry: Entry, lang: LanguageSpec, target: String, translations: TranslationRepository): DictionaryMeaning = try {
    meaning(entry, lang, target, translations)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    val persian = entry.persian.takeIf { target == Languages.PERSIAN.code }.orEmpty()
    DictionaryMeaning(entry, persian.ifEmpty { entry.en }, machineTranslated = persian.isNotEmpty(),
        translations = persian.map { ExampleTranslation(it, ExampleSource.MODEL) })
}
