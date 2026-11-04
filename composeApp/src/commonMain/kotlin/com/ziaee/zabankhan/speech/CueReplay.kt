package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.watch.VideoController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Wait for the seek to land before observing the end; always pause before opening the mic. */
suspend fun replayShadowCue(player: VideoController, startMs: Long, endMs: Long, rate: Float): Long {
    require(endMs > startMs && rate.isFinite() && rate > 0)
    val duration = ((endMs - startMs) / rate).toLong()
    try {
        player.seekTo(startMs); player.play()
        withTimeout(duration + 5000) {
            player.positionMs.first { it in startMs until endMs }
            player.positionMs.first { it >= endMs }
        }
        return duration
    } finally { player.pause() }
}
