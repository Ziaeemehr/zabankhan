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
    private val root = projectRoot / "src/commonMain/composeResources/files" / Languages.FRENCH.code
    private fun read(file: String) = FileSystem.SYSTEM.read(root / file) { readUtf8() }

    @Test fun farsiFrontAndSearchableFrenchUnitLabelsLoadFromBundledPack() {
        val manifest = parseDatasetManifest(read("dataset/manifest.json"))
        val pack = manifest.decks.firstOrNull { it.id == "comm-diag-a2b1-fa-fr" }
        assertNotNull(pack)
        assertEquals("Comm_diag_a2b1 · FA→FR", pack.name)

        val cards = parseDatasetDeck(read("dataset/${pack.file}"))
        val units = (1..24).map { it.toString().padStart(2, '0') }
        assertEquals(1343, cards.size)
        assertEquals(cards.size, cards.map { it.word.lowercase() }.distinct().size)
        assertTrue(units.all { unit -> cards.any { "Unité $unit —" in it.sentence } })
        assertTrue(cards.all { card -> card.word.any { it in '\u0600'..'\u06ff' } })
        assertTrue(cards.all { card -> card.meaning.isNotBlank() && '/' !in card.meaning })
        assertTrue(cards.all { card -> units.any { "Unité $it —" in card.sentence } })
        assertTrue(cards.all { card -> card.sentence.substringBefore("\nUnité ").isNotBlank() })

        val first = cards.first()
        assertEquals("چطوره؟ نظرت چیه؟", first.word)
        assertEquals("Ça te dit ?", first.meaning)
        assertTrue(first.sentence.contains("On va à la campagne ce week-end, ça te dit ?"))
        assertTrue(first.sentence.contains("Unité 01 — INVITER, ACCEPTER, REFUSER, S’EXCUSER"))

        val shared = cards.single { it.word == "به شرط اینکه" }
        assertTrue(shared.meaning.contains("à condition que + subjonctif"))
        assertTrue(shared.meaning.contains("à condition de + inf."))
        assertTrue(shared.meaning.contains("pourvu que"))
        assertTrue(shared.sentence.contains("Unité 06 —"))
        assertTrue(shared.sentence.contains("Unité 07 —"))
        assertTrue(shared.sentence.contains("Unité 24 —"))
    }

    @Test fun currentReleaseCsvImporterReadsAllCardsAndUnitSearchText() {
        val csvPath = projectRoot.parent!! / "docs/flashcards/Comm_diag_a2b1.csv"
        val csv = FileSystem.SYSTEM.read(csvPath) { readUtf8() }
        val parsed = parseCsvWords(csv)
        assertTrue(parsed.hasDeckColumn)
        assertEquals(1343, parsed.words.size)
        assertTrue(parsed.words.all { it.deck == "Comm_diag_a2b1 · FA→FR" })
        assertEquals(parsed.words.size, parsed.words.map { it.word.lowercase() }.distinct().size)
        assertTrue(parsed.words.all { card -> card.word.any { it in '\u0600'..'\u06ff' } })
        assertTrue(parsed.words.all { card -> card.sentence.contains("Unité ") })
    }
}
