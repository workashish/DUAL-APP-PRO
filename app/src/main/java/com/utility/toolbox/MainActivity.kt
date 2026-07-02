package com.utility.toolbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.utility.toolbox.service.LogManager
import com.utility.toolbox.ui.navigation.NavGraph
import com.utility.toolbox.ui.theme.DualSpaceTheme
import dagger.hilt.android.AndroidEntryPoint
import top.niunaijun.blackbox.BlackBoxCore
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var blackBoxEngine: com.utility.toolbox.service.BlackBoxEngine

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) LogManager.w("MainActivity", "Notification permission denied")
    }

    private val allFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) {
            LogManager.i("MainActivity", "✓ All Files Access granted")
        } else {
            LogManager.w("MainActivity", "All Files Access denied — BlackBox may not work properly")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.init(this)

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request All Files Access (critical for BlackBox on Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            LogManager.i("MainActivity", "Requesting All Files Access permission")
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            allFilesAccessLauncher.launch(intent)
        }

        try { BlackBoxCore.get().onBeforeMainActivityOnCreate(this) } catch (_: Exception) {}
        enableEdgeToEdge()
        setContent { DualSpaceTheme { NavGraph(navController = rememberNavController(), modifier = Modifier.fillMaxSize()) } }
        try { BlackBoxCore.get().onAfterMainActivityOnCreate(this) } catch (_: Exception) {}
    }
}
