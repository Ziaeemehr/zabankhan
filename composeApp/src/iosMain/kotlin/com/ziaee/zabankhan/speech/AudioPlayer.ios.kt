@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.speech

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.*
import platform.Foundation.NSURL

private class IosAudioPlayer : AudioPlayer {
    private val current = MutableStateFlow(0L)
    override val position = current.asStateFlow()
    private var player: AVAudioPlayer? = null
    override suspend fun play(path: String) = withContext(Dispatchers.Main.immediate) {
        stop()
        val session = AVAudioSession.sharedInstance()
        check(session.setCategory(AVAudioSessionCategoryPlayback, error = null))
        check(session.setActive(true, error = null))
        val engine = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
        player = engine
        try {
            check(engine.prepareToPlay() && engine.play()) { "Audio playback unavailable" }
            do {
                current.value = (engine.currentTime * 1000).toLong()
                delay(30)
            } while (engine.playing)
            check(engine.duration > 0 && engine.currentTime + .1 >= engine.duration) { "Audio playback interrupted" }
        } finally {
            engine.stop()
            if (player === engine) player = null
            current.value = 0
        }
    }
    override fun stop() { player?.stop(); current.value = 0 }
}
actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()
