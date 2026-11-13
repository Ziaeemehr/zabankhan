package com.ziaee.zabankhan.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoPlaylistDao {
    @Query("SELECT * FROM video_playlists WHERE lang = :lang ORDER BY name COLLATE NOCASE") fun observe(lang: String): Flow<List<VideoPlaylistEntity>>
    @Query("SELECT * FROM video_playlists WHERE lang = :lang ORDER BY name COLLATE NOCASE") suspend fun all(lang: String): List<VideoPlaylistEntity>
    @Query("SELECT * FROM video_playlists WHERE syncId = :id LIMIT 1") suspend fun get(id: String): VideoPlaylistEntity?
    @Upsert suspend fun upsert(playlist: VideoPlaylistEntity)
    @Query("DELETE FROM video_playlists WHERE syncId = :id") suspend fun delete(id: String)
    @Query("SELECT videoKey FROM video_playlist_memberships WHERE playlistSyncId = :id ORDER BY addedAtMs") suspend fun members(id: String): List<String>
    @Query("SELECT * FROM video_playlist_memberships WHERE playlistSyncId = :id AND videoKey = :key LIMIT 1") suspend fun membership(id: String, key: String): VideoPlaylistMembershipEntity?
    @Upsert suspend fun add(membership: VideoPlaylistMembershipEntity)
    @Query("DELETE FROM video_playlist_memberships WHERE playlistSyncId = :id AND videoKey = :key") suspend fun remove(id: String, key: String)
    @Query("DELETE FROM video_playlist_memberships WHERE playlistSyncId = :id") suspend fun clear(id: String)
    @Query("SELECT * FROM video_playlist_memberships") suspend fun allMemberships(): List<VideoPlaylistMembershipEntity>
    @Query("SELECT * FROM video_playlist_memberships WHERE playlistSyncId = :id") fun observeMemberships(id: String): Flow<List<VideoPlaylistMembershipEntity>>
}
