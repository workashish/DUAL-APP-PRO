package com.utility.toolbox.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Each cloned app is independent with its own spoofed device identity.
 * No workspace concept — every clone is a standalone virtual device.
 */
@Entity(
    tableName = "cloned_apps",
    indices = [
        Index("original_package"),
        Index("clone_package"),
        Index("user_id")
    ]
)
data class ClonedAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

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

    @ColumnInfo(name = "apk_path")
    val apkPath: String = "",

    @ColumnInfo(name = "data_path")
    val dataPath: String = "",

    @ColumnInfo(name = "user_id")
    val userId: Int,

    // ── Per-clone spoofed identity ──────────────────────────────────
    @ColumnInfo(name = "android_id")
    val androidId: String = "",

    @ColumnInfo(name = "device_model")
    val deviceModel: String = "",

    @ColumnInfo(name = "device_brand")
    val deviceBrand: String = "",

    @ColumnInfo(name = "device_fingerprint")
    val deviceFingerprint: String = "",

    @ColumnInfo(name = "device_serial")
    val deviceSerial: String = "",

    @ColumnInfo(name = "imei")
    val imei: String = "",

    @ColumnInfo(name = "mac_address")
    val macAddress: String = "",

    @ColumnInfo(name = "gsf_id")
    val gsfId: String = "",

    // ── GMS per clone ───────────────────────────────────────────────
    @ColumnInfo(name = "gms_installed")
    val gmsInstalled: Boolean = false,

    @ColumnInfo(name = "gms_install_date")
    val gmsInstallDate: Long = 0,

    // ── Status ──────────────────────────────────────────────────────
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
