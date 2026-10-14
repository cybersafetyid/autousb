package com.cybersafetyid.mobile.autousb.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybersafetyid.mobile.autousb.data.AppPreferences
import com.cybersafetyid.mobile.autousb.data.LogRepository
import com.cybersafetyid.mobile.autousb.service.AutoUsbService
import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import com.cybersafetyid.mobile.autousb.shell.ShizukuHelper
import com.cybersafetyid.mobile.autousb.ui.theme.AutousbTheme
import com.cybersafetyid.mobile.autousb.ui.theme.TerminalGreen
import com.cybersafetyid.mobile.autousb.utils.BundledApkInstaller
import com.cybersafetyid.mobile.autousb.utils.UsbInstaller
import kotlinx.coroutines.launch

@Composable
fun InfoScreen(
    prefs: AppPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isServiceRunning by prefs.isServiceRunning.collectAsState()
    val executionMethod by prefs.executionMethod.collectAsState()
    val lastServiceStatus by AutoUsbService.lastExecutionStatus.collectAsState()

    var adbTcpPort by remember { mutableStateOf("Loading...") }
    var deviceIps by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var isShizukuGranted by remember { mutableStateOf(ShizukuHelper.isShizukuPermissionGranted()) }
    var isShizukuInstalled by remember { mutableStateOf(ShizukuHelper.isShizukuInstalled()) }
    var isExecutingCommand by remember { mutableStateOf(false) }
    var isScanningUsb by remember { mutableStateOf(false) }

    fun refreshStatus() {
        scope.launch {
            adbTcpPort = ShellExecutor.getAdbTcpPort()
            deviceIps = ShellExecutor.getDeviceIpAddresses(context)
            isRootAvailable = ShellExecutor.isRootAvailable()
            isShizukuGranted = ShizukuHelper.isShizukuPermissionGranted()
            isShizukuInstalled = ShizukuHelper.isShizukuInstalled()
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Status Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AutoUSB Control Center",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Foreground Service & Shell Runner",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refreshStatus() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Status"
                )
            }
        }

        // --- Service Control Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Service Status",
                            tint = if (isServiceRunning) TerminalGreen else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Background Service",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { start ->
                            if (start) {
                                AutoUsbService.startService(context)
                            } else {
                                AutoUsbService.stopService(context)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isServiceRunning) TerminalGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.height(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "Status: RUNNING" else "Status: STOPPED",
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServiceRunning) TerminalGreen else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last Action: $lastServiceStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isExecutingCommand = true
                        scope.launch {
                            val script = prefs.getAutoExecuteScript()
                            val method = prefs.getExecutionMethod()
                            val res = ShellExecutor.executeCommand(script, method, context = context)
                            LogRepository.addLog(res)
                            val port = ShellExecutor.getAdbTcpPort()
                            val isPortActive = ShellExecutor.isAdbPortActive(port)

                            if (isPortActive) {
                                if (prefs.getShowIpToast()) {
                                    val toastMsg = ShellExecutor.buildAdbIpToastMessage(context, port)
                                    ShellExecutor.showToast(context, toastMsg)
                                } else {
                                    Toast.makeText(context, "ADB is active on port $port", Toast.LENGTH_SHORT).show()
                                }
                            } else if (res.isSuccess) {
                                Toast.makeText(context, "Script executed successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                val rawErr = res.stderr.trim()
                                val err = if (rawErr.isNotBlank()) rawErr.take(150) else "Exit code ${res.exitCode}"
                                Toast.makeText(context, "Command output:\n$err", Toast.LENGTH_LONG).show()
                            }
                            refreshStatus()
                            isExecutingCommand = false
                        }
                    },
                    enabled = !isExecutingCommand,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run Script"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExecutingCommand) "Executing..." else "Run Auto-Script Now",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- Always-Visible ADB Manual Fallback & Activation Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "ADB Activation Actions",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADB Activation & Fallback Tools",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Always available to activate ADB Port 5555 on local device or connected target phone over USB OTG:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action 1: Force Activate Local ADB TCP Port 5555
                Button(
                    onClick = {
                        scope.launch {
                            val script = ShellExecutor.DEFAULT_ADB_SCRIPT
                            val res = ShellExecutor.executeCommand(script, prefs.getExecutionMethod(), context = context)
                            LogRepository.addLog(res)
                            val port = ShellExecutor.getAdbTcpPort()
                            if (ShellExecutor.isAdbPortActive(port)) {
                                val toastMsg = ShellExecutor.buildAdbIpToastMessage(context, port)
                                ShellExecutor.showToast(context, toastMsg)
                            } else {
                                Toast.makeText(context, "Activation output:\n${res.stdout.ifBlank { res.stderr }}", Toast.LENGTH_LONG).show()
                            }
                            refreshStatus()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Activate ADB Port 5555 (Local)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action 2: Activate ADB Port 5555 to Another Phone over USB OTG Cable
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val res = com.cybersafetyid.mobile.autousb.shell.usb.UsbAdbHostEngine.enableAdbTcpOverUsbOtg(context)
                            Toast.makeText(context, res.stdout.ifBlank { res.stderr }, Toast.LENGTH_LONG).show()
                            refreshStatus()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Usb, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Activate via USB OTG Host (to HP/Target)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action 3: Settings & Shizuku Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openWirelessDebuggingSettings(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dev Settings")
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = BundledApkInstaller.installBundledShizuku(context)
                                    Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                                    refreshStatus()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Install Shizuku")
                        }
                    }
                }
            }
        }

        // --- USB Flashdrive Installer Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB Flashdrive",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "USB Flashdrive Installer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Auto-install APK files found on connected USB OTG storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        isScanningUsb = true
                        scope.launch {
                            val res = UsbInstaller.scanAndInstallApksFromUsb(context, prefs)
                            Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                            isScanningUsb = false
                        }
                    },
                    enabled = !isScanningUsb,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Usb, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanningUsb) "Scanning USB OTG..." else "Scan USB Drive & Install APK Now")
                }
            }
        }

        // --- Wireless ADB & Network Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "ADB Status",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wireless ADB & Network",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ADB TCP Port: $adbTcpPort",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Device IP Address(es):",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (deviceIps.isEmpty()) {
                    Text(
                        text = "Scanning network interfaces...",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    deviceIps.forEach { ip ->
                        Text(
                            text = "• $ip",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // --- Execution Environment & Privilege Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Execution Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Execution Environment",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Selected Method: ${executionMethod.displayName}",
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                val rootStatusText = when (isRootAvailable) {
                    true -> "Root Granted (su available)"
                    false -> "Non-Root (su not available)"
                    null -> "Checking Root status..."
                }

                val rootColor = when (isRootAvailable) {
                    true -> TerminalGreen
                    false -> MaterialTheme.colorScheme.secondary
                    null -> Color.Gray
                }

                Text(
                    text = "Root Status: $rootStatusText",
                    color = rootColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Spacer(modifier = Modifier.height(4.dp))

                    val shizukuStatusText = when {
                        isShizukuGranted -> "Shizuku Granted (ADB Shell uid=2000 active)"
                        isShizukuInstalled -> "Shizuku Installed (Permission required)"
                        else -> "Shizuku Service Not Running"
                    }

                    val shizukuColor = when {
                        isShizukuGranted -> TerminalGreen
                        isShizukuInstalled -> MaterialTheme.colorScheme.primary
                        else -> Color.Gray
                    }

                    Text(
                        text = "Shizuku Status: $shizukuStatusText",
                        color = shizukuColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isShizukuInstalled && !isShizukuGranted) {
                                    ShizukuHelper.requestShizukuPermission(1001)
                                }
                                refreshStatus()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isShizukuGranted) "Shizuku Active" else "Grant Shizuku")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isRootAvailable = null
                                    refreshStatus()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Re-test Env")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isRootAvailable = null
                                refreshStatus()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Re-test Env")
                    }
                }
            }
        }

        // --- System Information Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Information",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "System Uptime: ${formatUptime(SystemClock.elapsedRealtime())}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun openWirelessDebuggingSettings(context: Context) {
    try {
        val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open Developer Options", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun formatUptime(uptimeMs: Long): String {
    val seconds = (uptimeMs / 1000) % 60
    val minutes = (uptimeMs / (1000 * 60)) % 60
    val hours = (uptimeMs / (1000 * 60 * 60)) % 24
    val days = uptimeMs / (1000 * 60 * 60 * 24)
    return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m ${seconds}s"
}

@Preview(showBackground = true)
@Composable
fun InfoScreenPreview() {
    AutousbTheme {
        InfoScreen(prefs = AppPreferences(LocalContext.current))
    }
}
