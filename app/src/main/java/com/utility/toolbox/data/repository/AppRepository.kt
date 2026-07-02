package com.utility.toolbox.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import com.utility.toolbox.data.local.dao.ClonedAppDao
import com.utility.toolbox.data.local.entity.ClonedAppEntity
import com.utility.toolbox.domain.model.AppInfo
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.service.BlackBoxEngine
import com.utility.toolbox.service.LogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clonedAppDao: ClonedAppDao,
    private val blackBoxEngine: BlackBoxEngine
) {
    companion object { private const val TAG = "AppRepository" }

    fun getInstallableApps(): List<AppInfo> = try {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        context.packageManager.queryIntentActivities(intent, 0).mapNotNull { ri ->
            try {
                val pkg = ri.activityInfo.packageName
                val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                AppInfo(
                    packageName = pkg,
                    appName = appInfo.loadLabel(context.packageManager).toString(),
                    versionName = context.packageManager.getPackageInfo(pkg, 0).versionName ?: "",
                    versionCode = if (Build.VERSION.SDK_INT >= 28) context.packageManager.getPackageInfo(pkg, 0).longVersionCode.toInt()
                    else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(pkg, 0).versionCode,
                    icon = appInfo.loadIcon(context.packageManager),
                    sourceDir = appInfo.sourceDir,
                    isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    installDate = File(appInfo.sourceDir).lastModified()
                )
            } catch (_: Exception) { null }
        }.filter { !it.packageName.startsWith("com.utility.toolbox") }.sortedBy { it.appName }
    } catch (_: Exception) { emptyList() }

    fun getAllClonedApps(): Flow<List<ClonedApp>> = clonedAppDao.getAllApps().map { it.map(ClonedAppEntity::toDomain) }
    suspend fun getClonedApp(id: Long): ClonedApp? = clonedAppDao.getAppById(id)?.toDomain()

    suspend fun cloneApp(appInfo: AppInfo): Long = withContext(Dispatchers.IO) {
        val userId = blackBoxEngine.nextAvailableUserId()
        LogManager.i("Repo", "Cloning ${appInfo.appName} → userId=$userId")

        val installResult = if (blackBoxEngine.isInitialized()) {
            blackBoxEngine.installClone(appInfo.packageName, userId)
        } else BlackBoxEngine.CloneResult(true, appInfo.packageName)

        if (!installResult.success) {
            LogManager.e("Repo", "✗ Install failed: ${installResult.error}")
            return@withContext -1L
        }

        // Auto-install GMS for each clone
        if (blackBoxEngine.isInitialized()) {
            LogManager.i("GMS", "Auto-installing GMS for ${appInfo.appName} (user=$userId)")
            val gmsOk = blackBoxEngine.installGms(userId)
            LogManager.i("GMS", if (gmsOk) "✓ GMS installed for ${appInfo.appName}" else "✗ GMS install failed (may not be supported)")
        }

        val dataDir = File(context.filesDir, "clones/$userId/${appInfo.packageName}")
        dataDir.mkdirs()

        val identity = if (blackBoxEngine.isInitialized()) blackBoxEngine.generateIdentity(userId)
        else BlackBoxEngine.DeviceIdentity("0", "Unknown", "unknown", "unknown/unknown", "UNKNOWN", "", "", "")

        val cloneCount = clonedAppDao.getCloneCount(appInfo.packageName) + 1
        val displayName = if (cloneCount > 1) "${appInfo.appName} $cloneCount" else appInfo.appName
        val apkSize = try { File(appInfo.sourceDir).length() } catch (_: Exception) { 0L }

        val gmsInstalled = if (blackBoxEngine.isInitialized()) blackBoxEngine.isGmsInstalled(userId) else false

        val entity = ClonedAppEntity(
            originalPackage = appInfo.packageName, clonePackage = appInfo.packageName,
            appName = displayName, userId = userId, androidId = identity.androidId,
            deviceModel = identity.deviceModel, deviceBrand = identity.deviceBrand,
            deviceFingerprint = identity.deviceFingerprint, deviceSerial = identity.deviceSerial,
            imei = identity.imei, macAddress = identity.macAddress, gsfId = identity.gsfId,
            gmsInstalled = gmsInstalled, gmsInstallDate = if (gmsInstalled) System.currentTimeMillis() else 0,
            isInstalled = true, installDate = System.currentTimeMillis(),
            apkPath = appInfo.sourceDir, dataPath = dataDir.absolutePath,
            appSize = apkSize, versionName = appInfo.versionName, versionCode = appInfo.versionCode
        )
        val dbId = clonedAppDao.insert(entity)
        LogManager.i("Repo", "✓ Cloned ${appInfo.appName} (dbId=$dbId, user=$userId, gms=$gmsInstalled)")
        dbId
    }

    fun launchApp(app: ClonedApp): Boolean = if (blackBoxEngine.isInitialized()) {
        blackBoxEngine.launchClone(app.clonePackage, app.userId)
    } else {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(app.clonePackage)
            if (intent != null) { intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK; context.startActivity(intent); true } else false
        } catch (_: Exception) { false }
    }

    fun stopApp(app: ClonedApp): Boolean = if (blackBoxEngine.isInitialized()) blackBoxEngine.stopClone(app.clonePackage, app.userId) else false

    suspend fun clearCloneData(appId: Long): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        return if (blackBoxEngine.isInitialized()) blackBoxEngine.clearCloneData(app.clonePackage, app.userId) else false
    }

    suspend fun deleteClone(id: Long) {
        val app = clonedAppDao.getAppById(id) ?: return
        LogManager.i("Repo", "Deleting clone ${app.appName} (user=${app.userId})")
        if (blackBoxEngine.isInitialized()) blackBoxEngine.uninstallClone(app.clonePackage, app.userId)
        try { File(app.dataPath).deleteRecursively() } catch (_: Exception) {}
        if (app.hasShortcut) removeShortcut(app.toDomain())
        clonedAppDao.deleteById(id)
        LogManager.i("Repo", "✓ Deleted ${app.appName}")
    }

    suspend fun resetDeviceInfo(appId: Long): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        val newIdentity = blackBoxEngine.generateIdentity(app.userId)
        clonedAppDao.update(app.copy(
            androidId = newIdentity.androidId,
            deviceModel = newIdentity.deviceModel,
            deviceBrand = newIdentity.deviceBrand,
            deviceFingerprint = newIdentity.deviceFingerprint,
            deviceSerial = newIdentity.deviceSerial,
            imei = newIdentity.imei,
            macAddress = newIdentity.macAddress,
            deviceInfoResetCount = app.deviceInfoResetCount + 1
        ))
        LogManager.i("Repo", "✓ Identity reset for ${app.appName} (user=${app.userId})")
        return true
    }

    suspend fun resetGsfLicense(appId: Long): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        val newIdentity = blackBoxEngine.generateIdentity(app.userId)
        clonedAppDao.update(app.copy(
            androidId = newIdentity.androidId,
            gsfId = newIdentity.gsfId,
            gsfResetCount = app.gsfResetCount + 1
        ))
        LogManager.i("Repo", "✓ GSF license reset for ${app.appName} (user=${app.userId})")
        return true
    }

    suspend fun setCustomGsfLicense(appId: Long, customGsfId: String): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        clonedAppDao.update(app.copy(gsfId = customGsfId, gsfResetCount = app.gsfResetCount + 1))
        LogManager.i("Repo", "✓ Custom GSF set for ${app.appName}: $customGsfId")
        return true
    }
    suspend fun updateCustomName(id: Long, name: String) { clonedAppDao.getAppById(id)?.let { clonedAppDao.update(it.copy(customName = name)) } }
    suspend fun updateCustomIconColor(id: Long, color: Int) { clonedAppDao.getAppById(id)?.let { clonedAppDao.update(it.copy(customIconColor = color)) } }
    suspend fun updateShortcutStatus(id: Long, has: Boolean) { clonedAppDao.getAppById(id)?.let { clonedAppDao.update(it.copy(hasShortcut = has)) } }
    suspend fun updateLastLaunch(id: Long) { clonedAppDao.updateLastLaunch(id) }
    suspend fun updateRunningStatus(id: Long, running: Boolean) { clonedAppDao.updateRunningStatus(id, running) }

    fun createShortcut(app: ClonedApp): Boolean {
        return try {
            val sm = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
            if (!sm.isRequestPinShortcutSupported) return false
            val intent = if (blackBoxEngine.isInitialized()) Intent().apply { putExtra("clone_package", app.clonePackage); putExtra("user_id", app.userId); setClass(context, context.javaClass) }
            else context.packageManager.getLaunchIntentForPackage(app.clonePackage) ?: return false
            sm.requestPinShortcut(ShortcutInfo.Builder(context, "shortcut_${app.id}").setShortLabel(app.displayName).setLongLabel(app.displayName).setIntent(intent).build(), null)
            true
        } catch (_: Exception) { false }
    }

    private fun removeShortcut(app: ClonedApp) { try { (context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager).removeDynamicShortcuts(listOf("shortcut_${app.id}")) } catch (_: Exception) {} }

    fun installGms(userId: Int): Boolean = blackBoxEngine.installGms(userId)
    fun uninstallGms(userId: Int): Boolean = blackBoxEngine.uninstallGms(userId)
    fun isGmsInstalled(userId: Int): Boolean = blackBoxEngine.isGmsInstalled(userId)
    fun isGmsSupported(): Boolean = blackBoxEngine.isGmsSupported()

    suspend fun updateGmsStatus(id: Long, installed: Boolean) { clonedAppDao.updateGmsStatus(id, installed) }
    fun getAppIcon(packageName: String): Drawable? = try { context.packageManager.getApplicationInfo(packageName, 0).loadIcon(context.packageManager) } catch (_: Exception) { null }
    suspend fun setFakeIcon(appId: Long, path: String) { clonedAppDao.updateFakeIconPath(appId, path) }
}

private fun ClonedAppEntity.toDomain() = ClonedApp(
    id = id, originalPackage = originalPackage, clonePackage = clonePackage, appName = appName,
    customName = customName, customIconColor = customIconColor, versionName = versionName,
    versionCode = versionCode, apkPath = apkPath, dataPath = dataPath, userId = userId,
    androidId = androidId, deviceModel = deviceModel, deviceBrand = deviceBrand,
    deviceFingerprint = deviceFingerprint, deviceSerial = deviceSerial, imei = imei,
    macAddress = macAddress, gsfId = gsfId, gmsInstalled = gmsInstalled,
    gmsInstallDate = gmsInstallDate, isInstalled = isInstalled, isRunning = isRunning,
    installDate = installDate, lastLaunch = lastLaunch, appSize = appSize,
    cacheSize = cacheSize, hasShortcut = hasShortcut, isHidden = isHidden,
    fakeIconPath = fakeIconPath, deviceInfoResetCount = deviceInfoResetCount,
    gsfResetCount = gsfResetCount
)
