package com.utility.toolbox.ui.screens.rootcheck

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

data class RootCheckItem(
    val name: String,
    val description: String,
    val passed: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var checks by remember { mutableStateOf<List<RootCheckItem>>(emptyList()) }
    var hasRun by remember { mutableStateOf(false) }

    if (!hasRun) {
        checks = runRootChecks(context)
        hasRun = true
    }

    val protectedCount = checks.count { it.passed }
    val totalChecks = checks.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Root Detection Check") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (protectedCount == totalChecks) Color(0xFF1B5E20).copy(alpha = 0.15f) else Color(0xFFB71C1C).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(48.dp),
                            tint = if (protectedCount == totalChecks) Color(0xFF1B5E20) else Color(0xFFB71C1C))
                        Spacer(Modifier.height(8.dp))
                        Text(if (protectedCount == totalChecks) "Fully Protected" else "Vulnerabilities Found", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineSmall.fontSize)
                        Text("$protectedCount/$totalChecks checks passed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(checks) { check ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(if (check.passed) Color(0xFF1B5E20) else Color(0xFFB71C1C).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(if (check.passed) Icons.Default.Check else Icons.Default.Close, contentDescription = null,
                                tint = if (check.passed) Color.White else Color(0xFFB71C1C), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(check.name, fontWeight = FontWeight.Medium)
                            Text(check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Device Info", fontWeight = FontWeight.Bold)
                        Text("Manufacturer: ${Build.MANUFACTURER}", style = MaterialTheme.typography.bodySmall)
                        Text("Model: ${Build.MODEL}", style = MaterialTheme.typography.bodySmall)
                        Text("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
                        Text("Build: ${Build.DISPLAY}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun runRootChecks(context: android.content.Context): List<RootCheckItem> {
    val checks = mutableListOf<RootCheckItem>()

    // Check 1: Common root binaries
    val rootPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/xbin/su", "/data/local/bin/su")
    val suFound = rootPaths.any { File(it).exists() }
    checks.add(RootCheckItem("Root Binary (su)", "Check for su binary in common paths", !suFound))

    // Check 2: Root management apps
    val rootApps = listOf("com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser", "com.thirdparty.superuser", "com.noshufou.android.su")
    val pm = context.packageManager
    val rootAppInstalled = rootApps.any { pkg ->
        try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    }
    checks.add(RootCheckItem("Root Management App", "Check for Magisk, SuperSU, etc.", !rootAppInstalled))

    // Check 3: Test keys (debug/eng build)
    val isTestKey = Build.TAGS?.contains("test-keys") == true
    checks.add(RootCheckItem("Test Keys", "Check for test/eng signing keys", !isTestKey))

    // Check 4: Dangerous props
    val roSecure = try { Runtime.getRuntime().exec(arrayOf("getprop", "ro.secure")).inputStream.bufferedReader().readText().trim() } catch (_: Exception) { "" }
    val isSecure = roSecure == "1"
    checks.add(RootCheckItem("ro.secure", "Check if device is secure (ro.secure=1)", isSecure))

    // Check 5: SELinux status
    val selinux = try { Runtime.getRuntime().exec(arrayOf("getenforce")).inputStream.bufferedReader().readText().trim() } catch (_: Exception) { "Unknown" }
    checks.add(RootCheckItem("SELinux Status", "Current: $selinux", selinux == "Enforcing"))

    // Check 6: Build type
    val isUserBuild = Build.TYPE == "user"
    checks.add(RootCheckItem("Build Type", "Current: ${Build.TYPE}", isUserBuild))

    // Check 7: SafetyNet/Play Integrity basics
    val hasGms = try { pm.getPackageInfo("com.google.android.gms", 0); true } catch (_: Exception) { false }
    checks.add(RootCheckItem("Google Play Services", "GMS installed", hasGms))

    // Check 8: /system partition mount
    val systemMount = try {
        val lines = File("/proc/mounts").readLines()
        lines.any { it.contains("/system") && it.contains("rw,") }
    } catch (_: Exception) { false }
    checks.add(RootCheckItem("System Partition", "Check if /system is read-only", !systemMount))

    // Check 9: ADB root
    val isAdbRoot = try {
        val uid = android.os.Process.myUid()
        uid == 0
    } catch (_: Exception) { false }
    checks.add(RootCheckItem("ADB Root", "Check if running as root (uid=0)", !isAdbRoot))

    // Check 10: Magisk hide/zygisk
    val magiskDir = File("/data/adb/magisk")
    val hasMagisk = magiskDir.exists()
    checks.add(RootCheckItem("Magisk Directory", "Check /data/adb/magisk", !hasMagisk))

    return checks
}
