package com.utility.toolbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.utility.toolbox.data.local.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspaces ORDER BY created_at DESC")
    fun getAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getWorkspaceById(id: Long): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveWorkspace(): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE is_active = 1 LIMIT 1")
    fun getActiveWorkspaceFlow(): Flow<WorkspaceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity): Long

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Delete
    suspend fun delete(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE workspaces SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE workspaces SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun getCount(): Int

    @Query("UPDATE workspaces SET updated_at = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long = System.currentTimeMillis())
}
