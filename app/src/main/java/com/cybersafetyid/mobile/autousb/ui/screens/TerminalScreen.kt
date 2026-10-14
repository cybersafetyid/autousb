package com.cybersafetyid.mobile.autousb.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybersafetyid.mobile.autousb.data.AppPreferences
import com.cybersafetyid.mobile.autousb.data.LogRepository
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import com.cybersafetyid.mobile.autousb.ui.theme.AutousbTheme
import com.cybersafetyid.mobile.autousb.ui.theme.ErrorRed
import com.cybersafetyid.mobile.autousb.ui.theme.TerminalBackground
import com.cybersafetyid.mobile.autousb.ui.theme.TerminalGreen
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    prefs: AppPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentMethod by prefs.executionMethod.collectAsState()

    var inputCommand by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var isRootActive by remember { mutableStateOf(false) }

    val terminalLines = remember {
        mutableStateListOf(
            "AutoUSB Terminal v1.0 [Termux Style Engine]",
            "Type a command below or tap a preset quick chip.",
            "--------------------------------------------------"
        )
    }

    fun runScriptInTerminal(scriptToRun: String) {
        if (scriptToRun.isBlank() || isExecuting) return
        isExecuting = true
        terminalLines.add("${if (isRootActive) "#" else "$"} $scriptToRun")

        scope.launch {
            val result = ShellExecutor.executeCommand(
                script = scriptToRun,
                preferredMethod = currentMethod,
                onLineOutput = { line ->
                    terminalLines.add(line)
                }
            )

            LogRepository.addLog(result)
            terminalLines.add("[Process completed with exit code ${result.exitCode}]")
            terminalLines.add("")
            isExecuting = false
        }
    }

    LaunchedEffect(Unit) {
        isRootActive = ShellExecutor.isRootAvailable()
    }

    LaunchedEffect(terminalLines.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // --- Terminal Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Terminal Console",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isRootActive) "Mode: ROOT (#)" else "Mode: NON-ROOT ($)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRootActive) TerminalGreen else MaterialTheme.colorScheme.secondary
                )
            }

            Row {
                IconButton(onClick = {
                    val fullText = terminalLines.joinToString("\n")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Terminal Logs", fullText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Terminal logs copied!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = { terminalLines.clear() }) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Terminal")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Quick Preset Action Chips ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { runScriptInTerminal(ShellExecutor.DEFAULT_ADB_SCRIPT) },
                label = { Text("Enable ADB 5555") }
            )
            AssistChip(
                onClick = { runScriptInTerminal("setprop service.adb.tcp.port -1\nstop adbd\nstart adbd") },
                label = { Text("Disable ADB TCP") }
            )
            AssistChip(
                onClick = { runScriptInTerminal("getprop service.adb.tcp.port") },
                label = { Text("Get ADB Port") }
            )
            AssistChip(
                onClick = { runScriptInTerminal("ip a || ifconfig") },
                label = { Text("Check IP") }
            )
            AssistChip(
                onClick = { runScriptInTerminal("id") },
                label = { Text("Check Root id") }
            )
            AssistChip(
                onClick = { runScriptInTerminal("getprop ro.product.model") },
                label = { Text("Device Info") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Terminal Output Console ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = TerminalBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    terminalLines.forEach { line ->
                        val textColor = when {
                            line.startsWith("[ERR]") || line.contains("failed", ignoreCase = true) -> ErrorRed
                            line.startsWith("$") || line.startsWith("#") -> Color.White
                            line.startsWith("[Process completed") -> TerminalGreen
                            else -> TerminalGreen
                        }

                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = textColor,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Terminal Command Input Prompt ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRootActive) "# " else "$ ",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isRootActive) TerminalGreen else MaterialTheme.colorScheme.secondary
            )

            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type shell command...") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TerminalGreen,
                    unfocusedBorderColor = Color.Gray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val cmd = inputCommand
                        inputCommand = ""
                        runScriptInTerminal(cmd)
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val cmd = inputCommand
                    inputCommand = ""
                    runScriptInTerminal(cmd)
                },
                enabled = !isExecuting && inputCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalGreen,
                    contentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run Command")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TerminalScreenPreview() {
    AutousbTheme {
        TerminalScreen(prefs = AppPreferences(LocalContext.current))
    }
}
