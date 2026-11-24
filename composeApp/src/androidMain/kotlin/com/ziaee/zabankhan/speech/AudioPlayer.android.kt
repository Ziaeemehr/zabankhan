package com.ziaee.zabankhan.speech

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class AndroidAudioPlayer : AudioPlayer {
    private val current = MutableStateFlow(0L)
    override val position = current.asStateFlow()
    private var player: ExoPlayer? = null
    override suspend fun play(path: String) = playFrom(path, 0)
    override suspend fun playFrom(path: String, startMs: Long) = withContext(Dispatchers.Main.immediate) {
        stop()
        // Edge produces constant-bitrate MP3s that may have no embedded seek table.
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val engine = ExoPlayer.Builder(speechContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(speechContext, extractors))
            .build().also { player = it }
        val polling = launch { while (isActive) { current.value = engine.currentPosition.coerceAtLeast(0); delay(30) } }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                engine.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED && continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                })
                engine.setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))), startMs.coerceAtLeast(0))
                engine.prepare()
                engine.play()
            }
        } finally {
            polling.cancel()
            if (player === engine) player = null
            engine.release(); current.value = 0
        }
    }
    override fun stop() { player?.stop(); current.value = 0 }
}
actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()
