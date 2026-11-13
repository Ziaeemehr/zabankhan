package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.watch.VideoController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

private class CuePlayer : VideoController {
    override val positionMs = MutableStateFlow(9000L)
    override val isPlaying = MutableStateFlow(false)
    var sought = -1L
    var paused = 0
    override fun seekTo(ms: Long) { sought = ms }
    override fun play() { isPlaying.value = true }
    override fun pause() { paused++; isPlaying.value = false }
    override fun setRate(r: Float) = Unit
}
@OptIn(ExperimentalCoroutinesApi::class)
class CueReplayTest {
    @Test fun waitsForSeekThenPausesAtCueEnd() = runTest {
        val player = CuePlayer()
        val result = async { replayShadowCue(player, 1000, 2000, 1.25f) }
        runCurrent(); assertEquals(1000, player.sought); assertTrue(player.isPlaying.value)
        assertFalse(result.isCompleted) // cached position is already beyond the cue
        player.positionMs.value = 1000; runCurrent()
        player.positionMs.value = 1700; runCurrent(); assertFalse(result.isCompleted)
        player.positionMs.value = 1750; runCurrent() // stops 250 ms early to absorb position lag
        assertEquals(800, result.await()); assertEquals(1, player.paused)
    }
    @Test fun cancellationAndStalledPlaybackPause() = runTest {
        val player = CuePlayer()
        val job = launch { replayShadowCue(player, 1000, 2000, 1f) }
        runCurrent(); job.cancelAndJoin(); assertEquals(1, player.paused)
        try { replayShadowCue(player, 1000, 2000, 1f); fail("Expected timeout") }
        catch (_: TimeoutCancellationException) { assertEquals(2, player.paused) }
    }
}
