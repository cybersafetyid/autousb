package com.cybersafetyid.mobile.autousb.shell.lollipop

import android.content.Context
import com.cybersafetyid.mobile.autousb.shell.CommandResult
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object LollipopAdbEngine {

    suspend fun tryEnableAdbOnLollipop(
        context: Context,
        targetPort: Int = 5555,
        onLineOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        onLineOutput?.invoke("[INFO] Initializing Android 5.0 Lollipop Temp-Root & On-Device ADB Engine...")

        // 1. Check if port 5555 is already active
        if (ShellExecutor.isLocalPortListening(targetPort)) {
            val msg = "ADB TCP Port $targetPort is already active on local device."
            stdoutBuilder.append(msg)
            onLineOutput?.invoke("[OK] $msg")
            val duration = System.currentTimeMillis() - startTime
            return@withContext CommandResult(
                command = "setprop service.adb.tcp.port $targetPort",
                exitCode = 0,
                stdout = stdoutBuilder.toString(),
                stderr = "",
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = duration
            )
        }

        // 2. Try direct SystemProperties reflection & Raw Socket Property Service Injection
        try {
            val injectRes = LollipopPropertyInjector.enableAdbTcpPortIndependently(context, targetPort, onLineOutput)
            if (injectRes.isSuccess || ShellExecutor.isLocalPortListening(targetPort)) {
                val duration = System.currentTimeMillis() - startTime
                return@withContext injectRes.copy(durationMs = duration)
            }
        } catch (_: Exception) {}

        // 2. Prepare Temp-Root Exploit / Property Injector Script
        val scriptFile = File(context.filesDir, "run_lollipop_adb.sh")
        try {
            val scriptContent = """
                #!/system/bin/sh
                setprop service.adb.tcp.port $targetPort
                setprop persist.adb.tcp.port $targetPort
                setprop ctl.restart adbd
                stop adbd
                start adbd
            """.trimIndent()
            scriptFile.writeText(scriptContent)
            scriptFile.setExecutable(true, false)
        } catch (_: Exception) {}

        // 3. Candidates for temp-root & privileged execution binaries on Lollipop
        val tempRootBinaries = listOf(
            listOf("/system/bin/sh", scriptFile.absolutePath),
            listOf("su", "-c", scriptFile.absolutePath),
            listOf("/system/xbin/su", "-c", scriptFile.absolutePath),
            listOf("/system/bin/su", "-c", scriptFile.absolutePath),
            listOf("/data/local/tmp/temproot", scriptFile.absolutePath)
        )

        var isSuccessful = false

        for (cmd in tempRootBinaries) {
            try {
                onLineOutput?.invoke("[TRY] Executing: ${cmd.joinToString(" ")}")
                val process = ProcessBuilder(cmd).start()
                val os = DataOutputStream(process.outputStream)
                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                os.writeBytes("setprop service.adb.tcp.port $targetPort\n")
                os.writeBytes("setprop persist.adb.tcp.port $targetPort\n")
                os.writeBytes("stop adbd\nstart adbd\nexit\n")
                os.flush()

                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    stdoutBuilder.append(l).append("\n")
                    onLineOutput?.invoke(l)
                }

                while (stderrReader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    stderrBuilder.append(l).append("\n")
                }

                process.waitFor()
                os.close()
                stdoutReader.close()
                stderrReader.close()

                if (ShellExecutor.isLocalPortListening(targetPort)) {
                    isSuccessful = true
                    onLineOutput?.invoke("[SUCCESS] Wireless ADB enabled on Port $targetPort!")
                    break
                }
            } catch (_: Exception) {}
        }

        val duration = System.currentTimeMillis() - startTime
        if (isSuccessful || ShellExecutor.isLocalPortListening(targetPort)) {
            CommandResult(
                command = "setprop service.adb.tcp.port $targetPort",
                exitCode = 0,
                stdout = "ADB TCP Port $targetPort activated successfully!",
                stderr = "",
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = duration
            )
        } else {
            val errMsg = "Android 5.0 On-Device ADB Engine executed. Note: If your Lollipop firmware strictly enforces property SELinux, active property injection completed."
            stderrBuilder.append(errMsg)
            onLineOutput?.invoke("[INFO] $errMsg")
            CommandResult(
                command = "setprop service.adb.tcp.port $targetPort",
                exitCode = 1,
                stdout = stdoutBuilder.toString().trimEnd(),
                stderr = stderrBuilder.toString().trimEnd(),
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = duration
            )
        }
    }
}
