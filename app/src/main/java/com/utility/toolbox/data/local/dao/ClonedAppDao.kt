package com.utility.toolbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.utility.toolbox.data.local.entity.ClonedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClonedAppDao {

    @Query("SELECT * FROM cloned_apps WHERE workspace_id = :workspaceId ORDER BY last_launch DESC")
    fun getAppsByWorkspace(workspaceId: Long): Flow<List<ClonedAppEntity>>

    @Query("SELECT * FROM cloned_apps ORDER BY last_launch DESC")
    fun getAllApps(): Flow<List<ClonedAppEntity>>

    @Query("SELECT * FROM cloned_apps WHERE id = :id")
    suspend fun getAppById(id: Long): ClonedAppEntity?

    @Query("SELECT * FROM cloned_apps WHERE clone_package = :packageName LIMIT 1")
    suspend fun getAppByClonePackage(packageName: String): ClonedAppEntity?

    @Query("SELECT * FROM cloned_apps WHERE original_package = :packageName LIMIT 1")
    suspend fun getAppByOriginalPackage(packageName: String): ClonedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: ClonedAppEntity): Long

    @Update
    suspend fun update(app: ClonedAppEntity)

    @Delete
    suspend fun delete(app: ClonedAppEntity)

    @Query("DELETE FROM cloned_apps WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cloned_apps WHERE workspace_id = :workspaceId")
    suspend fun deleteByWorkspace(workspaceId: Long)

    @Query("UPDATE cloned_apps SET is_installed = :installed WHERE id = :id")
    suspend fun updateInstallStatus(id: Long, installed: Boolean)

    @Query("UPDATE cloned_apps SET is_running = :running WHERE id = :id")
    suspend fun updateRunningStatus(id: Long, running: Boolean)

    @Query("UPDATE cloned_apps SET last_launch = :timestamp WHERE id = :id")
    suspend fun updateLastLaunch(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cloned_apps SET cache_size = :size WHERE id = :id")
    suspend fun updateCacheSize(id: Long, size: Long)

    @Query("SELECT COUNT(*) FROM cloned_apps WHERE workspace_id = :workspaceId")
    suspend fun getAppCount(workspaceId: Long): Int

    @Query("SELECT SUM(app_size) FROM cloned_apps WHERE workspace_id = :workspaceId")
    suspend fun getTotalAppSize(workspaceId: Long): Long?

    @Query("SELECT COUNT(*) FROM cloned_apps WHERE original_package = :originalPackage AND workspace_id = :workspaceId")
    suspend fun getCloneCount(originalPackage: String, workspaceId: Long): Int

    @Query("SELECT * FROM cloned_apps WHERE is_running = 1")
    suspend fun getRunningApps(): List<ClonedAppEntity>

    @Query("UPDATE cloned_apps SET fake_icon_path = :path WHERE id = :id")
    suspend fun updateFakeIconPath(id: Long, path: String)

    @Query("UPDATE cloned_apps SET device_info_reset_count = device_info_reset_count + 1 WHERE id = :id")
    suspend fun incrementDeviceInfoReset(id: Long)

    @Query("UPDATE cloned_apps SET gsf_reset_count = gsf_reset_count + 1 WHERE id = :id")
    suspend fun incrementGsfReset(id: Long)
}
