@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.backup

import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.DB_NAME
import com.ziaee.zabankhan.data.iosDocumentsDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.*
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

actual fun backupPaths(): BackupPaths {
    val base = iosDocumentsDir()
    return BackupPaths("$base/$DB_NAME", base, "$base/Backups", "$base/BackupExports")
}
actual fun appVersion(): String = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.1.0"
actual fun shareBackup(path: String) {
    val controller = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), null)
    var presenter: UIViewController = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
    // iPad requires an anchored popover, including when only saving to Files.
    controller.popoverPresentationController?.sourceView = presenter.view
    controller.popoverPresentationController?.sourceRect = presenter.view.bounds
    presenter.presentViewController(controller, true, null)
}
private var pendingDriveUpload: String? = null
private var currentContainer: (() -> AppContainer)? = null
private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
fun installBackupLifecycle(container: () -> AppContainer) { currentContainer = container }

/** Called by the Swift host's scenePhase, on both platforms' equivalent lifecycle boundary. */
object IosBackupLifecycle {
    fun onScenePhaseChanged(active: Boolean, backgrounded: Boolean) {
        if (active) backupScope.launch { uploadPendingDriveBackup() }
        if (backgrounded) {
            val container = currentContainer?.invoke() ?: return
            val app = UIApplication.sharedApplication
            val task = app.beginBackgroundTaskWithName("Local backup", {})
            backupScope.launch {
                try { runCatching { automaticBackupIfDue(container) }.onFailure { BackupRuntime.lastFailure = it.message } }
                finally { app.endBackgroundTask(task) }
            }
        }
    }
}

internal actual fun openBackupDatabase(path: String): com.ziaee.zabankhan.data.AppDatabase =
    com.ziaee.zabankhan.data.buildDatabase(androidx.room.Room.databaseBuilder<com.ziaee.zabankhan.data.AppDatabase>(name = path))

internal actual suspend fun scheduleDriveUpload(path: String) = kotlinx.coroutines.withContext(Dispatchers.Main) {
    pendingDriveUpload = path
    uploadPendingDriveBackup()
}
private suspend fun uploadPendingDriveBackup() {
    if (UIApplication.sharedApplication.applicationState != platform.UIKit.UIApplicationState.UIApplicationStateActive) return
    val container = currentContainer?.invoke() ?: return
    val path = pendingDriveUpload ?: AutoBackup.latest()?.takeIf {
        (backupEpoch(it.name) ?: 0L) > (container.settings.backupPrefs.getLastDriveUploadAtMs() ?: 0L)
    }?.toString() ?: return
    try {
        if (uploadAutomaticBackup(path, container) && pendingDriveUpload == path) pendingDriveUpload = null
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (error: Exception) { BackupRuntime.lastFailure = error.message }
}

internal actual fun formatBackupTimestamp(atMs: Long): String = platform.Foundation.NSDateFormatter().apply {
    dateFormat = "MMM d, yyyy, HH:mm"
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(atMs / 1000.0))
internal actual fun formatFileSize(bytes: Long): String = platform.Foundation.NSByteCountFormatter.stringFromByteCount(bytes, platform.Foundation.NSByteCountFormatterCountStyleFile)

internal actual fun formatBackupSize(template: String, bytes: Long): String {
    val number = NSNumberFormatter().apply { minimumFractionDigits = 2u; maximumFractionDigits = 2u }
        .stringFromNumber(NSNumber(double = bytes / (1024.0 * 1024.0))).orEmpty()
    return template.replace("%1$.2f", number)
}
