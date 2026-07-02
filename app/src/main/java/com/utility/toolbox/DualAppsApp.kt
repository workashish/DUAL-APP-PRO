package com.utility.toolbox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.utility.toolbox.service.BlackBoxEngine
import com.utility.toolbox.service.LogManager
import dagger.hilt.android.HiltAndroidApp
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import javax.inject.Inject

/**
 * Application class for DualApps/Toolbox.
 *
 * Integrates BlackBox virtual engine for app cloning and device spoofing.
 * Initialization order matters:
 *   1. attachBaseContext() — BlackBox hooks into Android framework at the base context level
 *   2. onCreate() — Start the virtual engine after BlackBox is initialized
 *
 * CRITICAL: attachBaseContext() runs BEFORE Hilt injection is available.
 * So BlackBox framework init must happen manually via BlackBoxCore directly.
 * Then onCreate() uses the Hilt-injected BlackBoxEngine for post-init steps.
 */
@HiltAndroidApp
class DualAppsApp : Application() {

    @Inject
    lateinit var blackBoxEngine: BlackBoxEngine

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        val b = base ?: run {
            Log.e("DualAppsApp", "attachBaseContext called with null base — skipping BlackBox init")
            return
        }

        // ─────────────────────────────────────────────────────────────────
        // Initialize BlackBox virtual engine.
        // Hilt is NOT ready yet, so we initialize directly via BlackBoxCore.
        // Sync initialization is required for the engine to work correctly.
        //
        // The correct initialization sequence (from NewBlackbox App.kt):
        //   1. closeCodeInit() — close anti-reverse-engineering checks
        //   2. onBeforeMainApplicationAttach() — app lifecycle hook
        //   3. doAttachBaseContext() — hooks into Android framework
        //   4. onAfterMainApplicationAttach() — post-attach lifecycle hook
        //   5. addAppLifecycleCallback() — register lifecycle callbacks
        // ─────────────────────────────────────────────────────────────────
        try {
            // Step 1: Close anti-reverse-engineering code
            BlackBoxCore.get().closeCodeInit()

            // Step 2: Before attach notify lifecycle
            BlackBoxCore.get().onBeforeMainApplicationAttach(this, b)

            // Step 3: Attach to base context with client configuration
            // NOTE: isEnableLauncherActivity() returns false to skip the
            // BlackBox LauncherActivity splash screen. This provides:
            //   - Direct launch: cloned apps open immediately without an
            //     interstitial branded loading screen
            //   - Better compatibility: avoids potential crashes when the
            //     splash screen tries to load app info from the virtual env
            //   - Faster perceived startup: users see the cloned app directly
            BlackBoxCore.get().doAttachBaseContext(b, object : ClientConfiguration() {
                override fun getHostPackageName(): String = b.packageName
                override fun isEnableLauncherActivity(): Boolean = false
                override fun isHideRoot(): Boolean = true
                override fun isDisableFlagSecure(): Boolean = false
                override fun isEnableDaemonService(): Boolean = true
            })

            // Step 4: After attach notify lifecycle
            BlackBoxCore.get().onAfterMainApplicationAttach(this, b)

            // Step 5: Register lifecycle callback for cloned app lifecycle events
            BlackBoxCore.get().addAppLifecycleCallback(object : AppLifecycleCallback() {
                override fun onStoragePermissionNeeded(packageName: String?, userId: Int): Boolean {
                    Log.w("DualAppsApp", "Storage permission needed for: $packageName (user: $userId)")
                    return false
                }
            })

            // Mark BlackBox as framework-initialized (shared companion flag)
            BlackBoxEngine.markFrameworkInitialized()

            Log.i("DualAppsApp", "BlackBox framework initialized successfully")
        } catch (e: Exception) {
            Log.e("DualAppsApp", "BlackBox framework init failed", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Install in-app logging and crash handler
        try {
            LogManager.installCrashHandler()
            // Log key device info at startup
            LogManager.i("DualAppsApp", "App started — v${BuildConfig.VERSION_NAME} (SDK ${Build.VERSION.SDK_INT})")
            LogManager.d("DualAppsApp", "Package: $packageName")
        } catch (e: Exception) {
            Log.e("DualAppsApp", "Failed to init LogManager", e)
        }

        // Start the BlackBox virtual engine (Hilt is ready now)
        try {
            if (::blackBoxEngine.isInitialized) {
                blackBoxEngine.onCreate()
                Log.i("DualAppsApp", "BlackBox engine started")
                LogManager.i("DualAppsApp", "BlackBox engine started")
            } else {
                Log.w("DualAppsApp", "BlackBox engine not initialized (likely in black process)")
            }
        } catch (e: Exception) {
            Log.e("DualAppsApp", "BlackBox engine onCreate failed", e)
            LogManager.e("DualAppsApp", "BlackBox engine onCreate failed", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Clone service channel
            val cloneServiceChannel = NotificationChannel(
                CHANNEL_CLONE_SERVICE,
                "Clone Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows app cloning progress"
                setShowBadge(false)
            }

            // Clone app notifications channel
            val cloneAppsChannel = NotificationChannel(
                CHANNEL_CLONE_APPS,
                "Cloned Apps",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from your cloned apps"
            }

            // Bubble service channel
            val bubbleChannel = NotificationChannel(
                CHANNEL_BUBBLE,
                "Quick Switch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating bubble for quick app switching"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(cloneServiceChannel)
            notificationManager.createNotificationChannel(cloneAppsChannel)
            notificationManager.createNotificationChannel(bubbleChannel)
        }
    }

    companion object {
        const val CHANNEL_CLONE_SERVICE = "clone_service"
        const val CHANNEL_CLONE_APPS = "clone_apps"
        const val CHANNEL_BUBBLE = "bubble_service"
    }
}
