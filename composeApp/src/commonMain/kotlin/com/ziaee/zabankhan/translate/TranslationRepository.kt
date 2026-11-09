package com.ziaee.zabankhan.translate

import com.ziaee.zabankhan.lang.Languages

import com.ziaee.zabankhan.translate.llm.LlmTranslator
import com.ziaee.zabankhan.translate.llm.LlmCache
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** One dictionary group in the target language. */
data class MeaningSense(val pos: String, val meanings: List<String>, val engine: String? = null)
data class WordMeaning(val primary: String, val senses: List<MeaningSense>, val engine: String)

class TranslationRepository(private val http: HttpClient, private val cache: KeyValueCache, private val llm: LlmTranslator?) {
    var cloudEnabled: Boolean = true
    private val overrides = TranslationOverrides(cache)
    private val translations = TranslationCache(cache)
    private val modelCache = LlmCache(cache)
    private val bundledSentences = mutableMapOf<String, com.ziaee.zabankhan.content.BundledSentenceTranslations>()
    private val engines = listOf("google-gtx", "google-clients5", "mymemory")

    suspend fun translateText(text: String, source: String, target: String, sentenceContext: String? = null): Result<String> =
        translateTextWithSource(text, source, target, sentenceContext).map { it.text }

    suspend fun googleDictionaryFallback(text: String, source: String, target: String): Result<ExampleTranslation> = result {
        for (engine in listOf("google-gtx", "google-clients5")) {
            translations.get(text, source, target, engine)?.let { return@result ExampleTranslation(it, ExampleSource.GOOGLE, engine = engine) }
        }
        val service = TranslationService(http)
        val value = service.translateWithGoogle(text, target, source)
        translations.put(text, source, target, service.lastEngine, value)
        ExampleTranslation(value, ExampleSource.GOOGLE, engine = service.lastEngine)
    }

    suspend fun translateTextWithSource(text: String, source: String, target: String, sentenceContext: String? = null): Result<ExampleTranslation> = result {
        overrides.getOverride(text, source, target)?.let { return@result ExampleTranslation(it, ExampleSource.EDITED) }
        Languages.byCode(TranslationService.baseLanguage(source))?.let { spec ->
            val bundled = bundledSentences[spec.code] ?: com.ziaee.zabankhan.content.BundledSentenceTranslations.forLanguage(spec)
                .also { bundledSentences[spec.code] = it }
            bundled.get(text, target)?.let { return@result ExampleTranslation(it, ExampleSource.BUNDLED) }
        }
        if (TranslationService.baseLanguage(source) == TranslationService.baseLanguage(target)) return@result ExampleTranslation(text, ExampleSource.EDITED)
        if (llm != null) {
            modelCache.get(text, source, target, contextCacheId(llm.cacheId, sentenceContext))?.let { return@result ExampleTranslation(it, ExampleSource.MODEL, engine = llm.cacheId) }
            if (cloudEnabled) {
            try {
                val value = llm.translateLines(listOf(text), sentenceContext, null, target, source).single()
                modelCache.put(text, source, target, contextCacheId(llm.cacheId, sentenceContext), value)
                return@result ExampleTranslation(value, ExampleSource.MODEL, engine = llm.cacheId)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Continue through the free providers. */ }
            }
        }
        for (engine in engines) translations.get(text, source, target, engine)?.let { return@result ExampleTranslation(it, ExampleSource.GOOGLE, engine = engine) }
        val service = TranslationService(http)
        val value = service.translate(text, target, source).also { translations.put(text, source, target, service.lastEngine, it) }
        ExampleTranslation(value, ExampleSource.GOOGLE, engine = service.lastEngine)
    }

    private fun contextCacheId(engine: String, context: String?): String =
        if (context.isNullOrBlank()) engine else "$engine:context:${context.length}:$context"

    suspend fun lookupWord(word: String, sentence: String?, source: String, target: String): Result<WordMeaning> = result {
        overrides.getOverride(word, source, target)?.let { return@result WordMeaning(it, emptyList(), "override") }
        if (TranslationService.baseLanguage(source) == TranslationService.baseLanguage(target))
            return@result WordMeaning(word, emptyList(), "identity")
        if (llm != null) {
            modelCache.getMeaning(word, sentence, source, target, llm.cacheId)?.let {
                return@result WordMeaning(it, listOf(MeaningSense("", listOf(it))), llm.cacheId)
            }
            if (cloudEnabled) {
            try {
                val value = llm.meaning(word, sentence, target, source)
                modelCache.putMeaning(word, sentence, source, target, llm.cacheId, value)
                val alternatives = try { googleMeaning(word, sentence, source, target).let { value -> value.senses.map { it.copy(engine = value.engine) } } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                val seen = mutableSetOf(normalizePersian(value))
                val extra = alternatives.mapNotNull { sense ->
                    sense.meanings.filter { seen.add(normalizePersian(it)) }.takeIf { it.isNotEmpty() }
                        ?.let { MeaningSense(sense.pos, it, sense.engine) }
                }
                return@result WordMeaning(value, listOf(MeaningSense("", listOf(value))) + extra, llm.cacheId)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Continue through the free providers. */ }
            }
        }
        googleMeaning(word, sentence, source, target)
    }

    private suspend fun googleMeaning(word: String, sentence: String?, source: String, target: String): WordMeaning {
        val request = "${word.length}:$word${sentence?.let { "${it.length}:$it" } ?: "null"}"
        for (engine in engines) cache.get(translationCacheKey("meaning:$engine", source, target, request))?.let {
            return decodeMeaning(it)
        }
        val service = TranslationService(http)
        val direct = TranslationService.baseLanguage(target) == Languages.ENGLISH.code || TranslationService.baseLanguage(source) == Languages.ENGLISH.code
        val senseTarget = if (direct) target else Languages.ENGLISH.code
        val senses = filterWordSenses(service.wordSenses(word, senseTarget, source))
        val sentenceTranslation = sentence?.takeIf { senses.isNotEmpty() }?.takeIf { it.isNotBlank() }?.let {
            if (TranslationService.baseLanguage(source) == senseTarget) it else translateText(it, source, senseTarget).getOrNull()
        }
        val guessedPos = if (TranslationService.baseLanguage(source) == Languages.FRENCH.code) sentence?.let { guessFrenchPartOfSpeech(it, word) } else null
        val ranked = rankWordSenses(senses, sentenceTranslation, guessedPos).take(6)
        val options = try { ranked.mapNotNull { sense ->
            val translated = if (direct) sense.term else {
                val dictionary = service.wordSenses(sense.term, target, Languages.ENGLISH.code)
                val headTerm = if (dictionary.isEmpty() && sense.term.trim().split(Regex("\\s+")).size > 1) {
                    englishSenseHeadWord(sense.term)?.let { head ->
                        selectDictionaryTerm(service.wordSenses(head, target, Languages.ENGLISH.code)
                            .filter { it.partOfSpeech.equals(sense.partOfSpeech, true) }, sense.partOfSpeech)
                    }
                } else null
                selectDictionaryTerm(dictionary, sense.partOfSpeech) ?: headTerm ?: if (allowPlainSenseTranslation(sense.term)) {
                    service.translate(sense.term, target, Languages.ENGLISH.code)
                } else null
            }
            translated?.takeIf { it.isNotBlank() }?.let {
                sense.partOfSpeech to polishMeaning(word, it, source, target, sense.partOfSpeech)
            }
        }.distinctBy { normalizePersian(it.second) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { emptyList() }
        val value = if (options.isEmpty()) {
            val translated = service.translate(word, target, source)
            WordMeaning(polishMeaning(word, translated, source, target), emptyList(), service.lastEngine)
        } else WordMeaning(options.first().second,
            options.groupBy({ it.first }, { it.second }).map { MeaningSense(it.key, it.value) }, service.lastEngine)
        cache.put(translationCacheKey("meaning:${value.engine}", source, target, request), encodeMeaning(value))
        return value
    }
}

private suspend fun <T> result(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }

private fun encodeMeaning(value: WordMeaning): String = buildJsonObject {
    put("primary", value.primary); put("engine", value.engine)
    putJsonArray("senses") { value.senses.forEach { sense -> add(buildJsonObject {
        sense.engine?.let { put("engine", it) }; put("pos", sense.pos); put("meanings", JsonArray(sense.meanings.map(::JsonPrimitive)))
    }) } }
}.toString()
private fun decodeMeaning(raw: String): WordMeaning {
    val value = Json.parseToJsonElement(raw).jsonObject
    return WordMeaning(value.getValue("primary").jsonPrimitive.content,
        value.getValue("senses").jsonArray.map {
            val sense = it.jsonObject
            MeaningSense(sense.getValue("pos").jsonPrimitive.content, sense.getValue("meanings").jsonArray.map { term -> term.jsonPrimitive.content }, sense["engine"]?.jsonPrimitive?.contentOrNull)
        }, value.getValue("engine").jsonPrimitive.content)
}
