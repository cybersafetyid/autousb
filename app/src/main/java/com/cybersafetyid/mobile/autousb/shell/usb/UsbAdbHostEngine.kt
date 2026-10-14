package com.cybersafetyid.mobile.autousb.shell.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.cybersafetyid.mobile.autousb.shell.CommandResult
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UsbAdbHostEngine {

    private const val TAG = "AutoUSB"
    const val ACTION_USB_PERMISSION = "com.cybersafetyid.mobile.autousb.USB_PERMISSION"

    private const val A_CNXN = 0x4e584e43
    private const val A_OPEN = 0x4e45504f
    private const val A_OKAY = 0x59414b4f
    private const val A_CLSE = 0x45534c43

    private const val ADB_VERSION = 0x01000000
    private const val MAX_DATA = 1048576

    data class UsbAdbTarget(
        val device: UsbDevice,
        val usbInterface: UsbInterface,
        val endpointIn: UsbEndpoint,
        val endpointOut: UsbEndpoint
    )

    fun findAdbUsbTarget(context: Context): UsbAdbTarget? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        val deviceList = usbManager.deviceList ?: return null

        // Priority 1: Exact ADB match (class 255, subclass 66, protocol 1)
        for (device in deviceList.values) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == 255 && iface.interfaceSubclass == 66 && iface.interfaceProtocol == 1) {
                    val target = extractEndpoints(device, iface)
                    if (target != null) {
                        Log.i(TAG, "findAdbUsbTarget Priority 1 match: ${device.deviceName}")
                        return target
                    }
                }
            }
        }

        // Priority 2: Vendor-specific ADB match (class 255, subclass 66 or 1)
        for (device in deviceList.values) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == 255 && (iface.interfaceSubclass == 66 || iface.interfaceSubclass == 1)) {
                    val target = extractEndpoints(device, iface)
                    if (target != null) {
                        Log.i(TAG, "findAdbUsbTarget Priority 2 match: ${device.deviceName}")
                        return target
                    }
                }
            }
        }

        // Priority 3: Any vendor interface (class 255) with bulk IN and OUT endpoints
        for (device in deviceList.values) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == 255) {
                    val target = extractEndpoints(device, iface)
                    if (target != null) {
                        Log.i(TAG, "findAdbUsbTarget Priority 3 match: ${device.deviceName}")
                        return target
                    }
                }
            }
        }

        // Priority 4: Any non-audio/video USB interface with bulk IN and OUT endpoints
        for (device in deviceList.values) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass != UsbConstants.USB_CLASS_AUDIO && iface.interfaceClass != UsbConstants.USB_CLASS_VIDEO) {
                    val target = extractEndpoints(device, iface)
                    if (target != null) {
                        Log.i(TAG, "findAdbUsbTarget Priority 4 match: ${device.deviceName}")
                        return target
                    }
                }
            }
        }

        Log.d(TAG, "findAdbUsbTarget: No matching USB ADB interface found among ${deviceList.size} devices.")
        return null
    }

    private fun extractEndpoints(device: UsbDevice, iface: UsbInterface): UsbAdbTarget? {
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (j in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(j)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
                if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
            }
        }
        if (epIn != null && epOut != null) {
            return UsbAdbTarget(device, iface, epIn, epOut)
        }
        return null
    }

    suspend fun enableAdbTcpOverUsbOtg(
        context: Context,
        targetPort: Int = 5555,
        onLineOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "UsbAdbHostEngine.enableAdbTcpOverUsbOtg starting for targetPort=$targetPort...")
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            val errMsg = "USB Service unavailable on device."
            Log.e(TAG, errMsg)
            onLineOutput?.invoke("[ERR] $errMsg")
            return@withContext CommandResult(
                command = "tcpip $targetPort",
                exitCode = 1,
                stdout = "",
                stderr = errMsg,
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        val target = findAdbUsbTarget(context)
        if (target == null) {
            val errMsg = "No USB ADB OTG device/cable detected. Please connect USB OTG cable between devices."
            Log.w(TAG, errMsg)
            onLineOutput?.invoke("[ERR] $errMsg")
            return@withContext CommandResult(
                command = "tcpip $targetPort",
                exitCode = 1,
                stdout = "",
                stderr = errMsg,
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        if (!usbManager.hasPermission(target.device)) {
            Log.i(TAG, "Requesting USB permission for ${target.device.deviceName}...")
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                pendingIntentFlags
            )
            usbManager.requestPermission(target.device, permissionIntent)
            val errMsg = "USB Permission requested for ${target.device.deviceName}. Please tap 'OK' on the USB Permission dialog."
            onLineOutput?.invoke("[INFO] $errMsg")
            return@withContext CommandResult(
                command = "tcpip $targetPort",
                exitCode = 1,
                stdout = "",
                stderr = errMsg,
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        var connection: UsbDeviceConnection? = null
        try {
            Log.i(TAG, "Opening USB connection to ${target.device.deviceName}...")
            connection = usbManager.openDevice(target.device)
            if (connection == null || !connection.claimInterface(target.usbInterface, true)) {
                val errMsg = "Failed to claim USB interface for ${target.device.deviceName}."
                Log.e(TAG, errMsg)
                onLineOutput?.invoke("[ERR] $errMsg")
                return@withContext CommandResult(
                    command = "tcpip $targetPort",
                    exitCode = 1,
                    stdout = "",
                    stderr = errMsg,
                    executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // 1. Send CNXN Connection Packet over USB
            Log.d(TAG, "Sending ADB A_CNXN packet...")
            val hostBanner = "host::AutoUSB\u0000".toByteArray(Charsets.UTF_8)
            sendUsbPacket(connection, target.endpointOut, A_CNXN, ADB_VERSION, MAX_DATA, hostBanner)

            // 2. Read CNXN Response
            Log.d(TAG, "Reading ADB A_CNXN response...")
            val resp = readUsbPacket(connection, target.endpointIn)
            if (resp == null) {
                val errMsg = "No response from USB ADB device."
                Log.e(TAG, errMsg)
                onLineOutput?.invoke("[ERR] $errMsg")
                return@withContext CommandResult(
                    command = "tcpip $targetPort",
                    exitCode = 1,
                    stdout = "",
                    stderr = errMsg,
                    executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // 3. Send OPEN "tcpip:<targetPort>" Service Request
            Log.d(TAG, "Sending ADB A_OPEN 'tcpip:$targetPort' packet...")
            val serviceString = "tcpip:$targetPort\u0000"
            val localStreamId = 1
            sendUsbPacket(connection, target.endpointOut, A_OPEN, localStreamId, 0, serviceString.toByteArray(Charsets.UTF_8))

            val openResp = readUsbPacket(connection, target.endpointIn)
            val successMsg = "USB OTG ADB Host: Successfully sent 'tcpip $targetPort' to target device (${target.device.deviceName})!"
            Log.i(TAG, successMsg)
            onLineOutput?.invoke("[SUCCESS] $successMsg")

            CommandResult(
                command = "tcpip $targetPort",
                exitCode = 0,
                stdout = successMsg,
                stderr = "",
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            val errMsg = "USB OTG Transfer failed: ${e.localizedMessage}"
            Log.e(TAG, errMsg, e)
            onLineOutput?.invoke("[ERR] $errMsg")
            CommandResult(
                command = "tcpip $targetPort",
                exitCode = 1,
                stdout = "",
                stderr = errMsg,
                executionMethodUsed = ExecutionMethod.LOCAL_ADB,
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            try {
                connection?.releaseInterface(target.usbInterface)
                connection?.close()
            } catch (_: Exception) {}
        }
    }

    private fun sendUsbPacket(
        conn: UsbDeviceConnection,
        epOut: UsbEndpoint,
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

        val headerBytes = headerBuffer.array()
        conn.bulkTransfer(epOut, headerBytes, headerBytes.size, 3000)
        if (payload.isNotEmpty()) {
            conn.bulkTransfer(epOut, payload, payload.size, 3000)
        }
    }

    private fun readUsbPacket(conn: UsbDeviceConnection, epIn: UsbEndpoint): ByteArray? {
        val headerBytes = ByteArray(24)
        val readCount = conn.bulkTransfer(epIn, headerBytes, 24, 3000)
        if (readCount < 24) return null

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.int // cmd
        buffer.int // arg0
        buffer.int // arg1
        val dataLength = buffer.int

        return if (dataLength in 1 until MAX_DATA) {
            val payload = ByteArray(dataLength)
            conn.bulkTransfer(epIn, payload, dataLength, 3000)
            payload
        } else {
            byteArrayOf()
        }
    }

    private fun calculateCrc(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }
}
