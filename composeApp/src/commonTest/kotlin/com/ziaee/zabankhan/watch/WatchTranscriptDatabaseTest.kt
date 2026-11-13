package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.lang.Languages
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.*

internal suspend fun assertOptionalTranscriptSave(buildTestDatabase: (String) -> AppDatabase) {
    val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "watch-transcript-${Random.nextLong()}.db").toString()
    val database = buildTestDatabase(path)
    try {
        val dao = database.texts()
        val lang = Languages.ENGLISH
        val cues = listOf(SubtitleCue(0, 500, "Hello"), SubtitleCue(500, 1_000, "there."))
        val units = mergeSentenceUnits(cues, lang).units

        val opened = loadTranscriptDocument(dao, "video-1", lang, "Caption title", "https://youtube.com/watch?v=video-1", units)
        assertEquals(-1L, opened.id)
        assertTrue(dao.getAll(lang.code).isEmpty(), "Opening captions must not insert a library document")
        // A cancelled save dialog performs no persistence call either.
        assertTrue(dao.getAll(lang.code).isEmpty())

        val folderDao = database.libraryOrganizer()
        val saved = saveTranscriptToLibrary(database, "video-1", lang, "My title", opened.sourceUrl, units,
            folderId = null, newFolderName = "Clips", legacyCues = cues)
        val folderId = assertNotNull(saved.folderId)
        assertEquals("My title", saved.title)
        assertEquals(folderId, saved.folderId)
        assertEquals(1, folderDao.getFoldersOnce(lang.code).size)
        assertEquals(1, dao.getAll(lang.code).size)
        val reopened = loadTranscriptDocument(dao, "video-1", lang, "Caption title", opened.sourceUrl, units)
        assertEquals(saved.id, reopened.id)
        assertEquals("My title", reopened.title)
        assertEquals(folderId, reopened.folderId)

        dao.update(saved.copy(lastOffset = 6, readMark = 1))
        val repeated = saveTranscriptToLibrary(database, "video-1", lang, "Renamed", opened.sourceUrl, units, folderId = null, legacyCues = cues)
        assertEquals(saved.id, repeated.id, "Repeated save must update by external identity")
        assertEquals("Renamed", repeated.title)
        assertNull(repeated.folderId, "Explicit root selection must clear a prior folder")
        assertEquals(6, repeated.lastOffset)
        assertEquals(1, repeated.readMark)
        assertEquals(saved.body, repeated.body)
        assertEquals(1, dao.getAll(lang.code).size)

        val removedFolder = folderDao.insertFolder(com.ziaee.zabankhan.data.LibraryFolder(lang = lang.code, name = "Removed"))
        folderDao.deleteFolder(removedFolder)
        val afterFolderRemoval = saveTranscriptToLibrary(database, "video-1", lang, "Renamed", opened.sourceUrl, units, folderId = removedFolder, legacyCues = cues)
        assertNull(afterFolderRemoval.folderId, "A folder deleted while the dialog was open resolves to the root")
    } finally {
        database.close()
        FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
    }
}
