package com.utility.toolbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.utility.toolbox.ui.navigation.NavGraph
import com.utility.toolbox.ui.theme.DualSpaceTheme
import dagger.hilt.android.AndroidEntryPoint
import top.niunaijun.blackbox.BlackBoxCore
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var blackBoxEngine: com.utility.toolbox.service.BlackBoxEngine

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Log.w("MainActivity", "Notification permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        try { BlackBoxCore.get().onBeforeMainActivityOnCreate(this) } catch (_: Exception) {}
        enableEdgeToEdge()
        setContent { DualSpaceTheme { NavGraph(navController = rememberNavController(), modifier = Modifier.fillMaxSize()) } }
        try { BlackBoxCore.get().onAfterMainActivityOnCreate(this) } catch (_: Exception) {}
    }
}
