package com.ziaee.zabankhan.pdf

import kotlin.test.*

class PdfInkTest {
    @Test fun roundTrip() {
        val strokes = listOf(InkStroke("secondary", .004f, listOf(0f, 1f, .5f, .25f)))
        assertEquals(strokes, decodeInk(encodeInk(strokes)))
        assertEquals(emptyList(), decodeInk("invalid"))
    }
    @Test fun simplificationDropsNearbyPoints() {
        assertEquals(listOf(0f, 0f, .5f, .5f), simplifyInkPoints(listOf(0f, 0f, .0001f, .0001f, .5f, .5f)))
    }
    @Test fun eraserHitsSegmentsAndEndpoints() {
        val stroke = InkStroke(points = listOf(.1f, .2f, .9f, .2f))
        assertTrue(inkHit(stroke, .5f, .205f, .01f))
        assertTrue(inkHit(stroke, .095f, .2f, .01f))
        assertFalse(inkHit(stroke, .5f, .3f, .01f))
        assertTrue(inkHit(InkStroke(points = listOf(.2f, .3f)), .2f, .3f, .01f))
        assertFalse(inkHit(InkStroke(points = emptyList()), .2f, .3f, .01f))
    }
}
