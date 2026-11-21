package com.ziaee.zabankhan.content

import com.ziaee.zabankhan.data.VocabEntry
import com.ziaee.zabankhan.lang.Languages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8

class DatasetTest {
    @Test fun editedSentenceDoesNotReuseStaleTranslation() {
        val json = """[{"w":"hello","m":"سلام","s":"Hello!","st":{"fa":"سلام!","en":"Hello!"},"stMeta":{"fa":{"source":"stale"},"en":{"source":"${"Hello!".encodeUtf8().sha256().hex()}"}}}]"""
        val row = parseDatasetDeck(json).single()
        assertEquals(mapOf("en" to "Hello!"), row.sentenceTranslations)
    }

    @Test fun englishSentenceLookupUsesMeaningLanguageAndSourcePack() {
        val index = BundledSentenceTranslations(Languages.ENGLISH) {
            listOf(DatasetDeckRow("hello", "سلام", "Hello!", null, mapOf("fa" to "سلام!")))
        }
        assertEquals("سلام!", index.get(" HELLO! ", "fa-IR"))
        assertNull(index.get("Hello!", "en"))
        assertNull(index.get("Bonjour!", "fa"))
    }

    @Test fun repeatedNormalizedSentenceKeepsFirstTranslation() {
        val index = BundledSentenceTranslations(Languages.ENGLISH) {
            listOf(
                DatasetDeckRow("hello", "سلام", "Hello!", null, mapOf("fa" to "اول")),
                DatasetDeckRow("greeting", "درود", " HELLO! ", null, mapOf("fa-IR" to "دوم"))
            )
        }
        assertEquals("اول", index.get("hello!", "fa"))
    }

    @Test fun cleanupMovesReviewedCopyAndRemovesUnreviewedDuplicate() {
        val row = DatasetDeckRow("hello", "سلام", "Hello!", null)
        val duplicate = VocabEntry(id = 1, lang = "en", word = "hello", meaning = "سلام", listId = 4)
        val progress = duplicate.copy(id = 2, listId = null, textId = 0, leitnerBox = 3)
        val ownWord = progress.copy(id = 3, word = "unrelated")
        val plan = planDatasetCardCleanup(listOf(listOf(row) to 4L), listOf(duplicate, progress, ownWord))
        assertEquals(setOf(1L), plan.deleteIds)
        assertEquals(mapOf(2L to 4L), plan.moveToList)
    }

    @Test fun languagePackOwnsLessonAndStarterMetadata() {
        val manifest = parseDatasetManifest("""{"version":1,"decks":[],"stories":[],"meaningLanguage":"en","lessonLabel":"Unit","gradedStoriesFolder":"Stories","starterDeckIds":["english-a1"]}""")
        assertEquals("en", manifest.meaningLanguage)
        assertEquals(setOf("english-a1"), manifest.starterDeckIds)
        assertEquals("meaning — Unit 3", datasetMeaning("meaning", "3", manifest.lessonLabel))
        assertEquals("Unit 3", datasetMeaning("", "3", manifest.lessonLabel))
    }

    @Test fun manifestParsesOptionalStarterFlagsAndMergesSources() {
        val sourceBundled = DatasetSource.Bundled("fr")
        val sourceRemote = DatasetSource.Remote("fr")
        val bundled = parseDatasetManifest(
            """{"version":1,"decks":[{"id":"local","name":"Local","level":"A1","kind":"words","file":"decks/local.json","count":2,"starter":true},{"id":"old","name":"Old","level":"A2","kind":"words","file":"decks/old.json","count":1}],"stories":[{"id":"story-local","name":"Story","level":"A1","source":"Open","file":"stories/a.json","count":1,"starter":true}]}""",
            sourceBundled
        )
        val remote = parseDatasetManifest(
            """{"version":2,"decks":[{"id":"remote","name":"Remote","level":"A1","kind":"words","file":"decks/remote.json","count":3}],"stories":[]}""",
            sourceRemote
        )
        assertTrue(bundled.decks[0].starter)
        assertFalse(bundled.decks[1].starter)
        assertTrue(bundled.stories.single().starter)
        assertFalse(remote.decks.single().starter)

        val merged = mergeDatasetManifests(bundled, remote)
        assertEquals(listOf("local", "old", "remote"), merged.decks.map { it.id })
        assertEquals(listOf(sourceBundled, sourceBundled, sourceRemote), merged.decks.map { it.source })
        assertEquals(2, merged.version)
    }

    @Test fun mergeKeepsBundledCopyWhenRemoteRepeatsDeckId() {
        val bundled = parseDatasetManifest(
            """{"version":1,"decks":[{"id":"same","name":"B","level":"A1","kind":"words","file":"decks/s.json","count":1}],"stories":[]}""",
            DatasetSource.Bundled("fr")
        )
        val remote = parseDatasetManifest(
            """{"version":1,"decks":[{"id":"same","name":"R","level":"A1","kind":"words","file":"decks/s.json","count":1}],"stories":[]}""",
            DatasetSource.Remote("fr")
        )
        val merged = mergeDatasetManifests(bundled, remote)
        assertEquals(listOf("B"), merged.decks.map { it.name })
    }
}
