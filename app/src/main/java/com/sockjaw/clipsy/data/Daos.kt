package com.sockjaw.clipsy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets")
    fun observeAll(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE id = :id")
    fun observe(id: Long): Flow<Snippet?>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun get(id: Long): Snippet?

    @Query("SELECT COUNT(*) FROM snippets WHERE keyword = :keyword COLLATE NOCASE AND id != :exceptId")
    suspend fun countKeyword(keyword: String, exceptId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snippet: Snippet): Long

    @Update
    suspend fun update(snippet: Snippet)

    @Delete
    suspend fun delete(snippet: Snippet)

    @Query("UPDATE snippets SET useCount = useCount + 1, lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)

    @Query("UPDATE snippets SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE snippets SET counter = counter + 1 WHERE id = :id")
    suspend fun bumpCounter(id: Long)

    @Query("UPDATE snippets SET counter = 0 WHERE id = :id")
    suspend fun resetCounter(id: Long)

    @Query("SELECT * FROM snippets")
    suspend fun all(): List<Snippet>

    @Query("UPDATE snippets SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE snippets SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveTo(ids: List<Long>, folderId: Long?)

    @Query("UPDATE snippets SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)

    @Query("SELECT * FROM snippets WHERE folderId = :folderId")
    suspend fun inFolder(folderId: Long): List<Snippet>
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): Folder?

    @Query("SELECT * FROM folders WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): Folder?

    @Query("SELECT COUNT(*) FROM folders WHERE name = :name COLLATE NOCASE AND id != :exceptId")
    suspend fun countName(name: String, exceptId: Long): Int

    @Insert
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_events WHERE timestamp >= :since")
    fun observeSince(since: Long): Flow<List<UsageEvent>>

    @Query("SELECT * FROM usage_events WHERE snippetId = :snippetId AND timestamp >= :since")
    fun observeForSnippet(snippetId: Long, since: Long): Flow<List<UsageEvent>>

    @Insert
    suspend fun insert(event: UsageEvent)

    @Query("DELETE FROM usage_events WHERE snippetId = :snippetId")
    suspend fun deleteForSnippet(snippetId: Long)
}
