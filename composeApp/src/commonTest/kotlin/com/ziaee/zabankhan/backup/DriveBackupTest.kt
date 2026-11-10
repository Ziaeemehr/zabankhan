package com.ziaee.zabankhan.backup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import com.ziaee.zabankhan.sync.library.LibraryItem
import com.ziaee.zabankhan.sync.library.TOMBSTONE_RETENTION_MS
import kotlin.test.*

class DriveBackupTest {
    @Test fun syncUploadSerializesMetadataForNewAndExistingFiles() = runTest {
        for (existing in listOf(false, true)) {
            var uploaded = false
            val engine = MockEngine { request ->
                when (request.method) {
                    HttpMethod.Get -> respond(if (existing) """{"files":[{"id":"file-id","name":"zabankhan-sync-device.json"}]}""" else """{"files":[]}""")
                    HttpMethod.Post, HttpMethod.Patch -> {
                        assertEquals(if (existing) HttpMethod.Patch else HttpMethod.Post, request.method)
                        val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        assertTrue(body.contains("\"name\":\"zabankhan-sync-device.json\""))
                        assertEquals(!existing, body.contains("\"parents\":[\"appDataFolder\"]"))
                        assertTrue(body.contains("\r\n\r\nSYNC\r\n"))
                        uploaded = true
                        respond("{}")
                    }
                    else -> error("Unexpected request")
                }
            }
            HttpClient(engine).use { DriveBackupClient(it).uploadSyncFile("token", "device", "SYNC".encodeToByteArray()) }
            assertTrue(uploaded)
        }
    }

    @Test fun uploadListsEveryPageThenDeletesOnlyOlderBackups() = runTest {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            assertEquals("Bearer short-lived", request.headers[HttpHeaders.Authorization])
            when (request.method) {
                HttpMethod.Post -> {
                    assertEquals("multipart", request.url.parameters["uploadType"])
                    assertTrue(request.body.contentType.toString().startsWith("multipart/related; boundary="))
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
                    assertTrue(body.contains("zabankhan-backup-123.zip"))
                    assertTrue(body.contains("Content-Type: application/zip\r\n\r\nZIP"))
                    respond("{}")
                }
                HttpMethod.Get -> {
                    assertEquals("appDataFolder", request.url.parameters["spaces"])
                    // Sidecar lookup after pruning: unordered listing, no sidecars present.
                    if (request.url.parameters["orderBy"] == null) return@MockEngine respond("""{"files":[]}""")
                    assertEquals("createdTime desc", request.url.parameters["orderBy"])
                    val page = request.url.parameters["pageToken"]
                    if (page == null) respond("""{"nextPageToken":"second","files":[${(2..6).joinToString(",") { file(it) }}]}""")
                    else { assertEquals("second", page); respond("""{"files":[${file(1)}]}""") }
                }
                HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
                else -> error("Unexpected request")
            }
        }
        HttpClient(engine).use { DriveBackupClient(it).upload("short-lived", "ZIP".encodeToByteArray(), 123) }
        assertEquals(listOf("POST /upload/drive/v3/files", "GET /drive/v3/files", "GET /drive/v3/files", "DELETE /drive/v3/files/id1", "GET /drive/v3/files"), requests)
    }
    @Test fun pruningIgnoresUnrelatedFilesAndOrdersByCreationTime() {
        val backups = (1..7).map { DriveBackupFile("id$it", "zabankhan-backup-$it.zip", "2026-01-0${8-it}T00:00:00Z") }
        assertEquals(listOf("id6", "id7"), driveBackupsToDelete(backups + DriveBackupFile("other", "notes.zip", "2020-01-01T00:00:00Z")).map { it.id })
    }
    @Test fun pdfBlobUploadSkipsExistingHash() = runTest {
        var uploads = 0
        val hash = "a".repeat(64)
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            respond("""{"files":[{"id":"id","name":"zabankhan-pdf-$hash.pdf"}]}""")
        }
        HttpClient(engine).use { DriveBackupClient(it).uploadPdfBlob("token", hash, "/missing.pdf") }
        assertEquals(0, uploads)
    }
    @Test fun pdfGcAndMissingSelectionArePure() {
        assertEquals(setOf("b"), pdfBlobsToDelete(setOf("a", "b"), listOf(setOf("a"))))
        val refs = listOf(BackupPdfRef("a.pdf", 1, "a"), BackupPdfRef("b.pdf", 1, "b"))
        assertEquals(listOf("b.pdf"), pdfRefsToDownload(refs) { it == "a.pdf" }.map { it.path })
    }
    @Test fun libraryKeepSetAndTombstoneOnlyBlobs() {
        val day = 24L * 60 * 60 * 1000; val now = 100 * day
        val (live, fresh, old, side) = listOf("1", "2", "3", "4").map { it.repeat(64) }
        val items = listOf(
            LibraryItem("text", "text:a", now, blobs = listOf(live)),
            LibraryItem("text", "text:b", now - 29 * day, deleted = true, blobs = listOf(fresh)),
            LibraryItem("text", "text:c", now - TOMBSTONE_RETENTION_MS - 1, deleted = true, blobs = listOf(old)),
        )
        val keep = libraryBlobKeepSet(items, listOf(setOf(side)), now)
        assertEquals(setOf(live, fresh, side), keep)
        assertEquals(setOf(live, side), libraryBlobKeepSet(items, listOf(setOf(side)), now, ignoreTombstones = true))
        // Both name formats resolve to the hash the keep-set uses.
        assertEquals(live, blobHashFromName("zabankhan-pdf-$live.pdf")); assertEquals(live, blobHashFromName("zabankhan-file-$live"))
        // A blob also referenced live or by a sidecar is never tombstone-only.
        val shared = items + LibraryItem("text", "text:d", now, blobs = listOf(fresh))
        assertEquals(setOf(fresh, old), tombstoneOnlyBlobs(items, listOf(setOf(side)), now))
        assertEquals(setOf(old), tombstoneOnlyBlobs(shared, listOf(setOf(side)), now))
        assertEquals(setOf(old), tombstoneOnlyBlobs(items, listOf(setOf(fresh)), now))
    }
    @Test fun failedUploadNeverPrunes() = runTest {
        var count = 0
        HttpClient(MockEngine { count++; respond("error", HttpStatusCode.Forbidden) }).use {
            assertFailsWith<IllegalStateException> { DriveBackupClient(it).upload("token", byteArrayOf(), 1) }
        }
        assertEquals(1, count)
    }
    private fun file(day: Int) = """{"id":"id$day","name":"zabankhan-backup-$day.zip","createdTime":"2026-01-0${day}T00:00:00Z"}"""
}
