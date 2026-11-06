package com.ziaee.zabankhan.datapack

import com.ziaee.zabankhan.exams.parseExamQuestions
import com.ziaee.zabankhan.grammar.parseGrammarNotes
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.watch.*
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSProcessInfo
import kotlin.test.*

/** Unit-test executables have no Compose resource bundle; use the existing host fixture root. */
class BundledDataPackTest {
    private val root = (NSProcessInfo.processInfo.environment["PDF_FIXTURE"] as String).toPath()
        .parent!!.parent!!.parent!!.parent!! / "src/commonMain/composeResources/files" / Languages.FRENCH.code
    private fun read(file: String) = FileSystem.SYSTEM.read(root / file) { readUtf8() }

    @Test fun everyBundledGrammarNoteAndQuestionParsesWithoutSilentLoss() {
        val grammar = read("grammar/notes.json")
        val expectedNotes = Json.parseToJsonElement(grammar).jsonObject.getValue("notes").jsonArray.size
        assertEquals(expectedNotes, parseGrammarNotes(grammar, Languages.FRENCH).size)
        val questions = read("exams/tcf-questions.json")
        val tasks = Json.parseToJsonElement(questions).jsonObject.getValue("tasks").jsonArray
        val parsed = parseExamQuestions(questions)
        assertEquals(tasks.size, parsed.tasks.size)
        assertEquals(tasks.sumOf { it.jsonObject.getValue("items").jsonArray.size }, parsed.tasks.sumOf { it.items.size })
    }

    @Test fun fullLexiconAndIdiomsLoadAndMatchInflectedForms() {
        val lexicon = loadLemmaLexicon(read("lexicon/lemmas.tsv"), read("lexicon/common_lemmas.txt"), Languages.FRENCH)
        assertTrue(lexicon.wordForms.size > 10_000)
        assertTrue("avoir" in lexicon.lemmas("a"))
        assertTrue(lexicon.commonLemmas.isNotEmpty())
        val idioms = loadIdiomLexicon(read("lexicon/idioms.tsv"), Languages.FRENCH)
        assertTrue(idioms.byFirstWord.isNotEmpty())
        assertTrue(IdiomMatcher(idioms, Languages.FRENCH, lexicon).match("il a faim").isNotEmpty())
    }
}
