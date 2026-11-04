package com.ziaee.zabankhan.speech.edge

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.splitSentences
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.roundToInt
import kotlin.time.Instant

@Serializable
data class Boundary(val text: String, val offsetMs: Long, val durationMs: Long, val kind: String)
data class EdgeSpeech(val mp3: ByteArray, val boundaries: List<Boundary>)
data class SpeechChunk(val text: String, val start: Int)
data class TimedRange(val offsetMs: Long, val durationMs: Long, val range: IntRange)

object EdgeProtocol {
    const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val VERSION = "1-143.0.3650.75"
    const val ENDPOINT = "wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1"
    fun gec(now: Instant): String {
        val seconds = now.epochSeconds + 11_644_473_600L
        val ticks = (seconds - seconds % 300) * 10_000_000L
        return "$ticks$TOKEN".encodeUtf8().sha256().hex().uppercase()
    }
    fun ratePercent(rate: Float) = ((rate.coerceIn(.5f, 2f) - 1f) * 100).roundToInt()
    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
    fun escape(text: String): String = buildString {
        text.forEach { c -> append(when(c) {
            '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '\'' -> "&apos;"; '"' -> "&quot;"
            else -> if (c.code in 0..8 || c.code in 11..12 || c.code in 14..31) " " else c.toString()
        }) }
    }
    fun unescape(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
    fun ssml(text: String, voice: String, locale: String, rate: Int, pitch: Int = 0, volume: Int = 0) =
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${escape(locale)}'>" +
        "<voice name='${escape(voice)}'><prosody pitch='${signed(pitch)}Hz' rate='${signed(rate)}%' volume='${signed(volume)}%'>" +
        escape(text) + "</prosody></voice></speak>"
    fun timestamp(now: Instant): String {
        val d = now.toLocalDateTime(TimeZone.UTC)
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        fun pad(n: Int) = n.toString().padStart(2, '0')
        return "${days[d.dayOfWeek.ordinal]} ${months[d.monthNumber - 1]} ${pad(d.dayOfMonth)} ${d.year} ${pad(d.hour)}:${pad(d.minute)}:${pad(d.second)} GMT+0000 (Coordinated Universal Time)"
    }
    fun config(timestamp: String) = "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"true","wordBoundaryEnabled":"true"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""" + "\r\n"
    fun ssmlFrame(id: String, timestamp: String, ssml: String) = "X-RequestId:$id\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssml"
    fun headers(raw: String): Map<String, String> = raw.split("\r\n").mapNotNull {
        val colon = it.indexOf(':')
        if (colon <= 0) null else it.substring(0, colon).lowercase() to it.substring(colon + 1).trim()
    }.toMap()
    fun binaryAudio(bytes: ByteArray): ByteArray {
        require(bytes.size >= 2) { "Missing binary header length" }
        val length = ((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)
        require(length <= bytes.size - 2) { "Truncated audio header" }
        val headers = headers(bytes.decodeToString(2, 2 + length))
        require(headers["path"] == "audio") { "Unexpected binary path" }
        val audio = bytes.copyOfRange(2 + length, bytes.size)
        val type = headers["content-type"]
        require(type == "audio/mpeg" || (type == null && audio.isEmpty())) { "Invalid audio content type" }
        require(audio.isNotEmpty() || type == null) { "Empty audio frame" }
        return audio
    }
    fun metadata(json: String): List<Boundary> = Json.parseToJsonElement(json).jsonObject["Metadata"]!!.jsonArray.mapNotNull { entry ->
        val obj = entry.jsonObject
        val kind = obj["Type"]!!.jsonPrimitive.content
        if (kind != "SentenceBoundary" && kind != "WordBoundary") null else {
            val data = obj["Data"]!!.jsonObject
            Boundary(unescape(data["text"]!!.jsonObject["Text"]!!.jsonPrimitive.content),
                data["Offset"]!!.jsonPrimitive.long / 10_000, data["Duration"]!!.jsonPrimitive.long / 10_000, kind)
        }
    }
    /** Keep exact UTF-16 document coordinates, including repeated sentences and whitespace. */
    fun chunks(text: String, lang: LanguageSpec, max: Int = 1000): List<SpeechChunk> {
        require(max >= 2)
        val ends = splitSentences(text, lang).map { it.last + 1 }
        return buildList {
            var start = 0
            while (start < text.length) {
                while (start < text.length && text[start].isWhitespace()) start++
                if (start == text.length) break
                val limit = minOf(start + max, text.length)
                var end = ends.lastOrNull { it > start && it <= limit } ?: limit
                if (end == limit && limit < text.length) {
                    val space = (start until limit).lastOrNull { text[it].isWhitespace() }
                    if (space != null && space > start) end = space
                }
                if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
                var trimmed = end
                while (trimmed > start && text[trimmed - 1].isWhitespace()) trimmed--
                if (trimmed > start) add(SpeechChunk(text.substring(start, trimmed), start))
                start = end
            }
        }
    }
    fun ranges(text: String, boundaries: List<Boundary>, base: Int = 0): List<TimedRange> {
        val sentences = boundaries.filter { it.kind == "SentenceBoundary" }
        val chosen = sentences.ifEmpty { boundaries.filter { it.kind == "WordBoundary" } }
        // Service normalizes runs of whitespace; retain a map back to the original text.
        val indices = mutableListOf<Int>()
        val normalized = buildString {
            text.forEachIndexed { i, c ->
                if (!c.isWhitespace() || isEmpty() || last() != ' ') { append(if (c.isWhitespace()) ' ' else c); indices += i }
            }
        }
        var cursor = 0
        return chosen.sortedBy { it.offsetMs }.mapNotNull { boundary ->
            val needle = boundary.text.replace(Regex("\\s+"), " ").trim()
            val found = if (needle.isEmpty()) -1 else normalized.indexOf(needle, cursor)
            if (found < 0) null else {
                cursor = found + needle.length
                TimedRange(boundary.offsetMs, boundary.durationMs, (base + indices[found])..(base + indices[cursor - 1]))
            }
        }
    }
    fun rangeAt(ranges: List<TimedRange>, position: Long): IntRange? = ranges.lastOrNull {
        position >= it.offsetMs && position < it.offsetMs + it.durationMs
    }?.range
}
