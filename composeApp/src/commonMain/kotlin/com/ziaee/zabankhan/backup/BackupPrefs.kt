package com.ziaee.zabankhan.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/** French Reader BackupPrefs, persisted with the app's DataStore. */
class BackupPrefs(private val store: DataStore<Preferences>) {
    private suspend fun value(key: String): String? = store.data.first()[stringPreferencesKey("backup:$key")]
    private suspend fun put(key: String, value: String?) { store.edit { p ->
        val k = stringPreferencesKey("backup:$key")
        if (value == null) p.remove(k) else p[k] = value
    } }
    suspend fun backUpPdfFiles() = value("backup_pdfs") == "true"
    suspend fun setBackUpPdfFiles(enabled: Boolean) { store.edit {
        it[stringPreferencesKey("backup:backup_pdfs")] = enabled.toString()
        it[stringPreferencesKey("backup:pdf_backup_changed_at")] = Clock.System.now().toEpochMilliseconds().toString()
    } }
    internal suspend fun pdfBackupChangedAt() = value("pdf_backup_changed_at")?.toLongOrNull() ?: 0L
    suspend fun isAutoBackupEnabled() = value("auto_backup_enabled") != "false"
    suspend fun setAutoBackupEnabled(enabled: Boolean) = put("auto_backup_enabled", enabled.toString())
    suspend fun getLastBackupAtMs() = value("last_backup_at_ms")?.toLongOrNull()
    suspend fun setLastBackupAtMs(atMs: Long) = put("last_backup_at_ms", atMs.toString())
    internal suspend fun getLastDriveUploadAtMs() = value("last_drive_upload_at_ms")?.toLongOrNull()
    internal suspend fun setLastDriveUploadAtMs(atMs: Long) = put("last_drive_upload_at_ms", atMs.toString())
    internal suspend fun getLastAutoBackupAtMs() = value("last_auto_backup_at_ms")?.toLongOrNull()
    internal suspend fun getLastDatabaseStamp() = value("database_stamp")?.toLongOrNull()
    internal suspend fun setLastDatabaseStamp(stamp: Long) = put("database_stamp", stamp.toString())
    internal suspend fun getLastAutoBackupCounts(): BackupCounts? = value("auto_counts")?.let { kotlinx.serialization.json.Json.decodeFromString<BackupCounts>(it) }
    internal suspend fun recordAutomatic(counts: BackupCounts, atMs: Long) { store.edit {
        it[stringPreferencesKey("backup:auto_counts")] = kotlinx.serialization.json.Json.encodeToString(counts)
        it[stringPreferencesKey("backup:last_auto_backup_at_ms")] = atMs.toString()
        it[stringPreferencesKey("backup:last_backup_at_ms")] = atMs.toString()
    } }
    internal suspend fun clearLastAutoBackupCounts() { put("auto_counts", null); put("database_stamp", null) }
}
