package com.utility.toolbox.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Home : Screen(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )

    data object Clone : Screen(
        route = "clone",
        title = "Clone App",
        icon = Icons.Default.AddBox
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )

    data object Customize : Screen(
        route = "customize/{appId}",
        title = "Customize"
    ) {
        fun createRoute(appId: Long): String = "customize/$appId"
    }

    data object DeviceInfo : Screen(
        route = "device_info/{appId}",
        title = "Device Info"
    ) {
        fun createRoute(appId: Long): String = "device_info/$appId"
    }

    data object GsfLicense : Screen(
        route = "gsf_license/{appId}",
        title = "License Manager"
    ) {
        fun createRoute(appId: Long): String = "gsf_license/$appId"
    }

    data object IconFake : Screen(
        route = "icon_fake/{appId}",
        title = "Icon Fake"
    ) {
        fun createRoute(appId: Long): String = "icon_fake/$appId"
    }

    data object LogViewer : Screen(
        route = "log_viewer",
        title = "Log Viewer",
        icon = Icons.Default.BugReport
    )

    data object Onboarding : Screen(
        route = "onboarding",
        title = "Onboarding"
    )

    companion object {
        val bottomNavItems = listOf(Home, Clone, Settings)
    }
}
