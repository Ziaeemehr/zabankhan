package com.ziaee.zabankhan.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

class VideoPlaylistRepository(private val db: AppDatabase) {
    private val dao get() = db.videoPlaylists()
    fun observe(lang: String): Flow<List<VideoPlaylistEntity>> = dao.observe(lang)
    fun memberships(id: String): Flow<List<VideoPlaylistMembershipEntity>> = dao.observeMemberships(id)
    @OptIn(ExperimentalUuidApi::class)
    suspend fun create(lang: String, name: String): VideoPlaylistEntity {
        require(name.isNotBlank())
        val now = Clock.System.now().toEpochMilliseconds()
        return VideoPlaylistEntity(Uuid.random().toString().replace("-", ""), lang, name.trim(), now, now).also { dao.upsert(it) }
    }
    suspend fun rename(id: String, name: String) {
        require(name.isNotBlank())
        dao.get(id)?.let { dao.upsert(it.copy(name = name.trim(), updatedAtMs = Clock.System.now().toEpochMilliseconds())) }
    }
    suspend fun delete(id: String) = db.useWriterConnection { c -> c.immediateTransaction { dao.clear(id); dao.delete(id) } }
    suspend fun toggle(id: String, videoKey: String) = db.useWriterConnection { c -> c.immediateTransaction {
        if (dao.membership(id, videoKey) == null) {
            if (db.savedVideos().get(videoKey) == null) return@immediateTransaction
            dao.add(VideoPlaylistMembershipEntity(id, videoKey))
        } else dao.remove(id, videoKey)
    } }
}
