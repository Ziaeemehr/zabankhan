package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.LibrarySyncShadow
import com.ziaee.zabankhan.data.VideoChannelEntity
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.*

internal suspend fun assertVideoChannelLibraryRoundTrip(buildTestDatabase: (String) -> AppDatabase) {
    val pathA = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "channel-sync-a-${Random.nextLong()}.db").toString()
    val pathB = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "channel-sync-b-${Random.nextLong()}.db").toString()
    val a = buildTestDatabase(pathA); val b = buildTestDatabase(pathB)
    try {
        val saved = VideoChannelEntity("UCKqmnQFF2PgokPDdX-L2fwQ", "fr", "StreamTVFilms", "films", syncId = "stable-channel-id")
        a.videoChannels().upsert(saved)
        val created = LibrarySnapshotBuilder(a).build().getValue("videoChannel:stable-channel-id").second
        assertEquals(LibraryKinds.VIDEO_CHANNEL, created.kind)
        assertEquals("films", created.payload?.get("category")?.toString()?.trim('"'))
        LibraryApplier(b).apply(listOf(created), emptyMap())
        assertEquals(saved, b.videoChannels().get(saved.channelId))

        a.videoChannels().delete(saved.channelId)
        val shadow = LibrarySyncShadow("videoChannel:stable-channel-id", "old-hash", 1, false)
        a.librarySync().upsertAll(listOf(shadow))
        val deleted = detectLocalChanges(LibrarySnapshotBuilder(a).build(), mapOf(shadow.key to shadow), 10, false, false, emptyMap(), emptySet())
            .single { it.key == shadow.key }
        assertTrue(deleted.deleted)
        LibraryApplier(b).apply(listOf(deleted), emptyMap())
        assertNull(b.videoChannels().get(saved.channelId))
    } finally {
        a.close(); b.close()
        FileSystem.SYSTEM.delete(pathA.toPath(), mustExist = false); FileSystem.SYSTEM.delete(pathB.toPath(), mustExist = false)
    }
}
