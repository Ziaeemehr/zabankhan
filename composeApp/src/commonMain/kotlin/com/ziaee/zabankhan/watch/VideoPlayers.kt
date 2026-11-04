package com.ziaee.zabankhan.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeSource

interface VideoController {
    val positionMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setRate(r: Float)
}

/** Players may expose readiness and errors without adding platform types to the UI. */
interface VideoStatus {
    val error: StateFlow<String?>
}

@Composable expect fun YoutubePlayer(videoId: String, onController: (VideoController) -> Unit, modifier: Modifier = Modifier)
@Composable expect fun LocalVideoPlayer(path: String, onController: (VideoController) -> Unit, modifier: Modifier = Modifier)

enum class MediaFileKind { VIDEO, SUBTITLES }
@Composable expect fun rememberMediaPicker(kind: MediaFileKind, onPicked: (String?) -> Unit): () -> Unit

/** Shared bridge protocol; pending controls are replayed when the iframe becomes ready. */
internal class IframeController : VideoController, VideoStatus {
    override val positionMs = MutableStateFlow(0L)
    override val isPlaying = MutableStateFlow(false)
    override val error = MutableStateFlow<String?>(null)
    var evaluate: ((String) -> Unit)? = null
    private var ready = false
    private var desiredPlaying: Boolean? = null
    private var rate = 1f
    private var target: Long? = null
    private var pausedAt: Long? = null
    private val origin = TimeSource.Monotonic.markNow()
    private val gate = SeekTimeGate { origin.elapsedNow().inWholeMilliseconds }
    private fun send(script: String) { if (ready) evaluate?.invoke(script) }
    override fun play() { desiredPlaying = true; pausedAt = null; send("player.playVideo()") }
    override fun pause() { desiredPlaying = false; pausedAt = origin.elapsedNow().inWholeMilliseconds; isPlaying.value = false; send("player.pauseVideo()") }
    override fun seekTo(ms: Long) {
        target = ms.coerceAtLeast(0); gate.seekTo(target!!)
        positionMs.value = target!!
        send("seekRequest=${gate.request};player.seekTo(${target!! / 1000.0},true)")
    }
    override fun setRate(r: Float) { if (r.isFinite() && r > 0) { rate = r; send("player.setPlaybackRate($r)") } }
    fun onReady() {
        ready = true; error.value = null
        target?.let(::seekTo); setRate(rate)
        desiredPlaying?.let { if (it) play() else pause() }
    }
    fun onTime(seconds: Double, request: Int) {
        if (seconds.isFinite() && seconds >= 0) gate.acceptTime(request, kotlin.math.round(seconds * 1000).toLong())?.let { positionMs.value = it }
    }
    fun onState(state: Int) {
        val paused = pausedAt
        if (state == 1 && paused != null && origin.elapsedNow().inWholeMilliseconds - paused < 1_500) {
            send("player.pauseVideo()"); return
        }
        isPlaying.value = state == 1
    }
    fun onError(code: Int) { error.value = code.toString(); isPlaying.value = false }
    fun close() { pause(); ready = false; evaluate = null }
}

internal fun youtubePlayerHtml(videoId: String): String {
    require(Regex("[A-Za-z0-9_-]{11}").matches(videoId))
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;background:#000;overflow:hidden}#player{position:fixed;inset:0;width:100%;height:100%;border:0}</style></head>
        <body><div id="player"></div><script>
        var player,seekRequest=0;
        function bridge(type,value,request){
          if(window.webkit&&window.webkit.messageHandlers.watch){window.webkit.messageHandlers.watch.postMessage({type:type,value:value,request:request||0});}
          else if(window.WatchBridge){if(type==='ready')WatchBridge.ready();else if(type==='time')WatchBridge.time(value,request);else if(type==='state')WatchBridge.state(value);else WatchBridge.error(value);}
        }
        function onYouTubeIframeAPIReady(){player=new YT.Player('player',{videoId:'$videoId',host:'https://www.youtube-nocookie.com',
          playerVars:{playsinline:1,rel:0,origin:'https://www.youtube-nocookie.com'},events:{onReady:function(){bridge('ready',0);},
          onStateChange:function(e){bridge('state',e.data);},onError:function(e){bridge('error',e.data);}}});}
        setInterval(function(){if(player&&player.getCurrentTime)bridge('time',player.getCurrentTime(),seekRequest);},200);
        </script><script src="https://www.youtube.com/iframe_api"></script></body></html>
    """.trimIndent()
}
