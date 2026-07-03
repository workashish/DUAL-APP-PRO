package com.utility.toolbox.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.pm.InstallResult
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlackBoxEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val antiDetectionManager: AntiDetectionManager
) {
    companion object {
        private const val TAG = "BlackBoxEngine"
        private const val USER_ID_OFFSET = 1000
        private const val MAX_LAUNCH_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 300L

        @Volatile private var frameworkInitialized = false
        @Volatile private var engineStarted = false

        fun markFrameworkInitialized() { frameworkInitialized = true }
        fun isFrameworkInitialized(): Boolean = frameworkInitialized

        fun getInstance(context: Context): BlackBoxEngine {
            val appContext = context.applicationContext ?: context
            return Holder.instance ?: synchronized(this) {
                Holder.instance ?: BlackBoxEngine(appContext, AntiDetectionManager.getInstance(appContext)).also { Holder.instance = it }
            }
        }
        internal fun resetInstanceForTest() { Holder.instance = null }
        private object Holder { @Volatile var instance: BlackBoxEngine? = null }
    }

    data class DeviceIdentity(
        val androidId: String, val deviceModel: String, val deviceBrand: String,
        val deviceFingerprint: String, val deviceSerial: String, val gsfId: String,
        val imei: String, val macAddress: String
    )
    data class CloneResult(val success: Boolean, val packageName: String?, val error: String? = null)
    data class InstalledAppInfo(val packageName: String, val versionName: String, val versionCode: Long, val isSystemApp: Boolean)

    fun isInitialized(): Boolean = frameworkInitialized
    fun isEngineStarted(): Boolean = engineStarted

    fun onCreate() {
        if (!frameworkInitialized || engineStarted) return
        try {
            BlackBoxCore.get().doCreate()
            antiDetectionManager.initialize()
            engineStarted = true
            Log.i(TAG, "Engine started")
        } catch (e: Exception) { Log.e(TAG, "Engine start failed", e) }
    }

    fun installClone(packageName: String, userId: Int): CloneResult {
        if (!frameworkInitialized || !engineStarted) return CloneResult(false, null, "Engine not ready")
        ensureServicesReady()
        try {
            if (BlackBoxCore.get().isInstalled(packageName, userId)) return CloneResult(true, packageName)
        } catch (_: Exception) {}
        return try {
            val r: InstallResult = BlackBoxCore.get().installPackageAsUser(packageName, userId)
            if (r.success) CloneResult(true, r.packageName ?: packageName) else CloneResult(false, null, r.msg)
        } catch (e: Exception) { CloneResult(false, null, e.message) }
    }

    fun installCloneFromFile(apkFile: java.io.File, userId: Int): CloneResult {
        if (!frameworkInitialized) return CloneResult(false, null, "Engine not ready")
        if (!apkFile.exists()) return CloneResult(false, null, "APK not found")
        ensureServicesReady()
        return try {
            val r = BlackBoxCore.get().installPackageAsUser(apkFile, userId)
            if (r.success) CloneResult(true, r.packageName) else CloneResult(false, null, r.msg)
        } catch (e: Exception) { CloneResult(false, null, e.message) }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAUNCH — 4 strategies, tried in order until one works
    // ═══════════════════════════════════════════════════════════════════

    fun launchClone(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized || !engineStarted) { LogManager.e("BlackBox", "Engine not ready"); return false }
        ensureServicesReady()
        LogManager.i("BlackBox", "━━━ Launch $clonePackage (user=$userId) ━━━")

        // Verify installed
        val installed = try { BlackBoxCore.get().isInstalled(clonePackage, userId) } catch (_: Exception) { false }
        if (!installed) {
            val r = try { BlackBoxCore.get().installPackageAsUser(clonePackage, userId) } catch (_: Exception) { null }
            if (r == null || !r.success) { LogManager.e("BlackBox", "Not installed, reinstall failed"); return false }
        }

        // Strategy 1: BlackBox's internal launchApk (goes through proxy)
        if (launchViaBlackBoxInternal(clonePackage, userId)) return true

        // Strategy 2: Direct shadow intent with proper extras
        if (launchViaShadowIntent(clonePackage, userId)) return true

        // NO REAL APP FALLBACK — show error instead
        LogManager.e("BlackBox", "All proxy methods failed — clone cannot open in virtual environment")
        LogManager.i("BlackBox", "━━━ Complete (failed) ━━━")
        return false
    }

    /**
     * Strategy 1: Use BlackBox's internal launchApk which goes through the proxy system.
     */
    private fun launchViaBlackBoxInternal(clonePackage: String, userId: Int): Boolean {
        try {
            BlackBoxCore.get().onBeforeMainLaunchApk(clonePackage, userId)
            val result = BlackBoxCore.get().launchApk(clonePackage, userId)
            LogManager.i("BlackBox", "[internal] launchApk=$result")

            Thread.sleep(2000)
            val top = getTopActivity()
            val working = top.contains(clonePackage) || top.contains("ProxyActivity")
            LogManager.i("BlackBox", "[internal] Top=$top, working=$working")
            if (working) {
                LogManager.i("BlackBox", "✓ Launched via BlackBox internal")
                LogManager.i("BlackBox", "━━━ Complete ━━━")
                return true
            }
        } catch (e: Exception) {
            LogManager.w("BlackBox", "[internal] Failed: ${e.message}")
        }
        return false
    }

    /**
     * Strategy 3: Create shadow intent with all required extras and start ProxyActivity directly.
     */
    private fun launchViaShadowIntent(clonePackage: String, userId: Int): Boolean {
        try {
            val launchIntent = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(clonePackage, userId)
                ?: context.packageManager.getLaunchIntentForPackage(clonePackage)
            if (launchIntent == null) { LogManager.w("BlackBox", "[shadow] No launch intent"); return false }

            val targetComponent = launchIntent.component
            if (targetComponent == null) { LogManager.w("BlackBox", "[shadow] No component"); return false }

            // Get ActivityInfo through virtual PM
            val activityInfo = try {
                BlackBoxCore.getBPackageManager().getActivityInfo(targetComponent, 0, userId)
            } catch (_: Exception) {
                try { context.packageManager.getActivityInfo(targetComponent, 0) } catch (_: Exception) { null }
            }
            if (activityInfo == null) { LogManager.w("BlackBox", "[shadow] No ActivityInfo for $targetComponent"); return false }

            // Create shadow intent with proper extras matching ProxyActivityRecord format
            val proxyClass = Class.forName("top.niunaijun.blackbox.proxy.ProxyActivity\$P0")
            val shadow = Intent(context, proxyClass).apply {
                putExtra("_B_|_activity_info_", activityInfo as android.os.Parcelable)
                putExtra("_B_|_target_", launchIntent as android.os.Parcelable)
                putExtra("_B_|_user_id_", userId)
                // activityRecord binder is optional — ProxyActivityRecord.create() handles null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            LogManager.i("BlackBox", "[shadow] Starting ProxyActivity → ${targetComponent.flattenToString()}")
            context.startActivity(shadow)

            Thread.sleep(2000)
            val top = getTopActivity()
            val working = top.contains(clonePackage) || top.contains("ProxyActivity")
            LogManager.i("BlackBox", "[shadow] Top=$top, working=$working")
            if (working) {
                LogManager.i("BlackBox", "✓ Launched via shadow intent")
                LogManager.i("BlackBox", "━━━ Complete ━━━")
                return true
            }
        } catch (e: Exception) {
            LogManager.w("BlackBox", "[shadow] Failed: ${e.message}")
        }
        return false
    }

    private fun getTopActivity(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.getRunningTasks(1)?.firstOrNull()?.topActivity?.flattenToString() ?: ""
    }

    // ═══════════════════════════════════════════════════════════════════
    // MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    fun uninstallClone(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try {
            try { BlackBoxCore.get().clearPackage(clonePackage, userId) } catch (_: Exception) {}
            BlackBoxCore.get().uninstallPackageAsUser(clonePackage, userId); true
        } catch (e: Exception) { LogManager.e("BlackBox", "Uninstall failed: ${e.message}"); false }
    }

    fun stopClone(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().stopPackage(clonePackage, userId); true } catch (_: Exception) { false }
    }

    fun clearCloneData(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().clearPackage(clonePackage, userId); true } catch (_: Exception) { false }
    }

    fun isCloneInstalled(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().isInstalled(clonePackage, userId) } catch (_: Exception) { false }
    }

    fun installGms(userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().installGms(userId).success } catch (_: Exception) { false }
    }

    fun uninstallGms(userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().uninstallGms(userId) } catch (_: Exception) { false }
    }

    fun isGmsInstalled(userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().isInstallGms(userId) } catch (_: Exception) { false }
    }

    fun isGmsSupported(): Boolean = try { BlackBoxCore.get().isSupportGms() } catch (_: Exception) { false }

    fun createUser(userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().createUser(userId); true } catch (_: Exception) { false }
    }

    fun deleteUser(userId: Int) {
        if (!frameworkInitialized) return
        try { BlackBoxCore.get().deleteUser(userId) } catch (_: Exception) {}
    }

    fun nextAvailableUserId(): Int {
        val existing = try { BlackBoxCore.get().users.map { it.id } } catch (_: Exception) { emptyList() }
        var id = USER_ID_OFFSET + 1
        while (id in existing) id++
        return id
    }

    // ═══════════════════════════════════════════════════════════════════
    // IDENTITY GENERATION
    // ═══════════════════════════════════════════════════════════════════

    private val secureRandom = SecureRandom()

    private data class DeviceProfile(val brand: String, val model: String, val device: String, val hardware: String, val bootloader: String)

    private val deviceProfiles = listOf(
        DeviceProfile("google", "Pixel 8", "shiba", "gs201", "shiba-14.0-10812745"),
        DeviceProfile("google", "Pixel 8 Pro", "husky", "gs201", "husky-14.0-10812745"),
        DeviceProfile("google", "Pixel 7", "panther", "gs201", "panther-14.0-10812745"),
        DeviceProfile("samsung", "SM-S926B", "e3q", "s5e9945", "S926BXXS4AXE5"),
        DeviceProfile("samsung", "SM-S928B", "e3q", "s5e9945", "S928BXXS4AXE5"),
        DeviceProfile("samsung", "SM-S911B", "dm1q", "s5e8835", "S911BXXS5AXE1"),
        DeviceProfile("OnePlus", "CPH2583", "manet", "k6985v1_64", "DEVIL.240516.001"),
        DeviceProfile("Xiaomi", "23116PN5BC", "sheng", "k6989v1_64", "V816.0.24.0.UNACNXM"),
        DeviceProfile("OPPO", "PJZ110", "mondrian", "k6989v1_64", "PJZ110_14.0.90.140"),
        DeviceProfile("vivo", "V2324A", "manis", "k6989v1_64", "PD2324A_A_14.0.9.0"),
        DeviceProfile("Nothing", "A065", "pong", "mt6895", "Nothing_A065_202405141300"),
        DeviceProfile("motorola", "XT2401-1", "rothko", "k6985v1_64", "U1TDGS.37-12-28-6"),
        DeviceProfile("Sony", "XQ-EC72", "nuvia", "k6985v1_64", "66.1.A.12.120"),
        DeviceProfile("google", "Pixel Fold", "felix", "gs201", "felix-14.0-10812745"),
        DeviceProfile("google", "Pixel 9", "caiman", "gs201", "caiman-15.0-11214666")
    )

    fun generateIdentity(userId: Int): DeviceIdentity {
        val profile = deviceProfiles[userId % deviceProfiles.size]
        val androidIdBytes = ByteArray(8).also { secureRandom.nextBytes(it) }
        val androidId = androidIdBytes.joinToString("") { "%02x".format(it) }
        val serialBytes = ByteArray(8).also { secureRandom.nextBytes(it) }
        val serial = "RF${serialBytes.joinToString("") { "%02X".format(it) }}".take(16)
        val imeiMid = String.format("%010d", (userId * 100000L + (secureRandom.nextLong() and 0xFFFFF)))
        val imeiRaw = "35$imeiMid"
        val imei = "$imeiMid${calculateLuhnCheckDigit(imeiRaw)}".take(15)
        val macBytes = ByteArray(6).also { secureRandom.nextBytes(it) }
        macBytes[0] = (macBytes[0].toInt() and 0xFE).toByte()
        val mac = macBytes.joinToString(":") { "%02X".format(it) }
        val gsfBytes = ByteArray(8).also { secureRandom.nextBytes(it) }
        val gsfId = gsfBytes.joinToString("") { "%02X".format(it) }.take(16)
        val sdkVersion = listOf(34, 33, 35).random()
        val androidVer = when (sdkVersion) { 35 -> "15"; 33 -> "13"; else -> "14" }
        val buildId = "BP${String.format("%011d", secureRandom.nextLong() and 0xFFFFFFFFFF)}"
        val fingerprint = "${profile.brand}/${profile.device}/${profile.device}:${androidVer}/$buildId:user/release-keys"
        return DeviceIdentity(androidId, profile.model, profile.brand, fingerprint, serial, gsfId, imei, mac)
    }

    private fun calculateLuhnCheckDigit(digits: String): Int {
        var sum = 0; var alt = true
        for (i in digits.indices.reversed()) { var n = digits[i] - '0'; if (alt) { n *= 2; if (n > 9) n -= 9 }; sum += n; alt = !alt }
        return (10 - (sum % 10)) % 10
    }

    private fun ensureServicesReady() {
        try { if (!BlackBoxCore.get().areServicesAvailable()) BlackBoxCore.get().waitForServicesAvailable(5000) } catch (_: Exception) {}
    }

    fun getAntiDetectionManager(): AntiDetectionManager = antiDetectionManager
    fun runDetectionAudit(): Map<String, String> = antiDetectionManager.runFullDetectionAudit()
    fun getSecurityScore(): Int = antiDetectionManager.getSecurityScore()

    fun killAllCloneProcesses() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.forEach { proc ->
                if (proc.processName.contains(":p") || proc.processName.contains(":black")) {
                    am.killBackgroundProcesses(proc.processName)
                    LogManager.i("BlackBox", "Killed process: ${proc.processName}")
                }
            }
        } catch (e: Exception) { LogManager.w("BlackBox", "Failed to kill all processes: ${e.message}") }
    }
}
