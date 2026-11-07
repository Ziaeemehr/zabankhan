package com.ziaee.zabankhan.content

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.normalizeForMatch
import com.ziaee.zabankhan.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private fun sentenceKey(text: String, language: String, source: LanguageSpec) =
    normalizeForMatch(text, source).replace(Regex("\\s+"), " ").trim() to
        language.substringBefore('-').substringBefore('_').lowercase()

/** Sentence text and meaning language identify translations inside a learning-language pack. */
class BundledSentenceTranslations internal constructor(private val source: LanguageSpec, load: () -> List<DatasetDeckRow>) {
    private val index by lazy {
        buildMap<Pair<String, String>, String> {
            load().filter { it.sentence.isNotBlank() }.forEach { row ->
                row.sentenceTranslations.forEach { (language, value) ->
                    val key = sentenceKey(row.sentence, language, source)
                    if (key !in this) put(key, value)
                }
            }
        }
    }
    fun get(sentence: String, language: String): String? = index[sentenceKey(sentence, language, source)]

    companion object {
        suspend fun forLanguage(source: LanguageSpec): BundledSentenceTranslations = withContext(Dispatchers.IO) {
            val rows = try {
                val root = "files/${source.code}/dataset/"
                val manifest = parseDatasetManifest(Res.readBytes(root + "manifest.json").decodeToString())
                manifest.decks.flatMap { parseDatasetDeck(Res.readBytes(root + it.file).decodeToString()) }
            } catch (_: org.jetbrains.compose.resources.MissingResourceException) { emptyList() }
            BundledSentenceTranslations(source) { rows }
        }
    }
}
