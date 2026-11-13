package com.ziaee.zabankhan.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedVideoDao {
    @Query("SELECT * FROM saved_videos WHERE lang = :lang AND isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(lang: String): Flow<List<SavedVideoEntity>>
    @Query("SELECT * FROM saved_videos WHERE lang = :lang ORDER BY title COLLATE NOCASE") fun observeAll(lang: String): Flow<List<SavedVideoEntity>>
    @Query("SELECT * FROM saved_videos WHERE lang = :lang") suspend fun all(lang: String): List<SavedVideoEntity>
    @Query("SELECT * FROM saved_videos WHERE stableKey = :key LIMIT 1") suspend fun get(key: String): SavedVideoEntity?
    @Upsert suspend fun upsert(video: SavedVideoEntity)
}
