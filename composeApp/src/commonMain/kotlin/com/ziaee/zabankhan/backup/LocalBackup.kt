package com.ziaee.zabankhan.backup

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
import kotlin.time.Clock

const val BACKUP_SCHEMA_VERSION = 3
private const val MAX_BACKUP_BYTES = 256L * 1024 * 1024
class BackupTooLargeException : IllegalArgumentException("Backup exceeds 256 MiB")
@Serializable
data class BackupManifest(val appVersion: String, val schemaVersion: Int, val formatVersion: Int = 1, val filesRoot: String = "")

fun validateBackupManifest(manifest: BackupManifest, currentSchema: Int = BACKUP_SCHEMA_VERSION) {
    require(manifest.formatVersion == 1) { "Unsupported backup format" }
    require(manifest.schemaVersion in 1..currentSchema) { "Backup needs a newer app version" }
    require(manifest.appVersion.isNotBlank()) { "Missing application version" }
}

/** The five most recent successful automatic snapshots survive rotation. */
fun selectBackupsToKeep(names: List<String>): Set<String> = names.filter { it.endsWith(".zip") }.sortedDescending().take(5).toSet()

data class BackupPaths(val database: String, val files: String, val automatic: String, val exports: String)
expect fun backupPaths(): BackupPaths
expect fun shareBackup(path: String)
expect fun appVersion(): String
internal expect fun openBackupDatabase(path: String): com.ziaee.zabankhan.data.AppDatabase

object BackupRuntime {
    var onRestored: (() -> Unit)? = null
}
private val backupMutex = Mutex()
private val fs get() = FileSystem.SYSTEM
private val json = Json { ignoreUnknownKeys = true }

suspend fun createLocalBackup(container: AppContainer, automatic: Boolean = false): String = backupMutex.withLock {
    withContext(Dispatchers.IO) {
        container.settings.persistForBackup()
        // Checkpoint through the same Room connection pool before reading the main database file.
        container.database.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { statement ->
                check(statement.step() && statement.getLong(0) == 0L) { "Database is busy; try again" }
            }
            val paths = backupPaths()
            val entries = linkedMapOf("manifest.json" to json.encodeToString(BackupManifest(appVersion(), BACKUP_SCHEMA_VERSION, filesRoot = paths.files)).encodeToByteArray())
            val sources = linkedMapOf(DB_NAME to paths.database.toPath())
            val base = paths.files.toPath()
            val prefs = base / PREFS_FILE
            check(fs.exists(prefs)) { "Preferences have not been saved yet" }
            sources[PREFS_FILE] = prefs
            for (directory in listOf("pdfs", "videos")) {
                val root = base / directory
                if (fs.exists(root)) fs.listRecursively(root).forEach { file ->
                    if (fs.metadata(file).isRegularFile) sources["$directory/${file.relativeTo(root)}"] = file
                }
            }
            if (sources.entries.sumOf { (name, file) -> (fs.metadata(file).size ?: 0L) + 76L + 2 * name.encodeToByteArray().size } + 4096 > MAX_BACKUP_BYTES) throw BackupTooLargeException()
            sources.forEach { (name, file) -> entries[name] = fs.read(file) { readByteArray() } }
            val destination = (if (automatic) paths.automatic else paths.exports).toPath()
            fs.createDirectories(destination)
            val target = destination / "zabankhan-${Clock.System.now().toEpochMilliseconds().toString().padStart(16, '0')}.zip"
            val temp = target.parent!! / "${target.name}.tmp"
            val archive = ZipArchive.writeStored(entries)
            if (archive.size > MAX_BACKUP_BYTES) throw BackupTooLargeException()
            fs.write(temp) { write(archive) }
            fs.atomicMove(temp, target)
            if (automatic) {
                val snapshots = fs.list(destination).filter { it.name.startsWith("zabankhan-") && it.name.endsWith(".zip") }
                val keep = selectBackupsToKeep(snapshots.map { it.name })
                snapshots.filter { it.name !in keep }.forEach { fs.delete(it) }
            }
            target.toString()
        }
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
    require(entries.keys.all { it == "manifest.json" || it == DB_NAME || it == PREFS_FILE || it.startsWith("pdfs/") || it.startsWith("videos/") }) { "Unexpected backup entry" }
    return manifest
}

private fun readBackup(path: String): Map<String, ByteArray> {
    if ((fs.metadata(path.toPath()).size ?: 0L) > MAX_BACKUP_BYTES) throw BackupTooLargeException()
    return ZipArchive.read(fs.read(path.toPath()) { readByteArray() }, MAX_BACKUP_BYTES)
}

internal fun validateDatabaseHeader(bytes: ByteArray, schemaVersion: Int) {
    require(bytes.size >= 100 && bytes.copyOfRange(0, 16).decodeToString() == "SQLite format 3\u0000") { "Invalid SQLite database" }
    val actualVersion = (60..63).fold(0L) { n, i -> (n shl 8) or (bytes[i].toLong() and 255L) }
    require(actualVersion == schemaVersion.toLong()) { "Database schema does not match backup manifest" }
}

/** Validate and stage everything before closing the live container or replacing any data. */
suspend fun restoreLocalBackup(container: AppContainer, path: String) = backupMutex.withLock {
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
            createPrefsStore((stage / PREFS_FILE).toString(), CoroutineScope(stagedPrefsJob + Dispatchers.IO)).data.first()
        } finally { stagedPrefsJob.cancelAndJoin() }
        closed = true
        container.database.close()
        container.settings.closeForRestore()
        val base = paths.files.toPath()
        // Keep the previous files until replacement is complete so a failed move can roll back.
        val rollback = base / "restore-previous"
        fs.deleteRecursively(rollback, mustExist = false)
        fs.createDirectories(rollback)
        val targets = listOf(DB_NAME to paths.database.toPath(), PREFS_FILE to (base / PREFS_FILE), "pdfs" to (base / "pdfs"), "videos" to (base / "videos"))
        val moved = mutableListOf<Pair<String, Path>>()
        try {
            for ((name, target) in targets) {
                if (fs.exists(target)) fs.atomicMove(target, rollback / name)
                moved += name to target
                val source = stage / name
                if (fs.exists(source)) { fs.createDirectories(target.parent!!); fs.atomicMove(source, target) }
            }
            for (suffix in listOf("-wal", "-shm")) fs.delete((paths.database + suffix).toPath(), mustExist = false)
        } catch (failure: Exception) {
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

internal fun rebaseBackupPaths(db: SQLiteConnection, previousRoot: String, newRoot: String) {
    require(previousRoot.isNotBlank() && newRoot.isNotBlank())
    listOf("UPDATE texts SET pdfPath = replace(pdfPath, ?, ?), externalKey = replace(externalKey, ?, ?)",
        "UPDATE kv_cache SET value = replace(value, ?, ?) WHERE key LIKE 'watch:history:%'").forEachIndexed { index, sql ->
        db.prepare(sql).use { statement ->
            statement.bindText(1, previousRoot.trimEnd('/') + "/")
            statement.bindText(2, newRoot.trimEnd('/') + "/")
            if (index == 0) {
                statement.bindText(3, previousRoot.trimEnd('/') + "/")
                statement.bindText(4, newRoot.trimEnd('/') + "/")
            }
            statement.step()
        }
    }
}

suspend fun automaticBackupIfDue(container: AppContainer) {
    val dir = backupPaths().automatic.toPath()
    fs.createDirectories(dir)
    val last = fs.list(dir).filter { it.name.startsWith("zabankhan-") && it.name.endsWith(".zip") }
        .mapNotNull { it.name.removePrefix("zabankhan-").removeSuffix(".zip").toLongOrNull() }.maxOrNull()
    val now = Clock.System.now().toEpochMilliseconds()
    if (last == null || now - last >= 86_400_000L) createLocalBackup(container, automatic = true)
}
// Shortcut: Google Drive backup is deferred; local ZIP exports can be saved to Drive via its share target.
