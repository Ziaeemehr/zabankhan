package com.ziaee.zabankhan.backup

import kotlinx.coroutines.IO
import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import okio.Path.Companion.toPath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.ziaee.zabankhan.sync.DRIVE_SYNC_FILE_PREFIX
import com.ziaee.zabankhan.sync.MAX_SYNC_FILE_BYTES
import kotlin.time.Instant

const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
interface DriveAuth {
    val configured: Boolean
    val connected: Boolean
    suspend fun accessToken(): String?
    suspend fun disconnect()
}
internal expect suspend fun backgroundDriveAccessToken(): String?
internal expect fun driveSyncSupported(): Boolean
@Composable expect fun rememberDriveAuth(): DriveAuth

@Serializable data class DriveBackupFile(val id: String, val name: String, val createdTime: String)
@Serializable private data class DriveFiles(val files: List<DriveBackupFile> = emptyList(), val nextPageToken: String? = null)
@Serializable data class DriveSyncFile(val id: String, val name: String, val modifiedTime: String? = null)
@Serializable private data class DriveSyncFiles(val files: List<DriveSyncFile> = emptyList(), val nextPageToken: String? = null)
@Serializable private data class DriveAccount(val user: DriveUser)
@Serializable private data class DriveUser(val permissionId: String)
@Serializable private data class DriveSyncMetadata(val name: String)
// No default for parents: Json omits default values, and Drive must see appDataFolder.
@Serializable private data class DriveMetadata(val name: String, val parents: List<String>)

/** Only our ZIPs in the hidden application folder are eligible for rotation. */
fun driveBackupsToDelete(files: List<DriveBackupFile>): List<DriveBackupFile> = files
    .filter { it.name.startsWith("zabankhan-backup-") && it.name.endsWith(".zip") }
    .sortedByDescending { Instant.parse(it.createdTime) }.drop(5)

class DriveBackupClient(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val root = "https://www.googleapis.com/drive/v3/files"
    private fun HttpRequestBuilder.authorize(token: String) { bearerAuth(token) }
    private suspend fun HttpResponse.checked(): HttpResponse {
        check(status.isSuccess()) { "Drive request failed (${status.value})" }
        return this
    }
    suspend fun list(token: String): List<DriveBackupFile> {
        val files = mutableListOf<DriveBackupFile>()
        var page: String? = null
        do {
            val response = http.get(root) {
                authorize(token)
                parameter("spaces", "appDataFolder")
                parameter("orderBy", "createdTime desc")
                parameter("q", "trashed = false and 'appDataFolder' in parents and name contains 'zabankhan-backup-'")
                parameter("fields", "nextPageToken,files(id,name,createdTime)")
                parameter("pageSize", "100")
                page?.let { parameter("pageToken", it) }
            }.checked()
            val result = json.decodeFromString<DriveFiles>(response.bodyAsText())
            files += result.files.filter { it.name.startsWith("zabankhan-backup-") && it.name.endsWith(".zip") }
            page = result.nextPageToken
        } while (page != null)
        return files.sortedByDescending { Instant.parse(it.createdTime) }
    }

    /** Stable account identifier without exposing the user's email in the sync file. */
    suspend fun accountId(token: String): String {
        val response = http.get("https://www.googleapis.com/drive/v3/about") {
            authorize(token); parameter("fields", "user(permissionId)")
        }.checked()
        return json.decodeFromString<DriveAccount>(response.bodyAsText()).user.permissionId
    }

    suspend fun listSyncFiles(token: String): List<DriveSyncFile> {
        val files = mutableListOf<DriveSyncFile>()
        var page: String? = null
        do {
            val response = http.get(root) {
                authorize(token)
                parameter("spaces", "appDataFolder")
                parameter("q", "trashed = false and 'appDataFolder' in parents and name contains '$DRIVE_SYNC_FILE_PREFIX'")
                parameter("fields", "nextPageToken,files(id,name,modifiedTime)")
                parameter("pageSize", "100")
                page?.let { parameter("pageToken", it) }
            }.checked()
            val result = json.decodeFromString<DriveSyncFiles>(response.bodyAsText())
            files += result.files.filter { it.name.startsWith(DRIVE_SYNC_FILE_PREFIX) && it.name.endsWith(".json") }
            page = result.nextPageToken
        } while (page != null)
        return files
    }

    suspend fun downloadSyncFile(token: String, id: String): ByteArray {
        val response = http.get(root + "/" + id.encodeURLPathPart()) {
            authorize(token); parameter("alt", "media")
        }.checked()
        return response.bodyAsBytes().also { require(it.size <= MAX_SYNC_FILE_BYTES) { "Remote sync journal is too large" } }
    }

    /** Upsert only this installation's journal file; it never touches backup ZIPs or other devices. */
    suspend fun uploadSyncFile(token: String, deviceId: String, bytes: ByteArray) {
        require(bytes.size <= MAX_SYNC_FILE_BYTES) { "Sync journal is too large" }
        require(deviceId.isNotBlank() && deviceId.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        val name = "$DRIVE_SYNC_FILE_PREFIX$deviceId.json"
        val existing = listSyncFiles(token).firstOrNull { it.name == name }
        val boundary = "zabankhan-sync-" + kotlin.random.Random.nextLong().toString(16)
        val metadata = json.encodeToString(if (existing == null) DriveMetadata(name, listOf("appDataFolder")) else DriveSyncMetadata(name))
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".encodeToByteArray()
        val suffix = "\r\n--$boundary--\r\n".encodeToByteArray()
        val target = if (existing == null) "https://www.googleapis.com/upload/drive/v3/files"
            else "https://www.googleapis.com/upload/drive/v3/files/${existing.id.encodeURLPathPart()}"
        val request: suspend () -> HttpResponse = if (existing == null) {
            { http.post(target) {
                authorize(token); parameter("uploadType", "multipart"); parameter("fields", "id,name,modifiedTime")
                contentType(ContentType.parse("multipart/related; boundary=$boundary")); setBody(prefix + bytes + suffix)
            } }
        } else {
            { http.patch(target) {
                authorize(token); parameter("uploadType", "multipart"); parameter("fields", "id,name,modifiedTime")
                contentType(ContentType.parse("multipart/related; boundary=$boundary")); setBody(prefix + bytes + suffix)
            } }
        }
        request().checked()
    }
    suspend fun upload(token: String, bytes: ByteArray, timestamp: Long) {
        require(bytes.size <= 256 * 1024 * 1024)
        // A random MIME boundary avoids collisions with arbitrary ZIP contents.
        val boundary = "zabankhan-" + kotlin.random.Random.nextLong().toString(16)
        val metadata = json.encodeToString(DriveMetadata("zabankhan-backup-$timestamp.zip", listOf("appDataFolder")))
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n".encodeToByteArray()
        val suffix = "\r\n--$boundary--\r\n".encodeToByteArray()
        http.post("https://www.googleapis.com/upload/drive/v3/files") {
            authorize(token); parameter("uploadType", "multipart")
            contentType(ContentType.parse("multipart/related; boundary=$boundary"))
            setBody(prefix + bytes + suffix)
        }.checked()
        driveBackupsToDelete(list(token)).forEach { delete(token, it.id) }
    }
    suspend fun download(token: String, id: String, path: String) {
        // Stream to a bounded temporary file; never buffer an untrusted remote ZIP in memory.
        val target = path.toPath()
        val fs = okio.FileSystem.SYSTEM
        fs.createDirectories(requireNotNull(target.parent))
        try {
            http.prepareGet(root + "/" + id.encodeURLPathPart()) {
                authorize(token); parameter("alt", "media")
            }.execute { response ->
                response.checked()
                val channel = response.bodyAsChannel()
                var total = 0L
                fs.write(target) {
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count == -1) break
                        total += count
                        if (total > 256L * 1024 * 1024) throw BackupTooLargeException()
                        write(buffer, 0, count)
                    }
                }
            }
            validateBackup(path)
        } catch (error: Throwable) { fs.delete(target, mustExist = false); throw error }
    }
    suspend fun delete(token: String, id: String) {
        http.delete(root + "/" + id.encodeURLPathPart()) { authorize(token) }.checked()
    }
}

internal suspend fun uploadAutomaticBackup(path: String, container: com.ziaee.zabankhan.app.AppContainer? = null): Boolean {
    val token = backgroundDriveAccessToken() ?: return false
    val http = io.ktor.client.HttpClient()
    try {
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { okio.FileSystem.SYSTEM.read(path.toPath()) { readByteArray() } }
        DriveBackupClient(http).upload(token, bytes, kotlin.time.Clock.System.now().toEpochMilliseconds())
        container?.settings?.backupPrefs?.setLastBackupAtMs(kotlin.time.Clock.System.now().toEpochMilliseconds())
        container?.settings?.backupPrefs?.setLastDriveUploadAtMs(backupEpoch(path.toPath().name) ?: kotlin.time.Clock.System.now().toEpochMilliseconds())
        return true
    } finally { http.close() }
}
