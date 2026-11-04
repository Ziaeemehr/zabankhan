package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.data.TextDao
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.Languages

fun transcriptBody(cues: List<SubtitleCue>): String = cues.joinToString("\n\n") { it.text }
fun transcriptOffsets(cues: List<SubtitleCue>): List<Int> {
    var offset = 0
    return cues.map { cue -> offset.also { offset += cue.text.length + 2 } }
}

suspend fun saveTranscript(dao: TextDao, identity: String, lang: LanguageSpec, title: String, source: String?, cues: List<SubtitleCue>): TextDocument {
    require(lang in Languages.learnable)
    val key = "watch:${lang.code}:$identity"
    val existing = dao.getByExternalKey(key)
    val document = existing?.copy(title = title, body = transcriptBody(cues), sourceUrl = source)
        ?: TextDocument(lang = lang.code, title = title, body = transcriptBody(cues), sourceUrl = source, externalKey = key)
    return if (existing != null) { dao.update(document); document } else document.copy(id = dao.insert(document))
}

fun videoTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "${seconds / 3600}:${(seconds / 60 % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
