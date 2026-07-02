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

@HiltAndroidApp
class DualAppsApp : Application() {

    @Inject lateinit var blackBoxEngine: BlackBoxEngine

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        val b = base ?: return

        // ALL processes need BlackBox init in attachBaseContext
        // (including :black server process and :p0 proxy processes)
        try {
            BlackBoxCore.get().closeCodeInit()
            BlackBoxCore.get().onBeforeMainApplicationAttach(this, b)
            BlackBoxCore.get().doAttachBaseContext(b, object : ClientConfiguration() {
                override fun getHostPackageName(): String = b.packageName
                override fun isEnableLauncherActivity(): Boolean = false
                override fun isHideRoot(): Boolean = true
                override fun isEnableDaemonService(): Boolean = true
            })
            BlackBoxCore.get().onAfterMainApplicationAttach(this, b)
            BlackBoxCore.get().addAppLifecycleCallback(object : AppLifecycleCallback() {
                override fun onStoragePermissionNeeded(pkg: String?, userId: Int): Boolean = false
            })
            BlackBoxEngine.markFrameworkInitialized()
            Log.i("DualAppsApp", "BlackBox framework initialized (${getProcName()})")
        } catch (e: Exception) {
            Log.e("DualAppsApp", "BlackBox init failed", e)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Only skip HOST-SIDE heavy init in child processes
        // (LogManager, notifications, BlackBoxEngine singleton)
        if (isBlackBoxChildProcess()) {
            super.onCreate()
            return
        }

        try {
            LogManager.init(this)
            LogManager.installCrashHandler()
            LogManager.i("App", "=== DualSpace Started === v${BuildConfig.VERSION_NAME}")
            LogManager.i("App", "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            LogManager.i("App", "Package: $packageName")
        } catch (e: Exception) {
            Log.e("DualAppsApp", "LogManager init failed", e)
        }

        createNotificationChannels()

        try {
            if (::blackBoxEngine.isInitialized) {
                blackBoxEngine.onCreate()
                LogManager.i("DualAppsApp", "BlackBox engine started")
            }
        } catch (e: Exception) {
            LogManager.e("DualAppsApp", "BlackBox engine failed: ${e.message}")
        }
    }

    /**
     * Detect if we're running inside a BlackBox child process (proxy, black server).
     * Only used to skip HOST-SIDE init (LogManager, notifications) in child processes.
     * attachBaseContext() still runs for ALL processes.
     */
    private fun isBlackBoxChildProcess(): Boolean {
        return try {
            val procName = getProcName()
            procName.contains(":p") || procName.contains(":black") ||
                    procName.contains(":assist") || procName.contains(":provider")
        } catch (_: Exception) { false }
    }

    private fun getProcName(): String {
        return try {
            val pid = android.os.Process.myPid()
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: packageName
        } catch (_: Exception) { packageName }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CLONE_SERVICE, "Clone Service", NotificationManager.IMPORTANCE_LOW).apply { description = "Cloning progress"; setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CLONE_APPS, "Cloned Apps", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Notifications from cloned apps" })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_BUBBLE, "Quick Switch", NotificationManager.IMPORTANCE_LOW).apply { description = "Floating bubble"; setShowBadge(false) })
        }
    }

    companion object {
        const val CHANNEL_CLONE_SERVICE = "clone_service"
        const val CHANNEL_CLONE_APPS = "clone_apps"
        const val CHANNEL_BUBBLE = "bubble_service"
    }
}
