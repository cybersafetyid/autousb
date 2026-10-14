package com.net2software.mobile.autousb.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.net2software.mobile.autousb.data.AppPreferences
import com.net2software.mobile.autousb.shell.ExecutionMethod
import com.net2software.mobile.autousb.shell.ShellExecutor
import com.net2software.mobile.autousb.ui.theme.AutousbTheme
import com.net2software.mobile.autousb.ui.theme.TerminalGreen

@Composable
fun ConfigScreen(
    prefs: AppPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currentAutoStart by prefs.autoStartOnBoot.collectAsState()
    val currentAutoExec by prefs.autoExecuteOnStart.collectAsState()
    val currentAutoInstallUsb by prefs.autoInstallUsbApk.collectAsState()
    val currentUsbApkFileName by prefs.usbApkFileName.collectAsState()
    val currentMethod by prefs.executionMethod.collectAsState()
    val currentScript by prefs.autoExecuteScript.collectAsState()
    val currentPort by prefs.adbPort.collectAsState()
    val currentShowNotif by prefs.showNotification.collectAsState()
    val currentShowIpToast by prefs.showIpToast.collectAsState()

    var autoStartOnBoot by remember(currentAutoStart) { mutableStateOf(currentAutoStart) }
    var autoExecOnStart by remember(currentAutoExec) { mutableStateOf(currentAutoExec) }
    var autoInstallUsb by remember(currentAutoInstallUsb) { mutableStateOf(currentAutoInstallUsb) }
    var usbApkFileName by remember(currentUsbApkFileName) { mutableStateOf(currentUsbApkFileName) }
    var selectedMethod by remember(currentMethod) { mutableStateOf(currentMethod) }
    var scriptText by remember(currentScript) { mutableStateOf(currentScript) }
    var portText by remember(currentPort) { mutableStateOf(currentPort.toString()) }
    var showNotif by remember(currentShowNotif) { mutableStateOf(currentShowNotif) }
    var showIpToast by remember(currentShowIpToast) { mutableStateOf(currentShowIpToast) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configuration & Automation",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // --- Service & Boot Automation Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Automation Settings",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Start Service on Boot / Install",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Launch service when device boots or app updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoStartOnBoot,
                        onCheckedChange = { autoStartOnBoot = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Execute Script on Launch",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Execute default commands whenever service starts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoExecOnStart,
                        onCheckedChange = { autoExecOnStart = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Install APK from USB Flashdrive",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Scan USB drive on plug-in and install matching APK",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoInstallUsb,
                        onCheckedChange = { autoInstallUsb = it }
                    )
                }

                if (autoInstallUsb) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usbApkFileName,
                        onValueChange = { usbApkFileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Target APK Filename (use *.apk for any)") },
                        placeholder = { Text("autousb.apk or *.apk") },
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Service Notification",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Persistent notification showing active ADB state",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showNotif,
                        onCheckedChange = { showNotif = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show IP Address Toast",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Popup toast with IP address and adb connect command when connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showIpToast,
                        onCheckedChange = { showIpToast = it }
                    )
                }
            }
        }

        // --- Execution Method Selection Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Command Execution Method",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Choose how commands and installations should be executed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExecutionMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = method.displayName,
                                fontWeight = FontWeight.SemiBold
                            )
                            val desc = when (method) {
                                ExecutionMethod.AUTO -> "Auto-detect Root (su), fallback to Non-Root (sh)"
                                ExecutionMethod.ROOT -> "Always execute with SuperUser root permissions (su)"
                                ExecutionMethod.NON_ROOT -> "Standard local shell process (sh) without root"
                            }
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // --- Automated Command Script Editor Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Automated Command Script",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Commands executed when AutoUSB triggers. Each line is executed sequentially.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    label = { Text("Command Sequence") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("e.g. setprop service.adb.tcp.port 5555\nstop adbd\nstart adbd") },
                    singleLine = false
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Target ADB TCP Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }

        // --- Save & Reset Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scriptText = ShellExecutor.DEFAULT_ADB_SCRIPT
                    portText = "5555"
                    selectedMethod = ExecutionMethod.AUTO
                    autoStartOnBoot = true
                    autoExecOnStart = true
                    autoInstallUsb = true
                    usbApkFileName = "autousb.apk"
                    showNotif = true
                    showIpToast = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset Defaults")
            }

            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 5555
                    prefs.setAutoStartOnBoot(autoStartOnBoot)
                    prefs.setAutoExecuteOnStart(autoExecOnStart)
                    prefs.setAutoInstallUsbApk(autoInstallUsb)
                    prefs.setUsbApkFileName(usbApkFileName)
                    prefs.setExecutionMethod(selectedMethod)
                    prefs.setAutoExecuteScript(scriptText)
                    prefs.setAdbPort(port)
                    prefs.setShowNotification(showNotif)
                    prefs.setShowIpToast(showIpToast)

                    Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalGreen,
                    contentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Save")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConfigScreenPreview() {
    AutousbTheme {
        ConfigScreen(prefs = AppPreferences(LocalContext.current))
    }
}
