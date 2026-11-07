package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.ui.reader.readerRoute
import io.ktor.http.decodeURLQueryComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderRouteTest {
    @Test fun findArgumentRoundTripsPunctuationAndBothContentDirections() {
        listOf("English question? & answer #1", "متن فارسی", "l’été").forEach { query ->
            val route = readerRoute(7, query)
            assertEquals(query, route.substringAfter("?find=").decodeURLQueryComponent())
        }
        assertEquals("reader/7", readerRoute(7))
    }
}
