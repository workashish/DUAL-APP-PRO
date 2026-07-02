package com.utility.toolbox.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a workspace — an isolated environment for cloned apps.
 */
@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "icon_color")
    val iconColor: Int = 0xFF7C4DFF.toInt(),

    @ColumnInfo(name = "storage_path")
    val storagePath: String = "",

    @ColumnInfo(name = "profile_id")
    val profileId: Int = -1
)
