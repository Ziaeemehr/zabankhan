package com.ziaee.zabankhan.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FrenchReaderMigrationTest {
    @Test fun readsLegacyColumnsAndFileBackedTextWithoutChangingSource() {
        BundledSQLiteDriver().open(":memory:").use { db ->
            listOf(
                "CREATE TABLE texts (id INTEGER PRIMARY KEY, title TEXT, rawText TEXT, bodyPath TEXT, pdfPath TEXT, sourceUrl TEXT, level TEXT, folderId INTEGER, createdAtMs INTEGER)",
                "CREATE TABLE library_folders (id INTEGER PRIMARY KEY, name TEXT, parentId INTEGER, createdAtMs INTEGER)",
                "CREATE TABLE vocab_lists (id INTEGER PRIMARY KEY, name TEXT, createdAtMs INTEGER)",
                "CREATE TABLE vocab (id INTEGER PRIMARY KEY, word TEXT, sentence TEXT, meaning TEXT, textId INTEGER, listId INTEGER, learned INTEGER, leitnerBox INTEGER, nextReviewAtMs INTEGER, lastReviewedAtMs INTEGER, createdAtMs INTEGER)",
                "CREATE TABLE highlights (id INTEGER PRIMARY KEY, textId INTEGER, startOffset INTEGER, endOffset INTEGER, colorKey TEXT, createdAtMs INTEGER)",
                "CREATE TABLE review_log (id INTEGER PRIMARY KEY, entryId INTEGER, timestampMs INTEGER, knew INTEGER, boxBefore INTEGER, boxAfter INTEGER)",
                "INSERT INTO texts VALUES (7,'Bonjour','inline','/data/user/0/com.ziaee.frenchreader/files/text_bodies/7.txt','/data/user/0/com.ziaee.frenchreader/files/pdfs/7.pdf','https://example.org', 'A2',3,123)",
                "INSERT INTO library_folders VALUES (3,'Child',9,10)",
                "INSERT INTO library_folders VALUES (9,'Root',NULL,10)",
                "INSERT INTO vocab_lists VALUES (4,'Words',12)",
                "INSERT INTO vocab VALUES (8,'bonjour','Bonjour !','سلام',7,4,1,5,456,400,100)",
                "INSERT INTO highlights VALUES (2,7,0,7,'yellow',101)",
                "INSERT INTO review_log VALUES (6,8,400,1,4,5)",
                "PRAGMA user_version = 22",
            ).forEach { sql -> db.prepare(sql).use { it.step() } }
            val snapshot = readFrenchReaderSnapshot(db, mapOf("text_bodies/7.txt" to "File body".encodeToByteArray(), "pdfs/7.pdf" to byteArrayOf(1, 2))) { path, bytes ->
                assertTrue(path.endsWith("7.pdf")); assertContentEquals(byteArrayOf(1, 2), bytes); "/new/7.pdf"
            }
            assertEquals("File body", snapshot.texts.single().body)
            assertEquals("/new/7.pdf", snapshot.texts.single().pdfPath)
            assertEquals("A2", snapshot.texts.single().level)
            assertEquals(Languages.FRENCH.code, snapshot.texts.single().lang)
            val word = snapshot.words.single()
            assertEquals(Languages.PERSIAN.code, word.meaningLang)
            assertEquals(5, word.leitnerBox); assertTrue(word.learned)
            assertEquals(456L, word.nextReviewAtMs); assertEquals(400L, word.lastReviewedAtMs)
            assertEquals(8L, snapshot.reviews.single().entryId)
            db.prepare("PRAGMA user_version").use { assertTrue(it.step()); assertEquals(22L, it.getLong(0)) }
        }
    }

    @Test fun mapsIdsAfterDuplicatesAndRepeatImportIsIdempotent() = runTest {
        val lang = Languages.FRENCH.code
        val dao = MemoryMigrationDao()
        dao.insertText(TextDocument(id = 50, lang = lang, title = "Title", body = "Body"))
        dao.insertWord(VocabEntry(id = 60, lang = lang, word = "bonjour"))
        val snapshot = FrenchReaderSnapshot(
            listOf(TextDocument(id = 1, lang = lang, title = "Title", body = "Body", folderId = 3)),
            listOf(LibraryFolder(3, lang, "Child", 9), LibraryFolder(9, lang, "Root")),
            listOf(VocabEntry(id = 2, lang = lang, word = "Bonjour", textId = 1, listId = 4), VocabEntry(id = 5, lang = lang, word = "salut", textId = 1, listId = 4)),
            listOf(VocabList(4, lang, "List")),
            listOf(HighlightEntry(1, 1, 0, 4, "yellow")),
            listOf(ReviewLogEntry(1, 2, lang, 100, true, 1, 2)),
        )
        val first = dao.importSnapshot(snapshot)
        assertEquals(MigrationSummary(0, 1, 1, 1, 2), first)
        assertEquals(50L, dao.highlights.single().textId)
        assertEquals(60L, dao.reviews.single().entryId)
        val added = dao.words.last()
        assertEquals(50L, added.textId)
        assertEquals(dao.lists.single().id, added.listId)
        assertEquals(dao.folders.first { it.name == "Root" }.id, dao.folders.first { it.name == "Child" }.parentId)
        assertEquals(MigrationSummary(0, 0, 0, 0, 3), dao.importSnapshot(snapshot))
        assertEquals(2, dao.folders.size); assertEquals(1, dao.lists.size)
    }

    @Test fun cannotResolvePathsOutsideArchivedRoots() {
        val files = mapOf("pdfs/a.pdf" to byteArrayOf(1))
        assertNull(legacyFile(files, "/tmp/a.pdf", "pdfs"))
        assertNull(legacyFile(files, "pdfs/../a.pdf", "pdfs"))
        assertContentEquals(byteArrayOf(1), legacyFile(files, "/old/files/pdfs/a.pdf", "pdfs"))
    }
}

private class MemoryMigrationDao : MigrationDao() {
    val texts = mutableListOf<TextDocument>(); val words = mutableListOf<VocabEntry>()
    val folders = mutableListOf<LibraryFolder>(); val lists = mutableListOf<VocabList>()
    val highlights = mutableListOf<HighlightEntry>(); val reviews = mutableListOf<ReviewLogEntry>()
    private var next = 100L
    override suspend fun findText(lang: String, title: String, body: String) = texts.firstOrNull { it.lang == lang && it.title == title && it.body == body }?.id
    override suspend fun findWord(lang: String, word: String) = words.firstOrNull { it.lang == lang && it.word.lowercase() == word.lowercase() }?.id
    override suspend fun findFolder(lang: String, name: String, parent: Long?) = folders.firstOrNull { it.lang == lang && it.name == name && it.parentId == parent }?.id
    override suspend fun findList(lang: String, name: String) = lists.firstOrNull { it.lang == lang && it.name == name }?.id
    override suspend fun hasHighlight(text: Long, start: Int, end: Int, color: String) = highlights.count { it.textId == text && it.startOffset == start && it.endOffset == end && it.colorKey == color }
    override suspend fun hasReview(entry: Long, time: Long, knew: Boolean, before: Int, after: Int) = reviews.count { it.entryId == entry && it.timestampMs == time && it.knew == knew && it.boxBefore == before && it.boxAfter == after }
    override suspend fun insertText(value: TextDocument): Long { val id = value.id.takeIf { it != 0L } ?: next++; texts += value.copy(id = id); return id }
    override suspend fun insertWord(value: VocabEntry): Long { val id = value.id.takeIf { it != 0L } ?: next++; words += value.copy(id = id); return id }
    override suspend fun insertFolder(value: LibraryFolder): Long { val id = next++; folders += value.copy(id = id); return id }
    override suspend fun insertList(value: VocabList): Long { val id = next++; lists += value.copy(id = id); return id }
    override suspend fun insertHighlight(value: HighlightEntry): Long { val id = next++; highlights += value.copy(id = id); return id }
    override suspend fun insertReview(value: ReviewLogEntry): Long { val id = next++; reviews += value.copy(id = id); return id }
}
