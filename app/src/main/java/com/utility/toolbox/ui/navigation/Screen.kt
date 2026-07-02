package com.utility.toolbox.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Clone : Screen("clone", "Clone App", Icons.Default.AddBox)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object Customize : Screen("customize/{appId}", "Customize") { fun createRoute(appId: Long) = "customize/$appId" }
    data object DeviceInfo : Screen("device_info/{appId}", "Device Info") { fun createRoute(appId: Long) = "device_info/$appId" }
    data object GsfLicense : Screen("gsf_license/{appId}", "License Manager") { fun createRoute(appId: Long) = "gsf_license/$appId" }
    data object IconFake : Screen("icon_fake/{appId}", "Icon Fake") { fun createRoute(appId: Long) = "icon_fake/$appId" }
    data object LogViewer : Screen("log_viewer", "Logs", Icons.Default.BugReport)
    data object Onboarding : Screen("onboarding", "Onboarding")
    data object RootCheck : Screen("root_check", "Root Check")
    data object Permissions : Screen("permissions/{packageName}/{appName}", "Permissions") {
        fun createRoute(packageName: String, appName: String) = "permissions/$packageName/$appName"
    }

    companion object { val bottomNavItems = listOf(Home, Clone, Settings) }
}
