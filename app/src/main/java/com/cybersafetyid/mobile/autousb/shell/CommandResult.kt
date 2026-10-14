package com.cybersafetyid.mobile.autousb.shell

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CommandResult(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timestamp: Long = System.currentTimeMillis(),
    val executionMethodUsed: ExecutionMethod,
    val durationMs: Long = 0L
) {
    val isSuccess: Boolean
        get() = exitCode == 0

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
