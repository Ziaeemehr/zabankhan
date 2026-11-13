package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.data.AppDatabase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath

data class FileHashCacheEntry(val size: Long, val lastModified: Long, val hash: String)

class LibrarySnapshotBuilder(
    private val database: AppDatabase,
    private val fileHashCache: MutableMap<String, FileHashCacheEntry> = mutableMapOf(),
    private val hashFile: (String) -> String? = ::hashFileDefault,
) {
    /** Text keys whose file path is set but unreadable this round; they are left out so a transient failure never syncs as "file removed". */
    val unreadableTexts = mutableSetOf<String>()

    suspend fun build(): Map<String, Pair<String, LibraryItem>> {
        val dao = database.librarySync()
        unreadableTexts.clear()
        dao.backfillTextSyncIds(); dao.backfillFolderSyncIds()
        val texts = dao.texts()
        val folders = dao.folders()
        val textById = texts.associateBy { it.id }
        val out = linkedMapOf<String, Pair<String, LibraryItem>>()
        fun add(kind: String, key: String, payload: JsonObject, blobs: List<String> = emptyList()) {
            val item = LibraryItem(kind, key, 0, payload = payload, blobs = blobs, deviceId = "")
            out[key] = contentHash(item) to item
        }
        fun obj(vararg values: Pair<String, Any?>) = buildJsonObject {
            values.forEach { (k, v) -> when (v) {
                null -> put(k, kotlinx.serialization.json.JsonNull)
                is String -> put(k, v)
                is Boolean -> put(k, v)
                is Int -> put(k, v)
                is Long -> put(k, v)
                is Double -> put(k, v)
                else -> put(k, v.toString())
            } }
        }
        val folderSyncIds = folders.associate { it.id to it.syncId }
        folders.filter { it.syncId.isNotEmpty() }.forEach { f ->
            add(LibraryKinds.FOLDER, "folder:${f.syncId}", obj("name" to f.name, "lang" to f.lang, "parentSyncId" to f.parentId?.let(folderSyncIds::get), "createdAtMs" to f.createdAtMs))
        }
        texts.filter { it.syncId.isNotEmpty() }.forEach { t ->
            val pdfHash = fileHash(t.pdfPath)
            // The app never stores an inline body together with a bodyPath (editing clears bodyPath; only sync sets it, on empty-body rows), so the inline body wins.
            val bodyInline = t.body.isNotEmpty()
            val bodyHash = if (bodyInline) t.body.encodeUtf8().sha256().hex() else fileHash(t.bodyPath)
            val imageHash = fileHash(t.imagePath)
            if ((pdfHash == null && !t.pdfPath.isNullOrBlank()) || (!bodyInline && bodyHash == null && !t.bodyPath.isNullOrBlank()) || (imageHash == null && !t.imagePath.isNullOrBlank())) {
                // shortcut: a permanently missing file freezes this text's sync, upgrade by clearing dead paths.
                unreadableTexts += "text:${t.syncId}"
            }
            val blobs = listOfNotNull(pdfHash, bodyHash, imageHash)
            if ("text:${t.syncId}" !in unreadableTexts) add(LibraryKinds.TEXT, "text:${t.syncId}", obj(
                "lang" to t.lang, "title" to t.title, "folderSyncId" to t.folderId?.let(folderSyncIds::get), "createdAtMs" to t.createdAtMs,
                "sourceUrl" to t.sourceUrl, "sourceName" to t.sourceName, "author" to t.author,
                "license" to t.license, "publishedAt" to t.publishedAt, "level" to t.level, "pinned" to t.pinned, "readMark" to t.readMark,
                "voice" to t.voice, "ratePercent" to t.ratePercent, "translationLang" to t.translationLang,
                "pdfHash" to pdfHash, "bodyHash" to bodyHash, "imageHash" to imageHash,
                "pdfName" to t.pdfPath?.substringAfterLast('/'), "bodyName" to t.bodyPath?.substringAfterLast('/'), "imageName" to t.imagePath?.substringAfterLast('/'),
                "hasBodyInline" to bodyInline
            ), blobs)
            add(LibraryKinds.POSITION, "pos:${t.syncId}", obj("lastOffset" to t.lastOffset, "lastChunkIndex" to t.lastChunkIndex, "lastPositionMs" to t.lastPositionMs, "lastAccessedAtMs" to t.lastAccessedAtMs))
        }
        val tags = dao.tags()
        val tagById = tags.associateBy { it.id }
        tags.forEach { add(LibraryKinds.TAG, "tag:${it.lang}:${it.name}", obj("lang" to it.lang, "name" to it.name, "createdAtMs" to it.createdAtMs)) }
        dao.textTags().forEach { ref ->
            val text = textById[ref.textId]?.takeIf { it.syncId.isNotEmpty() } ?: return@forEach
            val tag = tagById[ref.tagId] ?: return@forEach
            add(LibraryKinds.TEXT_TAG, "textTag:${text.syncId}:${tag.lang}:${tag.name}", obj("textSyncId" to text.syncId, "lang" to tag.lang, "name" to tag.name))
        }
        dao.ink().forEach { row -> textById[row.textId]?.syncId?.takeIf(String::isNotEmpty)?.let { add(LibraryKinds.INK, "ink:$it:${row.page}", obj("strokes" to row.strokes)) } }
        dao.notes().forEach { n -> textById[n.textId]?.syncId?.takeIf(String::isNotEmpty)?.let { add(LibraryKinds.NOTE, "note:$it:${n.page}:${n.startWord}:${n.endWord}", obj("page" to n.page, "startWord" to n.startWord, "endWord" to n.endWord, "selectedText" to n.selectedText, "body" to n.body)) } }
        dao.pdfHighlights().forEach { h -> textById[h.textId]?.syncId?.takeIf(String::isNotEmpty)?.let { add(LibraryKinds.PDF_HIGHLIGHT, "pdfHighlight:$it:${h.page}:${h.startWord}:${h.endWord}", obj("page" to h.page, "startWord" to h.startWord, "endWord" to h.endWord, "colorKey" to h.colorKey, "createdAtMs" to h.createdAtMs)) } }
        dao.highlights().forEach { h -> textById[h.textId]?.syncId?.takeIf(String::isNotEmpty)?.let { add(LibraryKinds.HIGHLIGHT, "highlight:$it:${h.startOffset}:${h.endOffset}", obj("startOffset" to h.startOffset, "endOffset" to h.endOffset, "colorKey" to h.colorKey, "createdAtMs" to h.createdAtMs)) } }
        dao.videoChannels().forEach { c -> add(LibraryKinds.VIDEO_CHANNEL, "videoChannel:${c.syncId}", obj("channelId" to c.channelId, "lang" to c.lang, "title" to c.title, "category" to c.category, "level" to c.level)) }
        dao.savedVideos().forEach { v -> add(LibraryKinds.SAVED_VIDEO, v.stableKey, obj(
            "lang" to v.lang, "videoId" to v.videoId, "path" to v.path, "subtitlePath" to v.subtitlePath,
            "title" to v.title, "channel" to v.channel, "isFavorite" to v.isFavorite, "updatedAtMs" to v.updatedAtMs,
        )) }
        dao.videoPlaylists().forEach { p -> add(LibraryKinds.VIDEO_PLAYLIST, "videoPlaylist:${p.syncId}", obj("lang" to p.lang, "name" to p.name, "createdAtMs" to p.createdAtMs, "updatedAtMs" to p.updatedAtMs)) }
        dao.videoPlaylistMemberships().forEach { m -> add(LibraryKinds.VIDEO_PLAYLIST_MEMBER, "videoPlaylistMember:${m.playlistSyncId}:${m.videoKey}", obj("playlistSyncId" to m.playlistSyncId, "videoKey" to m.videoKey, "addedAtMs" to m.addedAtMs)) }
        return out
    }

    private fun fileHash(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val fs = FileSystem.SYSTEM
        val p = runCatching { path.toPath() }.getOrNull() ?: return null
        val metadata = runCatching { fs.metadata(p) }.getOrNull()?.takeIf { it.isRegularFile } ?: return null
        val size = metadata.size ?: return null
        val modified = metadata.lastModifiedAtMillis ?: 0L
        fileHashCache[path]?.takeIf { it.size == size && it.lastModified == modified }?.let { return it.hash }
        return hashFile(path)?.also { fileHashCache[path] = FileHashCacheEntry(size, modified, it) }
    }

    private companion object {
        fun hashFileDefault(path: String): String? = runCatching { FileSystem.SYSTEM.read(path.toPath()) { readByteString() }.sha256().hex() }.getOrNull()
    }
}
