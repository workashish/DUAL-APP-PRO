package com.utility.toolbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.utility.toolbox.MainActivity
import com.utility.toolbox.R
import com.utility.toolbox.data.local.entity.ClonedAppEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Foreground service that handles the app cloning process.
 * Extracts APK files, saves metadata, and installs clones.
 */
@AndroidEntryPoint
class AppClonerService : Service() {

    companion object {
        private const val TAG = "AppClonerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "clone_service"
        private const val ACTION_CLONE = "com.utility.toolbox.CLONE_APP"
        private const val ACTION_INSTALL = "com.utility.toolbox.INSTALL_CLONE"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_CLONE_NAME = "extra_clone_name"

        fun cloneApp(context: Context, packageName: String, userId: Int) {
            val intent = Intent(context, AppClonerService::class.java).apply {
                action = ACTION_CLONE
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_USER_ID, userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var notificationManager: NotificationManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_CLONE -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_STICKY
                val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
                if (userId == -1) return START_STICKY
                handleCloneApp(packageName, userId)
            }
        }

        return START_STICKY
    }

    /**
     * Handle clone app request.
     *
     * ⚠️ DEPRECATED: App cloning is now handled by AppRepository.cloneApp() which
     * calls BlackBoxEngine.installClone() for proper virtual environment installation.
     *
     * This service remains for legacy compatibility but does NOT perform actual cloning.
     * The correct flow: CloneViewModel → AppRepository.cloneApp() → BlackBoxEngine.installClone()
     */
    private fun handleCloneApp(packageName: String, userId: Int) {
        if (isRunning) return
        isRunning = true

        serviceScope.launch {
            try {
                Log.i(TAG, "cloneApp called for $packageName — " +
                    "redirecting to AppRepository flow...")
                // The actual cloning is handled by AppRepository / BlackBoxEngine
                // which is called from CloneViewModel.cloneSelectedApps()
                notifyCloneComplete(packageName, "")
            } catch (e: Exception) {
                Log.e(TAG, "Clone failed for $packageName", e)
                notifyCloneFailed(packageName)
            } finally {
                isRunning = false
                stopSelf()
            }
        }
    }

    private fun extractApk(packageName: String, targetDir: File): File? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val sourceFile = File(appInfo.sourceDir)
            if (!sourceFile.exists()) return null

            val apkFile = File(targetDir, "${packageName}_clone.apk")
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(apkFile).use { output ->
                    input.copyTo(output)
                }
            }
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract APK for $packageName", e)
            null
        }
    }

    private fun extractIcon(packageName: String, targetDir: File): File? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val icon = appInfo.loadIcon(packageManager)
            val iconFile = File(targetDir, "${packageName}_icon.png")

            // Convert Drawable to Bitmap and save as PNG
            val bitmap = if (icon is android.graphics.drawable.BitmapDrawable) {
                icon.bitmap
            } else {
                val width = if (icon.intrinsicWidth > 0) icon.intrinsicWidth else 512
                val height = if (icon.intrinsicHeight > 0) icon.intrinsicHeight else 512
                val b = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(b)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                b
            }

            FileOutputStream(iconFile).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            iconFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract icon for $packageName", e)
            null
        }
    }

    private fun extractAndInstallViaBlackBox(packageName: String, userId: Int) {
        // DEPRECATED: The old approach of copying APK and requesting system install 
        // is fundamentally wrong for a clone engine. 
        //
        // The correct flow uses BlackBox's installPackageAsUser() which installs
        // the app into the virtual environment WITHOUT creating a duplicate system install.
        // This is handled by BlackBoxEngine.installClone() / AppRepository.cloneApp().
        //
        // This method exists only as a reference and is NOT called in the current flow.
        // To restore: add action INSTALL_CLONE and wire it up.
        Log.w(TAG, "extractAndInstallViaBlackBox($packageName, $userId) — deprecated, no-op")
    }

    private fun getCloneStorageDir(userId: Int, packageName: String): File {
        val baseDir = File(
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "clones/$userId/apps/$packageName"
        )
        baseDir.mkdirs()
        return baseDir
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clone Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles app cloning operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DualSpace")
            .setContentText("Cloning app in progress…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    private fun notifyCloneComplete(packageName: String, apkPath: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clone Complete")
            .setContentText("Ready to install $packageName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun notifyCloneFailed(packageName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clone Failed")
            .setContentText("Failed to clone $packageName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
