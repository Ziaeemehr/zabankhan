package com.ziaee.zabankhan.data

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

@OptIn(ExperimentalForeignApi::class)
fun iosDocumentsDir(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        NSDocumentDirectory, NSUserDomainMask, null, true, null,
    )
    return requireNotNull(url?.path) { "No documents directory" }
}

fun iosDatabase(): AppDatabase =
    buildDatabase(Room.databaseBuilder<AppDatabase>(name = "${iosDocumentsDir()}/$DB_NAME"))

fun iosPrefsPath(): String = "${iosDocumentsDir()}/$PREFS_FILE"

/** Exclude re-downloadable or regenerable content from iCloud device backups. */
@OptIn(ExperimentalForeignApi::class)
fun excludeFromICloudBackup(path: String) {
    val manager = NSFileManager.defaultManager
    manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    NSURL.fileURLWithPath(path).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
}
