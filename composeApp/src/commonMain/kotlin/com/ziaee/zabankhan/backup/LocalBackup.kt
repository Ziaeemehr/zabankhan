package com.ziaee.zabankhan.backup

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.useWriterConnection
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteConnection
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.content.ZipArchive
import com.ziaee.zabankhan.data.DB_NAME
import com.ziaee.zabankhan.data.PREFS_FILE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import com.ziaee.zabankhan.data.createPrefsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.HashingSink
import okio.blackholeSink
import kotlin.time.Clock

const val BACKUP_SCHEMA_VERSION = 8
private const val MAX_BACKUP_BYTES = 256L * 1024 * 1024
class BackupTooLargeException : IllegalArgumentException("Backup exceeds 256 MiB")
@Serializable data class BackupPdfRef(val path: String, val size: Long, val sha256: String)
@Serializable
data class BackupManifest(val appVersion: String, val schemaVersion: Int, val formatVersion: Int = 1, val filesRoot: String = "", val includesPdfs: Boolean? = null, val pdfs: List<BackupPdfRef> = emptyList())

fun validateBackupManifest(manifest: BackupManifest, currentSchema: Int = BACKUP_SCHEMA_VERSION) {
    require(manifest.formatVersion == 1) { "Unsupported backup format" }
    require(manifest.schemaVersion in 1..currentSchema) { "Backup needs a newer app version" }
    require(manifest.appVersion.isNotBlank()) { "Missing application version" }
}

data class BackupPaths(val database: String, val files: String, val automatic: String, val exports: String)
expect fun backupPaths(): BackupPaths
expect fun shareBackup(path: String)
expect fun appVersion(): String
internal expect fun openBackupDatabase(path: String): com.ziaee.zabankhan.data.AppDatabase

object BackupRuntime {
    var lastFailure: String? = null
    var onRestored: (() -> Unit)? = null
}
internal val backupMutex = Mutex()
private val fs get() = FileSystem.SYSTEM
private val json = Json { ignoreUnknownKeys = true }

suspend fun createLocalBackup(container: AppContainer, automatic: Boolean = false, embedPdfs: Boolean = false): String = backupMutex.withLock {
    createLocalBackupLocked(container, automatic, embedPdfs)
}

internal suspend fun createLocalBackupLocked(container: AppContainer, automatic: Boolean = false, embedPdfs: Boolean = false): String = withContext(Dispatchers.IO) {
        container.settings.persistForBackup()
        // Checkpoint through the same Room connection pool before reading the main database file.
        container.database.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { statement ->
                check(statement.step() && statement.getLong(0) == 0L) { "Database is busy; try again" }
            }
            val paths = backupPaths()
            val pdfRefs = mutableListOf<BackupPdfRef>()
            var entries = linkedMapOf("manifest.json" to byteArrayOf())
            val sources = linkedMapOf(DB_NAME to paths.database.toPath())
            val base = paths.files.toPath()
            val prefs = base / PREFS_FILE
            check(fs.exists(prefs)) { "Preferences have not been saved yet" }
            sources[PREFS_FILE] = prefs
            for (directory in listOf("videos", "text_bodies", "text_images")) {
                val root = base / directory
                if (fs.exists(root)) fs.listRecursively(root).forEach { file ->
                    if (fs.metadata(file).isRegularFile) sources["$directory/${file.relativeTo(root)}"] = file
                }
            }
            run {
                val root = base / "pdfs"
                // Skip orphan PDFs left behind by deleted texts.
                val used = container.texts.pdfPaths().map { it.substringAfterLast('/') }.toSet()
                if (fs.exists(root)) fs.listRecursively(root).filter { fs.metadata(it).isRegularFile && it.name in used }.forEach { file ->
                    val hash = sha256Hex(file)
                    pdfRefs += BackupPdfRef(file.relativeTo(root).toString(), fs.metadata(file).size ?: 0L, hash)
                }
            }
            entries["manifest.json"] = json.encodeToString(BackupManifest(appVersion(), BACKUP_SCHEMA_VERSION, filesRoot = paths.files, includesPdfs = pdfRefs.isNotEmpty(), pdfs = pdfRefs)).encodeToByteArray()
            if (embedPdfs) {
                pdfRefs.forEach { ref ->
                    val p = ref.path.toPath()
                    require(!p.isAbsolute && p.segments.none { it == ".." }) { "Invalid PDF reference path" }
                    val file = base / "pdfs" / p
                    entries["pdfs/${ref.path}"] = fs.read(file) { readByteArray() }
                }
            }
            fun estimatedSize() = sources.entries.sumOf { (name, file) -> (fs.metadata(file).size ?: 0L) + 76L + 2 * name.encodeToByteArray().size } + 4096
            fun buildArchive(): ByteArray {
                sources.forEach { (name, file) -> entries[name] = fs.read(file) { readByteArray() } }
                return ZipArchive.writeStored(entries)
            }
            if (estimatedSize() > MAX_BACKUP_BYTES) {
                if (embedPdfs) {
                    entries.keys.filter { it.startsWith("pdfs/") }.forEach(entries::remove)
                    BackupRuntime.lastFailure = "PDFs were omitted because the backup exceeds 256 MiB"
                } else throw BackupTooLargeException()
            }
            var archive = buildArchive()
            if (archive.size > MAX_BACKUP_BYTES && embedPdfs) {
                entries.keys.filter { it.startsWith("pdfs/") }.forEach(entries::remove)
                BackupRuntime.lastFailure = "PDFs were omitted because the backup exceeds 256 MiB"
                archive = buildArchive()
            }
            val destination = (if (automatic) paths.automatic else paths.exports).toPath()
            fs.createDirectories(destination)
            var timestamp = Clock.System.now().toEpochMilliseconds()
            fun targetPath() = destination / "zabankhan-${timestamp.toString().padStart(16, '0')}.zip"
            while (fs.exists(targetPath())) timestamp++
            val target = targetPath()
            val temp = target.parent!! / "${target.name}.tmp"
            if (archive.size > MAX_BACKUP_BYTES) throw BackupTooLargeException()
            try { fs.write(temp) { write(archive) }; fs.atomicMove(temp, target) }
            finally { fs.delete(temp, mustExist = false) }
            if (automatic) container.settings.backupPrefs.setLastDatabaseStamp(databaseModifiedAt())
            target.toString()
        }
}

suspend fun validateBackup(path: String): BackupManifest = withContext(Dispatchers.IO) {
    val entries = readBackup(path)
    validateEntries(entries)
}

private fun validateEntries(entries: Map<String, ByteArray>): BackupManifest {
    val manifest = json.decodeFromString<BackupManifest>(requireNotNull(entries["manifest.json"]) { "Missing backup manifest" }.decodeToString())
    validateBackupManifest(manifest)
    validateDatabaseHeader(requireNotNull(entries[DB_NAME]) { "Missing database" }, manifest.schemaVersion)
    require(entries.containsKey(PREFS_FILE)) { "Missing preferences" }
    require(entries.keys.all { it == "manifest.json" || it == DB_NAME || it == PREFS_FILE || it.startsWith("pdfs/") || it.startsWith("videos/") || it.startsWith("text_images/") || it.startsWith("text_bodies/") }) { "Unexpected backup entry" }
    return manifest
}

private fun readBackup(path: String): Map<String, ByteArray> {
    if ((fs.metadata(path.toPath()).size ?: 0L) > MAX_BACKUP_BYTES) throw BackupTooLargeException()
    return ZipArchive.read(fs.read(path.toPath()) { readByteArray() }, MAX_BACKUP_BYTES)
}

internal fun validateDatabaseHeader(bytes: ByteArray, schemaVersion: Int) {
    require(bytes.size >= 100 && bytes.copyOfRange(0, 16).decodeToString() == "SQLite format 3\u0000") { "Invalid SQLite database" }
    val actualVersion = (60..63).fold(0L) { n, i -> (n shl 8) or (bytes[i].toLong() and 255L) }
    require(actualVersion in schemaVersion.toLong()..BACKUP_SCHEMA_VERSION.toLong()) { "Database schema does not match backup manifest" }
}

/** Validate and stage everything before closing the live container or replacing any data. */
suspend fun restoreLocalBackup(container: AppContainer, path: String, driveToken: String? = null) = backupMutex.withLock {
    requireNotNull(BackupRuntime.onRestored) { "No app reload handler installed" }
    var closed = false
    try { withContext(Dispatchers.IO) {
        val entries = readBackup(path)
        val manifest = validateEntries(entries)
        val paths = backupPaths()
        val stage = paths.files.toPath() / "restore-staging"
        fs.deleteRecursively(stage, mustExist = false)
        fs.createDirectories(stage)
        entries.filterKeys { it != "manifest.json" }.forEach { (name, bytes) ->
            val file = stage / name
            fs.createDirectories(file.parent!!)
            fs.write(file) { write(bytes) }
        }
        BundledSQLiteDriver().open((stage / DB_NAME).toString()).use { db ->
            db.prepare("PRAGMA integrity_check").use { statement ->
                check(statement.step() && statement.getText(0) == "ok" && !statement.step()) { "Backup database is damaged" }
            }
        }
        // Room opens lazily: execute a query now to validate identity/schema and run
        // supported migrations while all original files and the live app remain intact.
        val stagedDatabase = openBackupDatabase((stage / DB_NAME).toString())
        try {
            stagedDatabase.useReaderConnection { connection ->
                connection.usePrepared("SELECT COUNT(*) FROM texts") { it.step() }
            }
            stagedDatabase.sync().setMetadata(com.ziaee.zabankhan.data.SyncMetadataEntry("restore_pending", "1"))
            stagedDatabase.sync().setMetadata(com.ziaee.zabankhan.data.SyncMetadataEntry("library_restore_pending", "1"))
        } finally { stagedDatabase.close() }
        // File paths live inside the database and watch-history JSON. Rebase only the
        // previous sandbox prefix, before swapping the staged database into place.
        BundledSQLiteDriver().open((stage / DB_NAME).toString()).use { db ->
            if (manifest.filesRoot.isNotBlank() && manifest.filesRoot != paths.files) rebaseBackupPaths(db, manifest.filesRoot, paths.files)
            db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
                check(it.step() && it.getLong(0) == 0L) { "Staged database checkpoint failed" }
            }
        }
        // Validate the staged protobuf with DataStore's serializer before replacing
        // the current preferences; use a separate scope and stop its file actor first.
        val stagedPrefsJob = SupervisorJob()
        try {
            createPrefsStore((stage / PREFS_FILE).toString(), CoroutineScope(stagedPrefsJob + Dispatchers.IO)).edit {
                // Older archives included this credential; never restore it.
                it.remove(stringPreferencesKey("llm_api_key"))
                it.remove(stringPreferencesKey("backup:auto_counts"))
                it.remove(stringPreferencesKey("backup:database_stamp"))
            }
        } finally { stagedPrefsJob.cancelAndJoin() }
        val base = paths.files.toPath()
        val token = driveToken ?: runCatching { backgroundDriveAccessToken() }.getOrNull()
        val needed = pdfRefsToDownload(manifest.pdfs) { relative ->
            val p = relative.toPath()
            p.isAbsolute || p.segments.any { it == ".." } || fs.exists((base / "pdfs" / p)) || fs.exists((stage / "pdfs" / p))
        }
        var failedPdfs = 0
        if (token != null) {
            val http = io.ktor.client.HttpClient {
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 10 * 60 * 1000L
                    connectTimeoutMillis = 30_000L
                    socketTimeoutMillis = 30_000L
                }
            }
            val client = DriveBackupClient(http)
            try {
                for (ref in needed) try {
                    val p = ref.path.toPath()
                    require(!p.isAbsolute && p.segments.none { it == ".." }) { "Invalid PDF reference path" }
                    client.downloadPdfBlob(token, ref.sha256, (stage / "pdfs" / p).toString())
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failedPdfs++ }
            } finally { http.close() }
        } else failedPdfs = needed.size
        BackupRuntime.lastFailure = if (failedPdfs > 0) "$failedPdfs PDF files could not be restored" else null
        AutoBackup.protectCurrentStateLocked(container)
        closed = true
        container.database.close()
        container.settings.closeForRestore()
        val rollback = base / "restore-previous"
        val directories = restoreFileDirectories()
        val targets = listOf(DB_NAME to paths.database.toPath(), PREFS_FILE to (base / PREFS_FILE)) + directories.map { it to (base / it) }
        val moved = mutableListOf<Pair<String, Path>>()
        val addedPdfs = mutableListOf<Path>()
        try {
            for ((name, target) in targets) {
                if (fs.exists(target)) fs.atomicMove(target, rollback / name)
                moved += name to target
                val source = stage / name
                if (fs.exists(source)) { fs.createDirectories(target.parent!!); fs.atomicMove(source, target) }
            }
            mergeBackupPdfs(stage / "pdfs", base / "pdfs", addedPdfs)
            for (suffix in listOf("-wal", "-shm")) fs.delete((paths.database + suffix).toPath(), mustExist = false)
        } catch (failure: Exception) {
            addedPdfs.asReversed().forEach { fs.delete(it, mustExist = false) }
            moved.asReversed().forEach { (name, target) ->
                fs.deleteRecursively(target, mustExist = false)
                if (fs.exists(rollback / name)) fs.atomicMove(rollback / name, target)
            }
            throw failure
        } finally {
            fs.deleteRecursively(stage, mustExist = false)
        }
        fs.deleteRecursively(rollback, mustExist = false)
    } } finally {
        if (closed) withContext(NonCancellable + Dispatchers.Main) { BackupRuntime.onRestored?.invoke() }
    }
}

/** Streams the file through SHA-256 so large PDFs are never loaded into memory. */
internal fun sha256Hex(file: Path): String {
    val sink = HashingSink.sha256(blackholeSink())
    fs.read(file) { readAll(sink) }
    return sink.hash.hex()
}

internal fun mergeBackupPdfs(stageDir: Path, targetDir: Path, added: MutableList<Path> = mutableListOf()) {
    if (!fs.exists(stageDir)) return
    for (source in fs.listRecursively(stageDir)) {
        if (!fs.metadata(source).isRegularFile) continue
        val target = targetDir / source.relativeTo(stageDir)
        if (fs.exists(target)) continue
        fs.createDirectories(target.parent!!)
        fs.atomicMove(source, target)
        added += target
    }
}

internal fun rebaseBackupPaths(db: SQLiteConnection, previousRoot: String, newRoot: String) {
    require(previousRoot.isNotBlank() && newRoot.isNotBlank())
    listOf("UPDATE texts SET pdfPath = replace(pdfPath, ?, ?), externalKey = replace(externalKey, ?, ?), imagePath = replace(imagePath, ?, ?), bodyPath = replace(bodyPath, ?, ?)",
        "UPDATE kv_cache SET value = replace(value, ?, ?) WHERE key LIKE 'watch:history:%'").forEachIndexed { index, sql ->
        db.prepare(sql).use { statement ->
            statement.bindText(1, previousRoot.trimEnd('/') + "/")
            statement.bindText(2, newRoot.trimEnd('/') + "/")
            if (index == 0) {
                for (i in listOf(3, 5, 7)) {
                    statement.bindText(i, previousRoot.trimEnd('/') + "/")
                    statement.bindText(i + 1, newRoot.trimEnd('/') + "/")
                }
            }
            statement.step()
        }
    }
}

suspend fun automaticBackupIfDue(container: AppContainer) = AutoBackup.backUpIfChanged(container)
