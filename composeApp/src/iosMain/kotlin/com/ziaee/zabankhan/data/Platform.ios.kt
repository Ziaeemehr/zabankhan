package com.ziaee.zabankhan.data

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

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
