package com.ziaee.zabankhan.pdf

import com.ziaee.zabankhan.data.PdfTextNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PdfNoteMarkersTest {
    private fun note(start: Int, end: Int) = PdfTextNote(
        textId = 1, page = 0, startWord = start, endWord = end,
        selectedText = "selected", body = "note",
    )

    @Test fun markerFollowsTheLastWordAndStaysInsideThePage() {
        val words = listOf(
            PdfWord("first", 0, PdfRect(.1f, .2f, .3f, .25f)),
            PdfWord("last", 6, PdfRect(.7f, .2f, .9f, .25f)),
        )

        assertEquals(PdfNoteMarkerPosition(882, 240),
            placePdfNoteMarkers(listOf(note(0, 1)), words, 1000, 1400, 36, 4, rtl = false).single())
        assertEquals(PdfNoteMarkerPosition(682, 240),
            placePdfNoteMarkers(listOf(note(0, 1)), words, 1000, 1400, 36, 4, rtl = true).single())
    }

    @Test fun topEdgeAndUnresolvedTextStillHaveVisibleMarkers() {
        val words = listOf(PdfWord("top", 0, PdfRect(.96f, .005f, 1f, .03f)))

        assertEquals(PdfNoteMarkerPosition(64, 7),
            placePdfNoteMarkers(listOf(note(0, 0)), words, 100, 100, 36, 4, rtl = false).single())
        assertEquals(PdfNoteMarkerPosition(60, 4),
            placePdfNoteMarkers(listOf(note(0, 0)), emptyList(), 100, 100, 36, 4, rtl = false).single())
    }

    @Test fun nearbyNotesDoNotCoverEachOther() {
        val words = listOf(PdfWord("one", 0, PdfRect(.4f, .4f, .5f, .45f)))
        val markers = placePdfNoteMarkers(listOf(note(0, 0), note(0, 0)), words,
            200, 200, 36, 4, rtl = false)

        assertEquals(2, markers.size)
        assertNotEquals(markers[0], markers[1])
    }
}
