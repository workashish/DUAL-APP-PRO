package com.utility.toolbox.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a cloned app inside a workspace.
 * Stores metadata about the cloned application.
 */
@Entity(
    tableName = "cloned_apps",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspace_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workspace_id"),
        Index("original_package"),
        Index("clone_package")
    ]
)
data class ClonedAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "workspace_id")
    val workspaceId: Long,

    @ColumnInfo(name = "original_package")
    val originalPackage: String,

    @ColumnInfo(name = "clone_package")
    val clonePackage: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "custom_name")
    val customName: String = "",

    @ColumnInfo(name = "custom_icon_color")
    val customIconColor: Int? = null,

    @ColumnInfo(name = "version_name")
    val versionName: String = "",

    @ColumnInfo(name = "version_code")
    val versionCode: Int = 0,

    @ColumnInfo(name = "icon_path")
    val iconPath: String = "",

    @ColumnInfo(name = "apk_path")
    val apkPath: String = "",

    @ColumnInfo(name = "data_path")
    val dataPath: String = "",

    @ColumnInfo(name = "is_installed")
    val isInstalled: Boolean = false,

    @ColumnInfo(name = "is_running")
    val isRunning: Boolean = false,

    @ColumnInfo(name = "install_date")
    val installDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_launch")
    val lastLaunch: Long = 0L,

    @ColumnInfo(name = "app_size")
    val appSize: Long = 0L,

    @ColumnInfo(name = "cache_size")
    val cacheSize: Long = 0L,

    @ColumnInfo(name = "has_shortcut")
    val hasShortcut: Boolean = false,

    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,

    @ColumnInfo(name = "fake_icon_path")
    val fakeIconPath: String = "",

    @ColumnInfo(name = "device_info_reset_count")
    val deviceInfoResetCount: Int = 0,

    @ColumnInfo(name = "gsf_reset_count")
    val gsfResetCount: Int = 0
)
