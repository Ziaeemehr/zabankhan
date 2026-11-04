@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.ziaee.zabankhan.watch

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.ziaee.zabankhan.pdf.AppFiles
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.WebKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

private class WatchHandler(private val controller: IframeController) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        val data = didReceiveScriptMessage.body as? Map<*, *> ?: return
        val value = data["value"] as? Number
        when (data["type"]) {
            "ready" -> controller.onReady()
            "time" -> controller.onTime(value?.toDouble() ?: return, (data["request"] as? Number)?.toInt() ?: 0)
            "state" -> controller.onState(value?.toInt() ?: return)
            "error" -> controller.onError(value?.toInt() ?: return)
        }
    }
}

@Composable private fun PauseOnBackground(controller: VideoController) {
    DisposableEffect(controller) {
        val token = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { controller.pause() }
        onDispose { controller.pause(); NSNotificationCenter.defaultCenter.removeObserver(token) }
    }
}

@Composable actual fun YoutubePlayer(videoId: String, onController: (VideoController) -> Unit, modifier: Modifier) {
    key(videoId) {
        val controller = remember { IframeController() }
        val handler = remember { WatchHandler(controller) }
        PauseOnBackground(controller)
        LaunchedEffect(controller) { onController(controller) }
        UIKitView(modifier = modifier, factory = {
            val config = WKWebViewConfiguration().apply {
                allowsInlineMediaPlayback = true
                mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
                userContentController.addScriptMessageHandler(handler, "watch")
            }
            WKWebView(frame = platform.CoreGraphics.CGRectZero.readValue(), configuration = config).apply {
                controller.evaluate = { evaluateJavaScript(it, null) }
                loadHTMLString(youtubePlayerHtml(videoId), NSURL(string = "https://www.youtube-nocookie.com"))
            }
        }, onRelease = { view ->
            controller.close(); view.configuration.userContentController.removeScriptMessageHandlerForName("watch"); view.stopLoading()
        })
    }
}

private class VideoView(val playerLayer: AVPlayerLayer) : UIView(frame = platform.CoreGraphics.CGRectZero.readValue()) {
    init { layer.addSublayer(playerLayer) }
    override fun layoutSubviews() { super.layoutSubviews(); playerLayer.frame = bounds }
}

private class LocalController(val player: AVPlayer) : VideoController, VideoStatus {
    override val positionMs = MutableStateFlow(0L)
    override val isPlaying = MutableStateFlow(false)
    override val error = MutableStateFlow<String?>(null)
    private var rate = 1f
    override fun play() { player.play(); player.rate = rate; isPlaying.value = true }
    override fun pause() { player.pause(); isPlaying.value = false }
    override fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0)
        player.seekToTime(CMTimeMake(target, 1000), toleranceBefore = kCMTimeZero.readValue(), toleranceAfter = kCMTimeZero.readValue())
        positionMs.value = target
    }
    override fun setRate(r: Float) { if (r.isFinite() && r > 0) { rate = r; if (isPlaying.value) player.rate = r } }
}

@Composable actual fun LocalVideoPlayer(path: String, onController: (VideoController) -> Unit, modifier: Modifier) {
    val controller = remember(path) { LocalController(AVPlayer(uRL = NSURL.fileURLWithPath(path))) }
    PauseOnBackground(controller)
    LaunchedEffect(controller) { onController(controller) }
    DisposableEffect(controller) {
        val observer = controller.player.addPeriodicTimeObserverForInterval(CMTimeMake(1, 5), dispatch_get_main_queue()) { time ->
            val seconds = CMTimeGetSeconds(time)
            if (seconds.isFinite() && seconds >= 0) controller.positionMs.value = (seconds * 1000).toLong()
            controller.isPlaying.value = controller.player.rate > 0
            controller.player.currentItem?.error?.let { controller.error.value = it.localizedDescription }
        }
        onDispose { controller.pause(); controller.player.removeTimeObserver(observer); controller.player.replaceCurrentItemWithPlayerItem(null) }
    }
    UIKitView(modifier = modifier, factory = {
        VideoView(AVPlayerLayer.playerLayerWithPlayer(controller.player).apply { videoGravity = AVLayerVideoGravityResizeAspect })
    }, update = { it.playerLayer.player = controller.player }, onRelease = { it.playerLayer.player = null })
}

@Composable actual fun rememberMediaPicker(kind: MediaFileKind, onPicked: (String?) -> Unit): () -> Unit =
    com.ziaee.zabankhan.files.rememberFilePicker(listOf(if (kind == MediaFileKind.VIDEO)
        com.ziaee.zabankhan.files.FileKind.VIDEO else com.ziaee.zabankhan.files.FileKind.SUBTITLE)) { onPicked(it?.path) }
