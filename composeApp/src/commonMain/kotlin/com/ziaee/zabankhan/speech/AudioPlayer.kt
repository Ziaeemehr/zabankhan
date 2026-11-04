package com.ziaee.zabankhan.speech

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val position: StateFlow<Long>
    /** Suspends until playback completes; failure throws, cancellation stops playback. */
    suspend fun play(path: String)
    fun stop()
}
expect fun createAudioPlayer(): AudioPlayer
