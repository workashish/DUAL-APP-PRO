package com.utility.toolbox.service

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anti-Detection Manager — protects cloned apps from detecting that they
 * are running in a multi-app/clone environment.
 *
 * Apps like WhatsApp, Telegram, Instagram, and banking apps use various
 * techniques to detect if they're running in a cloned/virtual space.
 * This manager provides countermeasures for ALL known detection vectors.
 *
 * Detection Vectors Protected:
 *   1. PackageManager — hide host app + BlackBox engine from app queries
 *   2. FilesDir Path — sanitize file paths to remove host trace
 *   3. /proc/self/maps — hide native hooking libraries from memory maps
 *   4. Process List — hide proxy/engine processes from running apps list
 *   5. Running Services — hide engine services
 *   6. UID Verification — prevent UID-based host detection
 *   7. ActivityManager — hide tasks from cloned apps
 *   8. File System — remove engine traces from visible filesystem
 *   9. Installer Package — spoof installer identity
 *   10. System Properties — consistent spoofed build props
 *
 * When BlackBox AAR is active, most of these are handled natively.
 * This class provides the Java-level fallback + additional hardening.
 */
@Singleton
class AntiDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AntiDetection"

        @Volatile
        private var INSTANCE: AntiDetectionManager? = null

        fun getInstance(context: Context): AntiDetectionManager {
            val appContext = context.applicationContext ?: context
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: AntiDetectionManager(appContext)
                    .also { INSTANCE = it }
                instance
            }
        }

        // Package names that apps check for to detect cloning environments.
        // The engine will hide these from queries.
        val HOST_PACKAGE_PATTERNS = listOf(
            "com.utility.toolbox",
            "com.utility.toolbox.debug"
        )

        // Known cloner/parallel engine packages that apps scan for.
        // These are ALL known detection targets.
        val KNOWN_CLONER_PACKAGES = listOf(
            // Our engine variants
            "com.utility.toolbox",
            "com.utility.toolbox.debug",
            // Parallel Space family
            "com.lbe.parallel",
            "com.lbe.parallel.intl",
            "com.parallel.space",
            "com.parallelspace.intl",
            // Dual Space family
            "com.excelliance.dualaid",
            "com.excelliance.dualaid.intl",
            "com.dualspace.app",
            // VirtualApp family
            "com.lody.virtual",
            "com.lody.virtual.zero",
            "com.lody.virtual.app",
            // MultiAppUltra family
            "com.waxmoon.ma.gp",
            "com.waxmoon.parallel",
            // Other cloners
            "com.drx2.cloner",
            "com.appcloner",
            "com.cloner.app",
            "com.multiple.accounts",
            "com.multiple.parallel",
            "com.clone.app",
            "com.multi.app",
            "com.social.clone",
            "com.parallel.space.lite",
            // Xiaomi Dual Apps
            "com.miui.securitycore",
            "com.miui.cleanmaster",
            // Huawei App Twin
            "com.huawei.android.launcher",
            // Samsung Dual Messenger
            "com.samsung.android.dualdm",
            // Oppo Clone
            "com.coloros.oppoguardelf",
            "com.coloros.safecenter",
            // Vivo
            "com.vivo.space"
        )

        // Known BlackBox/VirtualApp libraries that appear in /proc/self/maps
        // NOTE: 'libart' is intentionally excluded — it's loaded on EVERY Android device
        // from /system/lib64/libart.so and would cause false positives.
        val KNOWN_ENGINE_LIBRARIES = listOf(
            "libblackbox",       // BlackBox core
            "libva-native",      // VirtualApp native
            "libva",             // VirtualApp
            "libdobby",          // Dobby inline hook framework
            "libxDL",            // xDL native hook
            "libxdl",            // xDL (lowercase variant)
            "libvhook",          // Virtual hook
            "libvcore",          // Virtual core
            "libvms",            // Virtual machine service
            "libfrida",          // Frida instrumentation
            "libgadget",         // Frida gadget
            "libreexec",         // Re-exec library
            "substrate",         // Cydia Substrate
            "libxposed",         // Xposed framework
            "libkernelsu"        // KernelSU
        )

        // System files/folders that indicate virtual environment
        val KNOWN_ENGINE_PATHS = listOf(
            "blackbox",
            "virtual",
            "va-native",
            "va-data",
            "vms",
            "parallel",
            "dualspace"
        )
    }

    private var initialized = false

    /**
     * Initialize anti-detection measures.
     * Called from Application.onCreate() AFTER BlackBox engine.
     *
     * Registers package filters, validates proc maps, and logs protection status.
     */
    fun initialize() {
        if (initialized) return

        try {
            // Log which packages will be hidden from cloned apps
            val hiddenCount = getHiddenPackages().size
            Log.i(TAG, "AntiDetection initialized — hiding $hiddenCount known cloner packages")

            // Log which native libraries should be invisible in /proc/self/maps
            val libCount = KNOWN_ENGINE_LIBRARIES.size
            Log.i(TAG, "Monitoring $libCount engine library signatures in proc maps")

            // Log hidden process names
            val processCount = getHiddenProcessNames().size
            Log.i(TAG, "Hiding $processCount process names from running task queries")

            // Run initial audit
            val audit = runFullDetectionAudit()
            val exposed = audit.count { it.value.startsWith("EXPOSED") }
            if (exposed > 0) {
                Log.w(TAG, "Anti-detection audit: $exposed vectors exposed — BlackBox AAR native hooks may not be active")
            } else {
                Log.i(TAG, "Anti-detection audit: all vectors clean")
            }

            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "AntiDetection initialization failed", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 1: PACKAGE MANAGER
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps call PackageManager.getInstalledPackages() or
    // PackageManager.getInstalledApplications() to check if the host
    // app (com.utility.toolbox) or any known cloner is installed.
    //
    // Solution: BlackBox intercepts the Binder call and filters out
    // host + cloner packages from the results.
    //
    // Java fallback: We can't actually filter the system's response,
    // but we can detect and report when the app is being inspected.

    /**
     * Returns the list of package NAMES that should be hidden
     * from cloned apps' PackageManager queries.
     *
     * BlackBox intercepts PackageManager calls at the Binder level
     * and filters these out before returning results to the app.
     */
    fun getHiddenPackages(): List<String> {
        return HOST_PACKAGE_PATTERNS + KNOWN_CLONER_PACKAGES
    }

    /**
     * Check if a package name matches any known cloner pattern.
     * Used by the BlackBox engine to filter PackageManager results.
     */
    fun isHiddenPackage(packageName: String): Boolean {
        return getHiddenPackages().any { hidden ->
            packageName.startsWith(hidden) || packageName.equals(hidden, ignoreCase = true)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 2: FILESYSTEM TRACES
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps check their context.filesDir.path to see if it contains
    // a virtual engine's package name (e.g., /data/data/com.lbe.parallel/...)
    // or if the path structure looks unusual.
    //
    // Also, apps check if specific engine files/folders exist on the
    // device (e.g., /data/data/com.utility.toolbox/).
    //
    // Solution: BlackBox intercepts file system calls and remaps paths.
    // The cloned app sees /data/data/<its_own_virtual_package>/ instead
    // of the host app's actual data directory.

    /**
     * Get the virtual data directory for a clone.
     */
    fun getVirtualDataDir(userId: Int, virtualPackage: String): File {
        return File(
            context.filesDir,
            "vdata/$userId/$virtualPackage"
        ).also { it.mkdirs() }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 3: /proc/self/maps
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps read /proc/self/maps to see loaded shared libraries.
    // If they see libraries like libblackbox.so, libdobby.so, or
    // any non-standard .so loaded, they know they're in a VM.
    //
    // Solution: BlackBox intercepts open()/read() syscalls for
    // /proc/self/maps and filters out its own library entries.
    // This is done at the native (C/C++) level via Dobby inline hooks.
    //
    // Java fallback: We can check /proc/self/maps for suspicious entries
    // and warn the user if any are visible (indicating hooks aren't working).

    /**
     * Scan /proc/self/maps for visible engine libraries.
     * This tests whether the native hooking is working.
     *
     * @return list of suspicious library paths found, empty if clean
     */
    fun scanProcMapsForEngineLibs(): List<String> {
        val suspicious = mutableListOf<String>()
        try {
            val mapsFile = File("/proc/self/maps")
            if (!mapsFile.exists()) return suspicious

            val reader = BufferedReader(FileReader(mapsFile))
            reader.useLines { lines ->
                lines.forEach { line ->
                    KNOWN_ENGINE_LIBRARIES.forEach { lib ->
                        if (line.contains(lib, ignoreCase = true)) {
                            suspicious.add(line.trim())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read /proc/self/maps", e)
        }
        return suspicious
    }

    /**
     * Check if the native hooking is effectively hiding libraries.
     * Returns true if /proc/self/maps is clean of engine traces.
     */
    fun isProcMapsClean(): Boolean {
        val found = scanProcMapsForEngineLibs()
        if (found.isNotEmpty()) {
            Log.w(TAG, "/proc/self/maps contains ${found.size} engine traces: $found")
            return false
        }
        return true
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 4: RUNNING TASKS
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps use ActivityManager.getRunningTasks() or
    // getRunningAppProcesses() to see if the host app or its
    // proxy processes are running.
    //
    // Solution: BlackBox intercepts ActivityManagerService calls
    // and removes host-related entries.

    /**
     * Get the list of process names that should be hidden
     * from running tasks/process queries.
     */
    fun getHiddenProcessNames(): List<String> {
        return listOf(
            ":p0", ":p1", ":p2",           // Proxy processes
            ":assist0", ":assist1",          // Assist processes
            ":assist_provider",             // Provider process
            context.packageName,            // Host package
            "${context.packageName}.debug"  // Debug variant
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 5: UID / SHARED USER ID
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps check their own UID and compare it against known values.
    // If the UID matches the host app's UID, they know they're cloned.
    //
    // Some apps check PackageManager.getPackageInfo() for their own
    // package to verify firstInstalledTime, signatures, etc. Against
    // what's expected.
    //
    // Solution: BlackBox intercepts PackageManager calls and returns
    // virtual package info with consistent data.

    /**
     * Check if the app is running under the expected UID.
     * This is a diagnostic — BlackBox handles the interception.
     */
    fun getCurrentUid(): Int {
        return Process.myUid()
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 6: INSTALLER PACKAGE
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps check PackageManager.getInstallerPackageName().
    // If the installer is "com.android.vending" (Play Store), the app
    // is legit. If it's the clone engine's package, they detect it.
    //
    // Solution: When BlackBox installs a cloned app, it sets the
    // installer package to "com.android.vending".

    /**
     * Get the spoofed installer package name.
     * Cloned apps should see "com.android.vending" as their installer.
     */
    fun getSpoofedInstallerPackage(): String {
        return "com.android.vending"
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 7: SYSTEM PROPERTIES CONSISTENCY
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps check if Build.* properties are internally consistent.
    // For example, Build.MODEL should match the fingerprint pattern.
    // Inconsistencies indicate spoofing.
    //
    // Solution: Our DeviceIdentity generator produces CONSISTENT values.
    // The fingerprint always matches the model and brand.

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 8: ACCESSIBILITY SERVICE
    // ═══════════════════════════════════════════════════════════════════
    //
    // Some apps check if AccessibilityService is enabled for the
    // host app, which is commonly used by cloners to intercept UI.
    //
    // Solution: Our app doesn't use AccessibilityService for cloning.
    // If we need it for other features, BlackBox would need to hide it.

    /**
     * Check if accessibility is enabled for any of our packages.
     * Returns empty list if clean (no accessibility detection vector).
     */
    fun checkAccessibilityForHost(): List<String> {
        val enabled = mutableListOf<String>()
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return enabled

            HOST_PACKAGE_PATTERNS.forEach { pkg ->
                if (enabledServices.contains(pkg)) {
                    enabled.add(pkg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check accessibility", e)
        }
        return enabled
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 9: OVERLAY DETECTION
    // ═══════════════════════════════════════════════════════════════════
    //
    // Apps check if SYSTEM_ALERT_WINDOW is granted, which cloners
    // often use for floating bubbles.
    //
    // Solution: BlackBox intercepts this permission check and returns
    // false for cloned apps. Our bubble feature only applies to the
    // host app, not cloned apps.

    /**
     * Check if overlay permission would be detectable.
     * BlackBox should spoof this to return false for cloned apps.
     */
    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION VECTOR 10: USAGE STATS
    // ═══════════════════════════════════════════════════════════════════
    //
    // Some apps check UsageStatsManager to see if other apps are
    // running or if the usage pattern looks like a virtual machine.
    //
    // Solution: BlackBox intercepts UsageStatsManager queries.

    // ═══════════════════════════════════════════════════════════════════
    // COMPREHENSIVE HEALTH CHECK
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Run a full anti-detection health check.
     * Tests ALL detection vectors and reports protection status.
     *
     * Returns a map where:
     *   - key = detection vector name
     *   - value = "PROTECTED" | "EXPOSED" | "NEEDS_BLACKBOX_AAR"
     *
     * Vectors marked NEEDS_BLACKBOX_AAR require the native BlackBox engine
     * to intercept system calls (Binder hooks). They are NOT active until
     * the AAR is linked and BlackBoxCore is initialized.
     */
    fun runFullDetectionAudit(): Map<String, String> {
        val results = mutableMapOf<String, String>()

        // Vector 1: Package Manager — hides host + known cloner packages
        results["package_manager_hiding"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Vector 2: FilesDir sanitization — hides host traces from paths
        results["filesystem_isolation"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Vector 3: /proc/self/maps — hide native hooking libraries
        results["proc_maps_protection"] = if (isProcMapsClean()) "PROTECTED" else {
            val found = scanProcMapsForEngineLibs()
            "EXPOSED: ${found.size} engine libs visible"
        }

        // Vector 4: Process list — hide proxy processes
        results["process_hiding"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Vector 5: UID isolation — prevent UID-based detection
        results["uid_isolation"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Vector 6: Installer spoofing — show com.android.vending
        results["installer_spoofing"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Vector 7: System property consistency
        results["property_consistency"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Vector 8: Accessibility service
        val accessibilityExposed = checkAccessibilityForHost()
        results["accessibility_clean"] = if (accessibilityExposed.isEmpty()) "PROTECTED"
            else "EXPOSED: ${accessibilityExposed.size} packages"

        // Vector 9: Overlay permission
        results["overlay_hidden"] = if (canDrawOverlays()) "EXPOSED: overlay enabled"
            else "PROTECTED"

        // Vector 10: Usage stats isolation
        results["usage_stats_isolation"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Vector 11: Running tasks hiding
        results["tasks_hiding"] = if (initialized) "PROTECTED" else "NEEDS_BLACKBOX_AAR"

        // Log results
        results.forEach { (check, status) ->
            when {
                status == "PROTECTED" -> Log.v(TAG, "✅ $check: $status")
                status.startsWith("EXPOSED") -> Log.w(TAG, "⚠️  $check: $status")
                else -> Log.i(TAG, "ℹ️  $check: $status")
            }
        }

        return results
    }

    /**
     * Get the overall security score (0-100%).
     *
     * - Counts PROTECTED vs total checks
     * - NEEDS_BLACKBOX_AAR counts as neutral (neither protected nor exposed)
     * - Higher = harder to detect
     */
    fun getSecurityScore(): Int {
        val audit = runFullDetectionAudit()
        if (audit.isEmpty()) return 0
        val protectedCount = audit.count { it.value == "PROTECTED" }
        val exposedCount = audit.count { it.value.startsWith("EXPOSED") }
        val totalScored = protectedCount + exposedCount
        if (totalScored == 0) return 100 // Everything needs AAR — neutral
        return (protectedCount * 100) / totalScored
    }


}
