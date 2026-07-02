package com.utility.toolbox.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.utility.toolbox.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_ADDED -> {
                val replaced = intent.action == Intent.ACTION_PACKAGE_REPLACED
                val label = if (replaced) "Updated" else "Installed"
                LogManager.i("AppUpdate", "$label: $packageName")

                if (replaced) {
                    checkClonesNeedUpdate(context, packageName)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                LogManager.i("AppUpdate", "Uninstalled: $packageName")
                handleAppUninstalled(context, packageName)
            }
        }
    }

    private fun checkClonesNeedUpdate(context: Context, packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val dao = db.clonedAppDao()
                val clones = dao.getClonesOf(packageName)

                if (clones.isEmpty()) return@launch

                val pm = context.packageManager
                val newPackageInfo = try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(packageName, 0)
                    }
                } catch (_: Exception) { return@launch }

                val newVersionCode = if (Build.VERSION.SDK_INT >= 28) {
                    newPackageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    newPackageInfo.versionCode.toLong()
                }

                val newVersionName = newPackageInfo.versionName ?: ""

                for (clone in clones) {
                    if (clone.versionCode.toLong() != newVersionCode) {
                        LogManager.i("AppUpdate", "Clone ${clone.appName} needs update: v${clone.versionCode} → v$newVersionCode")
                        // Update the clone record with new version info
                        dao.update(clone.copy(
                            versionName = newVersionName,
                            versionCode = newVersionCode.toInt()
                        ))
                    }
                }
            } catch (e: Exception) {
                LogManager.e("AppUpdate", "Error checking clones: ${e.message}")
            }
        }
    }

    private fun handleAppUninstalled(context: Context, packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val dao = db.clonedAppDao()
                val clones = dao.getClonesOf(packageName)
                for (clone in clones) {
                    // Mark clone as not installed but keep data
                    dao.update(clone.copy(isInstalled = false))
                    LogManager.i("AppUpdate", "Marked clone ${clone.appName} as uninstalled (data preserved)")
                }
            } catch (e: Exception) {
                LogManager.e("AppUpdate", "Error handling uninstall: ${e.message}")
            }
        }
    }
}
