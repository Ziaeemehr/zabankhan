package com.ziaee.zabankhan.pdf

import kotlin.test.*

class PdfInkTest {
    @Test fun roundTrip() {
        val oldStroke = InkStroke("secondary", .004f, listOf(0f, 1f, .5f, .25f))
        val newStroke = InkStroke("#FFB42318", .007f, listOf(.123456f, .987654f, .5f, .25f), highlighter = true)
        assertEquals(listOf(oldStroke, newStroke.copy(points = listOf(.1235f, .9877f, .5f, .25f))),
            decodeInk(encodeInk(listOf(oldStroke, newStroke))))
        assertEquals(listOf(InkStroke(points = listOf(0f, 1f))), decodeInk("""[{"points":[0,1]}]"""))
        assertEquals(emptyList(), decodeInk("invalid"))
    }
    @Test fun simplificationDropsNearbyPoints() {
        assertEquals(listOf(0f, 0f, .5f, .5f), simplifyInkPoints(listOf(0f, 0f, .0001f, .0001f, .5f, .5f)))
        assertEquals(listOf(0f, 0f, .0004f, 0f), simplifyInkPoints(listOf(0f, 0f, .0003f, 0f, .0004f, 0f)))
    }
    @Test fun curveUsesInteriorPointAsControlAndNextMidpointAsEnd() {
        assertEquals(listOf(InkCurveSegment(.5f, 1f, .75f, .5f)),
            inkCurveSegments(listOf(0f, 0f, .5f, 1f, 1f, 0f)))
        assertEquals(emptyList(), inkCurveSegments(listOf(0f, 0f, 1f, 1f)))
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
