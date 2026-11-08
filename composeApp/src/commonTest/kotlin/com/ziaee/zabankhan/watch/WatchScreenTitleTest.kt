package com.ziaee.zabankhan.watch

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchScreenTitleTest {
    @Test
    fun usesCaptionTitleWhenAvailable() {
        assertEquals("A lesson", watchScreenTitle("A lesson", "/videos/local.mp4", "abc123"))
    }

    @Test
    fun fallsBackToLocalVideoFilename() {
        assertEquals("lesson", watchScreenTitle(null, "/videos/lesson.mp4", "abc123"))
    }

    @Test
    fun fallsBackToVideoIdWhenNoTitleOrPath() {
        assertEquals("abc123", watchScreenTitle(" ", "", "abc123"))
    }
}
