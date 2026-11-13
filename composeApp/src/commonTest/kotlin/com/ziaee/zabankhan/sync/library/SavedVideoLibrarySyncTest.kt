package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.SavedVideoRepository
import com.ziaee.zabankhan.data.savedVideoStableKey
import com.ziaee.zabankhan.data.rebaseSavedVideoPath
import com.ziaee.zabankhan.backup.rebaseBackupPaths
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.*
import kotlinx.coroutines.flow.first
import com.ziaee.zabankhan.data.VideoPlaylistRepository

internal suspend fun assertSavedVideoLibraryRoundTrip(buildTestDatabase: (String) -> AppDatabase) {
    val pathA = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "favorite-sync-a-${Random.nextLong()}.db").toString()
    val pathB = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "favorite-sync-b-${Random.nextLong()}.db").toString()
    val a = buildTestDatabase(pathA); val b = buildTestDatabase(pathB)
    try {
        val repository = SavedVideoRepository(a)
        assertFailsWith<IllegalArgumentException> { repository.toggleFavorite("fr", "bad-id", "", "", "", "", 1) }
        repository.setFavorite("fr", "abcdefghijk", "", "captions.srt", "Saved title", "Channel", true, 10)
        repository.setFavorite("fr", "abcdefghijk", "", "", "", "", true, 11)
        val key = savedVideoStableKey("fr", "abcdefghijk", "")
        assertEquals(key, savedVideoStableKey("fr", "abcdefghijk", ""))
        assertNotEquals(key, savedVideoStableKey("en", "abcdefghijk", ""))
        assertEquals(key, a.savedVideos().all("fr").single().stableKey)
        assertTrue(a.savedVideos().all("en").isEmpty())
        val favorite = LibrarySnapshotBuilder(a).build().getValue(key).second
        assertEquals(LibraryKinds.SAVED_VIDEO, favorite.kind)
        LibraryApplier(b).apply(listOf(favorite), emptyMap())
        assertTrue(b.savedVideos().all("fr").single().isFavorite)

        repository.toggleFavorite("fr", "abcdefghijk", "", "", "", "", 12)
        val unfavorite = LibrarySnapshotBuilder(a).build().getValue(key).second
        LibraryApplier(b).apply(listOf(unfavorite), emptyMap())
        assertEquals(1, b.savedVideos().all("fr").size)
        assertFalse(b.savedVideos().all("fr").single().isFavorite)
        assertEquals("Saved title", b.savedVideos().all("fr").single().title)
        assertEquals("captions.srt", b.savedVideos().all("fr").single().subtitlePath)
        assertTrue(b.savedVideos().all("en").isEmpty())

        val channel = com.ziaee.zabankhan.data.VideoChannelEntity("UCabcdefghijk", "fr", "Channel", "films")
        a.videoChannels().upsert(channel)
        val initialStamp = a.librarySync().observeLibraryStamp().first()
        a.videoChannels().upsert(channel.copy(category = "other"))
        val categoryStamp = a.librarySync().observeLibraryStamp().first()
        assertNotEquals(initialStamp, categoryStamp)
        a.savedVideos().upsert(a.savedVideos().all("fr").single().copy(title = "Other title"))
        val titleStamp = a.librarySync().observeLibraryStamp().first()
        assertNotEquals(categoryStamp, titleStamp)
        a.savedVideos().upsert(a.savedVideos().all("fr").single().copy(isFavorite = true))
        val favoriteStamp = a.librarySync().observeLibraryStamp().first()
        assertNotEquals(titleStamp, favoriteStamp)

        repository.toggleFavorite("fr", "lmnopqrstuv", "", "subs.srt", "Local title", "Channel", 20)
        repository.toggleFavorite("fr", "lmnopqrstuv", "", "", "", "", 21)
        val toggledTwice = a.savedVideos().all("fr").single { it.videoId == "lmnopqrstuv" }
        assertFalse(toggledTwice.isFavorite)
        assertEquals("Local title", toggledTwice.title)
        assertEquals("subs.srt", toggledTwice.subtitlePath)
    } finally {
        a.close(); b.close()
        FileSystem.SYSTEM.delete(pathA.toPath(), mustExist = false); FileSystem.SYSTEM.delete(pathB.toPath(), mustExist = false)
    }
}

internal suspend fun assertSavedVideoBackupRebase(buildTestDatabase: (String) -> AppDatabase) {
    val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "favorite-rebase-${Random.nextLong()}.db").toString()
    val previousRoot = "/old/container/files"
    val newRoot = "/new/container/files"
    val oldVideo = "$previousRoot/videos/abc123.mp4"
    val oldSubs = "$previousRoot/videos/captions.srt"
    val key = savedVideoStableKey("fr", "", oldVideo)
    try {
        val original = buildTestDatabase(path)
        try {
            SavedVideoRepository(original).setFavorite("fr", "", oldVideo, oldSubs, "Local", "Channel", true, 1)
        } finally { original.close() }
        BundledSQLiteDriver().open(path).use { rebaseBackupPaths(it, previousRoot, newRoot) }
        val restored = buildTestDatabase(path)
        try {
            val favorite = restored.savedVideos().all("fr").single()
            assertTrue(favorite.isFavorite)
            assertEquals(key, favorite.stableKey)
            assertEquals(savedVideoStableKey("fr", "", "$newRoot/videos/abc123.mp4"), favorite.stableKey)
            assertEquals("$newRoot/videos/abc123.mp4", favorite.path)
            assertEquals("$newRoot/videos/captions.srt", favorite.subtitlePath)
        } finally { restored.close() }
    } finally {
        FileSystem.SYSTEM.delete(path.toPath(), mustExist = false)
        FileSystem.SYSTEM.delete("$path-wal".toPath(), mustExist = false)
        FileSystem.SYSTEM.delete("$path-shm".toPath(), mustExist = false)
    }
}

internal suspend fun assertVideoPlaylistSync(buildTestDatabase: (String) -> AppDatabase) {
    val aPath = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "playlist-a-${Random.nextLong()}.db").toString()
    val bPath = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "playlist-b-${Random.nextLong()}.db").toString()
    val a = buildTestDatabase(aPath); val b = buildTestDatabase(bPath)
    try {
        val videos = SavedVideoRepository(a)
        videos.setFavorite("fr", "abcdefghijk", "", "subs.vtt", "Film", "Channel", true, 10)
        val video = a.savedVideos().all("fr").single()
        val lists = VideoPlaylistRepository(a)
        val first = lists.create("fr", "Study")
        val second = lists.create("fr", "Rewatch")
        lists.toggle(first.syncId, video.stableKey)
        lists.toggle(first.syncId, video.stableKey)
        lists.toggle(first.syncId, video.stableKey)
        lists.toggle(second.syncId, video.stableKey)
        assertEquals(listOf(video.stableKey), a.videoPlaylists().members(first.syncId))
        val initial = LibrarySnapshotBuilder(a).build()
        LibraryApplier(b).apply(initial.values.map { it.second }, emptyMap())
        assertEquals(2, b.videoPlaylists().all("fr").size)
        assertEquals(video.stableKey, b.videoPlaylists().members(first.syncId).single())
        assertEquals(video.stableKey, b.videoPlaylists().members(second.syncId).single())
        val stamp = a.librarySync().observeLibraryStamp().first()
        lists.rename(first.syncId, "Grammar")
        assertNotEquals(stamp, a.librarySync().observeLibraryStamp().first())
        val renamed = LibrarySnapshotBuilder(a).build()
        LibraryApplier(b).apply(renamed.values.map { it.second }, emptyMap())
        assertEquals("Grammar", b.videoPlaylists().get(first.syncId)?.name)
        lists.delete(first.syncId)
        val removed = LibrarySnapshotBuilder(a).build()
        val oldKeys = initial.keys - removed.keys
        val tombstones = oldKeys.map { key -> initial.getValue(key).second.copy(deleted = true, payload = null, updatedAtMs = 30) }
        LibraryApplier(b).apply(removed.values.map { it.second } + tombstones, emptyMap())
        assertNull(b.videoPlaylists().get(first.syncId))
        assertTrue(b.videoPlaylists().members(first.syncId).isEmpty())
        assertTrue(b.savedVideos().get(video.stableKey)!!.isFavorite)
        assertEquals(video.stableKey, b.videoPlaylists().members(second.syncId).single())
    } finally {
        a.close(); b.close()
        FileSystem.SYSTEM.delete(aPath.toPath(), mustExist = false); FileSystem.SYSTEM.delete(bPath.toPath(), mustExist = false)
    }
}

class SavedVideoIdentityTest {
    @Test fun onlineVideoIdentityIncludesLearningLanguage() {
        assertEquals(savedVideoStableKey("fr", "id", ""), savedVideoStableKey("fr", "id", ""))
        assertNotEquals(savedVideoStableKey("fr", "id", ""), savedVideoStableKey("en", "id", ""))
    }

    @Test fun localVideoIdentityUsesPath() {
        assertEquals(savedVideoStableKey("fr", "", "/videos/one.mp4"), savedVideoStableKey("fr", "", "/videos/one.mp4"))
        assertNotEquals(savedVideoStableKey("fr", "", "/videos/one.mp4"), savedVideoStableKey("fr", "", "/videos/two.mp4"))
    }

    @Test fun managedVideoIdentityIgnoresSandboxRoot() {
        assertEquals(savedVideoStableKey("fr", "", "/old/files/videos/hash.mp4"),
            savedVideoStableKey("fr", "", "/new/files/videos/hash.mp4"))
        assertNotEquals(savedVideoStableKey("fr", "", "/new/files/videos/hash.mp4"),
            savedVideoStableKey("fr", "", "/new/files/videos/other.mp4"))
        assertEquals(savedVideoStableKey("fr", "", "/old/files/videos/hash.mp4"),
            savedVideoStableKey("fr", "", rebaseSavedVideoPath("/old/files/videos/hash.mp4", "/new/files")))
        assertEquals(savedVideoStableKey("fr", "", "videos/hash.mp4"),
            savedVideoStableKey("fr", "", "/new/files/videos/hash.mp4"))
    }

    @Test fun rebasingLeavesEmptyAndForeignPathsAlone() {
        assertEquals("", rebaseSavedVideoPath("", "/new/files"))
        assertEquals("https://example.org/video.mp4", rebaseSavedVideoPath("https://example.org/video.mp4", "/new/files"))
        assertEquals("relative/video.mp4", rebaseSavedVideoPath("relative/video.mp4", "/new/files"))
        assertEquals("/foreign/video.mp4", rebaseSavedVideoPath("/foreign/video.mp4", "/new/files"))
    }

    @Test fun localPathWinsWhenBothSourceFieldsExist() {
        assertEquals(savedVideoStableKey("fr", "abcdefghijk", "/videos/one.mp4"), savedVideoStableKey("fr", "differentID", "/videos/one.mp4"))
        assertNotEquals(savedVideoStableKey("fr", "abcdefghijk", "/videos/one.mp4"), savedVideoStableKey("fr", "abcdefghijk", "/videos/two.mp4"))
    }

}
