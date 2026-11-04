package com.ziaee.zabankhan.data

import android.content.Context
import androidx.room.Room

fun androidDatabase(context: Context): AppDatabase {
    val app = context.applicationContext
    return buildDatabase(Room.databaseBuilder<AppDatabase>(app, app.getDatabasePath(DB_NAME).absolutePath))
}

fun androidPrefsPath(context: Context): String =
    context.applicationContext.filesDir.resolve(PREFS_FILE).absolutePath
