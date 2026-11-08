package com.ziaee.zabankhan.dictionary

import kotlinx.serialization.json.Json
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFails

class OwnDictionaryTest {
    @Test fun splitAwareShardKeysMatchBuilderRule() {
        val split = setOf("se", "de", "a_", "_i")
        assertEquals("des", dictionaryShardKey("des", split))
        assertEquals("des", dictionaryShardKey("désormais", split))
        assertEquals("se_", dictionaryShardKey("se rendre compte", split))
        assertEquals("a__", dictionaryShardKey("à", split))
        assertEquals("_il", dictionaryShardKey("œil", split))
        assertEquals("se", dictionaryShardKey("se rendre compte"))
        assertEquals("de", dictionaryShardKey("désormais"))
        assertEquals("de", dictionaryShardKey("des"))
        assertEquals("a_", dictionaryShardKey("à"))
        assertEquals("_i", dictionaryShardKey("œil"))
    }

    @Test fun decodesCompactEntryAndAudioPrefix() {
        val shard = decodeDictionaryShard(Json.parseToJsonElement("""{"entries":[[7,"prendre","verb",5,"/pʁɑ̃dʁ/","",[0,"take.mp3"],[["to take","گرفتن",["transitive"],["saisir"],[["prends ma main","take my hand",""]]]],{"past_participle":"pris"},["prendre feu"],[]]],"form_labels":[],"redirects":{},"targets":{}}"""), listOf("https://audio/"))
        val entry = shard.entries.single()
        assertEquals(7L, entry.id)
        assertEquals("https://audio/take.mp3", entry.audio)
        assertEquals(listOf("pris"), entry.forms["past_participle"])
        assertEquals("take my hand", entry.senses.single().examples.single().en)
        assertEquals(listOf("prendre feu"), entry.expressions)
    }

    @Test fun redirectResolutionAndFormLabelsDecodeFromCompactShard() {
        val shard = decodeDictionaryShard(Json.parseToJsonElement("""{"entries":[],"form_labels":["imp.2s;ind.pres.1s,2s"],"redirects":{"prends":[[7,0]]},"targets":{"7":["prendre","verb"]}}"""))
        val redirect = shard.redirects.getValue("prends").single()
        assertEquals("prendre", redirect.lemma)
        assertEquals("verb", redirect.pos)
        assertEquals(listOf("imp.2s", "ind.pres.1s,2s"), redirect.labels)
        assertEquals(listOf(listOf("imp", "2s"), listOf("ind", "pres", "1s", "2s")), formLabelTokens(redirect.labels.joinToString(";")))
        assertEquals("imperative 2nd singular; indicative present 1st singular 2nd singular", formLabelText(redirect.labels.joinToString(";")))
        val lemmaShard = decodeDictionaryShard(Json.parseToJsonElement("""{"entries":[[7,"prendre","verb",5,"","","",[],{},[],[]]],"form_labels":[],"redirects":{},"targets":{}}"""))
        assertEquals("prendre", resolveDictionaryRedirects(shard, lemmaShard).single().word)
    }

    @Test fun idiomsMatchOrderedWords() {
        assertTrue(dictionaryIdiomMatches("prendre soin de", "Il faut prendre grand soin de soi."))
        assertFalse(dictionaryIdiomMatches("prendre soin de", "de soin prendre"))
    }

    @Test fun dictionaryLookupFallsBackFromLeadingArticleToHeadword() {
        assertEquals(listOf("le monde", "monde"), dictionaryLookupCandidates("le monde", Languages.FRENCH))
        assertEquals(listOf("l'homme", "homme"), dictionaryLookupCandidates("l’homme", Languages.FRENCH))
        assertEquals(listOf("the world", "world"), dictionaryLookupCandidates("the world", Languages.ENGLISH))
    }

    @Test fun lookupWithLeadingArticleLoadsHeadwordShard() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-article-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val http = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/index.json") -> respond("""{"lang":"fr","format":2,"version":1,"entries":1,"persian":1,"shards":["mo"],"split":{},"audio_prefixes":[]}""")
                request.url.encodedPath.endsWith("/mo.json") -> respond("""{"entries":[[1,"monde","noun",5,"/mɔ̃d/","","",[["world","جهان",[],[],[]]],{},[],[]]],"form_labels":[],"redirects":{},"targets":{}}""")
                else -> error("Unexpected dictionary request: ${request.url}")
            }
        })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs)
            assertEquals("monde", dictionary.lookup("le monde", Languages.FRENCH).entries.single().word)
            assertEquals("monde", dictionary.search("le monde", Languages.FRENCH).single().word)
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }

    @Test fun lookupReportsShardDownloadFailureInsteadOfEmptyResult() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-offline-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/index.json")) {
                respond("""{"lang":"fr","format":2,"version":1,"entries":1,"persian":1,"shards":["mo"],"split":{},"audio_prefixes":[]}""")
            } else respond("offline", HttpStatusCode.ServiceUnavailable)
        })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs)
            assertFails { dictionary.lookup("monde", Languages.FRENCH) }
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }
}
