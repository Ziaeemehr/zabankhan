package com.ziaee.zabankhan.data

import androidx.room.ConstructedBy
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.ByteString.Companion.encodeUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        TextDocument::class, TextSearchEntry::class, LibraryTag::class, TextTagCrossRef::class, HeadlineEntity::class, ResourceLink::class, ExamAttempt::class, StudySource::class, StudySession::class,
        PdfInk::class, PdfTextNote::class, ShadowAttempt::class, PdfHighlight::class, LibraryFolder::class, VocabEntry::class, VocabList::class,
        ReviewLogEntry::class, HighlightEntry::class, ActivityLogEntry::class, CacheEntry::class,
        SyncJournalEntry::class, SyncMetadataEntry::class, AppliedSyncOperation::class, SyncConflict::class,
        LibrarySyncShadow::class, VideoChannelEntity::class, SavedVideoEntity::class, VideoPlaylistEntity::class, VideoPlaylistMembershipEntity::class,
        // Sync reads all library-owned records through LibrarySyncDao.
    ],
    version = 11,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11)],
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
    abstract fun librarySync(): LibrarySyncDao
    abstract fun videoChannels(): VideoChannelDao
    abstract fun savedVideos(): SavedVideoDao
    abstract fun videoPlaylists(): VideoPlaylistDao
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
        backfillLibrarySyncIds(connection)
        connection.prepare("UPDATE texts SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''").use { it.step() }
        connection.prepare("UPDATE library_folders SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''").use { it.step() }
        val indexed = connection.prepare("SELECT value FROM kv_cache WHERE key='system:text-search-v5'").use { it.step() }
        if (!indexed) {
            connection.prepare("INSERT INTO texts_fts(texts_fts) VALUES('rebuild')").use { it.step() }
            connection.prepare("INSERT INTO kv_cache(key,value,updatedAtMs) VALUES('system:text-search-v5','ready',0)").use { it.step() }
        }
    }
}

/** Same row content gives the same id on every device/restore, so a restored v7 backup does not fork its texts. */
internal fun deterministicSyncId(seed: String, taken: MutableSet<String>): String {
    var n = 0
    while (true) {
        val id = (if (n == 0) seed else "$seed#$n").encodeUtf8().sha256().hex().take(32)
        if (taken.add(id)) return id
        n++
    }
}

internal fun textSyncSeed(lang: String, title: String, createdAtMs: Long, pdfOrBodyPath: String?) =
    "$lang|$title|$createdAtMs|${pdfOrBodyPath.orEmpty().substringAfterLast('/')}"

private fun backfillLibrarySyncIds(c: SQLiteConnection) {
    fun fill(table: String, columns: String, seed: (SQLiteStatement) -> String) {
        val taken = mutableSetOf<String>()
        c.prepare("SELECT syncId FROM $table WHERE syncId != ''").use { while (it.step()) taken += it.getText(0) }
        val updates = mutableListOf<Pair<Long, String>>()
        c.prepare("SELECT id, $columns FROM $table WHERE syncId = '' ORDER BY id").use { while (it.step()) updates += it.getLong(0) to deterministicSyncId(seed(it), taken) }
        updates.forEach { (id, syncId) -> c.prepare("UPDATE $table SET syncId = ? WHERE id = ?").use { it.bindText(1, syncId); it.bindLong(2, id); it.step() } }
    }
    fill("texts", "lang, title, createdAtMs, IFNULL(pdfPath, bodyPath)") { textSyncSeed(it.getText(1), it.getText(2), it.getLong(3), if (it.isNull(4)) null else it.getText(4)) }
    fill("library_folders", "lang, name, createdAtMs") { "${it.getText(1)}|${it.getText(2)}|${it.getLong(3)}" }
}
