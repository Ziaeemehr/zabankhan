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
        TextDocument::class, TextSearchEntry::class, LibraryTag::class, TextTagCrossRef::class, HeadlineEntity::class, ResourceLink::class, ExamAttempt::class, StudySource::class, StudySession::class,
        PdfInk::class, PdfTextNote::class, ShadowAttempt::class, PdfHighlight::class, LibraryFolder::class, VocabEntry::class, VocabList::class,
        ReviewLogEntry::class, HighlightEntry::class, ActivityLogEntry::class, CacheEntry::class,
        SyncJournalEntry::class, SyncMetadataEntry::class, AppliedSyncOperation::class, SyncConflict::class,
    ],
    version = 7,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7)],
    exportSchema = true,
)
@androidx.room.TypeConverters(StudyLogConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryOrganizer(): LibraryOrganizerDao
    abstract fun headlines(): HeadlineDao
    abstract fun resources(): ResourceDao
    abstract fun examAttempts(): ExamAttemptDao
    abstract fun studyLog(): StudyLogDao
    abstract fun migration(): MigrationDao
    abstract fun shadowAttempts(): ShadowAttemptDao
    abstract fun pdfInk(): PdfInkDao
    abstract fun pdfTextNotes(): PdfTextNoteDao
    abstract fun pdfHighlights(): PdfHighlightDao
    abstract fun texts(): TextDao
    abstract fun vocab(): VocabDao
    abstract fun sync(): SyncDao
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
    builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).addCallback(TextSearchCallback()).build()

/** The external-content FTS table uses Room's generated synchronization triggers. */
private class TextSearchCallback : RoomDatabase.Callback() {
    override fun onOpen(connection: androidx.sqlite.SQLiteConnection) {
        // Auto-migration adds these columns with their schema default. Fill legacy rows once
        // before the next query observes them; numeric Room IDs and all local references stay intact.
        connection.prepare("UPDATE vocab SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''").use { it.step() }
        connection.prepare("UPDATE vocab_lists SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''").use { it.step() }
        connection.prepare("UPDATE review_log SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''").use { it.step() }
        val indexed = connection.prepare("SELECT value FROM kv_cache WHERE key='system:text-search-v5'").use { it.step() }
        if (!indexed) {
            connection.prepare("INSERT INTO texts_fts(texts_fts) VALUES('rebuild')").use { it.step() }
            connection.prepare("INSERT INTO kv_cache(key,value,updatedAtMs) VALUES('system:text-search-v5','ready',0)").use { it.step() }
        }
    }
}
