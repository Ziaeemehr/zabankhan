package com.ziaee.zabankhan.backup

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DriveBackupTest {
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
        assertEquals(listOf("POST /upload/drive/v3/files", "GET /drive/v3/files", "GET /drive/v3/files", "DELETE /drive/v3/files/id1"), requests)
    }
    @Test fun pruningIgnoresUnrelatedFilesAndOrdersByCreationTime() {
        val backups = (1..7).map { DriveBackupFile("id$it", "zabankhan-backup-$it.zip", "2026-01-0${8-it}T00:00:00Z") }
        assertEquals(listOf("id6", "id7"), driveBackupsToDelete(backups + DriveBackupFile("other", "notes.zip", "2020-01-01T00:00:00Z")).map { it.id })
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
