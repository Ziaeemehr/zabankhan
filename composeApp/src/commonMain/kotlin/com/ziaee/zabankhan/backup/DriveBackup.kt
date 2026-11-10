package com.ziaee.zabankhan.backup

import kotlinx.coroutines.IO
import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.http.content.OutgoingContent
import okio.Path.Companion.toPath
import okio.FileSystem
import okio.Buffer
import okio.use
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.ziaee.zabankhan.sync.DRIVE_SYNC_FILE_PREFIX
import com.ziaee.zabankhan.sync.MAX_SYNC_FILE_BYTES
import com.ziaee.zabankhan.sync.library.LibraryItem
import com.ziaee.zabankhan.sync.library.LibraryWireFormat
import com.ziaee.zabankhan.sync.library.TOMBSTONE_RETENTION_MS
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
@Serializable data class DriveBlobFile(val id: String, val name: String, val size: String = "", val modifiedTime: String? = null)
@Serializable private data class DriveSyncFiles(val files: List<DriveSyncFile> = emptyList(), val nextPageToken: String? = null)
@Serializable private data class DriveAccount(val user: DriveUser)
@Serializable private data class DriveUser(val permissionId: String)
@Serializable private data class DriveSyncMetadata(val name: String)
// No default for parents: Json omits default values, and Drive must see appDataFolder.
@Serializable private data class DriveMetadata(val name: String, val parents: List<String>)
@Serializable private data class DrivePdfFiles(val files: List<DriveSyncFile> = emptyList(), val nextPageToken: String? = null)
@Serializable private data class DriveBlobFiles(val files: List<DriveBlobFile> = emptyList(), val nextPageToken: String? = null)

/** Only our ZIPs in the hidden application folder are eligible for rotation. */
fun driveBackupsToDelete(files: List<DriveBackupFile>): List<DriveBackupFile> = files
    .filter { it.name.startsWith("zabankhan-backup-") && it.name.endsWith(".zip") }
    .sortedByDescending { Instant.parse(it.createdTime) }.drop(5)

fun pdfBlobsToDelete(allBlobHashes: Set<String>, keptSidecarHashLists: List<Set<String>>): Set<String> =
    allBlobHashes - keptSidecarHashLists.flatten().toSet()
internal fun pdfRefsToDownload(refs: List<BackupPdfRef>, exists: (String) -> Boolean): List<BackupPdfRef> = refs.filterNot { exists(it.path) }

internal fun blobHashFromName(name: String): String? {
    val hash = when {
        name.startsWith("zabankhan-pdf-") && name.endsWith(".pdf") -> name.removePrefix("zabankhan-pdf-").removeSuffix(".pdf")
        name.startsWith("zabankhan-file-") -> name.removePrefix("zabankhan-file-")
        else -> return null
    }
    return hash.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}

internal fun libraryFileDeviceId(name: String): String? = name.removePrefix("zabankhan-library-")
    .removeSuffix(".json").takeIf { name.startsWith("zabankhan-library-") && name.endsWith(".json") && it.isNotBlank() }

/** Blobs that must survive GC: kept backup sidecars, live library items, and (unless ignored) tombstones inside their retention window. */
internal fun libraryBlobKeepSet(items: List<LibraryItem>, sidecarHashes: Collection<Set<String>>, nowMs: Long, ignoreTombstones: Boolean = false): Set<String> =
    sidecarHashes.flatten().toSet() + items.filter { !it.deleted || (!ignoreTombstones && nowMs - it.updatedAtMs <= TOMBSTONE_RETENTION_MS) }.flatMap { it.blobs }

/** Blobs referenced by tombstones and by nothing live or in a kept sidecar. */
internal fun tombstoneOnlyBlobs(items: List<LibraryItem>, sidecarHashes: Collection<Set<String>>, nowMs: Long): Set<String> =
    items.filter { it.deleted }.flatMap { it.blobs }.toSet() - libraryBlobKeepSet(items, sidecarHashes, nowMs, ignoreTombstones = true)

data class DriveCleanupResult(val files: Int, val bytes: Long)

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

    suspend fun listPdfBlobs(token: String, olderThan: Instant? = null): Set<String> = listBlobFiles(token, olderThan).mapNotNull { blobHashFromName(it.name) }.toSet()

    internal suspend fun listBlobFiles(token: String, olderThan: Instant? = null): List<DriveBlobFile> {
        val files = mutableListOf<DriveBlobFile>(); var page: String? = null
        do {
            val response = http.get(root) {
                authorize(token); parameter("spaces", "appDataFolder")
                parameter("q", "trashed = false and 'appDataFolder' in parents and name contains 'zabankhan-'")
                parameter("fields", "nextPageToken,files(id,name,size,modifiedTime)"); parameter("pageSize", "100")
                page?.let { parameter("pageToken", it) }
            }.checked()
            val result = json.decodeFromString<DriveBlobFiles>(response.bodyAsText())
            files += result.files.filter { file -> blobHashFromName(file.name) != null && (olderThan == null || file.modifiedTime?.let { Instant.parse(it) < olderThan } == true) }
            page = result.nextPageToken
        } while (page != null)
        return files
    }

    suspend fun listLibraryFiles(token: String): List<DriveSyncFile> {
        val out = mutableListOf<DriveSyncFile>(); var page: String? = null
        do {
            val response = http.get(root) { authorize(token); parameter("spaces", "appDataFolder"); parameter("q", "trashed = false and 'appDataFolder' in parents and name contains 'zabankhan-library-'"); parameter("fields", "nextPageToken,files(id,name,modifiedTime)"); parameter("pageSize", "100"); page?.let { parameter("pageToken", it) } }.checked()
            val result = json.decodeFromString<DrivePdfFiles>(response.bodyAsText())
            out += result.files.filter { libraryFileDeviceId(it.name) != null }; page = result.nextPageToken
        } while (page != null)
        return out
    }

    suspend fun downloadLibraryFile(token: String, id: String): ByteArray = http.get("$root/${id.encodeURLPathPart()}") { authorize(token); parameter("alt", "media") }.checked().bodyAsBytes().also { require(it.size <= com.ziaee.zabankhan.sync.library.MAX_LIBRARY_FILE_BYTES) { "Remote library file is too large" } }

    suspend fun uploadLibraryFile(token: String, deviceId: String, bytes: ByteArray) {
        require(bytes.size <= com.ziaee.zabankhan.sync.library.MAX_LIBRARY_FILE_BYTES)
        uploadJsonFile(token, "zabankhan-library-$deviceId.json", bytes, "library")
    }

    private suspend fun uploadJsonFile(token: String, name: String, bytes: ByteArray, label: String) {
        val matches = listLibraryFiles(token).filter { it.name == name }
        require(matches.size <= 1) { "Duplicate Drive file name: $name" }
        val old = matches.firstOrNull(); val boundary = "zabankhan-$label-" + kotlin.random.Random.nextLong().toString(16)
        val metadata = if (old == null) json.encodeToString(DriveMetadata(name, listOf("appDataFolder"))) else json.encodeToString(DriveSyncMetadata(name))
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".encodeToByteArray() + bytes + "\r\n--$boundary--\r\n".encodeToByteArray()
        val target = if (old == null) "https://www.googleapis.com/upload/drive/v3/files" else "https://www.googleapis.com/upload/drive/v3/files/${old.id.encodeURLPathPart()}"
        val request: suspend () -> HttpResponse = if (old == null) ({ http.post(target) { authorize(token); parameter("uploadType", "multipart"); contentType(ContentType.parse("multipart/related; boundary=$boundary")); setBody(body) } }) else ({ http.patch(target) { authorize(token); parameter("uploadType", "multipart"); contentType(ContentType.parse("multipart/related; boundary=$boundary")); setBody(body) } })
        request().checked()
    }

    suspend fun uploadBlob(token: String, sha256: String, source: okio.Path, knownExisting: Set<String>? = null) =
        streamBlob(token, sha256, source.toString(), "zabankhan-file-$sha256", "application/octet-stream", "zabankhan-file-", knownExisting)

    suspend fun uploadBlobBytes(token: String, sha256: String, bytes: ByteArray, knownExisting: Set<String>? = null) {
        require(sha256.matches(Regex("[0-9a-f]{64}")) && bytes.size.toLong() <= 256L * 1024 * 1024)
        require(okio.ByteString.of(*bytes).sha256().hex() == sha256) { "Blob hash mismatch" }
        if (sha256 in (knownExisting ?: listPdfBlobs(token))) return
        val boundary = "zabankhan-file-" + kotlin.random.Random.nextLong().toString(16)
        val metadata = json.encodeToString(DriveMetadata("zabankhan-file-$sha256", listOf("appDataFolder")))
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".encodeToByteArray()
        http.post("https://www.googleapis.com/upload/drive/v3/files") { authorize(token); parameter("uploadType", "multipart"); contentType(ContentType.parse("multipart/related; boundary=$boundary")); setBody(prefix + bytes + "\r\n--$boundary--\r\n".encodeToByteArray()) }.checked()
    }

    suspend fun uploadPdfBlob(token: String, sha256: String, path: String, knownExisting: Set<String>? = null) =
        streamBlob(token, sha256, path, "zabankhan-pdf-$sha256.pdf", "application/pdf", "zabankhan-pdf-", knownExisting)

    /** Streams the file into the multipart body; nothing but 8 KiB chunks is held in memory. */
    private suspend fun streamBlob(token: String, sha256: String, path: String, driveName: String, mime: String, boundaryPrefix: String, knownExisting: Set<String>?) {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        if (sha256 in (knownExisting ?: listPdfBlobs(token))) return
        val bytesHash = sha256Hex(path.toPath())
        require(bytesHash == sha256) { "Blob hash mismatch" }
        val file = path.toPath(); val fs = FileSystem.SYSTEM
        val size = fs.metadata(file).size ?: 0L
        if (size > 256L * 1024 * 1024) throw BackupTooLargeException()
        val boundary = boundaryPrefix + kotlin.random.Random.nextLong().toString(16)
        val metadata = json.encodeToString(DriveMetadata(driveName, listOf("appDataFolder")))
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".encodeToByteArray()
        val suffix = "\r\n--$boundary--\r\n".encodeToByteArray()
        http.post("https://www.googleapis.com/upload/drive/v3/files") {
            authorize(token); parameter("uploadType", "multipart"); contentType(ContentType.parse("multipart/related; boundary=$boundary"))
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.parse("multipart/related; boundary=$boundary")
                override val contentLength = prefix.size.toLong() + size + suffix.size
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully(prefix)
                    fs.source(file).use { source ->
                        val buffer = Buffer(); val bytes = ByteArray(8192)
                        while (true) { val n = source.read(buffer, bytes.size.toLong()); if (n == -1L) break; buffer.read(bytes, 0, n.toInt()); channel.writeFully(bytes, 0, n.toInt()) }
                    }
                    channel.writeFully(suffix)
                }
            })
        }.checked()
    }

    internal suspend fun downloadPdfBlob(token: String, sha256: String, targetPath: String, maxBytes: Long = 256L * 1024 * 1024) {
        val target = targetPath.toPath(); val temp = target.parent!! / (target.name + ".download-tmp"); val fs = FileSystem.SYSTEM
        fs.createDirectories(target.parent!!)
        try {
            val id = findPdfBlobId(token, sha256)
            http.prepareGet("$root/${id.encodeURLPathPart()}") { authorize(token); parameter("alt", "media") }.execute { response ->
                response.checked(); val channel = response.bodyAsChannel()
                fs.write(temp) { val bytes = ByteArray(8192); var total = 0L; while (true) { val n = channel.readAvailable(bytes, 0, bytes.size); if (n == -1) break; total += n; if (total > maxBytes) throw BackupTooLargeException(); write(bytes, 0, n) } }
            }
            val hash = sha256Hex(temp)
            require(hash == sha256) { "PDF hash mismatch" }
            if (!fs.exists(target)) fs.atomicMove(temp, target) else fs.delete(temp)
        } catch (error: Throwable) { fs.delete(temp, mustExist = false); throw error }
    }
    private suspend fun findPdfBlobId(token: String, sha256: String): String {
        var page: String? = null
        do {
            val response = http.get(root) { authorize(token); parameter("spaces", "appDataFolder"); parameter("q", "trashed = false and 'appDataFolder' in parents and name contains '$sha256'"); parameter("fields", "nextPageToken,files(id,name)"); page?.let { parameter("pageToken", it) } }.checked()
            val result = json.decodeFromString<DrivePdfFiles>(response.bodyAsText()); result.files.firstOrNull { blobHashFromName(it.name) == sha256 }?.let { return it.id }; page = result.nextPageToken
        } while (page != null)
        error("PDF blob missing")
    }
    /** In-memory download for small inline bodies; the size cap is enforced while streaming. */
    suspend fun downloadBlob(token: String, sha256: String, maxBytes: Long): ByteArray {
        val id = findPdfBlobId(token, sha256)
        val out = Buffer()
        http.prepareGet("$root/${id.encodeURLPathPart()}") { authorize(token); parameter("alt", "media") }.execute { response ->
            response.checked(); val channel = response.bodyAsChannel(); val chunk = ByteArray(8192)
            while (true) {
                val n = channel.readAvailable(chunk, 0, chunk.size); if (n == -1) break
                if (out.size + n > maxBytes) throw BackupTooLargeException()
                out.write(chunk, 0, n)
            }
        }
        return out.readByteArray().also { require(okio.ByteString.of(*it).sha256().hex() == sha256) { "Blob hash mismatch" } }
    }
    suspend fun deletePdfBlob(token: String, sha256: String) { delete(token, findPdfBlobId(token, sha256)) }

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
        val metadata = if (existing == null) json.encodeToString(DriveMetadata(name, listOf("appDataFolder")))
            else json.encodeToString(DriveSyncMetadata(name))
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
        val removed = driveBackupsToDelete(list(token))
        removed.forEach { delete(token, it.id) }
        if (removed.isNotEmpty()) {
            val sidecars = listSidecars(token)
            for (backup in removed) {
                val stamp = backup.name.removePrefix("zabankhan-backup-").removeSuffix(".zip")
                sidecars.firstOrNull { it.name == "zabankhan-backup-$stamp.pdfs.json" }?.let { delete(token, it.id) }
            }
        }
    }
    suspend fun uploadBackup(token: String, bytes: ByteArray, timestamp: Long, pdfRoot: String) {
        val manifestBytes = requireNotNull(com.ziaee.zabankhan.content.ZipArchive.read(bytes, 256L * 1024 * 1024)["manifest.json"])
        val manifest = json.decodeFromString<BackupManifest>(manifestBytes.decodeToString())
        val refs = manifest.pdfs
        val fs = FileSystem.SYSTEM
        val existing = listPdfBlobs(token)
        val uploadedHashes = refs.map { it.sha256 }.distinct().filter { it in existing }.toMutableSet()
        var failedPdfs = 0
        for (ref in refs.distinctBy { it.sha256 }) {
            if (ref.sha256 in existing) continue
            try {
                val path = (pdfRoot.toPath() / ref.path).toString()
                require(sha256Hex(path.toPath()) == ref.sha256) { "PDF changed since backup was created" }
                require((FileSystem.SYSTEM.metadata(path.toPath()).size ?: 0L) <= 256L * 1024 * 1024) { "PDF exceeds 256 MiB" }
                uploadPdfBlob(token, ref.sha256, path, existing)
                uploadedHashes += ref.sha256
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failedPdfs++ }
        }
        BackupRuntime.lastFailure = if (failedPdfs > 0) "$failedPdfs PDF files were not uploaded" else null
        val sidecarName = "zabankhan-backup-$timestamp.pdfs.json"
        val sidecar = json.encodeToString(uploadedHashes.toList())
        val boundary = "zabankhan-sidecar-" + kotlin.random.Random.nextLong().toString(16)
        val metadata = json.encodeToString(DriveMetadata(sidecarName, listOf("appDataFolder")))
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n".encodeToByteArray()
        val sidecarResponse = http.post("https://www.googleapis.com/upload/drive/v3/files") { authorize(token); parameter("uploadType", "multipart"); contentType(ContentType.parse("multipart/related; boundary=$boundary")); setBody(prefix + sidecar.encodeToByteArray() + "\r\n--$boundary--\r\n".encodeToByteArray()) }.checked()
        val sidecarId = runCatching { json.decodeFromString<DriveSyncFile>(sidecarResponse.bodyAsText()).id }.getOrNull()
        try { upload(token, bytes, timestamp) } catch (failure: Throwable) {
            if (sidecarId != null) runCatching { delete(token, sidecarId) }
            throw failure
        }
        runCatching { collectUnreferencedPdfBlobs(token) }
    }
    /** Throws if any kept sidecar or any library file cannot be read, so callers delete nothing on partial knowledge. */
    private suspend fun keptSidecarHashes(token: String): List<Set<String>> {
        val sidecars = listSidecars(token)
        return list(token).take(5).map { backup ->
            val stamp = backup.name.removePrefix("zabankhan-backup-").removeSuffix(".zip")
            val sidecar = sidecars.firstOrNull { it.name == "zabankhan-backup-$stamp.pdfs.json" } ?: error("Missing PDF sidecar")
            val response = http.get("$root/${sidecar.id.encodeURLPathPart()}") { authorize(token); parameter("alt", "media") }.checked()
            json.decodeFromString<List<String>>(response.bodyAsText()).toSet()
        }
    }
    private suspend fun allLibraryItems(token: String): List<LibraryItem> {
        val files = listLibraryFiles(token)
        if (files.isEmpty()) return emptyList()
        val accountId = accountId(token)
        return files.flatMap { LibraryWireFormat.decode(downloadLibraryFile(token, it.id), accountId, requireNotNull(libraryFileDeviceId(it.name))).items }
    }
    private suspend fun collectUnreferencedPdfBlobs(token: String) {
        val keep = libraryBlobKeepSet(allLibraryItems(token), keptSidecarHashes(token), kotlin.time.Clock.System.now().toEpochMilliseconds())
        // Grace period: another device may have uploaded a blob whose backup ZIP and sidecar are not up yet.
        val cutoff = kotlin.time.Clock.System.now() - kotlin.time.Duration.parse("24h")
        for (hash in listPdfBlobs(token, olderThan = cutoff) - keep) deletePdfBlob(token, hash)
    }
    /** Frees Drive space held only by deleted texts (tombstones); no grace period, never touches live or backup-referenced blobs. */
    suspend fun deleteTombstoneOnlyBlobs(token: String): DriveCleanupResult {
        val items = allLibraryItems(token)
        val doomed = tombstoneOnlyBlobs(items, keptSidecarHashes(token), kotlin.time.Clock.System.now().toEpochMilliseconds())
        var files = 0; var bytes = 0L
        for (file in listBlobFiles(token).filter { blobHashFromName(it.name) in doomed }) {
            delete(token, file.id); files++; bytes += file.size.toLongOrNull() ?: 0L
        }
        return DriveCleanupResult(files, bytes)
    }
    private suspend fun listSidecars(token: String): List<DriveSyncFile> {
        val out = mutableListOf<DriveSyncFile>(); var page: String? = null
        do { val response = http.get(root) { authorize(token); parameter("spaces", "appDataFolder"); parameter("q", "trashed = false and 'appDataFolder' in parents and name contains 'zabankhan-backup-'"); parameter("fields", "nextPageToken,files(id,name,modifiedTime)"); parameter("pageSize", "100"); page?.let { parameter("pageToken", it) } }.checked(); val result = json.decodeFromString<DrivePdfFiles>(response.bodyAsText()); out += result.files.filter { it.name.endsWith(".pdfs.json") }; page = result.nextPageToken } while (page != null)
        return out
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
        DriveBackupClient(http).uploadBackup(token, bytes, kotlin.time.Clock.System.now().toEpochMilliseconds(), backupPaths().files + "/pdfs")
        container?.settings?.backupPrefs?.setLastBackupAtMs(kotlin.time.Clock.System.now().toEpochMilliseconds())
        container?.settings?.backupPrefs?.setLastDriveUploadAtMs(backupEpoch(path.toPath().name) ?: kotlin.time.Clock.System.now().toEpochMilliseconds())
        return true
    } finally { http.close() }
}
