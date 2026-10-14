package com.cybersafetyid.mobile.autousb.shell.adb

import android.content.Context
import com.cybersafetyid.mobile.autousb.shell.CommandResult
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LocalAdbEngine {

    private const val A_CNXN = 0x4e584e43
    private const val A_OPEN = 0x4e45504f
    private const val A_OKAY = 0x59414b4f
    private const val A_CLSE = 0x45534c43
    private const val A_WRTE = 0x45545257

    private const val ADB_VERSION = 0x01000000
    private const val MAX_DATA = 1048576

    data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataCrc: Int,
        val magic: Int,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AdbPacket
            if (command != other.command) return false
            if (arg0 != other.arg0) return false
            if (arg1 != other.arg1) return false
            if (dataLength != other.dataLength) return false
            if (dataCrc != other.dataCrc) return false
            if (magic != other.magic) return false
            return payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = command
            result = 31 * result + arg0
            result = 31 * result + arg1
            result = 31 * result + dataLength
            result = 31 * result + dataCrc
            result = 31 * result + magic
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    fun getLocalListeningPorts(): List<Int> {
        val ports = mutableSetOf<Int>()
        val defaultPorts = listOf(5555, 5554, 5556, 37000, 38000, 39000, 40000, 41000, 42000, 43000, 44000, 45000)
        ports.addAll(defaultPorts)

        val procFiles = listOf(File("/proc/net/tcp"), File("/proc/net/tcp6"))
        for (file in procFiles) {
            if (!file.exists() || !file.canRead()) continue
            try {
                file.useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val localAddr = parts[1]
                            val state = parts[3]
                            if (state == "0A") { // TCP_LISTEN state
                                val portHex = localAddr.substringAfterLast(":", "")
                                val portInt = portHex.toIntOrNull(16)
                                if (portInt != null && portInt in 1024..65535) {
                                    ports.add(portInt)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return ports.toList().sorted()
    }

    suspend fun executeAdbCommand(
        context: Context,
        commandString: String,
        targetPort: Int = 5555,
        adbDaemonPorts: List<Int> = emptyList(),
        onLineOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        val candidatePorts = (getLocalListeningPorts() + adbDaemonPorts).distinct()

        for (port in candidatePorts) {
            try {
                val result = tryExecuteOnPort(commandString, port, onLineOutput)
                if (result != null) {
                    val duration = System.currentTimeMillis() - startTime
                    return@withContext result.copy(durationMs = duration)
                }
            } catch (_: Exception) {}
        }

        val errMsg = "Failed to connect to local ADB daemon on loopback 127.0.0.1."
        stderrBuilder.append(errMsg)
        onLineOutput?.invoke("[ERR] $errMsg")
        val duration = System.currentTimeMillis() - startTime
        CommandResult(
            command = commandString,
            exitCode = 1,
            stdout = stdoutBuilder.toString().trimEnd(),
            stderr = stderrBuilder.toString().trimEnd(),
            executionMethodUsed = ExecutionMethod.LOCAL_ADB,
            durationMs = duration
        )
    }

    private fun tryExecuteOnPort(
        commandString: String,
        port: Int,
        onLineOutput: ((String) -> Unit)?
    ): CommandResult? {
        Socket().use { socket ->
            try {
                socket.connect(InetSocketAddress("127.0.0.1", port), 350)
                socket.soTimeout = 3000

                val inputStream = socket.getInputStream()
                val outputStream = socket.getOutputStream()

                // 1. Send CNXN Connection Packet
                val hostBanner = "host::AutoUSB\u0000".toByteArray(Charsets.UTF_8)
                sendPacket(outputStream, A_CNXN, ADB_VERSION, MAX_DATA, hostBanner)

                // 2. Read CNXN Response
                val responsePacket = readPacket(inputStream) ?: return null
                if (responsePacket.command != A_CNXN && responsePacket.command != A_OKAY) {
                    return null
                }

                val stdoutBuilder = StringBuilder()

                // 3. Construct service payload
                val serviceString = if (commandString.trim() == "5555" || commandString.startsWith("tcpip:")) {
                    val p = commandString.removePrefix("tcpip:").trim().ifBlank { "5555" }
                    "tcpip:$p\u0000"
                } else if (commandString.startsWith("shell:")) {
                    "${commandString.trim()}\u0000"
                } else {
                    "shell:${commandString.trim()}\u0000"
                }

                val localStreamId = 1
                val servicePayload = serviceString.toByteArray(Charsets.UTF_8)
                sendPacket(outputStream, A_OPEN, localStreamId, 0, servicePayload)

                // 4. Read response stream
                var isClosed = false
                val readTimeoutMs = System.currentTimeMillis()
                while (!isClosed && (System.currentTimeMillis() - readTimeoutMs) < 4000) {
                    val pkt = readPacket(inputStream) ?: break
                    when (pkt.command) {
                        A_OKAY -> {
                            // Stream acknowledged
                        }
                        A_WRTE -> {
                            val text = String(pkt.payload, Charsets.UTF_8)
                            stdoutBuilder.append(text)
                            text.split("\n").filter { it.isNotBlank() }.forEach { line ->
                                onLineOutput?.invoke(line)
                            }
                            sendPacket(outputStream, A_OKAY, localStreamId, pkt.arg0, byteArrayOf())
                        }
                        A_CLSE -> {
                            sendPacket(outputStream, A_CLSE, localStreamId, pkt.arg0, byteArrayOf())
                            isClosed = true
                        }
                    }
                }

                val out = stdoutBuilder.toString().trimEnd()
                return CommandResult(
                    command = commandString,
                    exitCode = 0,
                    stdout = if (out.isBlank()) "OK (Port $port)" else out,
                    stderr = "",
                    executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                    durationMs = 0
                )
            } catch (_: Exception) {
                return null
            }
        }
    }

    private fun sendPacket(
        os: OutputStream,
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray
    ) {
        val magic = command xor -0x1
        val crc = calculateCrc(payload)

        val headerBuffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.putInt(command)
        headerBuffer.putInt(arg0)
        headerBuffer.putInt(arg1)
        headerBuffer.putInt(payload.size)
        headerBuffer.putInt(crc)
        headerBuffer.putInt(magic)

        os.write(headerBuffer.array())
        if (payload.isNotEmpty()) {
            os.write(payload)
        }
        os.flush()
    }

    private fun readPacket(inputStream: InputStream): AdbPacket? {
        val headerBytes = ByteArray(24)
        var totalRead = 0
        while (totalRead < 24) {
            val count = inputStream.read(headerBytes, totalRead, 24 - totalRead)
            if (count < 0) return null
            totalRead += count
        }

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val dataCrc = buffer.int
        val magic = buffer.int

        val payload = if (dataLength > 0 && dataLength < MAX_DATA) {
            val pBytes = ByteArray(dataLength)
            var pRead = 0
            while (pRead < dataLength) {
                val c = inputStream.read(pBytes, pRead, dataLength - pRead)
                if (c < 0) break
                pRead += c
            }
            pBytes
        } else {
            byteArrayOf()
        }

        return AdbPacket(command, arg0, arg1, dataLength, dataCrc, magic, payload)
    }

    private fun calculateCrc(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }
}
