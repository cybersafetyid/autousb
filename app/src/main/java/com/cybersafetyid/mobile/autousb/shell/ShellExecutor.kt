package com.cybersafetyid.mobile.autousb.shell

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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

    const val DEFAULT_ADB_SCRIPT = "setprop service.adb.tcp.port 5555\nsetprop persist.adb.tcp.port 5555\nsetprop ctl.restart adbd\nstop adbd\nstart adbd"

    @Volatile
    private var cachedRootCmd: List<String>? = null

    fun clearRootCache() {
        cachedRootCmd = null
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (cachedRootCmd != null) return@withContext true

        val candidates = listOf(
            listOf("su"),
            listOf("su", "0"),
            listOf("su", "-c", "id"),
            listOf("/system/xbin/su"),
            listOf("/system/bin/su")
        )

        for (cmd in candidates) {
            try {
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
                    cachedRootCmd = cmd
                    return@withContext true
                }
            } catch (_: Exception) {}
        }
        return@withContext false
    }

    suspend fun executeCommand(
        script: String,
        preferredMethod: ExecutionMethod,
        onLineOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val isRoot = isRootAvailable()

        val effectiveMethod = when (preferredMethod) {
            ExecutionMethod.AUTO -> if (isRoot) ExecutionMethod.ROOT else ExecutionMethod.NON_ROOT
            ExecutionMethod.ROOT -> ExecutionMethod.ROOT
            ExecutionMethod.NON_ROOT -> ExecutionMethod.NON_ROOT
        }

        if (effectiveMethod == ExecutionMethod.ROOT && !isRoot) {
            val errMsg = "Root permission (su) is required for this command, but Root is not available or denied."
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

        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()
        val exitCode: Int

        try {
            val cmdList = if (effectiveMethod == ExecutionMethod.ROOT) {
                cachedRootCmd ?: listOf("su")
            } else {
                listOf("sh")
            }
            val process = ProcessBuilder(cmdList).start()
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
                        onLineOutput?.invoke("[ERR] $currentLine")
                    }
                } catch (_: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            // Feed commands line-by-line into root/shell stdin
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
            val errorMsg = "Execution failed: ${e.localizedMessage ?: e.message}"
            stderrBuilder.append(errorMsg)
            onLineOutput?.invoke("[ERR] $errorMsg")
            val duration = System.currentTimeMillis() - startTime
            return@withContext CommandResult(
                command = script,
                exitCode = -1,
                stdout = stdoutBuilder.toString().trimEnd(),
                stderr = stderrBuilder.toString().trimEnd(),
                executionMethodUsed = effectiveMethod,
                durationMs = duration
            )
        }

        val duration = System.currentTimeMillis() - startTime
        CommandResult(
            command = script,
            exitCode = exitCode,
            stdout = stdoutBuilder.toString().trimEnd(),
            stderr = stderrBuilder.toString().trimEnd(),
            executionMethodUsed = effectiveMethod,
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

        if (port.isBlank() || port == "-1") "Disabled / None" else port
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
        } catch (_: Exception) {}

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
        } catch (_: Exception) {}

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
            } catch (_: Exception) {}
        }

        ips.distinctBy { it.second }.sortedBy { pair ->
            val name = pair.first.lowercase()
            when {
                name.startsWith("wlan") -> 0
                name.startsWith("eth") -> 1
                name.startsWith("rndis") -> 2
                name.startsWith("usb") -> 3
                else -> 4
            }
        }
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
        } catch (_: Exception) {}
    }
}
