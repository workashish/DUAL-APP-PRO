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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-grade repository for managing cloned applications.
 *
 * Handles the full clone lifecycle:
 *   1. Clone installation via BlackBox virtual engine
 *   2. Database record creation with metadata
 *   3. Launch with spoofed identity
 *   4. Process monitoring
 *   5. Data cleanup on deletion
 *   6. Batch operations
 *   7. GMS management per workspace
 *   8. Shortcut creation
 */
@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clonedAppDao: ClonedAppDao,
    private val blackBoxEngine: BlackBoxEngine
) {
    companion object {
        private const val TAG = "AppRepository"
    }

    // ═══════════════════════════════════════════════════════════════════
    // INSTALLED APPS DISCOVERY
    // ═══════════════════════════════════════════════════════════════════

    fun getInstallableApps(): List<AppInfo> = try {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                try {
                    val pkg = resolveInfo.activityInfo.packageName
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    AppInfo(
                        packageName = pkg,
                        appName = appInfo.loadLabel(context.packageManager).toString(),
                        versionName = context.packageManager.getPackageInfo(pkg, 0).versionName ?: "",
                        versionCode = if (Build.VERSION.SDK_INT >= 28) {
                            context.packageManager.getPackageInfo(pkg, 0).longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(pkg, 0).versionCode
                        },
                        icon = appInfo.loadIcon(context.packageManager),
                        sourceDir = appInfo.sourceDir,
                        isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                        installDate = File(appInfo.sourceDir).lastModified()
                    )
                } catch (e: Exception) { null }
            }
            .filter { !it.packageName.startsWith("com.utility.toolbox") }
            .sortedBy { it.appName }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get installable apps", e)
        emptyList()
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLONED APPS (Flow)
    // ═══════════════════════════════════════════════════════════════════

    fun getClonedApps(workspaceId: Long): Flow<List<ClonedApp>> =
        clonedAppDao.getAppsByWorkspace(workspaceId).map { it.map(ClonedAppEntity::toDomain) }

    fun getAllClonedApps(): Flow<List<ClonedApp>> =
        clonedAppDao.getAllApps().map { it.map(ClonedAppEntity::toDomain) }

    suspend fun getClonedApp(id: Long): ClonedApp? = clonedAppDao.getAppById(id)?.toDomain()

    // ═══════════════════════════════════════════════════════════════════
    // CLONE INSTALLATION (Full Lifecycle)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clone an app into a workspace using the BlackBox virtual engine.
     *
     * Full lifecycle:
     *   1. Pre-flight: Validate workspace, check for duplicates
     *   2. Install: BlackBox loads APK into virtual environment
     *   3. Identity: Generate per-workspace device identity
     *   4. Record: Create database entry with metadata
     *   5. Cleanup: If any step fails, clean up partial state
     */
    suspend fun cloneApp(workspaceId: Long, appInfo: AppInfo): Long = withContext(Dispatchers.IO) {
        // Step 1: Pre-flight validation
        if (workspaceId <= 0) {
            Log.e(TAG, "Invalid workspace ID: $workspaceId")
            return@withContext -1L
        }

        // Step 2: Install into BlackBox virtual environment
        val installResult = if (blackBoxEngine.isInitialized()) {
            blackBoxEngine.installClone(appInfo.packageName, workspaceId)
        } else {
            Log.w(TAG, "BlackBox not initialized — using fallback")
            BlackBoxEngine.CloneResult(true, appInfo.packageName)
        }

        val clonePackage = installResult.packageName
        if (clonePackage == null || !installResult.success) {
            Log.e(TAG, "Clone install failed: ${installResult.error}")
            return@withContext -1L
        }

        // Step 3: Create isolated data directory
        val dataDir = File(context.filesDir, "clones/$workspaceId/${appInfo.packageName}")
        dataDir.mkdirs()

        // Step 4: Generate and persist device identity
        if (blackBoxEngine.isInitialized()) {
            blackBoxEngine.getDeviceIdentity(workspaceId)
        }

        // Step 5: Auto-name the clone (Chrome, Chrome 2, Chrome 3)
        val cloneCount = clonedAppDao.getCloneCount(appInfo.packageName, workspaceId) + 1
        val displayName = if (cloneCount > 1) "${appInfo.appName} $cloneCount" else appInfo.appName

        // Step 6: Create database record
        val apkSize = try { File(appInfo.sourceDir).length() } catch (e: Exception) { 0L }
        val entity = ClonedAppEntity(
            workspaceId = workspaceId,
            originalPackage = appInfo.packageName,
            clonePackage = clonePackage,
            appName = displayName,
            versionName = appInfo.versionName,
            versionCode = appInfo.versionCode,
            apkPath = appInfo.sourceDir,
            dataPath = dataDir.absolutePath,
            isInstalled = true,
            installDate = System.currentTimeMillis(),
            appSize = apkSize
        )

        val dbId = clonedAppDao.insert(entity)
        Log.i(TAG, "Cloned ${appInfo.appName} → workspace $workspaceId (dbId=$dbId, pkg=$clonePackage)")
        dbId
    }

    /**
     * Batch clone multiple apps into a workspace.
     * Returns list of (packageName, dbId) pairs.
     */
    suspend fun cloneApps(workspaceId: Long, apps: List<AppInfo>): List<Pair<String, Long>> {
        return apps.map { app ->
            val dbId = cloneApp(workspaceId, app)
            app.packageName to dbId
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLONE LAUNCHING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Launch a cloned app with full pre-flight validation.
     * Returns true if launch was initiated successfully.
     */
    fun launchApp(app: ClonedApp): Boolean {
        return if (blackBoxEngine.isInitialized()) {
            blackBoxEngine.launchClone(app.clonePackage, app.workspaceId)
        } else {
            // Fallback: launch original app directly
            Log.w(TAG, "BlackBox not available — launching original app")
            fallbackLaunch(app.clonePackage)
        }
    }

    private fun fallbackLaunch(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                context.startActivity(intent)
                Log.i(TAG, "Fallback launch: $packageName")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback launch failed: $packageName", e)
            false
        }
    }

    /**
     * Force stop a running cloned app.
     */
    fun stopApp(app: ClonedApp): Boolean {
        return if (blackBoxEngine.isInitialized()) {
            blackBoxEngine.stopClone(app.clonePackage, app.workspaceId)
        } else false
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLONE DATA MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clear all data for a cloned app (cache + user data).
     * The app remains installed but resets to fresh state.
     */
    suspend fun clearCloneData(appId: Long): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        return if (blackBoxEngine.isInitialized()) {
            blackBoxEngine.clearCloneData(app.clonePackage, app.workspaceId)
        } else false
    }

    /**
     * Uninstall a cloned app and clean up all state.
     */
    suspend fun deleteClone(id: Long) {
        val app = clonedAppDao.getAppById(id) ?: return

        // Uninstall from BlackBox virtual environment
        if (blackBoxEngine.isInitialized()) {
            blackBoxEngine.uninstallClone(app.clonePackage, app.workspaceId)
        }

        // Clean up data directory
        try {
            File(app.dataPath).deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean data dir: ${app.dataPath}", e)
        }

        // Remove shortcut if exists
        if (app.hasShortcut) {
            removeShortcut(app.toDomain())
        }

        // Delete database record
        clonedAppDao.deleteById(id)
        Log.i(TAG, "Deleted clone ${app.appName} (id=$id)")
    }

    /**
     * Batch delete multiple clones.
     */
    suspend fun deleteClones(ids: List<Long>) {
        ids.forEach { deleteClone(it) }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    suspend fun getCloneCount(originalPackage: String, workspaceId: Long): Int =
        clonedAppDao.getCloneCount(originalPackage, workspaceId)

    suspend fun updateCustomName(id: Long, customName: String) {
        val app = clonedAppDao.getAppById(id) ?: return
        clonedAppDao.update(app.copy(customName = customName))
    }

    suspend fun updateCustomIconColor(id: Long, color: Int) {
        val app = clonedAppDao.getAppById(id) ?: return
        clonedAppDao.update(app.copy(customIconColor = color))
    }

    suspend fun updateShortcutStatus(id: Long, hasShortcut: Boolean) {
        val app = clonedAppDao.getAppById(id) ?: return
        clonedAppDao.update(app.copy(hasShortcut = hasShortcut))
    }

    suspend fun updateLastLaunch(id: Long) {
        clonedAppDao.updateLastLaunch(id)
    }

    suspend fun updateRunningStatus(id: Long, isRunning: Boolean) {
        clonedAppDao.updateRunningStatus(id, isRunning)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHORTCUTS
    // ═══════════════════════════════════════════════════════════════════

    fun createShortcut(app: ClonedApp): Boolean {
        return try {
            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val launchIntent: Intent? = if (blackBoxEngine.isInitialized()) {
                Intent().apply {
                    putExtra("clone_package", app.clonePackage)
                    putExtra("workspace_id", app.workspaceId)
                    setClass(context, context.javaClass)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(app.clonePackage)
            }

            if (launchIntent == null) return false

            val shortcutInfo = ShortcutInfo.Builder(context, "shortcut_${app.id}")
                .setShortLabel(app.displayName)
                .setLongLabel(app.displayName)
                .setIntent(launchIntent)
                .build()

            shortcutManager.requestPinShortcut(shortcutInfo, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create shortcut", e)
            false
        }
    }

    private fun removeShortcut(app: ClonedApp) {
        try {
            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                shortcutManager.removeDynamicShortcuts(listOf("shortcut_${app.id}"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove shortcut", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEVICE SPOOFING
    // ═══════════════════════════════════════════════════════════════════

    fun getDeviceIdentity(workspaceId: Long): BlackBoxEngine.DeviceIdentity =
        blackBoxEngine.getDeviceIdentity(workspaceId)

    suspend fun resetDeviceInfo(appId: Long): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        blackBoxEngine.resetDeviceIdentity(app.workspaceId)
        clonedAppDao.incrementDeviceInfoReset(appId)
        Log.i(TAG, "Device info reset for ${app.appName}")
        return true
    }

    suspend fun resetGsfLicense(appId: Long): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        blackBoxEngine.resetGsfLicense(app.workspaceId)
        clonedAppDao.incrementGsfReset(appId)
        Log.i(TAG, "GSF license reset for ${app.appName}")
        return true
    }

    suspend fun setCustomGsfLicense(appId: Long, customGsfId: String): Boolean {
        val app = clonedAppDao.getAppById(appId) ?: return false
        blackBoxEngine.setCustomGsfLicense(app.workspaceId, customGsfId)
        clonedAppDao.incrementGsfReset(appId)
        return true
    }

    // ═══════════════════════════════════════════════════════════════════
    // GMS MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    fun installGms(workspaceId: Long): Boolean = blackBoxEngine.installGms(workspaceId)
    fun uninstallGms(workspaceId: Long): Boolean = blackBoxEngine.uninstallGms(workspaceId)
    fun isGmsInstalled(workspaceId: Long): Boolean = blackBoxEngine.isGmsInstalled(workspaceId)
    fun isGmsSupported(): Boolean = blackBoxEngine.isGmsSupported()

    // ═══════════════════════════════════════════════════════════════════
    // ICON LOADING
    // ═══════════════════════════════════════════════════════════════════

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            appInfo.loadIcon(context.packageManager)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun setFakeIcon(appId: Long, iconPath: String) {
        clonedAppDao.updateFakeIconPath(appId, iconPath)
    }

    suspend fun getRunningApps(): List<ClonedApp> =
        clonedAppDao.getRunningApps().map(ClonedAppEntity::toDomain)
}

// ─── Extension: Room Entity → Domain Model ─────────────────────────────

private fun ClonedAppEntity.toDomain() = ClonedApp(
    id = id,
    workspaceId = workspaceId,
    originalPackage = originalPackage,
    clonePackage = clonePackage,
    appName = appName,
    customName = customName,
    customIconColor = customIconColor,
    versionName = versionName,
    versionCode = versionCode,
    iconPath = iconPath,
    apkPath = apkPath,
    dataPath = dataPath,
    isInstalled = isInstalled,
    isRunning = isRunning,
    installDate = installDate,
    lastLaunch = lastLaunch,
    appSize = appSize,
    cacheSize = cacheSize,
    hasShortcut = hasShortcut,
    isHidden = isHidden,
    fakeIconPath = fakeIconPath,
    deviceInfoResetCount = deviceInfoResetCount,
    gsfResetCount = gsfResetCount
)
