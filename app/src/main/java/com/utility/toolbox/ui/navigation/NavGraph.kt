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
import com.utility.toolbox.service.BlackBoxEngine
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

/**
 * Navigation graph for DualApps/Toolbox.
 *
 * Screens now use their own ViewModels to load real data from the repository.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    blackBoxEngine: BlackBoxEngine? = null,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Clone.route) {
            CloneScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(
            route = Screen.Customize.route,
            arguments = listOf(navArgument("appId") { type = NavType.LongType })
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getLong("appId") ?: return@composable
            CustomizeScreen(
                appId = appId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.DeviceInfo.route,
            arguments = listOf(navArgument("appId") { type = NavType.LongType })
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getLong("appId") ?: return@composable
            val vm: DeviceInfoViewModel = hiltViewModel()
            val state by vm.uiState.collectAsState()

            LaunchedEffect(appId) { vm.loadApp(appId) }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                state.app?.let { app ->
                    DeviceInfoScreen(
                        app = app,
                        deviceIdentity = state.deviceIdentity,
                        onBack = { navController.popBackStack() },
                        onResetDeviceInfo = { vm.resetDeviceInfo() },
                        onResetGsf = { vm.resetGsf() }
                    )
                }
            }
        }

        composable(
            route = Screen.GsfLicense.route,
            arguments = listOf(navArgument("appId") { type = NavType.LongType })
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getLong("appId") ?: return@composable
            val vm: GsfLicenseViewModel = hiltViewModel()
            val state by vm.uiState.collectAsState()

            LaunchedEffect(appId) { vm.loadApp(appId) }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                state.app?.let { app ->
                    GsfLicenseScreen(
                        app = app,
                        currentGsfId = state.currentGsfId,
                        onBack = { navController.popBackStack() },
                        onResetLicense = { vm.resetLicense() },
                        onSetLicense = { _, gsfId -> vm.setCustomLicense(gsfId) }
                    )
                }
            }
        }

        composable(
            route = Screen.IconFake.route,
            arguments = listOf(navArgument("appId") { type = NavType.LongType })
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getLong("appId") ?: return@composable
            // NOTE: IconFakeScreen loads its own app data internally
            // using LazyColumn with installed apps list
            IconFakeScreen(
                appId = appId,
                onBack = { navController.popBackStack() },
                onIconSelected = { }
            )
        }

        composable(Screen.LogViewer.route) {
            LogViewerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
