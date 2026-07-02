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

    @Query("SELECT * FROM cloned_apps ORDER BY last_launch DESC")
    fun getAllApps(): Flow<List<ClonedAppEntity>>

    @Query("SELECT * FROM cloned_apps WHERE id = :id")
    suspend fun getAppById(id: Long): ClonedAppEntity?

    @Query("SELECT * FROM cloned_apps WHERE clone_package = :packageName")
    suspend fun getAppsByPackage(packageName: String): List<ClonedAppEntity>

    @Query("SELECT * FROM cloned_apps WHERE original_package = :originalPackage")
    suspend fun getClonesOf(originalPackage: String): List<ClonedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: ClonedAppEntity): Long

    @Update
    suspend fun update(app: ClonedAppEntity)

    @Delete
    suspend fun delete(app: ClonedAppEntity)

    @Query("DELETE FROM cloned_apps WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE cloned_apps SET is_running = :running WHERE id = :id")
    suspend fun updateRunningStatus(id: Long, running: Boolean)

    @Query("UPDATE cloned_apps SET last_launch = :timestamp WHERE id = :id")
    suspend fun updateLastLaunch(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE cloned_apps SET cache_size = :size WHERE id = :id")
    suspend fun updateCacheSize(id: Long, size: Long)

    @Query("SELECT COUNT(*) FROM cloned_apps WHERE original_package = :originalPackage")
    suspend fun getCloneCount(originalPackage: String): Int

    @Query("SELECT * FROM cloned_apps WHERE is_running = 1")
    suspend fun getRunningApps(): List<ClonedAppEntity>

    @Query("UPDATE cloned_apps SET fake_icon_path = :path WHERE id = :id")
    suspend fun updateFakeIconPath(id: Long, path: String)

    @Query("UPDATE cloned_apps SET device_info_reset_count = device_info_reset_count + 1 WHERE id = :id")
    suspend fun incrementDeviceInfoReset(id: Long)

    @Query("UPDATE cloned_apps SET gsf_reset_count = gsf_reset_count + 1 WHERE id = :id")
    suspend fun incrementGsfReset(id: Long)

    @Query("UPDATE cloned_apps SET gms_installed = :installed, gms_install_date = :date WHERE id = :id")
    suspend fun updateGmsStatus(id: Long, installed: Boolean, date: Long = System.currentTimeMillis())
}
