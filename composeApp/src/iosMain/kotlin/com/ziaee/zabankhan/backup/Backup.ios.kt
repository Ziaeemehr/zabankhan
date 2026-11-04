@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.backup

import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.DB_NAME
import com.ziaee.zabankhan.data.iosDocumentsDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
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
private var backgroundObserver: Any? = null
private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
fun installBackupLifecycle(container: () -> AppContainer) {
    backgroundObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
    backgroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue,
    ) {
        val app = UIApplication.sharedApplication
        val task = app.beginBackgroundTaskWithName("Local backup", {})
        backupScope.launch {
            try { runCatching { automaticBackupIfDue(container()) } }
            finally { app.endBackgroundTask(task) }
        }
    }
}

internal actual fun openBackupDatabase(path: String): com.ziaee.zabankhan.data.AppDatabase =
    com.ziaee.zabankhan.data.buildDatabase(androidx.room.Room.databaseBuilder<com.ziaee.zabankhan.data.AppDatabase>(name = path))
