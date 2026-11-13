package com.ziaee.zabankhan.watch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShadowCuePauseTest {
    @Test fun seeksDoNotCountAsSentenceCompletion() {
        assertFalse(continuousShadowPlayback(1500, 11500, 250, 1f))
        assertFalse(continuousShadowPlayback(1500, 500, 250, 1f))
        assertTrue(continuousShadowPlayback(1500, 2000, 500, 1f))
        assertTrue(continuousShadowPlayback(1500, 1750, 500, .5f))
    }
    @Test fun autoPauseRequiresPlaybackToEnterTheSelectedCue() {
        assertFalse(shouldPauseShadowCue(true, true, false, 9000, 2000))
        assertFalse(shouldPauseShadowCue(true, true, true, 1999, 2000))
        assertTrue(shouldPauseShadowCue(true, true, true, 2000, 2000))
        assertFalse(shouldPauseShadowCue(false, true, true, 2000, 2000))
        assertFalse(shouldPauseShadowCue(true, false, true, 2000, 2000))
    }
}
