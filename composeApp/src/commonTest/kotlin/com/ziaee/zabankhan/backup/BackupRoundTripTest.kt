package com.ziaee.zabankhan.backup

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ziaee.zabankhan.content.ZipArchive
import com.ziaee.zabankhan.data.*
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

internal suspend fun assertBackupRoundTrip(buildTestDatabase: (String) -> AppDatabase) {
    val fs = FileSystem.SYSTEM
    val stem = "backup-round-trip-${Random.nextLong()}"
    val source = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "$stem-source.db"
    val restored = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "$stem-restored.db"
    val lang = "fr"
    try {
            val database = buildTestDatabase(source.toString())
            try {
                val dao = database.migration()
                dao.insertFolder(LibraryFolder(id = 1, lang = lang, name = "Folder"))
                dao.insertListRaw(VocabList(id = 1, lang = lang, name = "Deck"))
                dao.insertText(TextDocument(id = 1, lang = lang, title = "Text", body = "Saved text", lastOffset = 17, pinned = true))
                dao.insertWordRaw(VocabEntry(id = 1, lang = lang, word = "bonjour", textId = 1, listId = 1,
                    learned = true, leitnerBox = 4, nextReviewAtMs = 123_456, lastReviewedAtMs = 120_000))
                dao.insertReviewRaw(ReviewLogEntry(id = 1, entryId = 1, lang = lang, timestampMs = 120_000,
                    knew = true, boxBefore = 3, boxAfter = 4))
                dao.insertHighlight(HighlightEntry(id = 1, textId = 1, startOffset = 2, endOffset = 7, colorKey = "yellow"))
                dao.insertActivity(ActivityLogEntry(date = "2026-11-08", lang = lang, readingMs = 12_000, listeningMs = 3_000, watchingMs = 4_000))
                database.cache().put(CacheEntry(key = "grammar:favorites:fr", value = "unit-1", updatedAtMs = 10))
                dao.insertTag(LibraryTag(id = 1, name = "Important", createdAtMs = 11, lang = lang))
                dao.insertTagRef(TextTagCrossRef(textId = 1, tagId = 1))
                dao.insertAttempt(ExamAttempt(id = 1, examId = "exam", questionId = "q1", question = "Question",
                    answer = "Answer", score = 8.5, note = "note", createdAtMs = 20, updatedAtMs = 21, lang = lang))
                dao.insertStudySource(StudySource(id = 1, name = "Book", lang = lang, kind = SourceKind.BOOK,
                    defaultSkill = StudySkill.CE, createdAt = 23))
                dao.insertStudySession(StudySession(id = 1, date = 22, durationMin = 35, skill = StudySkill.CE,
                    sourceId = 1, note = "chapter 2", createdAt = 23, updatedAt = 24, lang = lang))
                dao.insertResource(ResourceLink(id = 1, title = "Resource", url = "https://example.org", lang = lang,
                    description = "Saved description", category = "reading"))
                dao.insertHeadline(HeadlineEntity(sourceId = "source", sourceLabel = "Source", externalId = "h1",
                    title = "Headline", snippet = "Snippet", articleUrl = "https://example.org/article", imageUrl = null,
                    publishedAtMs = 25, cachedAtMs = 26, lang = lang))
                dao.insertShadow(ShadowAttempt(id = 1, lang = lang, textId = 1, sentence = "Une phrase", chunkIndex = 2,
                    sentenceIndex = 3, engine = "device", matched = 8, total = 10, paceRatio = .9f, timestampMs = 27))
                database.pdfHighlights().insert(PdfHighlight(id = 1, textId = 1, page = 2, startWord = 3, endWord = 5, colorKey = "blue"))
                database.pdfInk().upsert(PdfInk(id = 1, textId = 1, page = 2, strokes = "stroke-data"))
                database.useWriterConnection { connection ->
                    connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
                }
            } finally {
                database.close()
            }

            val expected = USER_DATA_TABLES.associateWith { rows(source.toString(), it) }
            val archived = ZipArchive.writeStored(mapOf(DB_NAME to fs.read(source) { readByteArray() }))
            val databaseBytes = requireNotNull(ZipArchive.read(archived)[DB_NAME])
            fs.write(restored) { write(databaseBytes) }
            val actual = USER_DATA_TABLES.associateWith { rows(restored.toString(), it) }
            assertEquals(expected, actual)

            // Keep explicit assertions for the two histories most likely to be lost by a partial export.
            val vocabRow = actual.getValue("vocab").single()
            assertEquals(listOf("1", "fr", "bonjour", "", "1", null, null, "fa", "1", "1"), vocabRow.take(10))
            assertEquals("4", vocabRow[11]) // Leitner box
            assertEquals("123456", vocabRow[12]) // Next review timestamp
            assertEquals("120000", vocabRow[13]) // Last review timestamp
            assertEquals(1, actual.getValue("review_log").size)
            assertEquals("3", actual.getValue("review_log").single()[5]) // boxBefore
            assertEquals("4", actual.getValue("review_log").single()[6]) // boxAfter
            assertEquals(listOf("1", "Book", "book", "CE", null, "0", "23", "fr"), actual.getValue("study_source").single())
            assertEquals(listOf("1", "22", "35", "CE", "1", "chapter 2", "23", "24", "fr"), actual.getValue("study_session").single())
        } finally {
            fs.delete(source, mustExist = false)
            fs.delete(restored, mustExist = false)
            fs.delete(("$source-wal").toPath(), mustExist = false)
            fs.delete(("$source-shm").toPath(), mustExist = false)
            fs.delete(("$restored-wal").toPath(), mustExist = false)
            fs.delete(("$restored-shm").toPath(), mustExist = false)
    }
}

class BackupPdfMergeTest {
    @Test
    fun mergesOnlyMissingPdfFiles() {
        val fs = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "backup-pdf-merge-${Random.nextLong()}"
        val stage = root / "stage/pdfs"
        val target = root / "files/pdfs"
        try {
            fs.createDirectories(stage / "nested")
            fs.createDirectories(target / "nested")
            fs.write(stage / "nested/existing.pdf") { writeUtf8("archive") }
            fs.write(stage / "new.pdf") { writeUtf8("new") }
            fs.write(target / "nested/existing.pdf") { writeUtf8("local") }

            mergeBackupPdfs(stage, target)

            assertEquals("local", fs.read(target / "nested/existing.pdf") { readUtf8() })
            assertEquals("new", fs.read(target / "new.pdf") { readUtf8() })
        } finally { fs.deleteRecursively(root, mustExist = false) }
    }

    @Test fun oldManifestDefaultsToNoPdfReferences() {
        val old = Json.decodeFromString<BackupManifest>("""{"appVersion":"1","schemaVersion":7}""")
        assertTrue(old.pdfs.isEmpty())
    }
    @Test fun pdfReferencesRecordHashWithoutZipEntries() {
        val manifest = BackupManifest("1", 7, pdfs = listOf(BackupPdfRef("book.pdf", 3, "abc")))
        val zip = ZipArchive.writeStored(mapOf("manifest.json" to Json.encodeToString(manifest).encodeToByteArray()))
        val entries = ZipArchive.read(zip)
        assertTrue(entries.keys.none { it.startsWith("pdfs/") })
        assertEquals("abc", Json.decodeFromString<BackupManifest>(entries.getValue("manifest.json").decodeToString()).pdfs.single().sha256)
    }
}

private fun rows(path: String, table: String): List<List<String?>> =
    BundledSQLiteDriver().open(path).use { db -> db.rows(table) }

private fun SQLiteConnection.rows(table: String): List<List<String?>> =
    prepare("SELECT * FROM `$table` ORDER BY rowid").use { statement -> buildList {
        while (statement.step()) add((0 until statement.getColumnCount()).map { index ->
            if (statement.isNull(index)) null else statement.getText(index)
        })
    } }

// texts_fts is a derived Room index and is rebuilt from texts when the database opens.
private val USER_DATA_TABLES = listOf(
    "texts", "library_folders", "library_tags", "text_tags", "vocab", "vocab_lists", "review_log",
    "activity_log", "study_source", "study_session", "highlights", "pdf_highlights", "pdf_ink",
    "headlines", "resources", "exam_attempts", "shadow_attempts", "kv_cache",
)
