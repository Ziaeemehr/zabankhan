package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.data.TextDao
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.LibraryFolder
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.Languages
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

fun transcriptBody(cues: List<SubtitleCue>): String = cues.joinToString("\n\n") { it.text }
fun transcriptOffsets(cues: List<SubtitleCue>): List<Int> {
    var offset = 0
    return cues.map { cue -> offset.also { offset += cue.text.length + 2 } }
}

/** [legacyCues]: an existing body saved in that older layout is kept, so its highlights and reading position stay valid. */
suspend fun loadTranscriptDocument(dao: TextDao, identity: String, lang: LanguageSpec, title: String, source: String?, cues: List<SubtitleCue>): TextDocument {
    require(lang in Languages.learnable)
    val key = "watch:${lang.code}:$identity"
    return dao.getByExternalKey(key) ?: TextDocument(id = -1, lang = lang.code, title = title,
        body = transcriptBody(cues), sourceUrl = source, externalKey = key)
}

/** Explicit [folderId] is honored including null, which selects the library root. */
suspend fun saveTranscript(dao: TextDao, identity: String, lang: LanguageSpec, title: String, source: String?, cues: List<SubtitleCue>, folderId: Long?, legacyCues: List<SubtitleCue>? = null): TextDocument {
    require(lang in Languages.learnable)
    val key = "watch:${lang.code}:$identity"
    val existing = dao.getByExternalKey(key)
    val body = transcriptBodyFor(existing?.body, cues, legacyCues)
    val document = existing?.copy(title = title, body = body, sourceUrl = source, folderId = folderId)
        ?: TextDocument(lang = lang.code, title = title, body = transcriptBody(cues), sourceUrl = source, externalKey = key, folderId = folderId)
    return if (existing != null) { dao.update(document); document } else document.copy(id = dao.insert(document))
}

suspend fun saveTranscriptToLibrary(database: AppDatabase, identity: String, lang: LanguageSpec, title: String,
    source: String?, cues: List<SubtitleCue>, folderId: Long?, newFolderName: String? = null,
    legacyCues: List<SubtitleCue>? = null): TextDocument = database.useWriterConnection { connection -> connection.immediateTransaction {
    val organizer = database.libraryOrganizer()
    val folderName = newFolderName?.trim()?.takeIf { it.isNotEmpty() }
    val selectedFolder = if (folderName != null) {
        organizer.findFolderByName(lang.code, folderName)?.id ?: organizer.insertFolder(LibraryFolder(lang = lang.code, name = folderName))
    } else folderId?.takeIf { id -> organizer.getFoldersOnce(lang.code).any { it.id == id } }
    saveTranscript(database.texts(), identity, lang, title, source, cues, selectedFolder, legacyCues)
} }

internal fun transcriptBodyFor(existingBody: String?, cues: List<SubtitleCue>, legacyCues: List<SubtitleCue>?): String =
    existingBody?.takeIf { legacyCues != null && it == transcriptBody(legacyCues) } ?: transcriptBody(cues)

fun videoTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "${seconds / 3600}:${(seconds / 60 % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
