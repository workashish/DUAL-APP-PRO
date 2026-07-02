package com.utility.toolbox.ui.screens.deviceinfo

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.service.BlackBoxEngine

/**
 * Device Info screen.
 *
 * Shows the REAL device identity alongside the SPOOFED identity
 * that cloned apps see in this workspace.
 *
 * With BlackBox active:
 *   - The "Cloned App Identity" section shows REAL spoofed values
 *     that BlackBox generates per workspace
 *   - "Reset Device Info" generates a completely new identity
 *   - Each workspace gets a unique identity automatically
 *
 * Without BlackBox:
 *   - The spoofed section shows the generated-but-not-applied values
 *   - Cloned apps run normally (no interception)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    app: ClonedApp,
    deviceIdentity: BlackBoxEngine.DeviceIdentity?,
    onBack: () -> Unit,
    onResetDeviceInfo: (ClonedApp) -> Unit,
    onResetGsf: (ClonedApp) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Real device info (always reads from actual hardware) ────────────
    val realAndroidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown"
    val realDevice = "${Build.MANUFACTURER} ${Build.MODEL}"
    val realBrand = Build.BRAND
    val realProduct = Build.PRODUCT
    val realFingerprint = Build.FINGERPRINT
    val realSerial = try { Build.getSerial() } catch (e: Exception) { "Unknown" }
    val realBoard = Build.BOARD
    val realHardware = Build.HARDWARE
    val realDisplay = Build.DISPLAY
    val realSdk = "${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})"

    // ── Spoofed identity — from ClonedApp's embedded fields ────────────
    val spoofedAndroidId = app.androidId.ifBlank { "—" }
    val spoofedDevice = app.deviceModel.ifBlank { "—" }
    val spoofedBrand = app.deviceBrand.ifBlank { "—" }
    val spoofedFingerprint = app.deviceFingerprint.ifBlank { "—" }
    val spoofedSerial = app.deviceSerial.ifBlank { "—" }
    val spoofedImei = app.imei.ifBlank { "—" }
    val spoofedMac = app.macAddress.ifBlank { "—" }
    val spoofedGsfId = app.gsfId.ifBlank { "—" }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Device Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── App info header ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(app.iconColor), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.displayName.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "User ${app.userId} • Device ID resets: ${app.deviceInfoResetCount} • GSF resets: ${app.gsfResetCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Real Device Info ─────────────────────────────────────────
            SectionHeader("Real Device Identity", Icons.Default.PhoneAndroid)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    DeviceInfoRow("Android ID", realAndroidId, clipboard, snackbarHostState)
                    DeviceInfoRow("Device", realDevice)
                    DeviceInfoRow("Brand", realBrand)
                    DeviceInfoRow("Product", realProduct)
                    DeviceInfoRow("Board", realBoard)
                    DeviceInfoRow("Hardware", realHardware)
                    DeviceInfoRow("Display", realDisplay)
                    DeviceInfoRow("SDK", realSdk)
                    DeviceInfoRow("Fingerprint", realFingerprint, clipboard, snackbarHostState)
                    DeviceInfoRow("Serial", realSerial, clipboard, snackbarHostState)
                }
            }

            // ── Spoofed Device Info ──────────────────────────────────────
            SectionHeader(
                title = "Cloned App Identity (Spoofed)",
                icon = Icons.Default.DeviceHub
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    DeviceInfoRow("Android ID", spoofedAndroidId, clipboard, snackbarHostState)
                    DeviceInfoRow("Device Model", spoofedDevice)
                    DeviceInfoRow("Brand", spoofedBrand)
                    DeviceInfoRow("Serial", spoofedSerial, clipboard, snackbarHostState)
                    DeviceInfoRow("Fingerprint", spoofedFingerprint, clipboard, snackbarHostState)
                    DeviceInfoRow("IMEI", spoofedImei, clipboard, snackbarHostState)
                    DeviceInfoRow("MAC Address", spoofedMac, clipboard, snackbarHostState)
                }
            }

            // ── Action buttons ───────────────────────────────────────────
            Button(
                onClick = { onResetDeviceInfo(app) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Device Identity (New IDs)", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { onResetGsf(app) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset License (New GSF ID)", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
    clipboard: androidx.compose.ui.platform.ClipboardManager? = null,
    snackbarHostState: SnackbarHostState? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 1
        )
        if (clipboard != null && snackbarHostState != null && value != "—") {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(value))
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
