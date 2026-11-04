package com.ziaee.zabankhan.watch

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

private class WatchBridge(private val view: WebView, private val controller: IframeController) {
    @JavascriptInterface fun ready() { view.post { controller.onReady() } }
    @JavascriptInterface fun time(seconds: Double, request: Int) { view.post { controller.onTime(seconds, request) } }
    @JavascriptInterface fun state(state: Int) { view.post { controller.onState(state) } }
    @JavascriptInterface fun error(code: Int) { view.post { controller.onError(code) } }
}

@Composable private fun PauseOnBackground(controller: VideoController) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) controller.pause() }
        lifecycle.addObserver(observer)
        onDispose { controller.pause(); lifecycle.removeObserver(observer) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable actual fun YoutubePlayer(videoId: String, onController: (VideoController) -> Unit, modifier: Modifier) {
    key(videoId) {
        val controller = remember { IframeController() }
        LaunchedEffect(controller) { onController(controller) }
        PauseOnBackground(controller)
        AndroidView(modifier = modifier, factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false; settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.isForMainFrame
                }
                webChromeClient = WebChromeClient()
                addJavascriptInterface(WatchBridge(this, controller), "WatchBridge")
                controller.evaluate = { script -> evaluateJavascript(script, null) }
                loadDataWithBaseURL("https://www.youtube-nocookie.com", youtubePlayerHtml(videoId), "text/html", "UTF-8", null)
            }
        }, onRelease = { view ->
            controller.close(); view.removeJavascriptInterface("WatchBridge"); view.stopLoading(); view.destroy()
        })
    }
}

private class LocalController(val player: ExoPlayer) : VideoController, VideoStatus {
    override val positionMs = MutableStateFlow(0L)
    override val isPlaying = MutableStateFlow(false)
    override val error = MutableStateFlow<String?>(null)
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) { val target = ms.coerceAtLeast(0); player.seekTo(target); positionMs.value = target }
    override fun setRate(r: Float) { if (r.isFinite() && r > 0) player.setPlaybackSpeed(r) }
}

@Composable actual fun LocalVideoPlayer(path: String, onController: (VideoController) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val controller = remember(path) { LocalController(ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path)))); prepare()
    }) }
    PauseOnBackground(controller)
    LaunchedEffect(controller) {
        onController(controller)
        while (true) { controller.positionMs.value = controller.player.currentPosition.coerceAtLeast(0); controller.isPlaying.value = controller.player.isPlaying; delay(200) }
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { controller.error.value = error.errorCodeName }
        }
        controller.player.addListener(listener)
        onDispose { controller.player.removeListener(listener); controller.player.release() }
    }
    AndroidView(modifier = modifier, factory = { PlayerView(it).apply { useController = true } }, update = { it.player = controller.player }, onRelease = { it.player = null })
}

@Composable actual fun rememberMediaPicker(kind: MediaFileKind, onPicked: (String?) -> Unit): () -> Unit =
    com.ziaee.zabankhan.files.rememberFilePicker(listOf(if (kind == MediaFileKind.VIDEO)
        com.ziaee.zabankhan.files.FileKind.VIDEO else com.ziaee.zabankhan.files.FileKind.SUBTITLE)) { onPicked(it?.path) }
