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

private class MediaPickerDelegate(private val kind: MediaFileKind, private val scope: CoroutineScope, val picked: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { picked(null) }
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return picked(null)
        val scoped = url.startAccessingSecurityScopedResource()
        scope.launch {
            val path = try { withContext(Dispatchers.IO) {
                val ext = url.pathExtension.orEmpty().lowercase()
                require(ext in if (kind == MediaFileKind.VIDEO) listOf("mp4", "m4v", "mov") else listOf("srt", "vtt"))
                val fs = FileSystem.SYSTEM
                val directory = "${AppFiles.filesDir}/watch".toPath()
                fs.createDirectories(directory)
                val file = directory / (NSUUID().UUIDString + ".$ext")
                try {
                    fs.source(requireNotNull(url.path).toPath()).buffer().use { input -> fs.sink(file).buffer().use { it.writeAll(input) } }
                    fs.write((file.toString() + ".title").toPath()) { writeUtf8(url.lastPathComponent.orEmpty().substringBeforeLast('.')) }
                    file.toString()
                } catch (e: Exception) { fs.delete(file, mustExist = false); throw e }
            } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            finally { if (scoped) url.stopAccessingSecurityScopedResource() }
            picked(path)
        }
    }
}

@Composable actual fun rememberMediaPicker(kind: MediaFileKind, onPicked: (String?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val delegate = remember(kind) { MediaPickerDelegate(kind, scope) { callback(it) } }
    return remember(kind) { {
        val types = if (kind == MediaFileKind.VIDEO) listOf(UTTypeMPEG4Movie, UTTypeQuickTimeMovie) else listOfNotNull(UTType.typeWithFilenameExtension("srt"), UTType.typeWithFilenameExtension("vtt"))
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        picker.delegate = delegate
        var root = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (root?.presentedViewController != null) root = root.presentedViewController
        root?.presentViewController(picker, true, null) ?: callback(null)
    } }
}
