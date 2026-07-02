package com.utility.toolbox.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.utility.toolbox.ui.screens.clone.CloneScreen
import com.utility.toolbox.ui.screens.customize.CustomizeScreen
import com.utility.toolbox.ui.screens.deviceinfo.DeviceInfoScreen
import com.utility.toolbox.ui.screens.deviceinfo.DeviceInfoViewModel
import com.utility.toolbox.ui.screens.gsflicense.GsfLicenseScreen
import com.utility.toolbox.ui.screens.gsflicense.GsfLicenseViewModel
import com.utility.toolbox.ui.screens.home.HomeScreen
import com.utility.toolbox.ui.screens.iconfake.IconFakeScreen
import com.utility.toolbox.ui.screens.logviewer.LogViewerScreen
import com.utility.toolbox.ui.screens.onboarding.OnboardingScreen
import com.utility.toolbox.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = Screen.Home.route, modifier = modifier) {
        composable(Screen.Home.route) { HomeScreen(navController = navController) }
        composable(Screen.Clone.route) { CloneScreen(navController = navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController = navController) }

        composable(Screen.Customize.route, arguments = listOf(navArgument("appId") { type = NavType.LongType })) { back ->
            val appId = back.arguments?.getLong("appId") ?: return@composable
            CustomizeScreen(appId = appId, onBack = { navController.popBackStack() })
        }

        composable(Screen.DeviceInfo.route, arguments = listOf(navArgument("appId") { type = NavType.LongType })) { back ->
            val appId = back.arguments?.getLong("appId") ?: return@composable
            val vm: DeviceInfoViewModel = hiltViewModel()
            val state by vm.uiState.collectAsState()
            LaunchedEffect(appId) { vm.loadApp(appId) }
            if (state.isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else state.app?.let { app ->
                DeviceInfoScreen(app = app, deviceIdentity = null, onBack = { navController.popBackStack() },
                    onResetDeviceInfo = { vm.resetDeviceInfo() }, onResetGsf = { vm.resetGsf() })
            }
        }

        composable(Screen.GsfLicense.route, arguments = listOf(navArgument("appId") { type = NavType.LongType })) { back ->
            val appId = back.arguments?.getLong("appId") ?: return@composable
            val vm: GsfLicenseViewModel = hiltViewModel()
            val state by vm.uiState.collectAsState()
            LaunchedEffect(appId) { vm.loadApp(appId) }
            if (state.isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else state.app?.let { app ->
                GsfLicenseScreen(app = app, currentGsfId = state.currentGsfId, onBack = { navController.popBackStack() },
                    onResetLicense = { vm.resetLicense() }, onSetLicense = { _, id -> vm.setCustomLicense(id) })
            }
        }

        composable(Screen.IconFake.route, arguments = listOf(navArgument("appId") { type = NavType.LongType })) { back ->
            val appId = back.arguments?.getLong("appId") ?: return@composable
            IconFakeScreen(appId = appId, onBack = { navController.popBackStack() }, onIconSelected = { })
        }

        composable(Screen.LogViewer.route) { LogViewerScreen(onBack = { navController.popBackStack() }) }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(onFinish = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } } })
        }

        composable(Screen.RootCheck.route) {
            com.utility.toolbox.ui.screens.rootcheck.RootCheckScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Permissions.route, arguments = listOf(
            navArgument("packageName") { type = NavType.StringType },
            navArgument("appName") { type = NavType.StringType }
        )) { back ->
            val pkg = back.arguments?.getString("packageName") ?: return@composable
            val name = back.arguments?.getString("appName") ?: return@composable
            com.utility.toolbox.ui.screens.permissions.PermissionScreen(packageName = pkg, appName = name, onBack = { navController.popBackStack() })
        }
    }
}
