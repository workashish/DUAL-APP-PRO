package com.utility.toolbox.data.repository

import android.content.Context
import android.os.Environment
import com.utility.toolbox.data.local.dao.ClonedAppDao
import com.utility.toolbox.data.local.dao.WorkspaceDao
import com.utility.toolbox.data.local.entity.WorkspaceEntity
import com.utility.toolbox.domain.model.Workspace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workspaceDao: WorkspaceDao,
    private val clonedAppDao: ClonedAppDao
) {

    /**
     * Get all workspaces with app counts and sizes.
     */
    fun getAllWorkspaces(): Flow<List<Workspace>> {
        return workspaceDao.getAllWorkspaces().map { entities ->
            entities.map { entity ->
                val appCount = clonedAppDao.getAppCount(entity.id)
                val totalSize = clonedAppDao.getTotalAppSize(entity.id) ?: 0L
                entity.toDomain(appCount, totalSize)
            }
        }
    }

    suspend fun getActiveWorkspace(): Workspace? = workspaceDao.getActiveWorkspace()?.toDomain()

    fun getActiveWorkspaceFlow(): Flow<Workspace?> = workspaceDao.getActiveWorkspaceFlow().map { it?.toDomain() }

    suspend fun createWorkspace(name: String, iconColor: Int = 0xFF7C4DFF.toInt()): Workspace {
        val workspaceDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "workspaces/${name.replace(" ", "_").lowercase()}"
        )
        workspaceDir.mkdirs()

        val entity = WorkspaceEntity(
            name = name,
            storagePath = workspaceDir.absolutePath,
            iconColor = iconColor
        )
        val id = workspaceDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    suspend fun deleteWorkspace(id: Long) {
        // Delete storage directory
        val workspace = workspaceDao.getWorkspaceById(id)
        workspace?.storagePath?.let { path ->
            File(path).deleteRecursively()
        }

        // Delete cloned app data directories
        val apps = clonedAppDao.getAppsByWorkspace(id).first()
        apps.forEach { app ->
            try {
                File(app.dataPath).deleteRecursively()
            } catch (e: Exception) {
                // Log but continue cleanup
            }
        }

        // Delete database record (CASCADE will remove cloned_apps too)
        workspaceDao.deleteById(id)
    }

    suspend fun renameWorkspace(id: Long, newName: String) {
        val workspace = workspaceDao.getWorkspaceById(id) ?: return
        workspaceDao.update(workspace.copy(name = newName))
    }

    suspend fun switchWorkspace(id: Long) {
        workspaceDao.deactivate(id)
        workspaceDao.activate(id)
        workspaceDao.updateTimestamp(id)
    }

    suspend fun getWorkspaceStats(id: Long): WorkspaceStats {
        val appCount = clonedAppDao.getAppCount(id)
        val totalAppSize = clonedAppDao.getTotalAppSize(id) ?: 0L
        val runningCount = clonedAppDao.getRunningApps().count { it.workspaceId == id }

        return WorkspaceStats(
            appCount = appCount,
            totalSize = totalAppSize,
            runningApps = runningCount
        )
    }

    suspend fun getTotalStorageUsed(): Long {
        val baseDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "workspaces"
        )
        if (!baseDir.exists()) return 0L
        return baseDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

data class WorkspaceStats(
    val appCount: Int,
    val totalSize: Long,
    val runningApps: Int
)

// Extension to convert entity to domain model
private fun WorkspaceEntity.toDomain(
    appCount: Int = 0,
    totalSize: Long = 0L
) = Workspace(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isActive = isActive,
    iconColor = iconColor,
    storagePath = storagePath,
    profileId = profileId,
    appCount = appCount,
    totalSize = totalSize
)
