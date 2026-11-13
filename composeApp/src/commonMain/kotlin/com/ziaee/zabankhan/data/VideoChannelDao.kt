package com.ziaee.zabankhan.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoChannelDao {
    @Query("SELECT * FROM video_channels WHERE lang = :lang ORDER BY title COLLATE NOCASE")
    fun observe(lang: String): Flow<List<VideoChannelEntity>>
    @Query("SELECT * FROM video_channels") suspend fun all(): List<VideoChannelEntity>
    @Query("SELECT * FROM video_channels WHERE channelId = :id LIMIT 1") suspend fun get(id: String): VideoChannelEntity?
    @Upsert suspend fun upsert(channel: VideoChannelEntity)
    @Query("DELETE FROM video_channels WHERE channelId = :id") suspend fun delete(id: String)
}
