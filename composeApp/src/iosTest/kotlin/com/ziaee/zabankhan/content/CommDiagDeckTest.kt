package com.ziaee.zabankhan.content

import com.ziaee.zabankhan.lang.Languages
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSProcessInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommDiagDeckTest {
    private val projectRoot = (NSProcessInfo.processInfo.environment["PDF_FIXTURE"] as String).toPath()
        .parent!!.parent!!.parent!!.parent!!
    // Deck ships only in the sibling zabankhan-dict repo (downloaded at runtime).
    private val root = projectRoot.parent!!.parent!! / "zabankhan-dict/packs" / Languages.FRENCH.code
    private fun read(file: String) = FileSystem.SYSTEM.read(root / file) { readUtf8() }

    @Test fun farsiFrontAndSearchableFrenchUnitLabelsStayOutsideSpokenExamples() {
        val manifest = parseDatasetManifest(read("dataset/manifest.json"))
        val pack = manifest.decks.firstOrNull { it.id == "comm-diag-a2b1-fa-fr" }
        assertNotNull(pack)
        assertEquals("Comm_diag_a2b1 · FA→FR", pack.name)

        val cards = parseDatasetDeck(read("dataset/${pack.file}"))
        val units = (1..24).map { it.toString().padStart(2, '0') }
        assertEquals(1343, cards.size)
        assertEquals(cards.size, cards.map { it.word.lowercase() }.distinct().size)
        assertTrue(units.all { unit -> cards.any { "Unité $unit —" in it.meaning } })
        assertTrue(cards.all { card -> card.word.any { it in '\u0600'..'\u06ff' } })
        assertTrue(cards.all { card -> card.meaning.isNotBlank() && '/' !in card.meaning })
        assertTrue(cards.all { card -> units.any { "Unité $it —" in card.meaning } })
        assertTrue(cards.all { card -> "Unité " !in card.sentence && card.sentence.isNotBlank() })

        val first = cards.first()
        assertEquals("چطوره؟ نظرت چیه؟", first.word)
        assertTrue(first.meaning.startsWith("Ça te dit ?"))
        assertTrue(first.meaning.contains("Unité 01 — INVITER, ACCEPTER, REFUSER, S’EXCUSER"))
        assertTrue(first.sentence.contains("On va à la campagne ce week-end, ça te dit ?"))
        assertTrue("Unité " !in first.sentence)

        val shared = cards.single { it.word == "به شرط اینکه" }
        assertTrue(shared.meaning.contains("à condition que + subjonctif"))
        assertTrue(shared.meaning.contains("à condition de + inf."))
        assertTrue(shared.meaning.contains("pourvu que"))
        assertTrue(shared.meaning.contains("Unité 06 —"))
        assertTrue(shared.meaning.contains("Unité 07 —"))
        assertTrue(shared.meaning.contains("Unité 24 —"))
        assertTrue("Unité " !in shared.sentence)
    }

    @Test fun currentReleaseCsvImporterReadsAllCardsAndKeepsUnitSearchTextOutsideSentence() {
        val csvPath = root / "dataset/csv/Comm_diag_a2b1.csv"
        val csv = FileSystem.SYSTEM.read(csvPath) { readUtf8() }
        val parsed = parseCsvWords(csv)
        assertTrue(parsed.hasDeckColumn)
        assertEquals(1343, parsed.words.size)
        assertTrue(parsed.words.all { it.deck == "Comm_diag_a2b1 · FA→FR" })
        assertEquals(parsed.words.size, parsed.words.map { it.word.lowercase() }.distinct().size)
        assertTrue(parsed.words.all { card -> card.word.any { it in '\u0600'..'\u06ff' } })
        assertTrue(parsed.words.all { card -> card.meaning.orEmpty().contains("Unité ") })
        assertTrue(parsed.words.all { card -> "Unité " !in card.sentence })
    }
}
