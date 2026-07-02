package com.utility.toolbox.ui.screens.permissions

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class PermissionItem(
    val permission: String,
    val friendlyName: String,
    val isGranted: Boolean,
    val isDangerous: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    packageName: String,
    appName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var permissions by remember {
        mutableStateOf(loadPermissions(context, packageName))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$appName Permissions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Granted: ${permissions.count { it.isGranted }}/${permissions.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }

            items(permissions) { perm ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (perm.isGranted) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (perm.isGranted) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(perm.friendlyName, fontWeight = FontWeight.Medium)
                            Text(perm.permission, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (perm.isDangerous) {
                                Text("Dangerous permission", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadPermissions(context: android.content.Context, packageName: String): List<PermissionItem> {
    return try {
        val pm = context.packageManager
        val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }

        val requestedPermissions = pkgInfo.requestedPermissions ?: emptyArray()
        val grantedPermissions = pkgInfo.requestedPermissionsFlags ?: intArrayOf()

        val dangerousPermissions = setOf(
            "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR",
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.CAMERA", "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_PHONE_STATE", "android.permission.CALL_PHONE",
            "android.permission.READ_SMS", "android.permission.SEND_SMS",
            "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO", "android.permission.BLUETOOTH_CONNECT",
            "android.permission.POST_NOTIFICATIONS", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        )

        val friendlyNames = mapOf(
            "android.permission.INTERNET" to "Internet",
            "android.permission.ACCESS_NETWORK_STATE" to "Network State",
            "android.permission.ACCESS_WIFI_STATE" to "WiFi State",
            "android.permission.CAMERA" to "Camera",
            "android.permission.RECORD_AUDIO" to "Microphone",
            "android.permission.ACCESS_FINE_LOCATION" to "Precise Location",
            "android.permission.ACCESS_COARSE_LOCATION" to "Approximate Location",
            "android.permission.READ_EXTERNAL_STORAGE" to "Read Storage",
            "android.permission.WRITE_EXTERNAL_STORAGE" to "Write Storage",
            "android.permission.READ_MEDIA_IMAGES" to "Read Images",
            "android.permission.READ_MEDIA_VIDEO" to "Read Videos",
            "android.permission.READ_MEDIA_AUDIO" to "Read Audio",
            "android.permission.READ_CONTACTS" to "Read Contacts",
            "android.permission.WRITE_CONTACTS" to "Write Contacts",
            "android.permission.READ_PHONE_STATE" to "Phone State",
            "android.permission.CALL_PHONE" to "Make Calls",
            "android.permission.READ_SMS" to "Read SMS",
            "android.permission.SEND_SMS" to "Send SMS",
            "android.permission.POST_NOTIFICATIONS" to "Notifications",
            "android.permission.BLUETOOTH_CONNECT" to "Bluetooth",
            "android.permission.NEARBY_WIFI_DEVICES" to "Nearby WiFi",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" to "Selected Photos"
        )

        requestedPermissions.mapIndexed { index, perm ->
            val isGranted = (grantedPermissions.getOrElse(index) { 0 } and PackageManager.PERMISSION_GRANTED) != 0
            PermissionItem(
                permission = perm,
                friendlyName = friendlyNames[perm] ?: perm.substringAfterLast("."),
                isGranted = isGranted,
                isDangerous = perm in dangerousPermissions
            )
        }.sortedBy { it.permission }
    } catch (e: Exception) {
        emptyList()
    }
}
