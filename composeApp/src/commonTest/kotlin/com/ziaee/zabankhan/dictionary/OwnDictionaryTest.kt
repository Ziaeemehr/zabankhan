package com.ziaee.zabankhan.dictionary

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import com.ziaee.zabankhan.translate.TranslationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

class OwnDictionaryTest {
    @Test fun shardKeyUsesFirstWordAndNfdEquivalentAccentFolding() {
        assertEquals("ec", dictionaryShardKey("ÉCOLE primaire"))
        assertEquals("ec", dictionaryShardKey("E\u0301cole"))
        assertEquals("a_", dictionaryShardKey(" À bientôt "))
        assertEquals("l_", dictionaryShardKey("l’amour"))
        assertEquals("_u", dictionaryShardKey("œuvre")) // NFD does not expand ligatures.
        assertEquals("__", dictionaryShardKey("123"))
        assertEquals("__", dictionaryShardKey(""))
        assertEquals("_a", dictionaryShardKey("-ami"))
    }

    @Test fun idiomsMatchOrderedWordsWithGapsAndBoundaries() {
        assertTrue(dictionaryIdiomMatches("prendre soin de", "Il faut prendre grand soin de soi."))
        assertTrue(dictionaryIdiomMatches("à bientôt", "Alors, à très bientôt !"))
        assertFalse(dictionaryIdiomMatches("prendre soin de", "de soin prendre"))
        assertFalse(dictionaryIdiomMatches("peu à peu", "peu à"))
        assertFalse(dictionaryIdiomMatches("avoir peur", "avoir peureux"))
        assertFalse(dictionaryIdiomMatches("chat", "un chat"))
    }

    @Test fun cachesShardsAcrossInstancesRefreshesVersionsAndWorksOffline() = runTest {
        val fs = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "own-dictionary-test-${Random.nextLong()}"
        var version = 1
        var online = true
        val counts = mutableMapOf<String, Int>()
        val http = HttpClient(MockEngine { request ->
            check(online) { "offline" }
            val name = request.url.encodedPath.substringAfterLast('/')
            counts[name] = (counts[name] ?: 0) + 1
            when (name) {
                "index.json" -> respond("""{"lang":"fr","version":$version,"shards":["ch","pr"],"format":"json","entries":3,"persian":0}""")
                "ch.json" -> respond("""[{"word":"chat","pos":"noun","rank":$version,"en":["cat"]}]""")
                "pr.json" -> respond("""[{"word":"prendre soin de","pos":"verb","rank":2,"en":["take care of"]},{"word":"prendre peur","pos":"verb","rank":3}]""")
                else -> respond("missing", HttpStatusCode.NotFound)
            }
        })
        val cache = InMemoryKeyValueCache()
        try {
            val repository = OwnDictionary(http, cache, root)
            assertEquals("chat", repository.lookup("CHAT", Languages.FRENCH).single().word)
            assertEquals("chat", OwnDictionary(http, cache, root).lookup("chat", Languages.FRENCH).single().word)
            assertEquals(1, counts["ch.json"])
            assertEquals("prendre soin de", repository.lookup("Il faut prendre grand soin de soi.", Languages.FRENCH).single().word)
            version = 2
            assertEquals(2, repository.lookup("chat", Languages.FRENCH).single().rank)
            assertEquals(2, counts["ch.json"])
            val progress = mutableListOf<DictionaryDownload>()
            repository.downloadAll(Languages.FRENCH) { progress += it }
            assertTrue(progress.last().complete)
            assertTrue(repository.status(Languages.FRENCH).bytes > 0)
            online = false
            assertEquals("chat", OwnDictionary(http, cache, root).lookup("chat", Languages.FRENCH).single().word)
            assertEquals("prendre soin de", repository.lookup("prendre soin de", Languages.FRENCH).single().word)
            assertTrue(repository.lookup("chat", Languages.ENGLISH).isEmpty())
            repository.delete(Languages.FRENCH)
            assertEquals(0, repository.status(Languages.FRENCH).completed)
        } finally { http.close(); fs.deleteRecursively(root, mustExist = false) }
    }

    @Test fun persianUsesAuthoredMeaningOrCachesMachineMeaningByWordAndPos() = runTest {
        val cache = InMemoryKeyValueCache()
        var calls = 0
        val http = HttpClient(MockEngine {
            calls++
            respond("""[[["گربه","cat"]],null,"en"]""")
        })
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "own-dictionary-meaning-${Random.nextLong()}"
        try {
            val repository = OwnDictionary(http, cache, root)
            val translator = TranslationRepository(http, cache, null)
            val authored = OwnDictionaryEntry("chat", "noun", en = listOf("cat"), fa = JsonPrimitive("گربه"))
            assertFalse(repository.meaning(authored, Languages.FRENCH, "fa", translator).machineTranslated)
            assertEquals(0, calls)
            val entry = authored.copy(fa = null)
            val first = repository.meaning(entry, Languages.FRENCH, "fa", translator)
            assertEquals(listOf("گربه"), first.meanings)
            assertTrue(first.machineTranslated)
            assertEquals(first, OwnDictionary(http, cache, root).meaning(entry, Languages.FRENCH, "fa", translator))
            assertEquals(1, calls)
            assertEquals(listOf("cat"), repository.meaning(entry, Languages.FRENCH, "en", translator).meanings)
        } finally { http.close() }
    }
    @Test fun machineMeaningCacheDistinguishesAccentsAndPartOfSpeech() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine { request ->
            calls++
            val translated = when (request.url.parameters["q"]) {
                "coast" -> "ساحل"
                "rating" -> "رتبه"
                else -> "ارزیابی"
            }
            respond("""[[["$translated","source"]],null,"en"]""")
        })
        val cache = InMemoryKeyValueCache()
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "own-dictionary-accents-${Random.nextLong()}"
        try {
            val repository = OwnDictionary(http, cache, root)
            val translator = TranslationRepository(http, cache, null)
            val coast = OwnDictionaryEntry("côte", "noun", en = listOf("coast"))
            val rating = OwnDictionaryEntry("cote", "noun", en = listOf("rating"))
            val verb = OwnDictionaryEntry("cote", "verb", en = listOf("rate"))
            assertEquals(listOf("ساحل"), repository.meaning(coast, Languages.FRENCH, "fa", translator).meanings)
            assertEquals(listOf("رتبه"), repository.meaning(rating, Languages.FRENCH, "fa", translator).meanings)
            assertEquals(listOf("ارزیابی"), repository.meaning(verb, Languages.FRENCH, "fa", translator).meanings)
            assertEquals(3, calls)
        } finally { http.close() }
    }

}
