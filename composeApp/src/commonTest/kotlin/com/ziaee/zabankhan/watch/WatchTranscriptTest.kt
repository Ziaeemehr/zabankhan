package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.Languages
import kotlin.test.*

class WatchTranscriptTest {
    @Test fun transcriptOffsetsPointAtEachCueInSavedBody() {
        val cues = listOf(SubtitleCue(0, 1000, "Hello."), SubtitleCue(1000, 2000, "L’été arrive."))
        val body = transcriptBody(cues)
        val offsets = transcriptOffsets(cues)
        assertEquals(listOf(0, 8), offsets)
        cues.forEachIndexed { i, cue -> assertEquals(cue.text, body.substring(offsets[i], offsets[i] + cue.text.length)) }
    }
    @Test fun emptyTranscriptHasNoOffsets() { assertEquals("", transcriptBody(emptyList())); assertTrue(transcriptOffsets(emptyList()).isEmpty()) }
    @Test fun timesIncludeHoursAndClampNegativePositions() {
        assertEquals("0:00", videoTime(-50))
        assertEquals("1:05", videoTime(65_900))
        assertEquals("1:00:05", videoTime(3_605_000))
    }
    @Test fun routeEscapesLocalPathsAndQueryDelimiters() {
        val route = watchRoute(path = "/a/video ?&=é.mp4", subs = "/b/s.vtt")
        assertTrue(route.contains("path=%2Fa%2Fvideo%20%3F%26%3D%C3%A9.mp4"))
        assertTrue(route.endsWith("subs=%2Fb%2Fs.vtt"))
    }
    @Test fun iframeValidatesVideoIdentifier() {
        assertFailsWith<IllegalArgumentException> { youtubePlayerHtml("');alert(1)") }
        val html = youtubePlayerHtml("dQw4w9WgXcQ")
        assertTrue(html.contains("youtube-nocookie.com")); assertTrue(html.contains("},200)"))
    }
    @Test fun sharedBridgeRetainsControlsUntilReadyAndRejectsStaleSamples() {
        val controller = IframeController()
        val scripts = mutableListOf<String>()
        controller.evaluate = scripts::add
        controller.seekTo(5_000); controller.setRate(0.75f); controller.play()
        assertTrue(scripts.isEmpty())
        controller.onReady()
        assertTrue(scripts.any { it.contains("seekTo(5.0,true)") })
        assertTrue(scripts.any { it == "player.setPlaybackRate(0.75)" })
        controller.onTime(1.0, 0)
        assertEquals(5_000L, controller.positionMs.value)
        controller.onState(1); assertTrue(controller.isPlaying.value)
        controller.pause(); controller.onState(1); assertFalse(controller.isPlaying.value)
        controller.onError(150); assertEquals("150", controller.error.value)
        controller.close()
    }
}
