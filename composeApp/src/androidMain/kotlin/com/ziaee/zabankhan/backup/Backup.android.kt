package com.ziaee.zabankhan.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ziaee.zabankhan.ZabankhanApp
import com.ziaee.zabankhan.data.DB_NAME
import java.io.File
import java.util.concurrent.TimeUnit

private lateinit var appContext: Context
fun initializeBackup(context: Context) {
    appContext = context.applicationContext
    WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
        "daily-local-backup", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS).build(),
    )
}
actual fun backupPaths(): BackupPaths = BackupPaths(
    appContext.getDatabasePath(DB_NAME).absolutePath,
    appContext.filesDir.absolutePath,
    File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "Backups").absolutePath,
    File(appContext.cacheDir, "backups").absolutePath,
)
actual fun appVersion(): String = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0.1.0"
actual fun shareBackup(path: String) {
    val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.backupfiles", File(path))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("backup", uri)
    }
    appContext.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
class DailyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        automaticBackupIfDue((applicationContext as ZabankhanApp).container)
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

internal actual fun openBackupDatabase(path: String): com.ziaee.zabankhan.data.AppDatabase =
    com.ziaee.zabankhan.data.buildDatabase(androidx.room.Room.databaseBuilder<com.ziaee.zabankhan.data.AppDatabase>(appContext, path))

internal fun backupApplicationContext(): Context = appContext
internal actual suspend fun scheduleDriveUpload(path: String) {
    val request = androidx.work.OneTimeWorkRequestBuilder<DriveUploadWorker>()
        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
        .setInitialDelay(60, TimeUnit.SECONDS)
        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
    WorkManager.getInstance(appContext).enqueueUniqueWork("auto_backup_drive_upload", androidx.work.ExistingWorkPolicy.REPLACE, request)
}
class DriveUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val archive = AutoBackup.latest()
        if (archive != null) uploadAutomaticBackup(archive.toString(), (applicationContext as ZabankhanApp).container)
        Result.success()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
}

internal actual fun formatBackupTimestamp(atMs: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(atMs))
internal actual fun formatFileSize(bytes: Long): String = android.text.format.Formatter.formatFileSize(appContext, bytes)

internal actual fun formatBackupSize(template: String, bytes: Long): String = String.format(java.util.Locale.getDefault(), template, bytes / (1024.0 * 1024.0))
