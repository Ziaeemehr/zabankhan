package com.ziaee.zabankhan.speech.edge

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import okio.Buffer
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

open class EdgeTtsClient(private val http: HttpClient) {
    @OptIn(ExperimentalUuidApi::class)
    open suspend fun synthesize(text: String, voice: String, locale: String, ratePercent: Int): EdgeSpeech = withTimeout(60_000) {
        fun id() = Uuid.random().toString().replace("-", "")
        val now = Clock.System.now()
        val url = "${EdgeProtocol.ENDPOINT}?Ocp-Apim-Subscription-Key=${EdgeProtocol.TOKEN}" +
            "&ConnectionId=${id()}&Sec-MS-GEC=${EdgeProtocol.gec(now)}&Sec-MS-GEC-Version=${EdgeProtocol.VERSION}"
        val audio = Buffer()
        val boundaries = mutableListOf<Boundary>()
        var ended = false
        http.webSocket(urlString = url, request = {
            header("Pragma", "no-cache")
            header("Cache-Control", "no-cache")
            header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            header("Accept-Encoding", "gzip, deflate, br, zstd")
            header("Accept-Language", "en-US,en;q=0.9")
            header("Cookie", "muid=${id().uppercase()};")
        }) {
            val timestamp = EdgeProtocol.timestamp(now)
            send(Frame.Text(EdgeProtocol.config(timestamp)))
            send(Frame.Text(EdgeProtocol.ssmlFrame(id(), timestamp, EdgeProtocol.ssml(text, voice, locale, ratePercent))))
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> audio.write(EdgeProtocol.binaryAudio(frame.readBytes()))
                    is Frame.Text -> {
                        val message = frame.readText()
                        val split = message.indexOf("\r\n\r\n")
                        require(split >= 0) { "Missing text frame headers" }
                        when (EdgeProtocol.headers(message.substring(0, split))["path"]) {
                            "turn.end" -> { ended = true; break }
                            "audio.metadata" -> boundaries += EdgeProtocol.metadata(message.substring(split + 4))
                        }
                    }
                    else -> Unit
                }
            }
        }
        check(ended && audio.size > 0 && boundaries.isNotEmpty()) { "Incomplete online speech response" }
        EdgeSpeech(audio.readByteArray(), boundaries)
    }
}
