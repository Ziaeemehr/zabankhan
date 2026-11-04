package com.ziaee.zabankhan.data

import androidx.room.ConstructedBy
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        ShadowAttempt::class, PdfHighlight::class, TextDocument::class, LibraryFolder::class, VocabEntry::class, VocabList::class,
        ReviewLogEntry::class, HighlightEntry::class, ActivityLogEntry::class, CacheEntry::class,
    ],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun migration(): MigrationDao
    abstract fun shadowAttempts(): ShadowAttemptDao
    abstract fun pdfHighlights(): PdfHighlightDao
    abstract fun texts(): TextDao
    abstract fun vocab(): VocabDao
    abstract fun highlights(): HighlightDao
    abstract fun activity(): ActivityDao
    abstract fun cache(): CacheDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

const val DB_NAME = "zabankhan.db"

fun buildDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
