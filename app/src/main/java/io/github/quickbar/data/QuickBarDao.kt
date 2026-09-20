package io.github.quickbar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE enabled = 1 ORDER BY sortOrder, id")
    fun observeEnabled(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SnippetEntity): Long

    @Update
    suspend fun update(item: SnippetEntity)

    @Delete
    suspend fun delete(item: SnippetEntity)
}

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE enabled = 1 ORDER BY sortOrder, id")
    fun observeEnabled(): Flow<List<ScriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScriptEntity): Long

    @Update
    suspend fun update(item: ScriptEntity)

    @Delete
    suspend fun delete(item: ScriptEntity)
}

@Dao
interface ScriptStepDao {
    @Query("SELECT * FROM script_steps WHERE scriptId = :scriptId ORDER BY sortOrder, id")
    fun observeForScript(scriptId: Long): Flow<List<ScriptStepEntity>>

    @Query("SELECT * FROM script_steps WHERE scriptId = :scriptId ORDER BY sortOrder, id")
    suspend fun getForScript(scriptId: Long): List<ScriptStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScriptStepEntity): Long

    @Update
    suspend fun update(item: ScriptStepEntity)

    @Delete
    suspend fun delete(item: ScriptStepEntity)
}

