package com.utility.toolbox.domain.model

import android.graphics.drawable.Drawable

/**
 * Domain model for an installable app on the device.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Int,
    val icon: Drawable?,
    val sourceDir: String,
    val isSystemApp: Boolean,
    val installDate: Long
)
