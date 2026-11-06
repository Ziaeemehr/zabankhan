package com.ziaee.zabankhan.datapack

import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.watch.*
import com.ziaee.zabankhan.grammar.*
import com.ziaee.zabankhan.exams.*
import com.ziaee.zabankhan.resources.Res
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shortcut: move to downloadable packs per language when a second language gets data. */
private val bundledPacks = mapOf(Languages.FRENCH.code to "files/fr")
data class LanguageDataPack(val lemmas: LemmaLexicon, val idioms: InMemoryIdiomLexicon,
    val grammar: List<GrammarNote>, val questions: ExamQuestionSet, val posters: List<GrammarPoster> = emptyList(), val grammarLabels: Map<String, String> = emptyMap())
object LanguageDataPacks {
    private val mutex = Mutex()
    private val loaded = mutableMapOf<String, LanguageDataPack>()
    fun available(lang: String) = lang in bundledPacks
    suspend fun load(lang: String): LanguageDataPack? = mutex.withLock {
        loaded[lang]?.let { return@withLock it }
        val root = bundledPacks[lang] ?: return@withLock null
        val spec = requireNotNull(Languages.byCode(lang))
        withContext(Dispatchers.IO) {
            suspend fun read(file: String) = Res.readBytes("$root/$file").decodeToString()
            LanguageDataPack(loadLemmaLexicon(read("lexicon/lemmas.tsv"), read("lexicon/common_lemmas.txt"), spec),
                loadIdiomLexicon(read("lexicon/idioms.tsv"), spec), parseGrammarNotes(read("grammar/notes.json"), spec),
                parseExamQuestions(read("exams/tcf-questions.json")), parseGrammarPosters(read("grammar/posters.json"), spec),
                grammarLabels = Json.parseToJsonElement(read("grammar/labels.json")).jsonObject.mapValues { it.value.jsonPrimitive.content }).also { loaded[lang] = it }
        }
    }
}
