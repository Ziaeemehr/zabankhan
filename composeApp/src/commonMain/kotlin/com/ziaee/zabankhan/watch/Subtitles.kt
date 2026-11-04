package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

data class SubtitleToken(val text: String, val isWord: Boolean)

fun tokenizeSubtitle(text: String, lang: LanguageSpec): List<SubtitleToken> = buildList {
    var previous = 0
    for (token in tokenizeSurface(text, lang)) {
        if (token.start > previous) add(SubtitleToken(text.substring(previous, token.start), false))
        val normalized = token.text.replace('’', '\'').replace('ʼ', '\'').lowercase()
        val prefix = lang.elisionPrefixes.sortedByDescending { it.length }.firstOrNull { normalized.startsWith(it) && normalized.length > it.length }
        if (prefix != null) {
            add(SubtitleToken(token.text.take(prefix.length), true))
            add(SubtitleToken(token.text.drop(prefix.length), true))
        } else add(SubtitleToken(token.text, true))
        previous = token.end
    }
    if (previous < text.length) add(SubtitleToken(text.substring(previous), false))
}

fun findCueIndex(cues: List<SubtitleCue>, timeMs: Long): Int {
    var low = 0
    var high = cues.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (cues[mid].startMs <= timeMs) {
            found = mid
            low = mid + 1
        } else high = mid - 1
    }
    return found.takeIf { it >= 0 && timeMs < cues[it].endMs } ?: -1
}

internal fun cleanSubtitleText(text: String): String = decodeSubtitleEntities(
    text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
).replace('\u00a0', ' ').lines().joinToString("\n") { it.trim() }.trim()

internal fun normalizeCues(cues: List<SubtitleCue>): List<SubtitleCue> {
    val sorted = cues.filter { it.startMs >= 0 && it.endMs > it.startMs && it.text.isNotBlank() }
        .sortedBy { it.startMs }
        .groupBy { it.startMs }.map { (_, sameStart) ->
            // Two speakers shown at once in an imported file: keep both lines in one cue.
            sameStart.reduce { a, b -> a.copy(endMs = maxOf(a.endMs, b.endMs), text = a.text + "\n" + b.text) }
        }
    return sorted.mapIndexedNotNull { index, cue ->
        val end = minOf(cue.endMs, sorted.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE)
        cue.copy(endMs = end).takeIf { end > cue.startMs }
    }
}

private fun timestamp(raw: String): Long? {
    val parts = raw.replace(',', '.').split(':')
    if (parts.size !in 2..3) return null
    val secondsParts = parts.last().split('.')
    if (secondsParts.size != 2 || secondsParts[1].length != 3) return null
    val seconds = secondsParts[0].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
    val millis = secondsParts[1].toLongOrNull()?.takeIf { it in 0..999 } ?: return null
    val minutes = parts[parts.lastIndex - 1].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
    val hours = if (parts.size == 3) parts[0].toLongOrNull()?.takeIf { it in 0..100_000 } ?: return null else 0
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}

private fun parseTimedBlocks(input: String, vtt: Boolean): List<SubtitleCue> {
    val blocks = input.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\n[ \\t]*\n"))
    val timing = Regex("^\\s*(\\S+)\\s+-->\\s+(\\S+).*$")
    val cues = blocks.mapNotNull { block ->
        val lines = block.trim().lines()
        if (vtt && lines.firstOrNull()?.let { it == "WEBVTT" || it.startsWith("WEBVTT ") ||
                    it == "STYLE" || it == "REGION" || it == "NOTE" || it.startsWith("NOTE ") } == true) return@mapNotNull null
        val index = lines.indexOfFirst { timing.matches(it) }
        if (index !in 0..1) return@mapNotNull null
        val match = timing.matchEntire(lines[index]) ?: return@mapNotNull null
        val start = timestamp(match.groupValues[1]) ?: return@mapNotNull null
        val end = timestamp(match.groupValues[2]) ?: return@mapNotNull null
        SubtitleCue(start, end, cleanSubtitleText(lines.drop(index + 1).joinToString("\n")))
    }
    return normalizeCues(cues)
}

object SrtParser {
    fun parse(input: String): List<SubtitleCue> = parseTimedBlocks(input, false)
}

object VttParser {
    fun parse(input: String): List<SubtitleCue> = parseTimedBlocks(input, true)
}

object YoutubeTimedTextParser {
    fun parse(input: String, lang: LanguageSpec): List<SubtitleCue> {
        val events = runCatching { Json.parseToJsonElement(input).jsonObject["events"]?.jsonArray }.getOrNull() ?: return emptyList()
        val cues = mutableListOf<SubtitleCue>()
        for (element in events) {
            val event = element as? JsonObject ?: continue
            val segments = event["segs"] as? JsonArray ?: continue
            val start = event.long("tStartMs", -1)
            val duration = event.long("dDurationMs", 0)
            if (start < 0 || duration <= 0 || start > Long.MAX_VALUE - duration) continue
            val lookahead = MutableList(segments.size + 1) { "" }
            for (i in segments.lastIndex downTo 0) {
                lookahead[i] = cleanSubtitleText((segments[i] as? JsonObject)?.string("utf8").orEmpty()).ifBlank { lookahead[i + 1] }
            }
            var cueStart = start
            val text = StringBuilder()
            for (i in segments.indices) {
                text.append((segments[i] as? JsonObject)?.string("utf8").orEmpty())
                val next = segments.getOrNull(i + 1) as? JsonObject
                val offset = next?.long("tOffsetMs", -1) ?: -1
                if (isSentenceEnd(cleanSubtitleText(text.toString()), lookahead[i + 1], lang) && offset in 1 until duration && start + offset > cueStart) {
                    cues += SubtitleCue(cueStart, start + offset, cleanSubtitleText(text.toString()))
                    cueStart = start + offset
                    text.clear()
                }
            }
            cues += SubtitleCue(cueStart, start + duration, cleanSubtitleText(text.toString()))
        }
        return normalizeCues(cues)
    }
}

internal fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.long(key: String, default: Long): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: default

fun parseSubtitleFile(name: String?, content: String, lang: LanguageSpec): List<SubtitleCue> {
    val trimmed = content.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    return when {
        trimmed.startsWith("{") -> YoutubeTimedTextParser.parse(trimmed, lang)
        trimmed.startsWith("WEBVTT") || name?.endsWith(".vtt", true) == true -> VttParser.parse(content)
        else -> SrtParser.parse(content)
    }
}

internal fun decodeSubtitleEntities(text: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z]+);").replace(text) { match ->
    val raw = match.groupValues[1]
    val code = when {
        raw.startsWith("#x", true) -> raw.drop(2).toIntOrNull(16)
        raw.startsWith("#") -> raw.drop(1).toIntOrNull()
        else -> null
    }
    when {
        code != null && code in 1..0x10ffff && code !in 0xd800..0xdfff -> if (code <= 0xffff) code.toChar().toString() else {
            val n = code - 0x10000
            "${(0xd800 + (n shr 10)).toChar()}${(0xdc00 + (n and 1023)).toChar()}"
        }
        else -> mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "agrave" to "à", "ccedil" to "ç", "ocirc" to "ô", "ucirc" to "û", "icirc" to "î")[raw] ?: match.value
    }
}
