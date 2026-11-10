package com.ziaee.zabankhan.backup

import com.ziaee.zabankhan.content.ZipArchive
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

class DrivePdfStoreTest {
    private val hash = "b".repeat(64)

    private fun backupZip() = ZipArchive.writeStored(mapOf("manifest.json" to Json.encodeToString(
        BackupManifest("1", BACKUP_SCHEMA_VERSION, includesPdfs = true, pdfs = listOf(BackupPdfRef("missing.pdf", 10, hash)))
    ).encodeToByteArray()))

    @Test fun missingPdfStillUploadsZipAndSidecarGoesFirst() = runTest {
        val posts = mutableListOf<String>()
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Post -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    if (body.contains(".pdfs.json")) {
                        posts += "sidecar"
                        assertFalse(body.contains(hash), "a PDF that failed to upload must not be in the sidecar")
                    } else posts += "zip"
                    respond("""{"id":"new"}""")
                }
                HttpMethod.Get -> respond("""{"files":[]}""")
                else -> error("Unexpected ${request.method}")
            }
        }
        HttpClient(engine).use { DriveBackupClient(it).uploadBackup("t", backupZip(), 1, "/nonexistent-pdf-root") }
        assertEquals(listOf("sidecar", "zip"), posts)
        assertEquals("1 PDF files were not uploaded", BackupRuntime.lastFailure)
    }

    @Test fun gcSkipsWhenKeptBackupHasNoSidecar() = runTest {
        val deletes = mutableListOf<String>()
        var sidecarLists = 0
        val engine = MockEngine { request ->
            val q = request.url.parameters["q"].orEmpty()
            when (request.method) {
                HttpMethod.Post -> respond("""{"id":"new"}""")
                HttpMethod.Delete -> { deletes += request.url.encodedPath; respond("", HttpStatusCode.NoContent) }
                HttpMethod.Get -> when {
                    q.contains("zabankhan-pdf-") -> respond("""{"files":[{"id":"blob","name":"zabankhan-pdf-${"c".repeat(64)}.pdf","modifiedTime":"2020-01-01T00:00:00Z"}]}""")
                    request.url.parameters["orderBy"] != null -> respond("""{"files":[{"id":"z1","name":"zabankhan-backup-7.zip","createdTime":"2026-01-01T00:00:00Z"}]}""")
                    else -> { sidecarLists++; respond("""{"files":[]}""") }
                }
                else -> error("Unexpected ${request.method}")
            }
        }
        HttpClient(engine).use { DriveBackupClient(it).uploadBackup("t", backupZip(), 1, "/nonexistent-pdf-root") }
        assertEquals(emptyList(), deletes, "an unreadable/missing sidecar must stop GC")
        assertEquals(1, sidecarLists)
    }

    @Test fun downloadRejectsWrongHashOrOversizeAndLeavesNoFiles() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "pdf-store-${Random.nextLong().toULong()}"
        val target = dir / "a.pdf"
        val engine = MockEngine { request ->
            if (request.url.parameters["alt"] == "media") respond("not the pdf")
            else respond("""{"files":[{"id":"x","name":"zabankhan-pdf-$hash.pdf"}]}""")
        }
        try {
            HttpClient(engine).use { client ->
                assertFails { DriveBackupClient(client).downloadPdfBlob("t", hash, target.toString()) }
                assertFailsWith<BackupTooLargeException> { DriveBackupClient(client).downloadPdfBlob("t", hash, target.toString(), maxBytes = 3) }
            }
            assertFalse(FileSystem.SYSTEM.exists(target))
            assertEquals(emptyList(), FileSystem.SYSTEM.list(dir))
        } finally { FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    }
}
