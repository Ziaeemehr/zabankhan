package com.ziaee.zabankhan.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.Update
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface LibrarySyncDao {
    @Query("SELECT * FROM texts") suspend fun texts(): List<TextDocument>
    @Query("SELECT * FROM library_folders") suspend fun folders(): List<LibraryFolder>
    @Query("SELECT * FROM library_tags") suspend fun tags(): List<LibraryTag>
    @Query("SELECT * FROM text_tags") suspend fun textTags(): List<TextTagCrossRef>
    @Query("SELECT * FROM pdf_ink") suspend fun ink(): List<PdfInk>
    @Query("SELECT * FROM pdf_text_notes") suspend fun notes(): List<PdfTextNote>
    @Query("SELECT * FROM pdf_highlights") suspend fun pdfHighlights(): List<PdfHighlight>
    @Query("SELECT * FROM highlights") suspend fun highlights(): List<HighlightEntry>
    @Query("SELECT * FROM video_channels") suspend fun videoChannels(): List<VideoChannelEntity>
    @Query("SELECT * FROM saved_videos") suspend fun savedVideos(): List<SavedVideoEntity>
    @Query("SELECT * FROM saved_videos WHERE stableKey = :key LIMIT 1") suspend fun savedVideoByKey(key: String): SavedVideoEntity?
    @Upsert suspend fun upsertSavedVideo(row: SavedVideoEntity)
    @Query("DELETE FROM saved_videos WHERE stableKey = :key") suspend fun deleteSavedVideo(key: String)
    @Query("SELECT * FROM video_playlists") suspend fun videoPlaylists(): List<VideoPlaylistEntity>
    @Query("SELECT * FROM video_playlists WHERE syncId = :syncId LIMIT 1") suspend fun videoPlaylistBySyncId(syncId: String): VideoPlaylistEntity?
    @Upsert suspend fun upsertVideoPlaylist(row: VideoPlaylistEntity)
    @Query("DELETE FROM video_playlists WHERE syncId = :syncId") suspend fun deleteVideoPlaylist(syncId: String)
    @Query("SELECT * FROM video_playlist_memberships") suspend fun videoPlaylistMemberships(): List<VideoPlaylistMembershipEntity>
    @Upsert suspend fun upsertVideoPlaylistMembership(row: VideoPlaylistMembershipEntity)
    @Query("DELETE FROM video_playlist_memberships WHERE playlistSyncId = :playlistSyncId AND videoKey = :videoKey") suspend fun deleteVideoPlaylistMembership(playlistSyncId: String, videoKey: String)
    @Query("DELETE FROM video_playlist_memberships WHERE playlistSyncId = :playlistSyncId") suspend fun clearVideoPlaylistMemberships(playlistSyncId: String)
    @Query("DELETE FROM video_playlist_memberships WHERE videoKey = :videoKey") suspend fun deleteMembershipsForVideo(videoKey: String)
    @Query("SELECT * FROM video_channels WHERE syncId = :syncId LIMIT 1") suspend fun videoChannelBySyncId(syncId: String): VideoChannelEntity?
    @Upsert suspend fun upsertVideoChannel(row: VideoChannelEntity)
    @Query("DELETE FROM video_channels WHERE syncId = :syncId") suspend fun deleteVideoChannel(syncId: String)
    @Query("SELECT * FROM library_sync_shadow") suspend fun getAll(): List<LibrarySyncShadow>
    @Upsert suspend fun upsertAll(rows: List<LibrarySyncShadow>)
    @Query("DELETE FROM library_sync_shadow") suspend fun deleteAll()
    @Query("SELECT COUNT(*) || ':' || IFNULL(MAX(lastAccessedAtMs),0) FROM texts") fun observeTextsStamp(): Flow<String>
    // Cheap change detector across synced tables; deliberately ignores reading position and lastAccessedAtMs (synced on foreground/background instead).
    @Query("""SELECT (SELECT COUNT(*) || ':' || IFNULL(SUM(readMark + pinned + IFNULL(folderId,0) + length(title)),0) FROM texts) || '|' ||
        (SELECT COUNT(*) || ':' || IFNULL(SUM(length(name) + IFNULL(parentId,0)),0) FROM library_folders) || '|' ||
        (SELECT COUNT(*) FROM library_tags) || '|' || (SELECT COUNT(*) FROM text_tags) || '|' ||
        (SELECT COUNT(*) || ':' || IFNULL(SUM(length(strokes)),0) FROM pdf_ink) || '|' ||
        (SELECT COUNT(*) || ':' || IFNULL(SUM(length(body)),0) FROM pdf_text_notes) || '|' ||
        (SELECT COUNT(*) FROM pdf_highlights) || '|' || (SELECT COUNT(*) FROM highlights) || '|' ||
        IFNULL((SELECT GROUP_CONCAT(rowStamp, ',') FROM (SELECT hex(channelId) || ':' || hex(lang) || ':' || hex(title) || ':' || hex(category) || ':' || IFNULL(hex(level),'!') || ':' || hex(syncId) AS rowStamp FROM video_channels ORDER BY channelId)), '') || '|' ||
        IFNULL((SELECT GROUP_CONCAT(rowStamp, ',') FROM (SELECT hex(stableKey) || ':' || hex(lang) || ':' || hex(videoId) || ':' || hex(path) || ':' || hex(subtitlePath) || ':' || hex(title) || ':' || hex(channel) || ':' || isFavorite || ':' || updatedAtMs AS rowStamp FROM saved_videos ORDER BY stableKey)), '') || '|' ||
        IFNULL((SELECT GROUP_CONCAT(rowStamp, ',') FROM (SELECT hex(syncId) || ':' || hex(lang) || ':' || hex(name) || ':' || createdAtMs || ':' || updatedAtMs AS rowStamp FROM video_playlists ORDER BY syncId)), '') || '|' ||
        IFNULL((SELECT GROUP_CONCAT(rowStamp, ',') FROM (SELECT hex(playlistSyncId) || ':' || hex(videoKey) || ':' || addedAtMs AS rowStamp FROM video_playlist_memberships ORDER BY playlistSyncId, videoKey)), '')""") fun observeLibraryStamp(): Flow<String>
    @Query("SELECT * FROM texts WHERE syncId = :syncId LIMIT 1") suspend fun textBySyncId(syncId: String): TextDocument?
    @Query("SELECT * FROM library_folders WHERE syncId = :syncId LIMIT 1") suspend fun folderBySyncId(syncId: String): LibraryFolder?
    @Query("SELECT * FROM library_tags WHERE lang = :lang AND name = :name LIMIT 1") suspend fun tagByName(lang: String, name: String): LibraryTag?
    @Query("UPDATE texts SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''") suspend fun backfillTextSyncIds()
    @Query("UPDATE library_folders SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''") suspend fun backfillFolderSyncIds()
    @Query("UPDATE texts SET syncId = :new WHERE syncId = :old") suspend fun renameTextSyncId(old: String, new: String)
    @Query("UPDATE library_folders SET syncId = :new WHERE syncId = :old") suspend fun renameFolderSyncId(old: String, new: String)
    @Insert suspend fun insertText(row: TextDocument): Long
    @Update suspend fun updateText(row: TextDocument)
    @Query("DELETE FROM texts WHERE syncId = :syncId") suspend fun deleteTextBySyncId(syncId: String)
    @Insert suspend fun insertFolder(row: LibraryFolder): Long
    @Update suspend fun updateFolder(row: LibraryFolder)
    @Query("UPDATE texts SET folderId = NULL WHERE folderId = :id") suspend fun clearFolderTexts(id: Long)
    @Query("UPDATE library_folders SET parentId = NULL WHERE parentId = :id") suspend fun clearFolderChildren(id: Long)
    @Query("DELETE FROM library_folders WHERE id = :id") suspend fun deleteFolder(id: Long)
    @Query("UPDATE texts SET lastOffset = :offset, lastChunkIndex = :chunk, lastPositionMs = :position, lastAccessedAtMs = :accessed WHERE syncId = :syncId") suspend fun updatePosition(syncId: String, offset: Int, chunk: Int, position: Long, accessed: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(row: LibraryTag): Long
    @Query("DELETE FROM text_tags WHERE tagId = :id") suspend fun deleteTagRefs(id: Long)
    @Query("DELETE FROM library_tags WHERE id = :id") suspend fun deleteTag(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTextTag(row: TextTagCrossRef)
    @Query("DELETE FROM text_tags WHERE textId = :textId AND tagId = :tagId") suspend fun deleteTextTag(textId: Long, tagId: Long)
    @Query("SELECT * FROM pdf_ink WHERE textId = :textId AND page = :page LIMIT 1") suspend fun inkRow(textId: Long, page: Int): PdfInk?
    @Insert suspend fun insertInk(row: PdfInk)
    @Update suspend fun updateInk(row: PdfInk)
    @Query("DELETE FROM pdf_ink WHERE textId = :textId AND page = :page") suspend fun deleteInk(textId: Long, page: Int)
    @Query("SELECT * FROM pdf_text_notes WHERE textId = :textId AND page = :page AND startWord = :startWord AND endWord = :endWord LIMIT 1") suspend fun noteRow(textId: Long, page: Int, startWord: Int, endWord: Int): PdfTextNote?
    @Insert suspend fun insertNote(row: PdfTextNote)
    @Update suspend fun updateNote(row: PdfTextNote)
    @Query("DELETE FROM pdf_text_notes WHERE textId = :textId AND page = :page AND startWord = :startWord AND endWord = :endWord") suspend fun deleteNote(textId: Long, page: Int, startWord: Int, endWord: Int)
    @Query("SELECT * FROM pdf_highlights WHERE textId = :textId AND page = :page AND startWord = :startWord AND endWord = :endWord LIMIT 1") suspend fun pdfHighlightRow(textId: Long, page: Int, startWord: Int, endWord: Int): PdfHighlight?
    @Insert suspend fun insertPdfHighlight(row: PdfHighlight)
    @Update suspend fun updatePdfHighlight(row: PdfHighlight)
    @Query("DELETE FROM pdf_highlights WHERE textId = :textId AND page = :page AND startWord = :startWord AND endWord = :endWord") suspend fun deletePdfHighlight(textId: Long, page: Int, startWord: Int, endWord: Int)
    @Query("SELECT * FROM highlights WHERE textId = :textId AND startOffset = :start AND endOffset = :end LIMIT 1") suspend fun highlightRow(textId: Long, start: Int, end: Int): HighlightEntry?
    @Insert suspend fun insertHighlight(row: HighlightEntry)
    @Update suspend fun updateHighlight(row: HighlightEntry)
    @Query("DELETE FROM highlights WHERE textId = :textId AND startOffset = :start AND endOffset = :end") suspend fun deleteHighlight(textId: Long, start: Int, end: Int)

}

