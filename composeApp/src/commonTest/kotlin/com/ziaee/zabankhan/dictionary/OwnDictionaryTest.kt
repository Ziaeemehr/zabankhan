package com.ziaee.zabankhan.dictionary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.lang.loadLemmaLexicon
import com.ziaee.zabankhan.translate.ExampleSource
import com.ziaee.zabankhan.translate.TranslationRepository
import com.ziaee.zabankhan.translate.TranslationOverrides
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
    @Test fun packPersianIsMachineGeneratedButUserOverrideIsNot() = runTest {
        val http = HttpClient(MockEngine { error("Meanings should not require the network") })
        val cache = InMemoryKeyValueCache()
        val repository = TranslationRepository(http, cache, null)
        val dictionary = OwnDictionary(http, cache, FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "meaning-provenance")
        val entry = decodeDictionaryShard(Json.parseToJsonElement("""{"entries":[[7,"prendre","verb",5,"","","",[["to take","گرفتن",[],[],[]]],{},[],[]]],"form_labels":[],"redirects":{},"targets":{}}"""), emptyList()).entries.single()
        try {
            val persian = dictionary.meaning(entry, Languages.FRENCH, Languages.PERSIAN.code, repository)
            assertEquals(listOf("گرفتن"), persian.meanings)
            assertTrue(persian.machineTranslated)
            assertEquals(ExampleSource.MODEL, persian.translations.single().source)
            assertEquals(persian.translations.single(), dictionary.senseMeanings(entry, Languages.PERSIAN.code, repository).single().translation)
            assertFalse(dictionary.meaning(entry, Languages.FRENCH, Languages.ENGLISH.code, repository).machineTranslated)

            TranslationOverrides(cache).putOverride(
                "to take", Languages.ENGLISH.code, Languages.PERSIAN.code, "برداشتن")
            val withoutPersian = entry.copy(senses = entry.senses.map { it.copy(fa = "") })
            val edited = dictionary.meaning(withoutPersian, Languages.FRENCH, Languages.PERSIAN.code, repository)
            assertEquals(listOf("برداشتن"), edited.meanings)
            assertFalse(edited.machineTranslated)
            assertEquals(ExampleSource.EDITED, edited.translations.single().source)
        } finally { http.close() }
    }

    @Test fun monolingualPackParsesAndResolvesInflectedForms() = runTest {
        val indexJson = """{"lang":"fr","format":1,"version":1,"entries":3,"shards":["al","be","ch"],"sources":"French Wiktionnaire via Kaikki Wiktextract (CC BY-SA 4.0)","license":"https://creativecommons.org/licenses/by-sa/4.0/"}"""
        val alJson = """{"entries":[{"word":"aller","pos":"verb","gender":"","senses":[{"definition":"Se déplacer d'un lieu à un autre.","tags":[],"example":"Nous allons au marché."},{"definition":"Convenir, être dans un état donné.","tags":["informal"],"example":"Comment allez-vous ?"},{"definition":"Se mouvoir vers un lieu.","tags":["dated"],"example":""}]}]}"""
        val beJson = """{"entries":[{"word":"beau","pos":"adjective","gender":"","senses":[{"definition":"Qui plaît par sa forme ou son apparence.","tags":[],"example":"Un beau jardin."}]}]}"""
        val index = Json.parseToJsonElement(indexJson).jsonObject
        val shard = Json.parseToJsonElement(alJson)
        val entries = decodeMonolingualShard(shard)
        assertEquals(3, index["entries"]?.toString()?.toInt())
        val lexicon = loadLemmaLexicon("allions\taller\nbelles\tbeau", "", Languages.FRENCH)
        assertEquals("aller", lookupMonolingualDefinitions("allions", lexicon, entries, Languages.FRENCH).single().word)
        assertEquals("beau", lookupMonolingualDefinitions("belles", lexicon, entries + decodeMonolingualShard(
            Json.parseToJsonElement(beJson)), Languages.FRENCH).single().word)
        val aller = entries.single()
        assertEquals("Se déplacer d'un lieu à un autre.", aller.senses.first().definition)
        assertEquals("Nous allons au marché.", aller.senses.first().example)
    }

    @Test fun learnerPackDecodesFullAndMinimalEntries() {
        val entries = decodeLearnerShard(Json.parseToJsonElement("""{"entries":[{"word":"abandonner","pos":"verb","senses":[{"d":"Renoncer.","ex":"Il abandonne.","reg":"familier","syn":["quitter"],"ant":["continuer"]}],"more":["Cesser de soutenir."],"phrases":[{"p":"abandonner à","d":"Laisser."}],"pattern":"abandonner qqn","note":"Attention.","family":["abandon"]},{"word":"abri","pos":"noun","senses":[{"d":"Lieu protégé."}]}]}"""))
        assertEquals(2, entries.size)
        assertEquals(LearnerSense("Renoncer.", "Il abandonne.", "familier", listOf("quitter"), listOf("continuer")), entries[0].senses.single())
        assertEquals(LearnerPhrase("abandonner à", "Laisser."), entries[0].phrases.single())
        assertEquals(listOf("Cesser de soutenir."), entries[0].more)
        assertEquals("abandonner qqn", entries[0].pattern)
        assertEquals("Attention.", entries[0].note)
        assertEquals(listOf("abandon"), entries[0].family)
        assertEquals(LearnerEntry("abri", "noun", listOf(LearnerSense("Lieu protégé.")), emptyList(), emptyList(), "", "", emptyList()), entries[1])
    }

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

    @Test fun cachedWordFamilyResolvesInflectionsAndHomographsWithMeaningsForEachTab() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-family-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val lang = Languages.FRENCH.copy(monolingualPackId = null, learnerPackId = null)
        val directory = root / lang.code / "v2"
        fs.createDirectories(directory)
        fs.write(directory / "index.json") {
            writeUtf8("""{"lang":"fr","format":2,"version":1,"shards":["ca","pa"]}""")
        }
        fs.write(directory / "ca.json") {
            writeUtf8("""{"entries":[[1,"calmer","verb",5,"","","",[["to calm","آرام کردن"]],{},[],[],["Rendre calme."],[["paisible","adjective"],["calmer","verb"]]]],"form_labels":["ind.impf.3s"],"redirects":{"calmait":[[1,0]]},"targets":{"1":["calmer","verb"]}}""")
        }
        fs.write(directory / "pa.json") {
            writeUtf8("""{"entries":[[2,"paisible","noun",3,"","m","",[["person"]]],
                [3,"paisible","adjective",4,"","","",[["peaceful","آرام"]],{},[],[],["Qui vit en paix."]]]}""")
        }
        val http = HttpClient(MockEngine { respond("offline", HttpStatusCode.ServiceUnavailable) })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs)
            val base = dictionary.lookup("calmait", lang).entries.single()
            val family = dictionary.wordFamily(base, lang)
            assertEquals(listOf(3L, 1L), family.map { it.id })
            val relative = family.first()
            assertEquals("peaceful", relative.wordFamilyMeaning(Languages.ENGLISH.code, lang))
            assertEquals("آرام", relative.wordFamilyMeaning(Languages.PERSIAN.code, lang))
            assertEquals("Qui vit en paix.", relative.wordFamilyMeaning(lang.code, lang))
            assertEquals(relative.id, dictionary.lookup(relative.word, lang,
                exactEntry = DictionaryHeadword(relative.word, relative.pos)).entries.single().id)
            assertTrue(dictionary.wordFamily(relative, lang).isEmpty())
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }

    @Test fun bundledAdverbLinksEnrichCachedHeadwordsInBothDirections() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-adverbs-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val lang = Languages.FRENCH.copy(monolingualPackId = null, learnerPackId = null)
        val directory = root / lang.code / "v2"
        fs.createDirectories(directory)
        fs.write(directory / "index.json") {
            writeUtf8("""{"lang":"fr","format":2,"version":1,"shards":["ra"]}""")
        }
        fs.write(directory / "ra.json") {
            writeUtf8("""{"entries":[[1,"rapide","adjective",5],[2,"rapide","noun",4],[3,"rapidement","adverb",4]]}""")
        }
        val http = HttpClient(MockEngine { respond("offline", HttpStatusCode.ServiceUnavailable) })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs) { "rapide\tadjective\trapidement" }
            val entries = dictionary.lookup("rapide", lang).entries
            val adjective = entries.single { it.pos == "adjective" }
            val noun = entries.single { it.pos == "noun" }
            val adverb = dictionary.lookup("rapidement", lang).entries.single()
            assertEquals(listOf("rapidement"), dictionary.wordFamily(adjective, lang).map { it.word })
            assertEquals(listOf("rapide"), dictionary.wordFamily(adverb, lang).map { it.word })
            assertTrue(noun.wordFamily.isEmpty())
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }

    @Test fun moreFrequentPartOfSpeechLeadsEvenWithFewerSenses() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-pos-order-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val lang = Languages.FRENCH.copy(monolingualPackId = null, learnerPackId = null)
        val directory = root / lang.code / "v2"
        fs.createDirectories(directory)
        fs.write(directory / "index.json") { writeUtf8("""{"lang":"fr","format":2,"version":1,"shards":["pe"]}""") }
        fs.write(directory / "pe.json") {
            writeUtf8("""{"entries":[[1,"pendant","noun",2,"","m","",[["pendant"],["counterpart"]]],[2,"pendant","preposition",5,"","","",[["during"]]]]}""")
        }
        val http = HttpClient(MockEngine { respond("offline", HttpStatusCode.ServiceUnavailable) })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs, readNegationLinks = { "" }) { "" }
            assertEquals(listOf("preposition", "noun"), dictionary.lookup("pendant", lang).entries.map { it.pos })
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }

    @Test fun negationLinksPairOppositesInsideTheWordFamily() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-negations-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val lang = Languages.FRENCH.copy(monolingualPackId = null, learnerPackId = null)
        val directory = root / lang.code / "v2"
        fs.createDirectories(directory)
        fs.write(directory / "index.json") {
            writeUtf8("""{"lang":"fr","format":2,"version":1,"shards":["co","in"]}""")
        }
        fs.write(directory / "co.json") {
            writeUtf8("""{"entries":[[1,"comprendre","verb",5,"","","",[],{},[],[],[],[["compréhensible","adjective"]]],[2,"compréhensible","adjective",4]]}""")
        }
        fs.write(directory / "in.json") {
            writeUtf8("""{"entries":[[3,"incompréhensible","adjective",3]]}""")
        }
        val http = HttpClient(MockEngine { respond("offline", HttpStatusCode.ServiceUnavailable) })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs,
                readNegationLinks = { "# header\ncompréhensible\tadjective\tincompréhensible" }) { "" }
            val verb = dictionary.lookup("comprendre", lang).entries.single()
            val family = dictionary.wordFamily(verb, lang)
            assertEquals(listOf("compréhensible", "incompréhensible"), family.map { it.word })
            assertEquals("incompréhensible", family[0].opposite?.word)
            assertTrue(family[1].negative)

            val negative = dictionary.lookup("incompréhensible", lang).entries.single()
            assertEquals(DictionaryHeadword("compréhensible", "adjective"), negative.opposite)
            assertTrue(negative.negative)
            assertEquals(listOf("compréhensible"), dictionary.wordFamily(negative, lang).map { it.word })
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }

    @Test fun dictionaryAutocompleteFindsHeadwordsUnderSplitPrefix() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-autocomplete-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val lang = Languages.FRENCH
        val directory = root / lang.code / "v2"
        fs.createDirectories(directory)
        fs.write(directory / "index.json") {
            writeUtf8("""{"lang":"fr","format":2,"version":1,"shards":["cal","cas"],"split":{"ca":["cal","cas"]}}""")
        }
        fs.write(directory / "cal.json") {
            writeUtf8("""{"entries":[[1,"calme","noun",5],[2,"calme","adjective",5],[3,"calmement","adverb",4]]}""")
        }
        fs.write(directory / "cas.json") {
            writeUtf8("""{"entries":[[4,"cas","noun",5]]}""")
        }
        val http = HttpClient(MockEngine { respond("offline", HttpStatusCode.ServiceUnavailable) })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs)
            assertEquals(listOf("calme", "calmement", "cas"), dictionary.completeHeadwords("CA", lang))
            assertEquals(listOf("calmement"), dictionary.completeHeadwords("calme", lang))
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }

    @Test fun downloadReusesCachedShardsWithoutCreatingCopies() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-download-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val lang = Languages.FRENCH
        var shardRequests = 0
        val http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/index.json")) {
                respond("""{"lang":"fr","format":2,"version":1,"shards":["ca"]}""")
            } else {
                shardRequests++
                respond("""{"entries":[]}""")
            }
        })
        try {
            val dictionary = OwnDictionary(http, InMemoryKeyValueCache(), root, fs)
            dictionary.downloadAll(lang) { _, _ -> }
            val downloaded = dictionary.status(lang)
            dictionary.downloadAll(lang) { _, _ -> }
            assertTrue(downloaded.complete)
            assertEquals(downloaded, dictionary.status(lang))
            assertEquals(1, shardRequests)
            assertEquals(setOf("ca.json", "index.json"), fs.list(root / lang.code / "v2").map { it.name }.toSet())
        } finally {
            http.close()
            fs.deleteRecursively(root, mustExist = false)
        }
    }

    @Test fun frenchDefinitionsAreIncompleteUntilBothPackIndexesAreCached() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dictionary-partial-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val lang = Languages.FRENCH
        val directory = root / lang.code / "monolingual" / "v1"
        fs.createDirectories(directory)
        fs.write(directory / "index.json") {
            writeUtf8("""{"lang":"fr","format":1,"shards":["ca"]}""")
        }
        fs.write(directory / "ca.json") { writeUtf8("""{"entries":[]}""") }
        val http = HttpClient(MockEngine { error("Reading cached status must stay offline") })
        try {
            val status = OwnDictionary(http, InMemoryKeyValueCache(), root, fs).monolingualStatus(lang)
            assertEquals(1, status.completed)
            assertFalse(status.complete)
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
