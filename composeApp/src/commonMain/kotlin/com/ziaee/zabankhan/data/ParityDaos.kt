package com.ziaee.zabankhan.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryOrganizerDao {
    @Query("SELECT * FROM library_folders WHERE lang = :lang ORDER BY name COLLATE NOCASE")
    fun observeFolders(lang: String): Flow<List<LibraryFolder>>

    @Query("SELECT * FROM library_folders WHERE lang = :lang")
    suspend fun getFoldersOnce(lang: String): List<LibraryFolder>

    @Query("SELECT * FROM library_tags WHERE lang = :lang ORDER BY name COLLATE NOCASE")
    fun observeTags(lang: String): Flow<List<LibraryTag>>

    @Query("SELECT * FROM text_tags WHERE textId IN (SELECT id FROM texts WHERE lang = :lang)")
    fun observeAllTagRefs(lang: String): Flow<List<TextTagCrossRef>>

    @Insert
    suspend fun insertFolder(folder: LibraryFolder): Long

    @Query("SELECT * FROM library_folders WHERE lang = :lang AND name = :name COLLATE NOCASE AND (parentId IS :parentId) LIMIT 1")
    suspend fun findFolderByName(lang: String, name: String, parentId: Long? = null): LibraryFolder?

    @Query("UPDATE library_folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE library_folders SET parentId = :parentId WHERE id = :id")
    suspend fun moveFolder(id: Long, parentId: Long?)

    @Query("SELECT parentId FROM library_folders WHERE id = :id")
    suspend fun getFolderParentId(id: Long): Long?

    @Query("UPDATE library_folders SET parentId = :parentId WHERE parentId = :id")
    suspend fun moveChildFoldersUp(id: Long, parentId: Long?)

    @Query("UPDATE texts SET folderId = :parentId WHERE folderId = :id")
    suspend fun moveTextsUp(id: Long, parentId: Long?)

    @Query("DELETE FROM library_folders WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)

    @Query("DELETE FROM library_folders WHERE id IN (:ids)")
    suspend fun deleteFolderRows(ids: List<Long>)

    @Transaction
    suspend fun deleteFolder(id: Long) {
        val parentId = getFolderParentId(id)
        moveChildFoldersUp(id, parentId)
        moveTextsUp(id, parentId)
        deleteFolderRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: LibraryTag): Long

    @Query("SELECT * FROM library_tags WHERE lang = :lang AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTagByName(lang: String, name: String): LibraryTag?

    @Query("UPDATE library_tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    @Query("DELETE FROM text_tags WHERE tagId = :id")
    suspend fun deleteRefsForTag(id: Long)

    @Query("DELETE FROM library_tags WHERE id = :id")
    suspend fun deleteTagRow(id: Long)

    @Transaction
    suspend fun deleteTag(id: Long) {
        deleteRefsForTag(id)
        deleteTagRow(id)
    }

    @Query("DELETE FROM text_tags WHERE textId = :textId")
    suspend fun deleteRefsForText(textId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<TextTagCrossRef>)

    @Transaction
    suspend fun addTagsToTexts(textIds: List<Long>, tagIds: List<Long>) {
        insertTagRefs(textIds.distinct().flatMap { textId ->
            tagIds.distinct().map { tagId -> TextTagCrossRef(textId, tagId) }
        })
    }

    @Transaction
    suspend fun setTextTags(textId: Long, tagIds: List<Long>) {
        deleteRefsForText(textId)
        insertTagRefs(tagIds.distinct().map { TextTagCrossRef(textId, it) })
    }
}

@Dao
interface HeadlineDao {
    @Query("SELECT * FROM headlines WHERE lang = :lang ORDER BY publishedAtMs DESC LIMIT :limit")
    fun observeRecent(lang: String, limit: Int): Flow<List<HeadlineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HeadlineEntity>)

    @Query("SELECT * FROM headlines WHERE lang = :lang ORDER BY publishedAtMs DESC LIMIT :limit")
    suspend fun getRecent(lang: String, limit: Int): List<HeadlineEntity>

    @Query("DELETE FROM headlines WHERE lang = :lang AND sourceId = :sourceId")
    suspend fun clearSource(lang: String, sourceId: String)

    @Transaction
    suspend fun replaceSource(lang: String, sourceId: String, items: List<HeadlineEntity>) {
        clearSource(lang, sourceId)
        insertAll(items)
    }
}

@Dao
interface ResourceDao {
    @Query("SELECT * FROM resources WHERE lang = :lang ORDER BY createdAtMs DESC, id DESC")
    fun observeAll(lang: String): Flow<List<ResourceLink>>

    @Query("SELECT COUNT(*) FROM resources WHERE lang = :lang")
    suspend fun count(lang: String): Int

    @Query("SELECT * FROM resources WHERE lang = :lang ORDER BY createdAtMs DESC, id DESC")
    suspend fun getAllOnce(lang: String): List<ResourceLink>

    @Query("SELECT * FROM resources WHERE lang = :lang AND imageUrl IS NULL")
    suspend fun getWithoutImages(lang: String): List<ResourceLink>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(resource: ResourceLink): Long

    /** For seeding built-ins: a URL the user already has is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(resources: List<ResourceLink>)

    @Update
    suspend fun update(resource: ResourceLink)

    @Delete
    suspend fun delete(resource: ResourceLink)

    @Query("DELETE FROM resources WHERE lang = :lang AND url IN (:urls)")
    suspend fun deleteByUrls(lang: String, urls: Collection<String>)
}

@Dao
interface ExamAttemptDao {
    @Query("SELECT * FROM exam_attempts")
    fun observeProgressExams(): Flow<List<ExamAttempt>>

    @Insert suspend fun insert(attempt: ExamAttempt): Long
    @Update suspend fun update(attempt: ExamAttempt)
    @Query("DELETE FROM exam_attempts WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM exam_attempts WHERE lang = :lang AND examId = :examId ORDER BY createdAtMs, id")
    fun observeForExam(lang: String, examId: String): Flow<List<ExamAttempt>>
    @Query("SELECT * FROM exam_attempts WHERE lang = :lang AND examId = :examId AND questionId = :questionId ORDER BY createdAtMs, id")
    fun observeForQuestion(lang: String, examId: String, questionId: String): Flow<List<ExamAttempt>>
    @Query("SELECT COUNT(DISTINCT questionId) FROM exam_attempts WHERE lang = :lang AND examId = :examId")
    fun countPractisedQuestions(lang: String, examId: String): Flow<Int>
}
