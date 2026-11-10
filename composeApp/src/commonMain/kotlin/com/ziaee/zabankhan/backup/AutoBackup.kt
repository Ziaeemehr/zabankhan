package com.ziaee.zabankhan.backup

import androidx.room.useReaderConnection
import com.ziaee.zabankhan.app.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Clock
import kotlin.time.Instant

private const val AUTO_BACKUP_BUDGET_BYTES = 1024L * 1024 * 1024
private const val AUTO_BACKUP_ALWAYS_KEEP = 3
private const val AUTO_BACKUP_PREFIX = "zabankhan-"
private const val PROTECTED_BACKUP_PREFIX = "zabankhan-keep-"
private val fs get() = FileSystem.SYSTEM
internal fun isProtectedBackupFileName(name: String) = name.startsWith(PROTECTED_BACKUP_PREFIX) && name.endsWith(".zip")
internal fun isAutoBackupFileName(name: String) = name.startsWith(AUTO_BACKUP_PREFIX) && !isProtectedBackupFileName(name) && name.endsWith(".zip")
internal fun backupEpoch(name: String): Long? = name.removePrefix(if (isProtectedBackupFileName(name)) PROTECTED_BACKUP_PREFIX else AUTO_BACKUP_PREFIX).removeSuffix(".zip").toLongOrNull()
internal fun backupTimestamp(name: String): LocalDateTime? = backupEpoch(name)?.let { runCatching { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull() }
internal fun selectAutoBackupsToDelete(names: List<String>, now: LocalDateTime): List<String> {
    val parsed = names.filter(::isAutoBackupFileName).mapNotNull { name -> backupTimestamp(name)?.let { name to it } }
    val keep = selectBackupsToKeep(parsed.map { it.second }, now)
    return parsed.filter { it.second !in keep }.map { it.first }
}
internal fun selectProtectedBackupsToDelete(names: List<String>): List<String> =
    names.filter(::isProtectedBackupFileName).filter { backupTimestamp(it) != null }.sortedDescending().drop(5)
internal fun databaseModifiedAt(): Long {
    val path = backupPaths().database
    return listOf(path, "$path-wal").maxOf { fs.metadataOrNull(it.toPath())?.lastModifiedAtMillis ?: 0L }
}
internal suspend fun readBackupCounts(container: AppContainer): BackupCounts = container.database.useReaderConnection { connection ->
    suspend fun count(table: String): Long = connection.usePrepared("SELECT COUNT(*) FROM $table") { it.step(); it.getLong(0) }
    BackupCounts(count("vocab"), count("texts"), count("exam_attempts"))
}
internal expect suspend fun scheduleDriveUpload(path: String)

/** French Reader AutoBackup: change detection, protected snapshots and calendar rotation. */
object AutoBackup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun listBackups(): List<Path> {
        val root = backupPaths().automatic.toPath()
        if (!fs.exists(root)) return emptyList()
        return fs.list(root).filter { fs.metadata(it).isRegularFile && (isAutoBackupFileName(it.name) || isProtectedBackupFileName(it.name)) }
            .sortedByDescending { backupEpoch(it.name) ?: Long.MIN_VALUE }
    }
    fun latest(): Path? = listBackups().firstOrNull { isAutoBackupFileName(it.name) && backupTimestamp(it.name) != null }
    fun onAppBackgrounded(container: AppContainer) {
        scope.launch { runCatching { backUpIfChanged(container) }.onFailure { BackupRuntime.lastFailure = it.message } }
    }
    internal suspend fun protectCurrentStateLocked(container: AppContainer) {
        if (!hasUserData(container)) return
        val archive = createLocalBackupLocked(container).toPath()
        try { protect(archive) } finally { fs.delete(archive, mustExist = false) }
    }
    internal suspend fun protectBeforeSync(container: AppContainer) = backupMutex.withLock {
        withContext(Dispatchers.IO) { protectCurrentStateLocked(container) }
    }
    private fun protect(archive: Path) {
        val root = backupPaths().automatic.toPath()
        fs.createDirectories(root)
        var at = Clock.System.now().toEpochMilliseconds()
        fun target() = root / "$PROTECTED_BACKUP_PREFIX${at.toString().padStart(16, '0')}.zip"
        while (fs.exists(target())) at++
        val temp = root / ".protected-$at.tmp"
        try { fs.copy(archive, temp); fs.atomicMove(temp, target()) } finally { fs.delete(temp, mustExist = false) }
        selectProtectedBackupsToDelete(fs.list(root).map { it.name }).forEach { fs.delete(root / it) }
    }
    private suspend fun hasUserData(container: AppContainer) = readBackupCounts(container).let { it.vocab > 0 || it.texts > 0 || it.examAttempts > 0 }
    suspend fun backUpIfChanged(container: AppContainer) = backupMutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = container.settings.backupPrefs
            if (!prefs.isAutoBackupEnabled()) return@withContext
            val startedAt = Clock.System.now().toEpochMilliseconds()
            val lastAt = prefs.getLastAutoBackupAtMs() ?: 0L
            val pdfRoot = backupPaths().files.toPath() / "pdfs"
            val pdfModifiedAt = if (fs.exists(pdfRoot)) {
                (fs.listRecursively(pdfRoot).toList() + pdfRoot).maxOfOrNull { fs.metadata(it).lastModifiedAtMillis ?: 0L } ?: 0L
            } else 0L
            if (prefs.getLastDatabaseStamp() == databaseModifiedAt() && pdfModifiedAt <= lastAt) return@withContext
            if (!hasUserData(container)) return@withContext
            val current = readBackupCounts(container)
            val previous = prefs.getLastAutoBackupCounts()
            if (previous != null && isLargeLoss(previous, current)) {
                // Protection failure must not stop subsequent snapshots or time-based rotation.
                latest()?.let { runCatching { protect(it) }.onFailure { BackupRuntime.lastFailure = it.message } }
            }
            val archive = createLocalBackupLocked(container, automatic = true)
            prefs.recordAutomatic(current, startedAt)
            val root = backupPaths().automatic.toPath()
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            selectAutoBackupsToDelete(fs.list(root).map { it.name }, now).forEach { fs.delete(root / it) }
            trimToSizeBudget(root)
            scheduleDriveUpload(archive)
        }
    }
    private fun trimToSizeBudget(directory: Path) {
        val files = fs.list(directory).filter { isAutoBackupFileName(it.name) && backupTimestamp(it.name) != null }.sortedByDescending { it.name }
        var total = files.sumOf { fs.metadata(it).size ?: 0L }
        files.drop(AUTO_BACKUP_ALWAYS_KEEP).asReversed().forEach { file ->
            if (total <= AUTO_BACKUP_BUDGET_BYTES) return
            val size = fs.metadata(file).size ?: 0L
            fs.delete(file); total -= size
        }
    }
}
