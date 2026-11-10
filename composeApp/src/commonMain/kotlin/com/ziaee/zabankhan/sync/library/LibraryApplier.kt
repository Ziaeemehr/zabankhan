package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.HighlightEntry
import com.ziaee.zabankhan.data.LibraryFolder
import com.ziaee.zabankhan.data.LibraryTag
import com.ziaee.zabankhan.data.PdfHighlight
import com.ziaee.zabankhan.data.PdfInk
import com.ziaee.zabankhan.data.PdfTextNote
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.data.TextTagCrossRef
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull

data class TextFilePaths(val pdfPath: String?, val bodyPath: String?, val imagePath: String?, val inlineBody: String?)

class LibraryApplier(private val database: AppDatabase) {
    private val deletedPaths = mutableListOf<String>()

    suspend fun apply(items: List<LibraryItem>, localPaths: Map<String, TextFilePaths>): List<String> {
        deletedPaths.clear()
        val dao = database.librarySync()
        val applied = mutableListOf<String>()
        for (item in items.filter { it.kind == LibraryKinds.FOLDER && it.deleted }) {
            val old = dao.folderBySyncId(item.key.substringAfter(':')) ?: continue
            dao.clearFolderTexts(old.id); dao.clearFolderChildren(old.id); dao.deleteFolder(old.id); applied += item.key
        }
        val existingParents = dao.folders().let { rows -> val sidById = rows.associate { it.id to it.syncId }; rows.associate { it.syncId to it.parentId?.let(sidById::get) } }
        for ((item, parentSid) in orderFolders(existingParents, items.filter { it.kind == LibraryKinds.FOLDER && !it.deleted && it.payload != null })) {
            val payload = item.payload ?: continue
            val sid = item.key.substringAfter(':')
            val old = dao.folderBySyncId(sid)
            val parent = parentSid?.let { dao.folderBySyncId(it)?.id }
            val row = LibraryFolder(id = old?.id ?: 0, lang = payload.string("lang").orEmpty(), name = payload.string("name").orEmpty(), parentId = parent, createdAtMs = payload.long("createdAtMs") ?: old?.createdAtMs ?: 0, syncId = sid)
            if (old == null) dao.insertFolder(row) else dao.updateFolder(row)
            applied += item.key
        }
        for (item in items) {
            val payload = item.payload
            val parts = item.key.split(':')
            when (item.kind) {
                LibraryKinds.FOLDER -> {} // handled parent-first before this loop
                LibraryKinds.TEXT -> {
                    val sid = parts.getOrNull(1) ?: continue
                    val old = dao.textBySyncId(sid)
                    if (item.deleted) {
                        if (old != null) {
                            listOf(old.pdfPath, old.bodyPath, old.imagePath).filterNotNull().forEach(deletedPaths::add)
                            dao.deleteTextBySyncId(sid); applied += item.key
                        }
                    } else if (payload != null) {
                        val paths = localPaths[item.key]
                        if (old == null && paths == null) continue
                        // A named file with no usable hash must not null an existing local path.
                        fun keep(placed: String?, oldPath: String?, nameKey: String) = placed ?: oldPath?.takeIf { !payload.string(nameKey).isNullOrBlank() }
                        val folderId = payload.string("folderSyncId")?.let { dao.folderBySyncId(it)?.id }
                        val row = TextDocument(id = old?.id ?: 0, lang = payload.string("lang").orEmpty(), title = payload.string("title").orEmpty(), body = if (paths != null) paths.inlineBody.orEmpty() else old?.body.orEmpty(), createdAtMs = payload.long("createdAtMs") ?: old?.createdAtMs ?: 0,
                            lastAccessedAtMs = old?.lastAccessedAtMs ?: 0, lastOffset = old?.lastOffset ?: 0, folderId = folderId,
                            sourceUrl = payload.string("sourceUrl"), sourceName = payload.string("sourceName"), author = payload.string("author"), license = payload.string("license"), publishedAt = payload.long("publishedAt"),
                            imagePath = if (paths != null) keep(paths.imagePath, old?.imagePath, "imageName") else old?.imagePath, bodyPath = if (paths != null) keep(paths.bodyPath, old?.bodyPath, "bodyName") else old?.bodyPath, lastChunkIndex = old?.lastChunkIndex ?: 0, lastPositionMs = old?.lastPositionMs ?: 0,
                            voice = payload.string("voice").orEmpty(), ratePercent = payload.int("ratePercent") ?: 0, translationLang = payload.string("translationLang") ?: "fa", level = payload.string("level"),
                            pdfPath = if (paths != null) keep(paths.pdfPath, old?.pdfPath, "pdfName") else old?.pdfPath, externalKey = old?.externalKey, pinned = payload.bool("pinned") ?: false, readMark = payload.int("readMark") ?: 0, syncId = sid)
                        if (old == null) dao.insertText(row) else dao.updateText(row)
                        applied += item.key
                    }
                }
                LibraryKinds.POSITION -> {
                    if (item.deleted) continue
                    val sid = parts.getOrNull(1) ?: continue
                    if (payload != null && dao.textBySyncId(sid) != null) {
                        dao.updatePosition(sid, payload.int("lastOffset") ?: 0, payload.int("lastChunkIndex") ?: 0, payload.long("lastPositionMs") ?: 0, payload.long("lastAccessedAtMs") ?: 0); applied += item.key
                    }
                }
                LibraryKinds.TAG -> {
                    val lang = parts.getOrNull(1) ?: continue; val name = parts.drop(2).joinToString(":")
                    val old = dao.tagByName(lang, name)
                    if (item.deleted) { if (old != null) { dao.deleteTagRefs(old.id); dao.deleteTag(old.id); applied += item.key } }
                    else if (payload != null) { dao.insertTag(LibraryTag(name = name, lang = lang, createdAtMs = payload.long("createdAtMs") ?: 0)); applied += item.key }
                }
                LibraryKinds.TEXT_TAG -> {
                    val textId = parts.getOrNull(1)?.let { dao.textBySyncId(it)?.id } ?: continue
                    val lang = parts.getOrNull(2) ?: continue; val name = parts.drop(3).joinToString(":")
                    val tag = dao.tagByName(lang, name)
                    if (item.deleted) { if (tag != null) { dao.deleteTextTag(textId, tag.id); applied += item.key } }
                    else if (payload != null) { val actualTag = tag ?: run { dao.insertTag(LibraryTag(name = name, lang = lang)); dao.tagByName(lang, name) } ?: continue; dao.insertTextTag(TextTagCrossRef(textId, actualTag.id)); applied += item.key }
                }
                LibraryKinds.INK -> {
                    val textId = parts.getOrNull(1)?.let { dao.textBySyncId(it)?.id } ?: continue; val page = parts.getOrNull(2)?.toIntOrNull() ?: continue
                    if (item.deleted) { dao.deleteInk(textId, page); applied += item.key } else if (payload != null) { val old = dao.inkRow(textId, page); val row = PdfInk(id = old?.id ?: 0, textId = textId, page = page, strokes = payload.string("strokes").orEmpty()); if (old == null) dao.insertInk(row) else dao.updateInk(row); applied += item.key }
                }
                LibraryKinds.NOTE -> {
                    val textId = parts.getOrNull(1)?.let { dao.textBySyncId(it)?.id } ?: continue; val page = parts.getOrNull(2)?.toIntOrNull() ?: continue; val start = parts.getOrNull(3)?.toIntOrNull() ?: continue; val end = parts.getOrNull(4)?.toIntOrNull() ?: continue
                    if (item.deleted) { dao.deleteNote(textId, page, start, end); applied += item.key } else if (payload != null) { val old = dao.noteRow(textId, page, start, end); val row = PdfTextNote(id = old?.id ?: 0, textId = textId, page = page, startWord = start, endWord = end, selectedText = payload.string("selectedText").orEmpty(), body = payload.string("body").orEmpty()); if (old == null) dao.insertNote(row) else dao.updateNote(row); applied += item.key }
                }
                LibraryKinds.PDF_HIGHLIGHT -> {
                    val textId = parts.getOrNull(1)?.let { dao.textBySyncId(it)?.id } ?: continue; val page = parts.getOrNull(2)?.toIntOrNull() ?: continue; val start = parts.getOrNull(3)?.toIntOrNull() ?: continue; val end = parts.getOrNull(4)?.toIntOrNull() ?: continue
                    if (item.deleted) { dao.deletePdfHighlight(textId, page, start, end); applied += item.key } else if (payload != null) { val old = dao.pdfHighlightRow(textId, page, start, end); val row = PdfHighlight(id = old?.id ?: 0, textId = textId, page = page, startWord = start, endWord = end, colorKey = payload.string("colorKey").orEmpty(), createdAtMs = payload.long("createdAtMs") ?: 0); if (old == null) dao.insertPdfHighlight(row) else dao.updatePdfHighlight(row); applied += item.key }
                }
                LibraryKinds.HIGHLIGHT -> {
                    val textId = parts.getOrNull(1)?.let { dao.textBySyncId(it)?.id } ?: continue; val start = parts.getOrNull(2)?.toIntOrNull() ?: continue; val end = parts.getOrNull(3)?.toIntOrNull() ?: continue
                    if (item.deleted) { dao.deleteHighlight(textId, start, end); applied += item.key } else if (payload != null) { val old = dao.highlightRow(textId, start, end); val row = HighlightEntry(id = old?.id ?: 0, textId = textId, startOffset = start, endOffset = end, colorKey = payload.string("colorKey").orEmpty(), createdAtMs = payload.long("createdAtMs") ?: 0); if (old == null) dao.insertHighlight(row) else dao.updateHighlight(row); applied += item.key }
                }
            }
        }
        return applied
    }

    suspend fun deletedFilePaths(): List<String> = deletedPaths.toList()
}

/**
 * Parent-first order with the effective parent syncId of each live folder. Cycles (also through existing folders) are broken
 * by giving the first folder (by key) that closes the cycle no parent.
 */
internal fun orderFolders(existingParents: Map<String, String?>, folders: List<LibraryItem>): List<Pair<LibraryItem, String?>> {
    val parentOf = existingParents.toMutableMap()
    folders.forEach { parentOf[it.key.substringAfter(':')] = it.payload?.string("parentSyncId") }
    for (f in folders.sortedBy { it.key }) {
        val sid = f.key.substringAfter(':')
        val seen = mutableSetOf(sid)
        var p = parentOf[sid]
        while (p != null) {
            if (p == sid) { parentOf[sid] = null; break }
            if (!seen.add(p)) break
            p = parentOf[p]
        }
    }
    fun depth(sid: String): Int { var d = 0; var p = parentOf[sid]; while (p != null && d <= parentOf.size) { d++; p = parentOf[p] }; return d }
    return folders.sortedWith(compareBy<LibraryItem>({ depth(it.key.substringAfter(':')) }, { it.key })).map { it to parentOf[it.key.substringAfter(':')] }
}

private fun kotlinx.serialization.json.JsonObject.value(key: String) = this[key]?.jsonPrimitive
private fun kotlinx.serialization.json.JsonObject.string(key: String) = value(key)?.contentOrNull
private fun kotlinx.serialization.json.JsonObject.int(key: String) = value(key)?.intOrNull
private fun kotlinx.serialization.json.JsonObject.long(key: String) = value(key)?.longOrNull
private fun kotlinx.serialization.json.JsonObject.bool(key: String) = value(key)?.booleanOrNull
