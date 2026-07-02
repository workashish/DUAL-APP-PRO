package com.utility.toolbox.domain.model

import com.utility.toolbox.ui.theme.WorkspaceColorInts

data class ClonedApp(
    val id: Long,
    val originalPackage: String,
    val clonePackage: String,
    val appName: String,
    val customName: String = "",
    val customIconColor: Int? = null,
    val versionName: String,
    val versionCode: Int,
    val apkPath: String,
    val dataPath: String,
    val userId: Int,
    val androidId: String,
    val deviceModel: String,
    val deviceBrand: String,
    val deviceFingerprint: String,
    val deviceSerial: String,
    val imei: String,
    val macAddress: String,
    val gsfId: String,
    val gmsInstalled: Boolean,
    val gmsInstallDate: Long,
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val installDate: Long,
    val lastLaunch: Long,
    val appSize: Long,
    val cacheSize: Long,
    val hasShortcut: Boolean = false,
    val isHidden: Boolean = false,
    val fakeIconPath: String = "",
    val deviceInfoResetCount: Int = 0,
    val gsfResetCount: Int = 0
) {
    val displayName: String
        get() = customName.ifBlank { appName }

    val iconColor: Int
        get() = customIconColor ?: WorkspaceColorInts[id.toInt() % WorkspaceColorInts.size]

    val isRecentlyUsed: Boolean
        get() = System.currentTimeMillis() - lastLaunch < 24 * 60 * 60 * 1000L

    val deviceLabel: String
        get() = "$deviceBrand $deviceModel"
}
