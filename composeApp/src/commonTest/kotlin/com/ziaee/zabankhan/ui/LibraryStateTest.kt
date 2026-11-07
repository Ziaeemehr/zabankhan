package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.watch.ChannelVideo
import com.ziaee.zabankhan.watch.VideoCatalog
import com.ziaee.zabankhan.watch.searchVideoChannels
import com.ziaee.zabankhan.watch.videoLevelOverlaps
import kotlin.test.*

class LibraryStateTest {
    private val fr = Languages.FRENCH
    private fun doc(id: Long, title: String, body: String = "", created: Long = 0, read: Long = 0, folder: Long? = null, level: String? = null) =
        TextDocument(id = id, lang = fr.code, title = title, body = body, createdAtMs = created, lastAccessedAtMs = read, folderId = folder, level = level)

    @Test fun sortsByNewestTitleAndLastRead() {
        val docs = listOf(doc(1, "Été", created = 1, read = 30), doc(2, "abeille", created = 3, read = 10), doc(3, "Zèbre", created = 2, read = 20))
        assertEquals(listOf(2L, 3L, 1L), projectLibrary(docs, "", LibrarySort.NEWEST, -1, LevelFilter.All, fr).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), projectLibrary(docs, "", LibrarySort.TITLE, -1, LevelFilter.All, fr).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), projectLibrary(docs, "", LibrarySort.LAST_READ, -1, LevelFilter.All, fr).map { it.id })
    }

    @Test fun filtersFolderLevelAndBody() {
        val docs = listOf(doc(1, "A", "le café", folder = 5, level = "B1"), doc(2, "B", "rien"), doc(3, "Café", level = "A2"))
        assertEquals(listOf(1L, 3L), projectLibrary(docs, "cafe", LibrarySort.TITLE, -1, LevelFilter.All, fr).map { it.id })
        assertEquals(listOf(2L, 3L), projectLibrary(docs, "", LibrarySort.TITLE, 0, LevelFilter.All, fr).map { it.id })
        assertEquals(listOf(1L), projectLibrary(docs, "", LibrarySort.TITLE, -1, LevelFilter.Level("B1"), fr).map { it.id })
    }

    @Test fun snippetSurroundsAccentInsensitiveMatch() {
        val body = "Première phrase très longue pour remplir le début du texte. Ensuite vient le café au lait, puis la suite du récit qui continue encore longtemps après."
        val snippet = assertNotNull(bodySnippet(body, "cafe", fr, radius = 20))
        assertTrue("café" in snippet, snippet)
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"), snippet)
        assertNull(bodySnippet(body, "absent", fr))
        assertNull(bodySnippet(body, "  ", fr))
    }

    @Test fun selectionTogglesAndSelectsAllVisible() {
        assertEquals(setOf(1L, 2L), toggleSelection(setOf(1L), 2L))
        assertEquals(emptySet(), toggleSelection(setOf(1L), 1L))
        assertEquals(setOf(1L, 2L, 3L), selectAllVisible(setOf(1L), setOf(2L, 3L)))
        assertEquals(setOf(9L), selectAllVisible(setOf(2L, 3L, 9L), setOf(2L, 3L)))
    }

    @Test fun readingStatusFollowsMarkAndProgress() {
        val body = "x".repeat(100)
        assertEquals(ReadingStatus.NOT_STARTED, readingStatus(doc(1, "t", body)))
        assertEquals(ReadingStatus.IN_PROGRESS, readingStatus(doc(1, "t", body, read = 5).copy(lastOffset = 40)))
        assertEquals(ReadingStatus.READ, readingStatus(doc(1, "t", body, read = 5).copy(lastOffset = 99)))
        assertEquals(ReadingStatus.READ, readingStatus(doc(1, "t", body).copy(readMark = READ_MARK_FINISHED)))
        assertEquals(ReadingStatus.IN_PROGRESS, readingStatus(doc(1, "t", body, read = 5).copy(lastOffset = 99, readMark = READ_MARK_NOT_FINISHED)))
    }

    @Test fun homeMetricsEstimateRemainingMinutes() {
        val body = List(400) { "mot" }.joinToString(" ")
        val metrics = homeReadingMetrics(doc(1, "t", body).copy(lastOffset = body.length * 3 / 4))
        assertEquals(2, metrics.estimatedTotalMinutes)
        assertEquals(1, metrics.estimatedRemainingMinutes)
        assertEquals(74, metrics.progressPercent)
        assertEquals("1:05", videoResumeTime(65_000))
    }

    @Test fun videoLevelsAndChannelSearchWorkPerLanguage() {
        assertTrue(videoLevelOverlaps("A2–B2", "B1"))
        assertFalse(videoLevelOverlaps(null, "B1"))
        assertTrue(videoLevelOverlaps("C1", "C1+"))
        val en = Languages.ENGLISH
        val channels = VideoCatalog.channels(en)
        assertEquals(listOf("VOA Learning English"), searchVideoChannels(channels, "voa", null, emptyMap(), en).map { it.title })
        val feed = mapOf(channels.last().channelId to listOf(ChannelVideo("abcdefghijk", "Linking sounds", "", "")))
        assertEquals(channels.last(), searchVideoChannels(channels, "linking", null, feed, en).single())
        assertTrue(searchVideoChannels(VideoCatalog.channels(fr), "", "A1–A2", emptyMap(), fr).all { videoLevelOverlaps(it.level, "A1–A2") })
    }
}
