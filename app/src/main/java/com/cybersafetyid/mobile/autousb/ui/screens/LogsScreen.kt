package com.cybersafetyid.mobile.autousb.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybersafetyid.mobile.autousb.data.LogRepository
import com.cybersafetyid.mobile.autousb.shell.CommandResult
import com.cybersafetyid.mobile.autousb.ui.theme.AutousbTheme
import com.cybersafetyid.mobile.autousb.ui.theme.ErrorRed
import com.cybersafetyid.mobile.autousb.ui.theme.TerminalBackground
import com.cybersafetyid.mobile.autousb.ui.theme.TerminalGreen

@Composable
fun LogsScreen(
    modifier: Modifier = Modifier
) {
    val logs by LogRepository.logs.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Execution History Logs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${logs.size} recorded log entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (logs.isNotEmpty()) {
                IconButton(onClick = { LogRepository.clearLogs() }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Logs")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No execution history recorded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogCardItem(log)
                }
            }
        }
    }
}

@Composable
private fun LogCardItem(log: CommandResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "[${log.executionMethodUsed.name}]",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (log.isSuccess) "EXIT: 0" else "EXIT: ${log.exitCode}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (log.isSuccess) TerminalGreen else ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Command:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalBackground)
                    .padding(8.dp)
            ) {
                Text(
                    text = log.command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            if (log.stdout.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Output:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalBackground)
                        .padding(8.dp)
                ) {
                    Text(
                        text = log.stdout,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalGreen
                    )
                }
            }

            if (log.stderr.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Errors:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ErrorRed
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalBackground)
                        .padding(8.dp)
                ) {
                    Text(
                        text = log.stderr,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ErrorRed
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LogsScreenPreview() {
    AutousbTheme {
        LogsScreen()
    }
}
