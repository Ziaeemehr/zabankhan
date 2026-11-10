package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.data.AppDatabase
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun assertLibraryApply(buildTestDatabase: (String) -> AppDatabase) {
    val dbPath = (okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "library-apply-${Random.nextLong()}.db").toString()
    val fs = okio.FileSystem.SYSTEM
    val db = buildTestDatabase(dbPath)
    try {
        val applier = LibraryApplier(db)
        fun item(kind: String, key: String, payload: kotlinx.serialization.json.JsonObject? = null, deleted: Boolean = false) = LibraryItem(kind, key, 1, deleted, payload)
        val folder = item(LibraryKinds.FOLDER, "folder:f1", buildJsonObject { put("name", "Books"); put("lang", "fa"); put("createdAtMs", 10L) })
        val text = item(LibraryKinds.TEXT, "text:t1", buildJsonObject {
            put("lang", "fa"); put("title", "First"); put("folderSyncId", "f1"); put("createdAtMs", 20L)
            put("sourceUrl", "https://example.org"); put("sourceName", "Source"); put("author", "Author"); put("license", "CC")
            put("publishedAt", 30L); put("level", "A2"); put("pinned", true); put("readMark", 2); put("voice", "voice"); put("ratePercent", 5); put("translationLang", "en")
        })
        val pos = item(LibraryKinds.POSITION, "pos:t1", buildJsonObject { put("lastOffset", 44); put("lastChunkIndex", 3); put("lastPositionMs", 55L); put("lastAccessedAtMs", 66L) })
        val tag = item(LibraryKinds.TAG, "tag:fa:Favorite", buildJsonObject { put("lang", "fa"); put("name", "Favorite"); put("createdAtMs", 70L) })
        val ref = item(LibraryKinds.TEXT_TAG, "textTag:t1:fa:Favorite", buildJsonObject { put("textSyncId", "t1"); put("lang", "fa"); put("name", "Favorite") })
        val ink = item(LibraryKinds.INK, "ink:t1:2", buildJsonObject { put("strokes", "ink-data") })
        val note = item(LibraryKinds.NOTE, "note:t1:2:3:4", buildJsonObject { put("page", 2); put("startWord", 3); put("endWord", 4); put("selectedText", "word"); put("body", "note") })
        val missing = item(LibraryKinds.TEXT, "text:missing", text.payload)
        val missingRef = item(LibraryKinds.TEXT_TAG, "textTag:missing:fa:Favorite", ref.payload)
        val applied = applier.apply(listOf(folder, text, pos, tag, ref, ink, note, missing, missingRef), mapOf("text:t1" to TextFilePaths("/pdf/a", "/body/a", "/image/a", "inline text")))
        assertTrue("text:t1" in applied); assertTrue("textTag:t1:fa:Favorite" in applied)
        assertFalse("text:missing" in applied); assertFalse("textTag:missing:fa:Favorite" in applied)
        val dao = db.librarySync()
        var saved = dao.textBySyncId("t1")!!
        assertEquals("First", saved.title); assertEquals(44, saved.lastOffset); assertEquals(3, saved.lastChunkIndex); assertEquals(55L, saved.lastPositionMs); assertEquals(66L, saved.lastAccessedAtMs)
        assertEquals("inline text", saved.body); assertEquals("/pdf/a", saved.pdfPath); assertEquals("/body/a", saved.bodyPath); assertEquals("/image/a", saved.imagePath); assertTrue(saved.pinned)
        assertEquals("Books", dao.folderBySyncId("f1")!!.name)
        assertEquals(1, dao.textTags().size); assertEquals("ink-data", dao.ink().single().strokes); assertEquals("note", dao.notes().single().body)
        assertNull(dao.textBySyncId("missing"))

        val changed = item(LibraryKinds.TEXT, "text:t1", text.payload!!.let { kotlinx.serialization.json.JsonObject(it + ("title" to kotlinx.serialization.json.JsonPrimitive("Changed"))) })
        val changedPos = item(LibraryKinds.POSITION, "pos:t1", buildJsonObject { put("lastOffset", 88); put("lastChunkIndex", 7); put("lastPositionMs", 99L); put("lastAccessedAtMs", 111L) })
        applier.apply(listOf(changed, changedPos), emptyMap())
        saved = dao.textBySyncId("t1")!!
        assertEquals("Changed", saved.title); assertEquals(88, saved.lastOffset); assertEquals("inline text", saved.body)

        applier.apply(listOf(item(LibraryKinds.NOTE, "note:t1:2:3:4", deleted = true), item(LibraryKinds.TEXT, "text:t1", deleted = true)), emptyMap())
        assertNull(dao.textBySyncId("t1")); assertTrue(dao.ink().isEmpty()); assertTrue(dao.notes().isEmpty())
        assertEquals(listOf("/pdf/a", "/body/a", "/image/a"), applier.deletedFilePaths())
        assertFalse("text:missing" in applier.apply(listOf(missing), emptyMap()))
    } finally {
        db.close()
        fs.delete(dbPath.toPath(), mustExist = false)
        fs.delete("$dbPath-wal".toPath(), mustExist = false)
        fs.delete("$dbPath-shm".toPath(), mustExist = false)
    }
}
