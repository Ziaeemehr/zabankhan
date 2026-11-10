package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.backup.DriveBackupClient
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.LibraryFolder
import com.ziaee.zabankhan.data.LibrarySyncShadow
import com.ziaee.zabankhan.data.SyncMetadataEntry
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.data.deterministicSyncId
import com.ziaee.zabankhan.data.textSyncSeed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun it(kind: String, key: String, at: Long, payload: JsonObject? = buildJsonObject { put("v", 1) }, deleted: Boolean = false, device: String = "") =
    LibraryItem(kind, key, at, deleted, if (deleted) null else payload, deviceId = device)
private fun cur(i: LibraryItem) = contentHash(i) to i
private fun sh(i: LibraryItem, at: Long = i.updatedAtMs) = LibrarySyncShadow(i.key, contentHash(i), at, i.deleted)

class LibrarySyncReviewLogicTest {
    @Test fun slowClockStillWinsWithItsNewerEdit() {
        val old = it(LibraryKinds.TEXT, "text:a", 1000); val edited = old.copy(payload = buildJsonObject { put("v", 2) })
        val out = detectLocalChanges(mapOf(edited.key to cur(edited)), mapOf(old.key to sh(old)), 100, false, remoteMax = mapOf("text:a" to 5000L)).single()
        assertEquals(5001, out.updatedAtMs)
        val viaShadow = detectLocalChanges(mapOf(edited.key to cur(edited)), mapOf(old.key to sh(old)), 100, false).single()
        assertEquals(1001, viaShadow.updatedAtMs)
        val del = detectLocalChanges(emptyMap(), mapOf(old.key to sh(old)), 100, false, remoteMax = mapOf("text:a" to 7000L)).single()
        assertEquals(7001, del.updatedAtMs)
    }

    @Test fun restoreStampsNowEvenIfUnchangedAndMakesNoTombstones() {
        val a = it(LibraryKinds.TEXT, "text:a", 10); val gone = it(LibraryKinds.TEXT, "text:gone", 10)
        val out = detectLocalChanges(mapOf(a.key to cur(a)), mapOf(a.key to sh(a), gone.key to sh(gone)), 9000, true, restore = true)
        assertEquals(listOf("text:a"), out.map { x -> x.key }); assertEquals(9000, out.single().updatedAtMs)
        // A remote tombstone older than the restore loses.
        val winners = mergeWinners(out, listOf(it(LibraryKinds.TEXT, "text:a", 5000, deleted = true, device = "r")))
        assertEquals(false, winners.getValue("text:a").deleted)
    }

    @Test fun firstSyncStampsPositionTagsZeroButNotTexts() {
        val items = listOf(it(LibraryKinds.POSITION, "pos:a", 0), it(LibraryKinds.TAG, "tag:fa:x", 0), it(LibraryKinds.TEXT_TAG, "textTag:a:fa:x", 0), it(LibraryKinds.TEXT, "text:a", 0))
        val out = detectLocalChanges(items.associate { x -> x.key to cur(x) }, emptyMap(), 500, true).associate { x -> x.key to x.updatedAtMs }
        assertEquals(mapOf("pos:a" to 0L, "tag:fa:x" to 0L, "textTag:a:fa:x" to 0L, "text:a" to 500L), out)
    }

    @Test fun frozenKeysAreNeitherChangedNorDeleted() {
        val a = it(LibraryKinds.TEXT, "text:a", 10)
        assertEquals(emptyList(), detectLocalChanges(emptyMap(), mapOf(a.key to sh(a)), 99, false, frozen = setOf("text:a")))
    }

    private fun text(key: String, lang: String = "fr", title: String = "T", pdf: String? = null, body: String? = null) = it(LibraryKinds.TEXT, key, 1, buildJsonObject {
        put("lang", lang); put("title", title); put("pdfHash", pdf?.let { p -> JsonPrimitive(p) } ?: JsonNull); put("bodyHash", body?.let { b -> JsonPrimitive(b) } ?: JsonNull)
    })
    private fun folder(key: String, name: String, parent: String? = null) = it(LibraryKinds.FOLDER, key, 1, buildJsonObject { put("lang", "fr"); put("name", name); put("parentSyncId", parent?.let { p -> JsonPrimitive(p) } ?: JsonNull) })

    @Test fun firstSyncMatchesSameTextsAndFolders() {
        val local = listOf(text("text:l1", pdf = "h1"), text("text:l2", body = "b1", title = "Same"), text("text:l3", body = "b2", title = "Other"), folder("folder:lf", "Books"), folder("folder:lc", "Sub", "lf"))
        val remote = listOf(text("text:r1", pdf = "h1", title = "x"), text("text:r2", body = "b1", title = "Same"), text("text:r3", body = "b2", title = "Different"), folder("folder:rf", "Books"), folder("folder:rc", "Sub", "rf"))
        val m = matchLocalToRemote(local.associateBy { x -> x.key }, emptySet(), remote)
        assertEquals(mapOf("text:l1" to "text:r1", "text:l2" to "text:r2", "folder:lf" to "folder:rf", "folder:lc" to "folder:rc"), m)
        // Texts with shadow history are never re-keyed.
        assertEquals(emptyMap(), matchLocalToRemote(local.associateBy { x -> x.key }, local.map { x -> x.key }.toSet(), remote))
    }

    @Test fun foldersApplyParentFirstAndBreakCycles() {
        fun f(sid: String, parent: String?) = folder("folder:$sid", sid, parent)
        val ordered = orderFolders(emptyMap(), listOf(f("a", "b"), f("b", "c"), f("c", null))).map { (x, p) -> x.key to p }
        assertEquals(listOf("folder:c" to null, "folder:b" to "c", "folder:a" to "b"), ordered)
        val cycle = orderFolders(emptyMap(), listOf(f("x", "y"), f("y", "x"))).associate { (x, p) -> x.key to p }
        assertEquals(1, cycle.values.count { p -> p == null }); assertEquals(2, cycle.size)
        // A cycle that closes through an already existing folder is broken too.
        val viaExisting = orderFolders(mapOf("p" to "q", "q" to null), listOf(f("q", "p"))).single()
        assertNull(viaExisting.second)
    }

    @Test fun deterministicSyncIdIsStableAndCollisionFree() {
        val seed = textSyncSeed("fr", "T", 5, "/a/b/c.pdf")
        assertEquals(deterministicSyncId(seed, mutableSetOf()), deterministicSyncId(textSyncSeed("fr", "T", 5, "/other/c.pdf"), mutableSetOf()))
        assertEquals(32, deterministicSyncId(seed, mutableSetOf()).length)
        val taken = mutableSetOf<String>()
        assertNotEquals(deterministicSyncId(seed, taken), deterministicSyncId(seed, taken))
    }

    @Test fun failedBlobsSurfaceAsErrorMessage() {
        assertNull(LibrarySyncResult(1, 1, 0).errorMessage())
        assertEquals("2 files could not be uploaded or downloaded", LibrarySyncResult(0, 0, 2).errorMessage())
    }

    @Test fun blobUploadStreamsAndDownloadIsCappedAndHashChecked() = runTest {
        val bytes = "blob-${Random.nextLong()}".encodeToByteArray(); val hash = ByteString.of(*bytes).sha256().hex()
        val file = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "blob-stream-${Random.nextLong().toULong()}.bin"
        try {
            FileSystem.SYSTEM.write(file) { write(bytes) }
            val bodies = mutableListOf<Any>()
            HttpClient(MockEngine { r -> bodies += r.body; respond("""{"id":"x"}""") }).let { DriveBackupClient(it).uploadBlob("t", hash, file, emptySet()) }
            assertTrue(bodies.single() is OutgoingContent.WriteChannelContent)
            fun client(content: ByteArray) = DriveBackupClient(HttpClient(MockEngine { r ->
                if (r.url.parameters["alt"] == "media") respond(content) else respond("""{"files":[{"id":"x","name":"zabankhan-file-$hash"}]}""")
            }))
            assertTrue(client(bytes).downloadBlob("t", hash, 1024).contentEquals(bytes))
            assertFails { client(bytes).downloadBlob("t", hash, 3) }
            assertFails { client("tampered".encodeToByteArray()).downloadBlob("t", hash, 1024) }
        } finally { FileSystem.SYSTEM.delete(file, mustExist = false) }
    }
}

internal suspend fun assertSyncIdsGeneratedAndBackfilled(build: (String) -> AppDatabase) = withDb(build) { db, _ ->
    val id = db.texts().insert(TextDocument(lang = "fr", title = "A", body = "b"))
    assertTrue(db.texts().getById(id)!!.syncId.isNotEmpty())
    db.libraryOrganizer().insertFolder(LibraryFolder(lang = "fr", name = "F"))
    assertTrue(db.librarySync().folders().single().syncId.isNotEmpty())
    db.librarySync().insertText(TextDocument(lang = "fr", title = "Legacy", body = "b", syncId = ""))
    db.librarySync().insertFolder(LibraryFolder(lang = "fr", name = "Legacy", syncId = ""))
    LibrarySnapshotBuilder(db).build()
    assertTrue(db.librarySync().texts().all { t -> t.syncId.isNotEmpty() }); assertTrue(db.librarySync().folders().all { f -> f.syncId.isNotEmpty() })
}

internal suspend fun assertDeterministicBackfillOnOpen(build: (String) -> AppDatabase) {
    val ids = (1..2).map { n ->
        val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "det-$n-${Random.nextLong()}.db").toString()
        try {
            build(path).also { db -> db.librarySync().insertText(TextDocument(lang = "fr", title = "T", body = "", createdAtMs = 5, pdfPath = "/dir$n/a.pdf", syncId = "")); db.close() }
            val db = build(path)
            try { db.librarySync().texts().single().syncId } finally { db.close() }
        } finally { listOf("", "-wal", "-shm").forEach { s -> FileSystem.SYSTEM.delete("$path$s".toPath(), mustExist = false) } }
    }
    assertEquals(ids[0], ids[1])
    assertEquals(deterministicSyncId(textSyncSeed("fr", "T", 5, "/x/a.pdf"), mutableSetOf()), ids[0])
}

internal suspend fun assertApplierKeepsLocalStateAndOrdersFolders(build: (String) -> AppDatabase) = withDb(build) { db, _ ->
    val dao = db.librarySync(); val applier = LibraryApplier(db)
    dao.insertText(TextDocument(lang = "fr", title = "Old", body = "", pdfPath = "/files/p.pdf", externalKey = "ek", syncId = "t1"))
    fun textItem(named: Boolean) = LibraryItem(LibraryKinds.TEXT, "text:t1", 5, payload = buildJsonObject {
        put("lang", "fr"); put("title", "New"); put("pdfHash", JsonNull); if (named) put("pdfName", "p.pdf") else put("pdfName", JsonNull)
    })
    val none = TextFilePaths(null, null, null, null)
    applier.apply(listOf(textItem(true)), mapOf("text:t1" to none))
    dao.textBySyncId("t1")!!.let { t -> assertEquals("New", t.title); assertEquals("/files/p.pdf", t.pdfPath); assertEquals("ek", t.externalKey) }
    applier.apply(listOf(textItem(false)), mapOf("text:t1" to none))
    assertNull(dao.textBySyncId("t1")!!.pdfPath)

    fun f(sid: String, parent: String?) = LibraryItem(LibraryKinds.FOLDER, "folder:$sid", 1, payload = buildJsonObject { put("lang", "fr"); put("name", sid); put("parentSyncId", parent?.let { JsonPrimitive(it) } ?: JsonNull) })
    applier.apply(listOf(f("a", "b"), f("b", null), f("x", "y"), f("y", "x")), emptyMap())
    assertEquals(dao.folderBySyncId("b")!!.id, dao.folderBySyncId("a")!!.parentId)
    assertEquals(1, listOf("x", "y").count { dao.folderBySyncId(it)!!.parentId == null })
}

private suspend fun withDb(build: (String) -> AppDatabase, body: suspend (AppDatabase, String) -> Unit) {
    val fs = FileSystem.SYSTEM
    val dbPath = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "review-${Random.nextLong()}.db").toString()
    val root = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "review-files-${Random.nextLong()}").toString()
    val db = build(dbPath)
    try { fs.createDirectories("$root/pdfs".toPath()); body(db, root) } finally {
        db.close(); fs.deleteRecursively(root.toPath(), mustExist = false)
        listOf("", "-wal", "-shm").forEach { s -> fs.delete("$dbPath$s".toPath(), mustExist = false) }
    }
}

private class FakeDrive {
    val libraries = linkedMapOf<String, ByteArray>()
    val blobs = linkedMapOf<String, ByteArray>()
    var failStreams = false
    var onLibraryGet: suspend () -> Unit = {}
    fun client() = HttpClient(MockEngine { request ->
        val q = request.url.parameters["q"].orEmpty(); val path = request.url.encodedPath
        when (request.method) {
            HttpMethod.Get -> when {
                path.endsWith("/about") -> respond("""{"user":{"permissionId":"acct"}}""")
                request.url.parameters["alt"] == "media" -> path.substringAfterLast('/').let { id ->
                    if (id.startsWith("L-")) { onLibraryGet(); respond(libraries.getValue(id.removePrefix("L-"))) } else respond(blobs.getValue(id.removePrefix("B-")))
                }
                q.contains("zabankhan-library-") -> respond("""{"files":[${libraries.keys.joinToString(",") { d -> """{"id":"L-$d","name":"zabankhan-library-$d.json"}""" }}]}""")
                else -> {
                    val wanted = Regex("[0-9a-f]{64}").find(q)?.value
                    respond("""{"files":[${blobs.keys.filter { h -> wanted == null || h == wanted }.joinToString(",") { h -> """{"id":"B-$h","name":"zabankhan-pdf-$h.pdf"}""" }}]}""")
                }
            }
            HttpMethod.Post, HttpMethod.Patch -> {
                val content = request.body
                if (content is OutgoingContent.ByteArrayContent) {
                    val text = content.bytes().decodeToString()
                    val dev = Regex("zabankhan-library-([A-Za-z0-9_-]+)\\.json").find(text)!!.groupValues[1]
                    val start = text.indexOf("\r\n\r\n", text.indexOf("\r\n\r\n") + 4) + 4
                    libraries[dev] = text.substring(start, text.lastIndexOf("\r\n--")).encodeToByteArray()
                    respond("""{"id":"new"}""")
                } else if (failStreams) respond("fail", HttpStatusCode.InternalServerError) else respond("""{"id":"new"}""")
            }
            else -> error("Unexpected ${request.method}")
        }
    })
    fun put(device: String, vararg items: LibraryItem) {
        libraries[device] = LibraryWireFormat.encode(LibraryStateFile(LIBRARY_SCHEMA_VERSION, "acct", device, items.map { x -> x.copy(deviceId = device) }))
    }
    fun items(device: String) = LibraryWireFormat.decode(libraries.getValue(device), "acct", device).items.associateBy { x -> x.key }
}

private fun remoteText(sid: String, title: String, at: Long, pdfHash: String? = null, pdfName: String? = null) = LibraryItem(LibraryKinds.TEXT, "text:$sid", at, payload = buildJsonObject {
    // Same field set and types LibrarySnapshotBuilder emits, so an unchanged text round-trips to the same hash.
    put("lang", "fr"); put("title", title); put("folderSyncId", JsonNull); put("createdAtMs", 6L)
    listOf("sourceUrl", "sourceName", "author", "license", "publishedAt", "level").forEach { k -> put(k, JsonNull) }
    put("pinned", false); put("readMark", 0); put("voice", ""); put("ratePercent", 0); put("translationLang", "fa")
    put("pdfHash", pdfHash?.let { h -> JsonPrimitive(h) } ?: JsonNull); put("bodyHash", JsonNull); put("imageHash", JsonNull)
    put("pdfName", pdfName?.let { n -> JsonPrimitive(n) } ?: JsonNull); put("bodyName", JsonNull); put("imageName", JsonNull); put("hasBodyInline", false)
}, blobs = listOfNotNull(pdfHash))

private fun hashOf(bytes: ByteArray) = ByteString.of(*bytes).sha256().hex()

internal suspend fun assertEngineReviewScenarios(build: (String) -> AppDatabase) {
    // 1a: an unreadable file must not turn into "pdf removed" on other devices.
    withDb(build) { db, root ->
        val drive = FakeDrive(); val dao = db.librarySync()
        val bytes = "pdf-${Random.nextLong()}".encodeToByteArray(); val h = hashOf(bytes); val path = "$root/pdfs/a.pdf"
        FileSystem.SYSTEM.write(path.toPath()) { write(bytes) }
        dao.insertText(TextDocument(lang = "fr", title = "A", body = "", pdfPath = path, syncId = "t1"))
        val engine = LibrarySyncEngine(db, drive.client(), "dev1") { root }
        engine.syncOnce("t", "acct") {}
        assertEquals(h, (drive.items("dev1").getValue("text:t1").payload!!["pdfHash"] as JsonPrimitive).content)
        FileSystem.SYSTEM.delete(path.toPath())
        engine.syncOnce("t", "acct") {}
        assertEquals(h, (drive.items("dev1").getValue("text:t1").payload!!["pdfHash"] as JsonPrimitive).content)
        assertTrue(dao.getAll().any { s -> s.key == "text:t1" && !s.deleted })
        assertEquals(path, dao.textBySyncId("t1")!!.pdfPath)
    }
    // 1b/c: remote text with a name but no hash keeps the local path and file.
    withDb(build) { db, root ->
        val drive = FakeDrive(); val dao = db.librarySync()
        val bytes = "pdf-${Random.nextLong()}".encodeToByteArray(); val h = hashOf(bytes)
        drive.blobs[h] = bytes; drive.put("remote1", remoteText("t1", "Remote", 1000, h, "a.pdf"))
        val engine = LibrarySyncEngine(db, drive.client(), "dev1") { root }
        engine.syncOnce("t", "acct") {}
        val path = dao.textBySyncId("t1")!!.pdfPath!!
        drive.put("remote1", remoteText("t1", "Renamed", 3000, null, "a.pdf"))
        engine.syncOnce("t", "acct") {}
        dao.textBySyncId("t1")!!.let { t -> assertEquals("Renamed", t.title); assertEquals(path, t.pdfPath) }
        assertTrue(FileSystem.SYSTEM.exists(path.toPath()))
    }
    // 7: first sync adopts the remote id of a PDF both devices have and takes the remote reading position.
    withDb(build) { db, root ->
        val drive = FakeDrive(); val dao = db.librarySync()
        val bytes = "pdf-${Random.nextLong()}".encodeToByteArray(); val h = hashOf(bytes); val path = "$root/pdfs/mine.pdf"
        FileSystem.SYSTEM.write(path.toPath()) { write(bytes) }
        dao.insertText(TextDocument(lang = "fr", title = "Mine", body = "", pdfPath = path, lastOffset = 5, syncId = "bLocal"))
        drive.blobs[h] = bytes
        drive.put("remote1", remoteText("t1", "Remote", 1000, h, "a.pdf"),
            LibraryItem(LibraryKinds.POSITION, "pos:t1", 1000, payload = buildJsonObject { put("lastOffset", 77); put("lastChunkIndex", 0); put("lastPositionMs", 0L); put("lastAccessedAtMs", 0L) }))
        LibrarySyncEngine(db, drive.client(), "dev1") { root }.syncOnce("t", "acct") {}
        val t = dao.texts().single()
        assertEquals("t1", t.syncId); assertEquals(77, t.lastOffset)
    }
    // 3a: after a restore, the restored text beats an older remote tombstone.
    withDb(build) { db, root ->
        val drive = FakeDrive(); val dao = db.librarySync()
        dao.insertText(TextDocument(lang = "fr", title = "Keep", body = "", syncId = "t1"))
        val engine = LibrarySyncEngine(db, drive.client(), "dev1") { root }
        engine.syncOnce("t", "acct") {}
        dao.upsertAll(dao.getAll().filter { s -> s.key == "text:t1" }.map { s -> s.copy(updatedAtMs = 100) })
        drive.put("remote1", LibraryItem(LibraryKinds.TEXT, "text:t1", 5000, deleted = true))
        db.sync().setMetadata(SyncMetadataEntry("library_restore_pending", "1"))
        engine.syncOnce("t", "acct") {}
        assertNotNull(dao.textBySyncId("t1"))
        assertTrue(drive.items("dev1").getValue("text:t1").let { x -> !x.deleted && x.updatedAtMs > 5000 })
    }
    // 4: an edit made while the sync runs is detected (and re-stamped) next round.
    withDb(build) { db, root ->
        val drive = FakeDrive(); val dao = db.librarySync()
        drive.put("remote1", remoteText("t1", "Remote", 1000))
        val engine = LibrarySyncEngine(db, drive.client(), "dev1") { root }
        engine.syncOnce("t", "acct") {}
        var edited = false
        drive.onLibraryGet = { if (!edited) { edited = true; dao.updateText(dao.textBySyncId("t1")!!.copy(title = "Edited")) } }
        engine.syncOnce("t", "acct") {}
        assertTrue(edited)
        drive.onLibraryGet = {}
        engine.syncOnce("t", "acct") {}
        val own = drive.items("dev1").getValue("text:t1")
        assertEquals("Edited", (own.payload!!["title"] as JsonPrimitive).content); assertTrue(own.updatedAtMs > 1000)
    }
    // 14: a text whose blob failed to upload is not published until the blob is on Drive.
    withDb(build) { db, root ->
        val drive = FakeDrive(); val dao = db.librarySync()
        val bytes = "pdf-${Random.nextLong()}".encodeToByteArray(); val path = "$root/pdfs/a.pdf"
        FileSystem.SYSTEM.write(path.toPath()) { write(bytes) }
        dao.insertText(TextDocument(lang = "fr", title = "A", body = "", pdfPath = path, syncId = "t1"))
        val engine = LibrarySyncEngine(db, drive.client(), "dev1") { root }
        drive.failStreams = true
        val bad = engine.syncOnce("t", "acct") {}
        assertEquals(1, bad.failedBlobs); assertNotNull(bad.errorMessage())
        assertTrue("text:t1" !in drive.items("dev1")); assertTrue("pos:t1" in drive.items("dev1"))
        drive.failStreams = false
        assertEquals(0, engine.syncOnce("t", "acct") {}.failedBlobs)
        assertTrue("text:t1" in drive.items("dev1"))
    }
}
