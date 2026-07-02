package com.utility.toolbox.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.pm.InstallResult
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each clone gets its own BlackBox userId with unique spoofed identity.
 * No workspace concept — every clone is a standalone virtual device.
 */
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
                Holder.instance ?: BlackBoxEngine(appContext, AntiDetectionManager.getInstance(appContext))
                    .also { Holder.instance = it }
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

    fun isInitialized(): Boolean = frameworkInitialized
    fun isEngineStarted(): Boolean = engineStarted

    fun onCreate() {
        if (!frameworkInitialized || engineStarted) return
        try {
            BlackBoxCore.get().doCreate()
            antiDetectionManager.initialize()
            engineStarted = true
            Log.i(TAG, "BlackBox engine started")
        } catch (e: Exception) { Log.e(TAG, "Engine start failed", e) }
    }

    // ── Install ──────────────────────────────────────────────────────

    fun installClone(packageName: String, userId: Int): CloneResult {
        if (!frameworkInitialized || !engineStarted) return CloneResult(false, null, "Engine not ready")
        ensureServicesReady()
        try {
            if (BlackBoxCore.get().isInstalled(packageName, userId)) {
                LogManager.i("BlackBox", "Already installed: $packageName (user=$userId)")
                return CloneResult(true, packageName)
            }
        } catch (_: Exception) {}
        return try {
            val result: InstallResult = BlackBoxCore.get().installPackageAsUser(packageName, userId)
            if (result.success) {
                LogManager.i("BlackBox", "✓ Installed $packageName (user=$userId)")
                CloneResult(true, result.packageName ?: packageName)
            } else {
                LogManager.e("BlackBox", "✗ Install failed: ${result.msg}")
                CloneResult(false, null, result.msg)
            }
        } catch (e: Exception) {
            LogManager.e("BlackBox", "✗ Install exception: ${e.message}")
            CloneResult(false, null, e.message)
        }
    }

    fun installCloneFromFile(apkFile: java.io.File, userId: Int): CloneResult {
        if (!frameworkInitialized) return CloneResult(false, null, "Engine not ready")
        if (!apkFile.exists()) return CloneResult(false, null, "APK not found")
        ensureServicesReady()
        return try {
            val result = BlackBoxCore.get().installPackageAsUser(apkFile, userId)
            if (result.success) CloneResult(true, result.packageName) else CloneResult(false, null, result.msg)
        } catch (e: Exception) { CloneResult(false, null, e.message) }
    }

    // ── Launch ───────────────────────────────────────────────────────

    fun launchClone(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized || !engineStarted) { LogManager.e("BlackBox", "Cannot launch $clonePackage — engine not ready"); return false }
        ensureServicesReady()

        LogManager.i("BlackBox", "━━━ Launching $clonePackage (user=$userId) ━━━")

        // Pre-flight checks
        val installed = try { BlackBoxCore.get().isInstalled(clonePackage, userId) } catch (_: Exception) { false }
        LogManager.i("BlackBox", "  isInstalled=$installed, services=${try { BlackBoxCore.get().areServicesAvailable() } catch (_: Exception) { false }}, allFiles=${try { BlackBoxCore.get().hasAllFilesAccess() } catch (_: Exception) { false }}")
        if (!installed) {
            val r = try { BlackBoxCore.get().installPackageAsUser(clonePackage, userId) } catch (_: Exception) { null }
            if (r == null || !r.success) { LogManager.e("BlackBox", "  ✗ Not installed and reinstall failed"); return false }
        }

        // Step 1: Try BlackBox launchApk (goes through proxy)
        try { BlackBoxCore.get().onBeforeMainLaunchApk(clonePackage, userId) } catch (_: Exception) {}
        val launchResult = try { BlackBoxCore.get().launchApk(clonePackage, userId) } catch (_: Exception) { false }
        LogManager.i("BlackBox", "  launchApk=$launchResult")

        // Check if proxy actually started the activity
        Thread.sleep(1500)
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val topActivity = am.getRunningTasks(1)?.firstOrNull()?.topActivity?.flattenToString() ?: ""
        val proxyWorking = topActivity.contains(clonePackage) || topActivity.contains("ProxyActivity")
        LogManager.i("BlackBox", "  Top activity: $topActivity, proxyWorking=$proxyWorking")

        if (proxyWorking) {
            LogManager.i("BlackBox", "  ✓ Proxy launched successfully")
            LogManager.i("BlackBox", "━━━ Complete ━━━")
            return true
        }

        // Step 2: Try using BlackBox's startActivity with proper intent
        LogManager.w("BlackBox", "  Proxy didn't work, trying direct BlackBox startActivity")
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(clonePackage)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                BlackBoxCore.get().startActivity(intent, userId)
                LogManager.i("BlackBox", "  ✓ BlackBox.startActivity sent")

                Thread.sleep(1500)
                val topAfter = am.getRunningTasks(1)?.firstOrNull()?.topActivity?.flattenToString() ?: ""
                LogManager.i("BlackBox", "  Top after startActivity: $topAfter")

                if (topAfter.contains(clonePackage)) {
                    LogManager.i("BlackBox", "  ✓ BlackBox.startActivity worked!")
                    LogManager.i("BlackBox", "━━━ Complete ━━━")
                    return true
                }
            }
        } catch (e: Exception) {
            LogManager.w("BlackBox", "  BlackBox.startActivity failed: ${e.message}")
        }

        // Step 3: Try launching through ProxyPendingActivity (delayed intent)
        LogManager.w("BlackBox", "  Trying ProxyPendingActivity")
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(clonePackage)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                val pendingClass = Class.forName("top.niunaijun.blackbox.proxy.ProxyPendingActivity${'$'}P0")
                val pendingIntent = android.content.Intent(context, pendingClass).apply {
                    putExtra("_B_|_user_id_", userId)
                    putExtra("_B_|_target_", intent)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(pendingIntent)
                LogManager.i("BlackBox", "  ✓ ProxyPendingActivity intent sent")

                Thread.sleep(1500)
                val topAfter = am.getRunningTasks(1)?.firstOrNull()?.topActivity?.flattenToString() ?: ""
                LogManager.i("BlackBox", "  Top after pending: $topAfter")

                if (topAfter.contains(clonePackage)) {
                    LogManager.i("BlackBox", "  ✓ ProxyPendingActivity worked!")
                    LogManager.i("BlackBox", "━━━ Complete ━━━")
                    return true
                }
            }
        } catch (e: Exception) {
            LogManager.w("BlackBox", "  ProxyPendingActivity failed: ${e.message}")
        }

        // Step 4: Last resort — launch real app (no isolation)
        LogManager.w("BlackBox", "  All proxy methods failed — launching real app (no isolation)")
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(clonePackage)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                LogManager.i("BlackBox", "  ✓ Started real $clonePackage")
            }
        } catch (e: Exception) {
            LogManager.e("BlackBox", "  ✗ All methods failed: ${e.message}")
        }

        LogManager.i("BlackBox", "━━━ Complete ━━━")
        return true
    }

    // ── Management ───────────────────────────────────────────────────

    fun uninstallClone(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try {
            try { BlackBoxCore.get().clearPackage(clonePackage, userId) } catch (_: Exception) {}
            BlackBoxCore.get().uninstallPackageAsUser(clonePackage, userId)
            LogManager.i("BlackBox", "✓ Uninstalled $clonePackage (user=$userId)"); true
        } catch (e: Exception) { LogManager.e("BlackBox", "✗ Uninstall failed: ${e.message}"); false }
    }

    fun stopClone(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try {
            BlackBoxCore.get().stopPackage(clonePackage, userId)
            LogManager.i("BlackBox", "✓ Stopped $clonePackage (user=$userId)"); true
        } catch (e: Exception) {
            LogManager.e("BlackBox", "✗ Stop failed: ${e.message}"); false
        }
    }

    fun killCloneProcess(clonePackage: String) {
        try {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(clonePackage)
            LogManager.i("BlackBox", "✓ Killed process for $clonePackage")
        } catch (e: Exception) {
            LogManager.w("BlackBox", "Failed to kill process: ${e.message}")
        }
    }

    fun killAllCloneProcesses() {
        try {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.forEach { proc ->
                if (proc.processName.contains(":p") || proc.processName.contains(":black")) {
                    am.killBackgroundProcesses(proc.processName)
                    LogManager.i("BlackBox", "Killed process: ${proc.processName}")
                }
            }
        } catch (e: Exception) {
            LogManager.w("BlackBox", "Failed to kill all processes: ${e.message}")
        }
    }

    fun clearCloneData(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try {
            BlackBoxCore.get().clearPackage(clonePackage, userId)
            LogManager.i("BlackBox", "✓ Cleared data for $clonePackage (user=$userId)"); true
        } catch (e: Exception) { LogManager.e("BlackBox", "✗ Clear failed: ${e.message}"); false }
    }

    fun isCloneInstalled(clonePackage: String, userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().isInstalled(clonePackage, userId) } catch (_: Exception) { false }
    }

    // ── GMS Per Clone ────────────────────────────────────────────────

    fun installGms(userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try {
            val ok = BlackBoxCore.get().installGms(userId).success
            LogManager.i("BlackBox", if (ok) "✓ GMS installed (user=$userId)" else "✗ GMS install failed"); ok
        } catch (e: Exception) { LogManager.e("BlackBox", "✗ GMS exception: ${e.message}"); false }
    }

    fun uninstallGms(userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try {
            val ok = BlackBoxCore.get().uninstallGms(userId)
            LogManager.i("BlackBox", if (ok) "✓ GMS removed (user=$userId)" else "✗ GMS remove failed"); ok
        } catch (e: Exception) { LogManager.e("BlackBox", "✗ GMS exception: ${e.message}"); false }
    }

    fun isGmsInstalled(userId: Int): Boolean {
        if (!frameworkInitialized) return false
        return try { BlackBoxCore.get().isInstallGms(userId) } catch (_: Exception) { false }
    }

    fun isGmsSupported(): Boolean = try { BlackBoxCore.get().isSupportGms() } catch (_: Exception) { false }

    // ── User Management ──────────────────────────────────────────────

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

    // ── Identity Generation ──────────────────────────────────────────

    fun generateIdentity(userId: Int): DeviceIdentity {
        val secureRandom = SecureRandom()
        val profile = deviceProfiles[userId % deviceProfiles.size]

        val androidIdBytes = ByteArray(8).also { secureRandom.nextBytes(it) }
        val androidId = androidIdBytes.joinToString("") { "%02x".format(it) }

        val serialBytes = ByteArray(8).also { secureRandom.nextBytes(it) }
        val serial = "RF${serialBytes.joinToString("") { "%02X".format(it) }}".take(16)

        val imeiMid = String.format("%010d", (userId * 100000L + (secureRandom.nextLong() and 0xFFFFF)))
        val imeiRaw = "35$imeiMid"
        val imeiCheck = calculateLuhnCheckDigit(imeiRaw)
        val imei = "$imeiMid$imeiCheck".take(15)

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
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (alt) { n *= 2; if (n > 9) n -= 9 }
            sum += n; alt = !alt
        }
        return (10 - (sum % 10)) % 10
    }

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

    // ── Helpers ──────────────────────────────────────────────────────

    private fun ensureServicesReady() {
        try { if (!BlackBoxCore.get().areServicesAvailable()) BlackBoxCore.get().waitForServicesAvailable(5000) } catch (_: Exception) {}
    }

    fun getAntiDetectionManager(): AntiDetectionManager = antiDetectionManager
    fun runDetectionAudit(): Map<String, String> = antiDetectionManager.runFullDetectionAudit()
    fun getSecurityScore(): Int = antiDetectionManager.getSecurityScore()
}
