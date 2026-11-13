package com.ziaee.zabankhan.watch

// A stale position from before a seek must not pause the newly started sentence.
internal fun shouldPauseShadowCue(enabled: Boolean, playing: Boolean, enteredCue: Boolean, positionMs: Long, endMs: Long) =
    enabled && playing && enteredCue && positionMs >= endMs

internal fun continuousShadowPlayback(previousMs: Long, positionMs: Long, elapsedMs: Long, rate: Float): Boolean =
    positionMs >= previousMs && positionMs - previousMs <= elapsedMs.coerceAtLeast(0) * rate + 1000
