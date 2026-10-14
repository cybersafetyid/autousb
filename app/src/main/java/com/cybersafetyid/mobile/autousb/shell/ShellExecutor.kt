package com.cybersafetyid.mobile.autousb.shell

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.cybersafetyid.mobile.autousb.shell.adb.LocalAdbEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.Locale

object ShellExecutor {

    private const val TAG = "AutoUSB"

    const val DEFAULT_ADB_SCRIPT = "setprop service.adb.tcp.port 5555\nsetprop persist.adb.tcp.port 5555\nsetprop ctl.restart adbd\nstop adbd\nstart adbd"

    @Volatile
    private var cachedRootCmd: List<String>? = null

    fun clearRootCache() {
        cachedRootCmd = null
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (cachedRootCmd != null) return@withContext true

        val candidates = listOf(
            listOf("su", "0"),
            listOf("/system/xbin/su", "0"),
            listOf("/system/bin/su", "0"),
            listOf("su"),
            listOf("/system/xbin/su"),
            listOf("/system/bin/su"),
            listOf("su", "-c", "id")
        )

        for (cmd in candidates) {
            try {
                Log.d(TAG, "Testing root candidate: $cmd")
                val process = ProcessBuilder(cmd).start()
                val os = DataOutputStream(process.outputStream)
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                os.writeBytes("id\nexit\n")
                os.flush()

                val outputBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    outputBuilder.append(line)
                }
                val exitCode = process.waitFor()

                val output = outputBuilder.toString()
                if (exitCode == 0 || output.contains("uid=0")) {
                    Log.i(TAG, "Root available via command: $cmd (output: $output)")
                    cachedRootCmd = cmd
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Root candidate failed ($cmd): ${e.message}")
            }
        }
        Log.w(TAG, "No root binary (su) available on device.")
        return@withContext false
    }

    fun isShizukuAvailable(): Boolean {
        return ShizukuHelper.isShizukuPermissionGranted()
    }

    suspend fun executeCommand(
        script: String,
        preferredMethod: ExecutionMethod,
        context: Context? = null,
        onLineOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val isRoot = isRootAvailable()
        val isShizuku = ShizukuHelper.isShizukuPermissionGranted()

        var effectiveMethod = when (preferredMethod) {
            ExecutionMethod.AUTO -> {
                when {
                    isRoot -> ExecutionMethod.ROOT
                    isShizuku -> ExecutionMethod.SHIZUKU
                    else -> ExecutionMethod.LOCAL_ADB
                }
            }
            ExecutionMethod.ROOT -> ExecutionMethod.ROOT
            ExecutionMethod.LOCAL_ADB -> ExecutionMethod.LOCAL_ADB
            ExecutionMethod.SHIZUKU -> ExecutionMethod.SHIZUKU
            ExecutionMethod.NON_ROOT -> ExecutionMethod.NON_ROOT
        }

        Log.i(TAG, "ShellExecutor.executeCommand preferredMethod=$preferredMethod -> effectiveMethod=$effectiveMethod")
        Log.d(TAG, "Executing script:\n$script")

        if (effectiveMethod == ExecutionMethod.LOCAL_ADB && context != null) {
            Log.i(TAG, "Executing via LocalAdbEngine over loopback socket...")
            val adbRes = LocalAdbEngine.executeAdbCommand(
                context = context,
                commandString = script,
                onLineOutput = onLineOutput
            )
            if (adbRes.isSuccess) {
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "LocalAdbEngine succeeded in ${duration}ms")
                return@withContext adbRes.copy(durationMs = duration)
            }
            Log.w(TAG, "LocalAdbEngine failed, falling back to NON_ROOT shell execution.")
            effectiveMethod = ExecutionMethod.NON_ROOT
        }

        if (effectiveMethod == ExecutionMethod.ROOT && !isRoot) {
            if (isShizuku) {
                effectiveMethod = ExecutionMethod.SHIZUKU
                Log.i(TAG, "Root unavailable. Falling back to Shizuku ADB execution.")
                onLineOutput?.invoke("[INFO] Root unavailable. Falling back to Shizuku ADB execution.")
            } else {
                val errMsg = "Root permission (su) is required for this command, but Root is not available or denied."
                Log.e(TAG, errMsg)
                onLineOutput?.invoke("[ERR] $errMsg")
                val duration = System.currentTimeMillis() - startTime
                return@withContext CommandResult(
                    command = script,
                    exitCode = 1,
                    stdout = "",
                    stderr = errMsg,
                    executionMethodUsed = ExecutionMethod.ROOT,
                    durationMs = duration
                )
            }
        }

        if (effectiveMethod == ExecutionMethod.SHIZUKU && !isShizuku) {
            val errMsg = if (ShizukuHelper.isShizukuInstalled()) {
                "Shizuku is installed, but permission is not granted. Please grant Shizuku permission in AutoUSB."
            } else {
                "Shizuku service is not running or not installed. Please install and start Shizuku."
            }
            Log.e(TAG, errMsg)
            onLineOutput?.invoke("[ERR] $errMsg")
            val duration = System.currentTimeMillis() - startTime
            return@withContext CommandResult(
                command = script,
                exitCode = 1,
                stdout = "",
                stderr = errMsg,
                executionMethodUsed = ExecutionMethod.SHIZUKU,
                durationMs = duration
            )
        }

        val result = runProcessInternal(script, effectiveMethod, onLineOutput)
        var stdoutText = result.stdout
        var stderrText = result.stderr
        var exitCode = result.exitCode

        // Auto-retry via Lollipop Engine, Shizuku, Local ADB, or USB OTG if setprop failed on non-rooted device
        val isSetpropFailed = (script.contains("setprop") || script.contains("stop adbd") || script.contains("start adbd")) &&
                (exitCode != 0 || stderrText.contains("failed to set property", ignoreCase = true) ||
                        stderrText.contains("Permission denied", ignoreCase = true) ||
                        stderrText.contains("must be root", ignoreCase = true) ||
                        stderrText.contains("Operation not permitted", ignoreCase = true))

        if (isSetpropFailed && effectiveMethod == ExecutionMethod.NON_ROOT) {
            Log.w(TAG, "Non-root setprop execution returned error (exitCode=$exitCode, stderr=$stderrText). Triggering fallback engines...")
            val isLollipop = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            if (isLollipop && context != null) {
                Log.i(TAG, "Android 5.0 Lollipop detected. Triggering On-Device SystemProperties & Property Service Injector...")
                onLineOutput?.invoke("[INFO] Android 5.0 Lollipop detected. Triggering On-Device SystemProperties & Property Service Injector...")
                val lolRes = com.cybersafetyid.mobile.autousb.shell.lollipop.LollipopAdbEngine.tryEnableAdbOnLollipop(context, onLineOutput = onLineOutput)
                if (lolRes.isSuccess || isLocalPortListening(5555)) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "LollipopAdbEngine succeeded in ${duration}ms")
                    return@withContext lolRes.copy(durationMs = duration)
                }
            } else if (ShizukuHelper.isShizukuPermissionGranted()) {
                Log.i(TAG, "Non-root setprop failed. Auto-retrying via Shizuku ADB process...")
                onLineOutput?.invoke("[INFO] Non-root setprop failed. Auto-retrying via Shizuku ADB process...")
                val shizukuRes = runProcessInternal(script, ExecutionMethod.SHIZUKU, onLineOutput)
                if (shizukuRes.exitCode == 0 || shizukuRes.stderr.isBlank()) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "Shizuku execution succeeded in ${duration}ms")
                    return@withContext shizukuRes.copy(
                        executionMethodUsed = ExecutionMethod.SHIZUKU,
                        durationMs = duration
                    )
                }
            } else if (context != null) {
                Log.i(TAG, "Non-root setprop failed. Auto-retrying via Local ADB Engine...")
                onLineOutput?.invoke("[INFO] Non-root setprop failed. Auto-retrying via Local ADB Engine...")
                val localAdbRes = LocalAdbEngine.executeAdbCommand(context, script, onLineOutput = onLineOutput)
                if (localAdbRes.isSuccess) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "LocalAdbEngine succeeded in ${duration}ms")
                    return@withContext localAdbRes.copy(durationMs = duration)
                }

                Log.i(TAG, "Trying UsbAdbHostEngine over USB OTG...")
                onLineOutput?.invoke("[INFO] Trying UsbAdbHostEngine over USB OTG...")
                val usbRes = com.cybersafetyid.mobile.autousb.shell.usb.UsbAdbHostEngine.enableAdbTcpOverUsbOtg(context, onLineOutput = onLineOutput)
                if (usbRes.isSuccess) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "UsbAdbHostEngine succeeded in ${duration}ms")
                    return@withContext usbRes.copy(durationMs = duration)
                }
            }

            if (isLollipop) {
                val tip = "\n[AutoUSB Tip] Android 5.0 (Lollipop) Non-Root Device:\n" +
                        "On Android 5.0 without Root, 'setprop' is restricted by OS permissions.\n" +
                        "AutoUSB SystemProperties & Property Service Injection completed."
                stderrText = if (stderrText.isBlank()) tip else "$stderrText\n$tip"
                onLineOutput?.invoke(tip)
            } else if (ShizukuHelper.isShizukuInstalled()) {
                val tip = "\n[AutoUSB Tip] 'setprop' failed because device is not rooted.\n" +
                        "Shizuku service is detected on your device!\n" +
                        "Please grant Shizuku permission to AutoUSB in the Config tab to enable ADB TCP port without root."
                stderrText = if (stderrText.isBlank()) tip else "$stderrText\n$tip"
                onLineOutput?.invoke(tip)
            } else {
                val tip = "\n[AutoUSB Tip] 'setprop' failed because device is not rooted.\n" +
                        "How to set ADB TCP Port without Root:\n" +
                        " 1. Embedded Wireless ADB Engine: Pair Wireless Debugging in Config/Info tab.\n" +
                        " 2. Install Bundled Shizuku APK: Tap 'Install Bundled Shizuku APK' in Info tab."
                stderrText = if (stderrText.isBlank()) tip else "$stderrText\n$tip"
                onLineOutput?.invoke(tip)
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "ShellExecutor.executeCommand finished: method=$effectiveMethod, exitCode=$exitCode, duration=${duration}ms")
        CommandResult(
            command = script,
            exitCode = exitCode,
            stdout = stdoutText,
            stderr = stderrText,
            executionMethodUsed = effectiveMethod,
            durationMs = duration
        )
    }

    private fun runProcessInternal(
        script: String,
        method: ExecutionMethod,
        onLineOutput: ((String) -> Unit)?
    ): CommandResult {
        val startTime = System.currentTimeMillis()
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()
        val exitCode: Int

        try {
            val process: Process = when (method) {
                ExecutionMethod.ROOT -> {
                    val cmdList = cachedRootCmd ?: listOf("su")
                    Log.d(TAG, "Starting process: $cmdList")
                    ProcessBuilder(cmdList).start()
                }
                ExecutionMethod.SHIZUKU -> {
                    Log.d(TAG, "Starting Shizuku process: sh")
                    ShizukuHelper.execShizukuProcess(arrayOf("sh"))
                        ?: throw IllegalStateException("Shizuku process could not be started")
                }
                else -> {
                    Log.d(TAG, "Starting local shell process: sh")
                    ProcessBuilder("sh").start()
                }
            }

            val os = DataOutputStream(process.outputStream)
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdoutThread = Thread {
                try {
                    var line: String?
                    while (stdoutReader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        synchronized(stdoutBuilder) {
                            stdoutBuilder.append(currentLine).append("\n")
                        }
                        Log.d(TAG, "[STDOUT] $currentLine")
                        onLineOutput?.invoke(currentLine)
                    }
                } catch (_: Exception) {}
            }

            val stderrThread = Thread {
                try {
                    var line: String?
                    while (stderrReader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        synchronized(stderrBuilder) {
                            stderrBuilder.append(currentLine).append("\n")
                        }
                        Log.w(TAG, "[STDERR] $currentLine")
                        onLineOutput?.invoke("[ERR] $currentLine")
                    }
                } catch (_: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            val lines = script.trim().split("\n").filter { it.isNotBlank() }
            for (line in lines) {
                os.writeBytes("$line\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            exitCode = process.waitFor()
            stdoutThread.join(2000)
            stderrThread.join(2000)
            os.close()
            stdoutReader.close()
            stderrReader.close()

        } catch (e: Exception) {
            val errorMsg = "Execution failed (${method.displayName}): ${e.localizedMessage ?: e.message}"
            Log.e(TAG, errorMsg, e)
            stderrBuilder.append(errorMsg)
            onLineOutput?.invoke("[ERR] $errorMsg")
            val duration = System.currentTimeMillis() - startTime
            return CommandResult(
                command = script,
                exitCode = -1,
                stdout = stdoutBuilder.toString().trimEnd(),
                stderr = stderrBuilder.toString().trimEnd(),
                executionMethodUsed = method,
                durationMs = duration
            )
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "runProcessInternal completed: exitCode=$exitCode in ${duration}ms")
        return CommandResult(
            command = script,
            exitCode = exitCode,
            stdout = stdoutBuilder.toString().trimEnd(),
            stderr = stderrBuilder.toString().trimEnd(),
            executionMethodUsed = method,
            durationMs = duration
        )
    }

    suspend fun getAdbTcpPort(): String = withContext(Dispatchers.IO) {
        var port = ""
        try {
            val result = executeCommand("getprop service.adb.tcp.port", ExecutionMethod.NON_ROOT)
            port = result.stdout.trim()
        } catch (_: Exception) {}

        if (port.isBlank() || port == "-1") {
            try {
                val persistResult = executeCommand("getprop persist.adb.tcp.port", ExecutionMethod.NON_ROOT)
                port = persistResult.stdout.trim()
            } catch (_: Exception) {}
        }

        if (port.isBlank() || port == "-1") {
            try {
                val listenResult = executeCommand("getprop service.adb.listen_addrs", ExecutionMethod.NON_ROOT)
                val listenOut = listenResult.stdout.trim()
                if (listenOut.isNotBlank()) {
                    val match = Regex(":(\\d+)").find(listenOut)
                    if (match != null) {
                        port = match.groupValues[1]
                    }
                }
            } catch (_: Exception) {}
        }

        if (port.isBlank() || port == "-1" || port.equals("Disabled / None", ignoreCase = true)) {
            if (isLocalPortListening(5555)) {
                port = "5555"
            }
        }

        val finalPort = if (port.isBlank() || port == "-1") "Disabled / None" else port
        Log.d(TAG, "getAdbTcpPort resolved: $finalPort")
        finalPort
    }

    fun isLocalPortListening(port: Int, timeoutMs: Int = 300): Boolean {
        if (port <= 0 || port > 65535) return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isAdbPortActive(port: String): Boolean {
        val clean = port.trim()
        if (clean.isBlank() || clean.equals("Disabled / None", ignoreCase = true) || clean == "-1") return false
        val p = clean.toIntOrNull() ?: return false
        return p > 0
    }

    fun isValidIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        if (trimmed.isBlank() || trimmed == "127.0.0.1" || trimmed == "0.0.0.0" || trimmed.startsWith("169.254.")) return false
        val parts = trimmed.split(".")
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }

    @Suppress("DEPRECATION")
    suspend fun getRawIpv4Addresses(context: Context): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val ips = mutableListOf<Pair<String, String>>()
        val appContext = context.applicationContext ?: context

        // 1. Query ConnectivityManager LinkProperties
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activeNet = cm.activeNetwork
                    if (activeNet != null) {
                        val lp = cm.getLinkProperties(activeNet)
                        val ifaceName = lp?.interfaceName ?: "wlan0"
                        lp?.linkAddresses?.forEach { linkAddr ->
                            val addr = linkAddr.address
                            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                                val host = addr.hostAddress ?: ""
                                if (isValidIpv4(host)) {
                                    ips.add(Pair(ifaceName, host))
                                }
                            }
                        }
                    }
                }

                cm.allNetworks.forEach { net ->
                    val lp = cm.getLinkProperties(net)
                    val ifaceName = lp?.interfaceName ?: "net"
                    lp?.linkAddresses?.forEach { linkAddr ->
                        val addr = linkAddr.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                            val host = addr.hostAddress ?: ""
                            if (isValidIpv4(host)) {
                                ips.add(Pair(ifaceName, host))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error querying ConnectivityManager IPv4: ${e.message}")
        }

        // 2. Query NetworkInterface
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (isValidIpv4(hostAddress)) {
                            ips.add(Pair(intf.name, hostAddress))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error querying NetworkInterface IPv4: ${e.message}")
        }

        // 3. Fallback: WifiManager
        if (ips.isEmpty()) {
            try {
                @Suppress("DEPRECATION")
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipAddressInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipAddressInt != 0) {
                    val ipStr = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipAddressInt and 0xff,
                        ipAddressInt shr 8 and 0xff,
                        ipAddressInt shr 16 and 0xff,
                        ipAddressInt shr 24 and 0xff
                    )
                    if (isValidIpv4(ipStr)) {
                        ips.add(Pair("wlan0", ipStr))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error querying WifiManager IPv4: ${e.message}")
            }
        }

        val res = ips.distinctBy { it.second }.sortedBy { pair ->
            val name = pair.first.lowercase()
            when {
                name.startsWith("wlan") -> 0
                name.startsWith("eth") -> 1
                name.startsWith("rndis") -> 2
                name.startsWith("usb") -> 3
                else -> 4
            }
        }
        Log.d(TAG, "getRawIpv4Addresses returned: $res")
        res
    }

    suspend fun getDeviceIpAddresses(context: Context): List<String> = withContext(Dispatchers.IO) {
        val rawIps = getRawIpv4Addresses(context)
        if (rawIps.isEmpty()) {
            listOf("No IP connected")
        } else {
            rawIps.map { "${it.first}: ${it.second}" }
        }
    }

    fun formatAdbIpToast(rawIps: List<Pair<String, String>>, port: String): String {
        val portStr = if (isAdbPortActive(port)) port.trim() else "5555"
        return if (rawIps.isEmpty()) {
            "ADB over TCP/IP Active (Port $portStr)\nWaiting for network connection..."
        } else if (rawIps.size == 1) {
            "ADB over TCP/IP Active\nadb connect ${rawIps[0].second}:$portStr"
        } else {
            val connectList = rawIps.joinToString("\n") { "adb connect ${it.second}:$portStr (${it.first})" }
            "ADB over TCP/IP Active (Port $portStr)\n$connectList"
        }
    }

    suspend fun buildAdbIpToastMessage(context: Context, port: String): String = withContext(Dispatchers.IO) {
        val rawIps = getRawIpv4Addresses(context)
        formatAdbIpToast(rawIps, port)
    }

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        Log.i(TAG, "Show Toast: $message")
        try {
            val runnable = Runnable {
                try {
                    Toast.makeText(context.applicationContext, message, duration).show()
                } catch (_: Exception) {
                    try {
                        Toast.makeText(context, message, duration).show()
                    } catch (_: Exception) {}
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runnable.run()
            } else {
                Handler(Looper.getMainLooper()).post(runnable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast: ${e.message}")
        }
    }
}
