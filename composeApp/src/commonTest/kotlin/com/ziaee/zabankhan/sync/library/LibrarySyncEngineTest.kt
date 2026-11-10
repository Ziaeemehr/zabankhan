package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.data.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal suspend fun assertLibrarySyncEngine(buildTestDatabase: (String) -> AppDatabase) {
    val fs = FileSystem.SYSTEM
    val dbPath = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "library-engine-${Random.nextLong()}.db").toString()
    val db = buildTestDatabase(dbPath)
    val pdfName = "engine-test-${Random.nextLong().toString(16).removePrefix("-")}.pdf"
    val pdfBytes = "%PDF-1.4 engine test ${Random.nextLong()}".encodeToByteArray()
    val pdfHash = ByteString.of(*pdfBytes).sha256().hex()
    val filesRoot = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "library-engine-files-${Random.nextLong()}").toString()
    val pdfPath = "$filesRoot/pdfs/$pdfName"
    try {
        fun remoteFile(textItemDeleted: Boolean) = LibraryWireFormat.encode(LibraryStateFile(LIBRARY_SCHEMA_VERSION, "acct", "remote1", buildList {
            add(LibraryItem(LibraryKinds.FOLDER, "folder:f1", 1000, payload = buildJsonObject { put("name", "Books"); put("lang", "fr"); put("parentSyncId", JsonNull); put("createdAtMs", 5L) }, deviceId = "remote1"))
            add(if (textItemDeleted) LibraryItem(LibraryKinds.TEXT, "text:t1", 2000, deleted = true, deviceId = "remote1")
            else LibraryItem(LibraryKinds.TEXT, "text:t1", 1000, payload = buildJsonObject {
                // Same field set (and types) LibrarySnapshotBuilder emits, so an unchanged text round-trips to the same hash.
                put("lang", "fr"); put("title", "Remote"); put("folderSyncId", "f1"); put("createdAtMs", 6L)
                listOf("sourceUrl", "sourceName", "author", "license", "publishedAt", "level").forEach { put(it, JsonNull) }
                put("pinned", false); put("readMark", 0); put("voice", ""); put("ratePercent", 0); put("translationLang", "fa")
                put("pdfHash", pdfHash); put("bodyHash", JsonNull); put("imageHash", JsonNull)
                put("pdfName", pdfName); put("bodyName", JsonNull); put("imageName", JsonNull); put("hasBodyInline", false)
            }, blobs = listOf(pdfHash), deviceId = "remote1"))
        }))
        var libraryBytes = remoteFile(false)
        val uploads = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            val q = request.url.parameters["q"].orEmpty()
            val path = request.url.encodedPath
            when (request.method) {
                HttpMethod.Get -> when {
                    path.endsWith("/about") -> respond("""{"user":{"permissionId":"acct"}}""")
                    request.url.parameters["alt"] == "media" -> respond(if (path.endsWith("/L1")) libraryBytes else pdfBytes)
                    q.contains("zabankhan-library-") -> respond("""{"files":[{"id":"L1","name":"zabankhan-library-remote1.json"}]}""")
                    q.contains(pdfHash) -> respond("""{"files":[{"id":"B1","name":"zabankhan-pdf-$pdfHash.pdf"}]}""")
                    else -> respond("""{"files":[]}""")
                }
                HttpMethod.Post, HttpMethod.Patch -> {
                    uploads += (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: "stream"
                    respond("""{"id":"new"}""")
                }
                else -> error("Unexpected ${request.method}")
            }
        })
        val engine = LibrarySyncEngine(db, http, "dev1") { filesRoot }
        var protectCalls = 0
        val dao = db.librarySync()

        val first = engine.syncOnce("t", "acct") { protectCalls++ }
        assertEquals(LibrarySyncResult(2, 1, 0), first)
        assertEquals(0, protectCalls)
        assertEquals("Books", dao.folderBySyncId("f1")!!.name)
        assertEquals(pdfPath, dao.textBySyncId("t1")!!.pdfPath)
        assertTrue(fs.read(pdfPath.toPath()) { readByteArray() }.contentEquals(pdfBytes))
        val own = uploads.single { it.contains("zabankhan-library-dev1.json") }
        assertTrue(own.contains("text:t1") && own.contains("folder:f1"))
        assertEquals(1, uploads.count { it == "stream" })

        assertEquals(0, engine.syncOnce("t", "acct") { protectCalls++ }.applied)

        libraryBytes = remoteFile(true)
        assertEquals(1, engine.syncOnce("t", "acct") { protectCalls++ }.applied)
        assertEquals(1, protectCalls)
        assertNull(dao.textBySyncId("t1"))
        assertTrue(!fs.exists(pdfPath.toPath()))
        assertNotNull(dao.folderBySyncId("f1"))
    } finally {
        db.close()
        fs.deleteRecursively(filesRoot.toPath(), mustExist = false)
        fs.delete(dbPath.toPath(), mustExist = false)
        fs.delete("$dbPath-wal".toPath(), mustExist = false)
        fs.delete("$dbPath-shm".toPath(), mustExist = false)
    }
}
