package com.ziaee.zabankhan.speech

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val position: StateFlow<Long>
    /** Suspends until playback completes; failure throws, cancellation stops playback. */
    suspend fun play(path: String)
    /** Start an already synthesized clip at its real audio position. */
    suspend fun playFrom(path: String, startMs: Long) = play(path)
    fun stop()
}
expect fun createAudioPlayer(): AudioPlayer
