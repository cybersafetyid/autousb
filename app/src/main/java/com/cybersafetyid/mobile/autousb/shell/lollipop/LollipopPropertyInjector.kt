package com.cybersafetyid.mobile.autousb.shell.lollipop

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.provider.Settings
import android.util.Log
import com.cybersafetyid.mobile.autousb.shell.CommandResult
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LollipopPropertyInjector {

    private const val TAG = "AutoUSB"
    private const val PROP_MSG_SETPROP = 1

    fun setGlobalSettingsAdbPort(context: Context, port: Int = 5555): Boolean {
        return try {
            val cr = context.contentResolver
            val res1 = Settings.Global.putInt(cr, "adb_port", port)
            val res2 = Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            val res3 = Settings.Global.putInt(cr, "adb_enabled", 1)
            Log.d(TAG, "setGlobalSettingsAdbPort adb_port=$port -> res1=$res1, res2=$res2, res3=$res3")
            res1 || res2 || res3
        } catch (e: Throwable) {
            Log.d(TAG, "setGlobalSettingsAdbPort failed: ${e.message}")
            false
        }
    }

    suspend fun setSystemPropertyViaReflection(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, key, value)
            Log.d(TAG, "SystemProperties.set reflection key=$key, value=$value succeeded.")
            true
        } catch (e: Throwable) {
            Log.d(TAG, "SystemProperties.set reflection key=$key failed: ${e.message}")
            false
        }
    }

    suspend fun sendRawPropertyServiceMessage(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        var socket: LocalSocket? = null
        try {
            socket = LocalSocket()
            socket.connect(LocalSocketAddress("property_service", LocalSocketAddress.Namespace.RESERVED))
            val os: OutputStream = socket.outputStream

            val buffer = ByteBuffer.allocate(252).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(PROP_MSG_SETPROP)

            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val keyPadded = ByteArray(32)
            System.arraycopy(keyBytes, 0, keyPadded, 0, minOf(keyBytes.size, 32))
            buffer.put(keyPadded)

            val valBytes = value.toByteArray(Charsets.UTF_8)
            val valPadded = ByteArray(216)
            System.arraycopy(valBytes, 0, valPadded, 0, minOf(valBytes.size, 216))
            buffer.put(valPadded)

            os.write(buffer.array())
            os.flush()

            Log.d(TAG, "sendRawPropertyServiceMessage socket write key=$key, value=$value succeeded.")
            true
        } catch (e: Throwable) {
            Log.d(TAG, "sendRawPropertyServiceMessage socket write key=$key failed: ${e.message}")
            false
        } finally {
            try {
                socket?.close()
            } catch (_: Throwable) {}
        }
    }

    suspend fun enableAdbTcpPortIndependently(
        context: Context,
        targetPort: Int = 5555,
        onLineOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "LollipopPropertyInjector.enableAdbTcpPortIndependently starting for targetPort=$targetPort...")
        onLineOutput?.invoke("[INFO] Lollipop Property Injector: Setting service.adb.tcp.port = $targetPort...")

        val portStr = targetPort.toString()

        // 1. Try Settings.Global database modification
        val settingsRes = setGlobalSettingsAdbPort(context, targetPort)
        onLineOutput?.invoke("[INFO] Settings.Global adb_port set -> $settingsRes")

        // 2. Try reflection on android.os.SystemProperties
        val ref1 = setSystemPropertyViaReflection("service.adb.tcp.port", portStr)
        val ref2 = setSystemPropertyViaReflection("persist.adb.tcp.port", portStr)
        setSystemPropertyViaReflection("ctl.stop", "adbd")
        setSystemPropertyViaReflection("ctl.start", "adbd")
        setSystemPropertyViaReflection("ctl.restart", "adbd")

        onLineOutput?.invoke("[INFO] Reflection SystemProperties set: service.adb.tcp.port -> $ref1, persist.adb.tcp.port -> $ref2")

        // 3. Try raw socket injection to /dev/socket/property_service
        val sock1 = sendRawPropertyServiceMessage("service.adb.tcp.port", portStr)
        val sock2 = sendRawPropertyServiceMessage("persist.adb.tcp.port", portStr)
        sendRawPropertyServiceMessage("ctl.stop", "adbd")
        sendRawPropertyServiceMessage("ctl.start", "adbd")
        sendRawPropertyServiceMessage("ctl.restart", "adbd")

        onLineOutput?.invoke("[INFO] Raw Socket property_service set: service.adb.tcp.port -> $sock1, persist.adb.tcp.port -> $sock2")

        // 4. Verify if port 5555 is listening on local socket
        val isListening = ShellExecutor.isLocalPortListening(targetPort)
        val duration = System.currentTimeMillis() - startTime

        if (isListening) {
            val msg = "Success: ADB TCP Port $targetPort is now ACTIVE on local device!"
            Log.i(TAG, msg)
            onLineOutput?.invoke("[SUCCESS] $msg")
            CommandResult(
                command = "setprop service.adb.tcp.port $targetPort",
                exitCode = 0,
                stdout = msg,
                stderr = "",
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = duration
            )
        } else {
            val msg = "Property set executed (Settings: $settingsRes, Reflection: $ref1, Socket: $sock1)."
            Log.w(TAG, "Property set executed but local port $targetPort is not listening yet.")
            CommandResult(
                command = "setprop service.adb.tcp.port $targetPort",
                exitCode = if (settingsRes || ref1 || sock1) 0 else 1,
                stdout = msg,
                stderr = if (settingsRes || ref1 || sock1) "" else "Property service request denied.",
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = duration
            )
        }
    }
}
