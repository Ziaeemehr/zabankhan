package com.ziaee.zabankhan.grammar

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GrammarDataTest {
    private fun note(id: String, title: String, aliases: List<String> = emptyList(), prose: String = "") = GrammarNote(
        id, "basics", "A1", title, "عنوان", emptyList(), aliases, emptyList(),
        listOf(GrammarBlock.Prose("summary", listOf(GrammarParagraph("p", prose))))
    )

    @Test fun aliasTitleMatchRanksBeforeBodyMatchAndPersianCompoundsMatch() {
        val index = GrammarSearchIndex(listOf(
            note("body", "Other", prose = "صفت ملکی"),
            note("alias", "Possessives", listOf("صفت ملکی", "نقل‌قول")),
        ), Languages.FRENCH)
        assertEquals(listOf("alias", "body"), index.search("صفت ملكي").map { it.note.id })
        assertEquals("alias", index.search("نقل قول").single().note.id)
    }

    @Test fun accentFoldRetainsSnippetOffsets() {
        val result = GrammarSearchIndex(listOf(note("body", "Other", prose = "Le cafe\u0301 est délicieux.")), Languages.FRENCH)
            .search("café").single()
        val snippet = assertNotNull(result.snippet)
        val range = snippet.highlights.single()
        assertEquals("cafe\u0301", snippet.text.substring(range.start, range.end))
    }

    @Test fun mostRecentOpenMovesToFrontAndHistoryIsBounded() {
        val original = (1..25).map { GrammarRecent("$it", it.toLong()) }
        assertEquals((25 downTo 6).map(Int::toString), normalizeGrammarRecents(original).map { it.id })
        val opened = openGrammarRecent(original, "10", 100)
        assertEquals("10", opened.first().id)
        assertEquals(20, opened.size)
        assertEquals(1, opened.count { it.id == "10" })
        val state = GrammarPrefsState(setOf("a/b + فارسی"), opened)
        assertEquals(state, decodeGrammarPrefs(encodeGrammarPrefs(state)))
        assertEquals(GrammarPrefsState(), decodeGrammarPrefs("broken"))
    }

    @Test fun preferencesPersistAndStaySeparateForLearningLanguages() = runTest {
        val cache = InMemoryKeyValueCache()
        val first = GrammarPrefs(cache, Languages.FRENCH.code)
        first.opened("articles")
        first.toggleFavourite("articles")
        val restored = GrammarPrefs(cache, Languages.FRENCH.code)
        restored.load()
        assertEquals("articles", restored.state.value.recents.single().id)
        assertEquals(setOf("articles"), restored.state.value.favourites)
        val english = GrammarPrefs(cache, Languages.ENGLISH.code)
        english.load()
        assertEquals(GrammarPrefsState(), english.state.value)
    }

    @Test fun englishAlternativeTitlesUseEnglishStopWords() {
        val index = GrammarSearchIndex(listOf(note("choice", "Affect or effect")), Languages.ENGLISH)
        assertEquals("choice", index.search("affect and effect").single().note.id)
    }

    @Test fun postersReadLearningLanguageAndSkipMalformedEntries() {
        val posters = parseGrammarPosters("""{"version":1,"posters":[{"id":"tenses","titleEn":"Tenses","titleFa":"زمان‌ها","file":"tenses.pdf","notes":["present"]},{"id":"broken"}]}""", Languages.ENGLISH)
        assertEquals("Tenses", posters.single().titleLearning)
        assertEquals("Tenses", posters.single().titleEnglish)
        assertEquals(listOf("present"), posters.single().notes)
        assertTrue(parseGrammarPosters("bad", Languages.ENGLISH).isEmpty())
    }

    @Test fun postersUseEnglishTranslationWhenPresentAndLearningTitleAsFallback() {
        val json = """{"version":1,"posters":[{"id":"translated","titleFr":"Les temps","titleEn":"Tenses","titleFa":"زمان‌ها","file":"t.pdf"},{"id":"fallback","titleFr":"Les articles","titleFa":"حرف تعریف","file":"a.pdf"}]}"""
        val posters = parseGrammarPosters(json, Languages.FRENCH)
        assertEquals(listOf("Tenses", "Les articles"), posters.map { it.titleEnglish })
    }

    @Test fun ligaturesFollowLearningLanguageNormalization() {
        assertEquals("coeur ae", foldForGrammarSearch("cœur Æ", Languages.FRENCH))
    }

    @Test fun englishGrammarReadsEnglishFields() {
        val json = """{"version":1,"notes":[{"id":"articles","group":"basics","level":"A1","titleEn":"Articles","titleFa":"حرف تعریف","aliasesEn":["a an the"],"aliasesFa":[],"related":[],"blocks":[{"type":"summary","markdown":[]},{"type":"usage","markdown":[]},{"type":"structure","patterns":[],"text":[]},{"type":"examples","items":[{"id":"one","en":"An apple","fa":"یک سیب","highlights":[[0,2]]}]}]}]}"""
        val parsed = parseGrammarNotes(json, Languages.ENGLISH).single()
        assertEquals("Articles", parsed.titleLearning)
        assertEquals("Articles", parsed.titleEnglish)
        assertEquals(listOf("a an the"), parsed.aliasesLearning)
        assertEquals("An apple", (parsed.blocks.last() as GrammarBlock.Examples).items.single().learning.text)
    }
}
