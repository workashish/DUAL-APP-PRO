package com.utility.toolbox.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.pm.InstallResult
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-grade wrapper around the BlackBox virtual app engine.
 *
 * Manages the full lifecycle of cloned apps:
 *   - Installation into isolated virtual environments
 *   - Launch with pre-flight checks and crash prevention
 *   - Process monitoring and lifecycle tracking
 *   - Data cleanup and cache management
 *   - GMS (Google Play Services) installation per workspace
 *   - Per-workspace device identity spoofing
 *   - Root hiding and anti-detection integration
 *
 * Ref: https://github.com/ALEX5402/NewBlackbox
 *
 * ⚠️ DUAL-INSTANCE SAFETY:
 *   attachBaseContext() runs BEFORE Hilt injection is ready, so we use a companion
 *   object-level `frameworkInitialized` flag that persists across instances.
 */
@Singleton
class BlackBoxEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val antiDetectionManager: AntiDetectionManager
) {
    companion object {
        private const val TAG = "BlackBoxEngine"

        /** Workspace 1 → userId 1001, workspace 2 → userId 1002, etc. */
        private const val USER_ID_OFFSET = 1000

        /** Max concurrent launch retry attempts */
        private const val MAX_LAUNCH_ATTEMPTS = 3

        /** Delay between launch retries (ms) */
        private const val RETRY_DELAY_MS = 300L

        @Volatile
        private var frameworkInitialized = false

        @Volatile
        private var engineStarted = false

        fun markFrameworkInitialized() {
            frameworkInitialized = true
        }

        fun isFrameworkInitialized(): Boolean = frameworkInitialized

        fun getInstance(context: Context): BlackBoxEngine {
            val appContext = context.applicationContext ?: context
            return Holder.instance ?: synchronized(this) {
                Holder.instance ?: BlackBoxEngine(
                    appContext,
                    AntiDetectionManager.getInstance(appContext)
                ).also { Holder.instance = it }
            }
        }

        internal fun resetInstanceForTest() {
            Holder.instance = null
        }

        private object Holder {
            @Volatile
            var instance: BlackBoxEngine? = null
        }
    }

    data class DeviceIdentity(
        val androidId: String,
        val deviceModel: String,
        val deviceBrand: String,
        val deviceFingerprint: String,
        val deviceSerial: String,
        val gsfId: String,
        val imei: String,
        val macAddress: String,
        val screenWidth: Int = 1080,
        val screenHeight: Int = 2400,
        val dpi: Int = 420,
        val sdkVersion: Int = 34,
        val bootloader: String = "unknown",
        val radioVersion: String = "",
        val kernelVersion: String = ""
    )

    data class CloneResult(
        val success: Boolean,
        val packageName: String?,
        val error: String? = null
    )

    data class InstalledAppInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val isSystemApp: Boolean
    )

    fun isInitialized(): Boolean = frameworkInitialized
    fun isEngineStarted(): Boolean = engineStarted

    // ═══════════════════════════════════════════════════════════════════
    // ENGINE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    fun onCreate() {
        if (!frameworkInitialized) {
            Log.w(TAG, "BlackBox framework not initialized — skipping onCreate")
            return
        }
        if (engineStarted) {
            Log.d(TAG, "BlackBox engine already started — skipping")
            return
        }
        try {
            BlackBoxCore.get().doCreate()
            antiDetectionManager.initialize()
            engineStarted = true
            Log.i(TAG, "BlackBox engine started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BlackBox engine", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLONE INSTALLATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Install a cloned app into a workspace's virtual environment.
     *
     * Pre-flight checks:
     *   - Validates engine state
     *   - Checks if already installed (avoids duplicate install)
     *   - Waits for BlackBox services to be ready
     *
     * @param packageName The original app's package name
     * @param workspaceId The workspace to install into
     * @return CloneResult with success status and details
     */
    fun installClone(packageName: String, workspaceId: Long): CloneResult {
        if (!frameworkInitialized) {
            return CloneResult(false, null, "BlackBox framework not initialized")
        }
        if (!engineStarted) {
            return CloneResult(false, null, "BlackBox engine not started")
        }

        val userId = workspaceIdToUserId(workspaceId)

        // Pre-flight: Wait for services to be ready
        ensureServicesReady()

        // Check if already installed
        try {
            if (BlackBoxCore.get().isInstalled(packageName, userId)) {
                Log.d(TAG, "$packageName already installed in workspace $workspaceId")
                return CloneResult(true, packageName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Install check failed, proceeding with install", e)
        }

        // Install into virtual environment
        return try {
            val result: InstallResult = BlackBoxCore.get().installPackageAsUser(packageName, userId)
            if (result.success) {
                Log.i(TAG, "Installed $packageName → workspace $workspaceId (userId=$userId)")
                CloneResult(true, result.packageName ?: packageName)
            } else {
                Log.e(TAG, "Install failed: ${result.msg}")
                CloneResult(false, null, result.msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Install exception for $packageName", e)
            CloneResult(false, null, e.message)
        }
    }

    /**
     * Install an APK file into a workspace (for sideloaded apps).
     */
    fun installCloneFromFile(apkFile: File, workspaceId: Long): CloneResult {
        if (!frameworkInitialized) {
            return CloneResult(false, null, "BlackBox framework not initialized")
        }
        if (!apkFile.exists()) {
            return CloneResult(false, null, "APK file not found: ${apkFile.absolutePath}")
        }

        val userId = workspaceIdToUserId(workspaceId)
        ensureServicesReady()

        return try {
            val result = BlackBoxCore.get().installPackageAsUser(apkFile as java.io.File, userId)
            if (result.success) {
                Log.i(TAG, "Installed from file → workspace $workspaceId (userId=$userId)")
                CloneResult(true, result.packageName)
            } else {
                CloneResult(false, null, result.msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "File install exception", e)
            CloneResult(false, null, e.message)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLONE LAUNCHING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Launch a cloned app with full pre-flight validation and crash prevention.
     *
     * Launch sequence:
     *   1. Validate engine state
     *   2. Wait for BlackBox services
     *   3. Verify package is installed (auto-reinstall if missing)
     *   4. Call onBeforeMainLaunchApk (app-specific crash prevention)
     *   5. Launch with retry logic
     *   6. Return success/failure
     */
    fun launchClone(clonePackage: String, workspaceId: Long): Boolean {
        if (!frameworkInitialized || !engineStarted) {
            Log.w(TAG, "Cannot launch $clonePackage — engine not ready")
            return false
        }

        val userId = workspaceIdToUserId(workspaceId)

        // Step 1: Ensure services are available
        ensureServicesReady()

        // Step 2: Verify installation (auto-reinstall if missing)
        if (!ensureInstalled(clonePackage, userId, workspaceId)) {
            return false
        }

        // Step 3: Pre-launch hook (app-specific crash prevention)
        try {
            BlackBoxCore.get().onBeforeMainLaunchApk(clonePackage, userId)
        } catch (e: Exception) {
            Log.w(TAG, "onBeforeMainLaunchApk failed for $clonePackage", e)
        }

        // Step 4: Launch with retry
        for (attempt in 1..MAX_LAUNCH_ATTEMPTS) {
            try {
                val success = BlackBoxCore.get().launchApk(clonePackage, userId)
                if (success) {
                    Log.i(TAG, "Launched $clonePackage (workspace=$workspaceId, userId=$userId, attempt=$attempt)")
                    return true
                }
                Log.w(TAG, "Launch attempt $attempt/$MAX_LAUNCH_ATTEMPTS returned false for $clonePackage")
            } catch (e: Exception) {
                Log.w(TAG, "Launch attempt $attempt/$MAX_LAUNCH_ATTEMPTS failed: ${e.message}")
            }
            if (attempt < MAX_LAUNCH_ATTEMPTS) {
                Thread.sleep(RETRY_DELAY_MS)
            }
        }

        Log.e(TAG, "All $MAX_LAUNCH_ATTEMPTS launch attempts failed for $clonePackage")
        return false
    }

    /**
     * Launch a cloned app via Intent (for custom launch configurations).
     */
    fun launchCloneIntent(clonePackage: String, workspaceId: Long): Boolean {
        if (!frameworkInitialized || !engineStarted) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            BlackBoxCore.get().startActivity(
                context.packageManager.getLaunchIntentForPackage(clonePackage),
                userId
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Intent launch failed for $clonePackage", e)
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLONE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Uninstall a cloned app and clean up its data.
     */
    fun uninstallClone(clonePackage: String, workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            // Clear data first
            try {
                BlackBoxCore.get().clearPackage(clonePackage, userId)
            } catch (e: Exception) {
                Log.w(TAG, "ClearPackage failed (non-fatal)", e)
            }
            // Then uninstall
            BlackBoxCore.get().uninstallPackageAsUser(clonePackage, userId)
            Log.i(TAG, "Uninstalled $clonePackage from workspace $workspaceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed for $clonePackage", e)
            false
        }
    }

    /**
     * Force stop a running cloned app.
     */
    fun stopClone(clonePackage: String, workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            BlackBoxCore.get().stopPackage(clonePackage, userId)
            Log.i(TAG, "Stopped $clonePackage in workspace $workspaceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed for $clonePackage", e)
            false
        }
    }

    /**
     * Clear all data for a cloned app (cache + user data).
     */
    fun clearCloneData(clonePackage: String, workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            BlackBoxCore.get().clearPackage(clonePackage, userId)
            Log.i(TAG, "Cleared data for $clonePackage in workspace $workspaceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Clear data failed for $clonePackage", e)
            false
        }
    }

    /**
     * Check if a cloned app is installed in a workspace.
     */
    fun isCloneInstalled(clonePackage: String, workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            BlackBoxCore.get().isInstalled(clonePackage, userId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get all apps installed in a workspace's virtual environment.
     */
    fun getInstalledClones(workspaceId: Long): List<InstalledAppInfo> {
        if (!frameworkInitialized) return emptyList()
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            BlackBoxCore.get().getInstalledPackages(0, userId).map { pkg ->
                InstalledAppInfo(
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else pkg.versionCode.toLong(),
                    isSystemApp = (pkg.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list installed clones", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GMS (Google Play Services) MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Install Google Play Services in a workspace.
     * Required for apps that depend on GMS (messaging apps, maps, etc.).
     */
    fun installGms(workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            val result = BlackBoxCore.get().installGms(userId)
            if (result.success) {
                Log.i(TAG, "GMS installed in workspace $workspaceId")
                true
            } else {
                Log.e(TAG, "GMS install failed: ${result.msg}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "GMS install exception", e)
            false
        }
    }

    /**
     * Uninstall Google Play Services from a workspace.
     */
    fun uninstallGms(workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            val result = BlackBoxCore.get().uninstallGms(userId)
            Log.i(TAG, "GMS uninstalled from workspace $workspaceId: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "GMS uninstall exception", e)
            false
        }
    }

    /**
     * Check if GMS is installed in a workspace.
     */
    fun isGmsInstalled(workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            BlackBoxCore.get().isInstallGms(userId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if the device supports GMS.
     */
    fun isGmsSupported(): Boolean {
        return try {
            BlackBoxCore.get().isSupportGms()
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORKSPACE USER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create a BlackBox user for a workspace.
     */
    fun createWorkspaceUser(workspaceId: Long): Boolean {
        if (!frameworkInitialized) return false
        val userId = workspaceIdToUserId(workspaceId)
        return try {
            BlackBoxCore.get().createUser(userId)
            Log.i(TAG, "Created BlackBox user $userId for workspace $workspaceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create user for workspace $workspaceId", e)
            false
        }
    }

    /**
     * Delete a BlackBox user and all its data.
     */
    fun deleteWorkspaceUser(workspaceId: Long) {
        if (!frameworkInitialized) return
        val userId = workspaceIdToUserId(workspaceId)
        try {
            BlackBoxCore.get().deleteUser(userId)
            Log.i(TAG, "Deleted BlackBox user $userId for workspace $workspaceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user for workspace $workspaceId", e)
        }
    }

    /**
     * Get all BlackBox users.
     */
    fun getWorkspaceUsers(): List<Pair<Int, String>> {
        if (!frameworkInitialized) return emptyList()
        return try {
            BlackBoxCore.get().users.map { it.id to (it.name ?: "Workspace ${it.id}") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEVICE IDENTITY SPOOFING
    // ═══════════════════════════════════════════════════════════════════

    fun getDeviceIdentity(workspaceId: Long): DeviceIdentity {
        val prefs = context.getSharedPreferences("blackbox_device_$workspaceId", Context.MODE_PRIVATE)
        return try {
            DeviceIdentity(
                androidId = prefs.getString("android_id", null) ?: generateAndStoreIdentity(workspaceId, prefs),
                deviceModel = prefs.getString("device_model", null) ?: "",
                deviceBrand = prefs.getString("device_brand", null) ?: "",
                deviceFingerprint = prefs.getString("device_fingerprint", null) ?: "",
                deviceSerial = prefs.getString("device_serial", null) ?: "",
                gsfId = prefs.getString("gsf_id", null) ?: "",
                imei = prefs.getString("imei", null) ?: "",
                macAddress = prefs.getString("mac_address", null) ?: "",
                screenWidth = prefs.getInt("screen_width", 1080),
                screenHeight = prefs.getInt("screen_height", 2400),
                dpi = prefs.getInt("dpi", 420),
                sdkVersion = prefs.getInt("sdk_version", 34),
                bootloader = prefs.getString("bootloader", null) ?: "unknown",
                radioVersion = prefs.getString("radio_version", null) ?: "",
                kernelVersion = prefs.getString("kernel_version", null) ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device identity", e)
            generateFreshIdentity(workspaceId)
        }
    }

    fun resetDeviceIdentity(workspaceId: Long) {
        try {
            val prefs = context.getSharedPreferences("blackbox_device_$workspaceId", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.i(TAG, "Device identity reset for workspace $workspaceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset device identity", e)
        }
    }

    fun resetGsfLicense(workspaceId: Long) {
        try {
            val prefs = context.getSharedPreferences("blackbox_device_$workspaceId", Context.MODE_PRIVATE)
            prefs.edit().remove("android_id").remove("gsf_id").apply()
            Log.i(TAG, "GSF license reset for workspace $workspaceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset GSF license", e)
        }
    }

    fun setCustomGsfLicense(workspaceId: Long, customGsfId: String) {
        try {
            val prefs = context.getSharedPreferences("blackbox_device_$workspaceId", Context.MODE_PRIVATE)
            prefs.edit().putString("gsf_id", customGsfId).apply()
            Log.i(TAG, "Custom GSF license set for workspace $workspaceId: $customGsfId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom GSF license", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // IDENTITY GENERATION (Consistent brand/model pairs)
    // ═══════════════════════════════════════════════════════════════════

    private val secureRandom = SecureRandom()

    private data class DeviceProfile(
        val brand: String, val model: String, val device: String,
        val manufacturer: String, val product: String, val hardware: String,
        val bootloader: String, val radio: String
    )

    private val deviceProfiles = listOf(
        DeviceProfile("google", "Pixel 8", "shiba", "Google", "shiba", "gs201", "shiba-14.0-10812745", ""),
        DeviceProfile("google", "Pixel 8 Pro", "husky", "Google", "husky", "gs201", "husky-14.0-10812745", ""),
        DeviceProfile("google", "Pixel 7", "panther", "Google", "panther", "gs201", "panther-14.0-10812745", ""),
        DeviceProfile("samsung", "SM-S926B", "e3q", "samsung", "e3q", "s5e9945", "S926BXXS4AXE5", ""),
        DeviceProfile("samsung", "SM-S928B", "e3q", "samsung", "e3q", "s5e9945", "S928BXXS4AXE5", ""),
        DeviceProfile("samsung", "SM-S911B", "dm1q", "samsung", "dm1q", "s5e8835", "S911BXXS5AXE1", ""),
        DeviceProfile("OnePlus", "CPH2583", "manet", "OnePlus", "manet", "k6985v1_64", "DEVIL.240516.001", ""),
        DeviceProfile("Xiaomi", "23116PN5BC", "sheng", "Xiaomi", "sheng", "k6989v1_64", "V816.0.24.0.UNACNXM", ""),
        DeviceProfile("OPPO", "PJZ110", "mondrian", "OPPO", "mondrian", "k6989v1_64", "PJZ110_14.0.90.140(EX01V110P02)", ""),
        DeviceProfile("vivo", "V2324A", "manis", "vivo", "manis", "k6989v1_64", "PD2324A_A_14.0.9.0.W30.V0100L", ""),
        DeviceProfile("Nothing", "A065", "pong", "Nothing", "pong", "mt6895", "Nothing_A065_202405141300", ""),
        DeviceProfile("motorola", "XT2401-1", "rothko", "motorola", "rothko", "k6985v1_64", "U1TDGS.37-12-28-6", ""),
        DeviceProfile("Sony", "XQ-EC72", "nuvia", "Sony", "nuia", "k6985v1_64", "66.1.A.12.120", ""),
        DeviceProfile("google", "Pixel Fold", "felix", "Google", "felix", "gs201", "felix-14.0-10812745", ""),
        DeviceProfile("google", "Pixel 9", "caiman", "Google", "caiman", "gs201", "caiman-15.0-11214666", "")
    )

    private fun generateAndStoreIdentity(workspaceId: Long, prefs: android.content.SharedPreferences): String {
        val identity = generateFreshIdentity(workspaceId)
        prefs.edit().apply {
            putString("android_id", identity.androidId)
            putString("device_model", identity.deviceModel)
            putString("device_brand", identity.deviceBrand)
            putString("device_fingerprint", identity.deviceFingerprint)
            putString("device_serial", identity.deviceSerial)
            putString("gsf_id", identity.gsfId)
            putString("imei", identity.imei)
            putString("mac_address", identity.macAddress)
            putInt("screen_width", identity.screenWidth)
            putInt("screen_height", identity.screenHeight)
            putInt("dpi", identity.dpi)
            putInt("sdk_version", identity.sdkVersion)
            putString("bootloader", identity.bootloader)
            putString("radio_version", identity.radioVersion)
            putString("kernel_version", identity.kernelVersion)
            apply()
        }
        return identity.androidId
    }

    private fun generateFreshIdentity(workspaceId: Long): DeviceIdentity {
        val profile = deviceProfiles[(workspaceId and Long.MAX_VALUE).toInt() % deviceProfiles.size]
        val salt = secureRandom.nextLong()
        val androidIdBytes = ByteArray(8)
        secureRandom.nextBytes(androidIdBytes)
        val androidId = androidIdBytes.joinToString("") { "%02x".format(it) }

        val serialBytes = ByteArray(8)
        secureRandom.nextBytes(serialBytes)
        val serial = "RF${serialBytes.joinToString("") { "%02X".format(it) }}".take(16)

        val imeiPrefix = "35"
        val imeiMid = String.format("%010d", (workspaceId * 100000 + (salt and 0xFFFFF)))
        val imeiRaw = "$imeiPrefix$imeiMid"
        val imeiCheck = calculateLuhnCheckDigit(imeiRaw)
        val imei = "$imeiMid$imeiCheck".take(15)

        val macBytes = ByteArray(6)
        secureRandom.nextBytes(macBytes)
        macBytes[0] = (macBytes[0].toInt() and 0xFE).toByte() // Locally administered, unicast
        val mac = macBytes.joinToString(":") { "%02X".format(it) }

        val gsfBytes = ByteArray(8)
        secureRandom.nextBytes(gsfBytes)
        val gsfId = gsfBytes.joinToString("") { "%02X".format(it) }.take(16)

        val androidVersions = listOf(34, 33, 35)
        val sdkVersion = androidVersions[(workspaceId and Long.MAX_VALUE).toInt() % androidVersions.size]
        val androidVer = when (sdkVersion) {
            35 -> "15"
            34 -> "14"
            33 -> "13"
            else -> "14"
        }

        val buildId = "BP${String.format("%011d", salt and 0xFFFFFFFFFF)}"
        val fingerprint = "${profile.brand}/${profile.device}/${profile.device}:${androidVer}/$buildId:user/release-keys"

        return DeviceIdentity(
            androidId = androidId,
            deviceModel = profile.model,
            deviceBrand = profile.brand,
            deviceFingerprint = fingerprint,
            deviceSerial = serial,
            gsfId = gsfId,
            imei = imei,
            macAddress = mac,
            screenWidth = listOf(1080, 1440, 1200).random(),
            screenHeight = listOf(2400, 3120, 2688).random(),
            dpi = listOf(420, 480, 560).random(),
            sdkVersion = sdkVersion,
            bootloader = profile.bootloader,
            radioVersion = profile.radio,
            kernelVersion = "5.${(10..15).random()}.${(0..99).random()}-android${androidVer}"
        )
    }

    private fun calculateLuhnCheckDigit(digits: String): Int {
        var sum = 0
        var alternate = true
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (alternate) { n *= 2; if (n > 9) n -= 9 }
            sum += n
            alternate = !alternate
        }
        return (10 - (sum % 10)) % 10
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private fun ensureServicesReady() {
        try {
            if (!BlackBoxCore.get().areServicesAvailable()) {
                BlackBoxCore.get().waitForServicesAvailable(5000)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Service readiness check failed", e)
        }
    }

    private fun ensureInstalled(packageName: String, userId: Int, workspaceId: Long): Boolean {
        return try {
            if (!BlackBoxCore.get().isInstalled(packageName, userId)) {
                Log.w(TAG, "$packageName not installed, re-installing...")
                val result = BlackBoxCore.get().installPackageAsUser(packageName, userId)
                if (!result.success) {
                    Log.e(TAG, "Re-install failed: ${result.msg}")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Install verification failed", e)
            true // Proceed anyway
        }
    }

    fun workspaceIdToUserId(workspaceId: Long): Int {
        val safeId = (workspaceId and Long.MAX_VALUE).let { if (it > Int.MAX_VALUE.toLong()) it % 10000 else it }
        return USER_ID_OFFSET + safeId.toInt()
    }

    fun getAntiDetectionManager(): AntiDetectionManager = antiDetectionManager
    fun runDetectionAudit(): Map<String, String> = antiDetectionManager.runFullDetectionAudit()
    fun getSecurityScore(): Int = antiDetectionManager.getSecurityScore()
}
