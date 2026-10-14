package com.net2software.mobile.autousb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.net2software.mobile.autousb.MainActivity
import com.net2software.mobile.autousb.R
import com.net2software.mobile.autousb.data.AppPreferences
import com.net2software.mobile.autousb.data.LogRepository
import com.net2software.mobile.autousb.shell.ShellExecutor
import com.net2software.mobile.autousb.utils.UsbInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AutoUsbService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: AppPreferences
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkReceiverRegistered = false
    private var lastNotifiedIpSummary: String = ""
    private var ipDiscoveryJob: Job? = null

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            triggerNetworkCheck()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        _isRunning.value = true
        prefs.setServiceRunning(true)
        startForegroundServiceNotification("AutoUSB Service Running")
        registerNetworkMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (prefs.getAutoExecuteOnStart()) {
            executeAutoScript()
        } else {
            triggerNetworkCheck()
        }

        return START_STICKY
    }

    private fun executeAutoScript() {
        serviceScope.launch {
            val script = prefs.getAutoExecuteScript()
            val method = prefs.getExecutionMethod()
            _lastExecutionStatus.value = "Executing auto-script..."

            val result = ShellExecutor.executeCommand(script, method)
            LogRepository.addLog(result)

            if (prefs.getAutoInstallUsbApk()) {
                val usbSummary = UsbInstaller.scanAndInstallApksFromUsb(this@AutoUsbService, prefs)
                if (usbSummary.isNotBlank()) {
                    _lastExecutionStatus.value = usbSummary
                }
            }

            val port = ShellExecutor.getAdbTcpPort()
            val isAdbActive = ShellExecutor.isAdbPortActive(port)

            if (isAdbActive) {
                triggerNetworkCheck(forceToast = false)
                startIpDiscoveryJob(maxAttempts = 30, intervalMs = 1500L)
            } else if (result.isSuccess) {
                val statusText = "ADB Port: $port"
                _lastExecutionStatus.value = statusText
                updateNotification(statusText)
            } else {
                val rawErr = result.stderr.trim()
                val isRootErr = rawErr.contains("must be root", ignoreCase = true) ||
                        rawErr.contains("Root permission", ignoreCase = true) ||
                        rawErr.contains("Permission denied", ignoreCase = true)
                val errDetail = when {
                    isRootErr -> "Root access required to enable ADB port ($rawErr)"
                    rawErr.isNotBlank() -> rawErr.take(120)
                    else -> "Exit code ${result.exitCode}"
                }
                val statusText = "Failed: $errDetail"
                _lastExecutionStatus.value = statusText
                updateNotification(statusText)
            }
        }
    }

    private fun triggerNetworkCheck(forceToast: Boolean = false) {
        serviceScope.launch {
            val port = ShellExecutor.getAdbTcpPort()
            if (!ShellExecutor.isAdbPortActive(port)) return@launch

            val ipPairs = ShellExecutor.getRawIpv4Addresses(this@AutoUsbService)
            if (ipPairs.isNotEmpty()) {
                val ipSummary = ipPairs.joinToString(",") { "${it.first}:${it.second}" }
                val primaryIp = ipPairs.first().second
                val statusText = "ADB Active: adb connect $primaryIp:$port"
                val toastMessage = ShellExecutor.buildAdbIpToastMessage(this@AutoUsbService, port)

                _lastExecutionStatus.value = statusText
                updateNotification(
                    contentText = "adb connect $primaryIp:$port",
                    title = "AutoUSB - ADB Active (Port $port)",
                    bigText = toastMessage
                )

                val shouldShowToast = prefs.getShowIpToast() &&
                        (forceToast || (ipSummary != lastNotifiedIpSummary))

                if (shouldShowToast) {
                    lastNotifiedIpSummary = ipSummary
                    ShellExecutor.showToast(this@AutoUsbService, toastMessage)
                }
            } else {
                val statusText = "ADB Wireless Active on Port: $port"
                _lastExecutionStatus.value = statusText
                updateNotification(statusText, title = "AutoUSB - ADB Active")
                lastNotifiedIpSummary = ""
                startIpDiscoveryJob(maxAttempts = 60, intervalMs = 2000L)
            }
        }
    }

    private fun startIpDiscoveryJob(maxAttempts: Int = 60, intervalMs: Long = 2000L) {
        ipDiscoveryJob?.cancel()
        ipDiscoveryJob = serviceScope.launch {
            for (attempt in 1..maxAttempts) {
                delay(if (attempt == 1) 500L else intervalMs)
                val port = ShellExecutor.getAdbTcpPort()
                if (!ShellExecutor.isAdbPortActive(port)) continue

                val ipPairs = ShellExecutor.getRawIpv4Addresses(this@AutoUsbService)
                if (ipPairs.isNotEmpty()) {
                    val ipSummary = ipPairs.joinToString(",") { "${it.first}:${it.second}" }
                    val primaryIp = ipPairs.first().second
                    val statusText = "ADB Active: adb connect $primaryIp:$port"
                    val toastMessage = ShellExecutor.buildAdbIpToastMessage(this@AutoUsbService, port)

                    _lastExecutionStatus.value = statusText
                    updateNotification(
                        contentText = "adb connect $primaryIp:$port",
                        title = "AutoUSB - ADB Active (Port $port)",
                        bigText = toastMessage
                    )

                    if (prefs.getShowIpToast() && ipSummary != lastNotifiedIpSummary) {
                        lastNotifiedIpSummary = ipSummary
                        ShellExecutor.showToast(this@AutoUsbService, toastMessage)
                    }
                    break
                }
            }
        }
    }

    private fun registerNetworkMonitoring() {
        registerNetworkCallback()
        registerNetworkBroadcastReceiver()
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) return

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    triggerNetworkCheck()
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    triggerNetworkCheck()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    triggerNetworkCheck()
                }

                override fun onLost(network: Network) {
                    triggerNetworkCheck()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    networkCallback?.let {
                        connectivityManager?.registerDefaultNetworkCallback(it)
                    }
                } catch (_: Exception) {
                    val request = NetworkRequest.Builder().build()
                    networkCallback?.let {
                        connectivityManager?.registerNetworkCallback(request, it)
                    }
                }
            } else {
                val request = NetworkRequest.Builder().build()
                networkCallback?.let {
                    connectivityManager?.registerNetworkCallback(request, it)
                }
            }
        } catch (_: Exception) {}
    }

    private fun registerNetworkBroadcastReceiver() {
        if (isNetworkReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                @Suppress("DEPRECATION")
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction("android.net.wifi.STATE_CHANGE")
                addAction("android.net.wifi.WIFI_STATE_CHANGED")
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(networkReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(networkReceiver, filter)
            }
            isNetworkReceiverRegistered = true
        } catch (_: Exception) {
            try {
                registerReceiver(networkReceiver, IntentFilter().apply {
                    @Suppress("DEPRECATION")
                    addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                })
                isNetworkReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        networkCallback = null
    }

    private fun unregisterNetworkBroadcastReceiver() {
        if (!isNetworkReceiverRegistered) return
        try {
            unregisterReceiver(networkReceiver)
        } catch (_: Exception) {}
        isNetworkReceiverRegistered = false
    }

    private fun startForegroundServiceNotification(contentText: String) {
        createNotificationChannel()

        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (_: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        contentText: String,
        title: String = "AutoUSB Background Service",
        bigText: String? = null
    ) {
        if (!prefs.getShowNotification()) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText, title, bigText))
    }

    private fun buildNotification(
        contentText: String,
        title: String = "AutoUSB Background Service",
        bigText: String? = null
    ): Notification {
        return try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

            val stopIntent = Intent(this, AutoUsbService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingIntentFlags)

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            try {
                builder.setColor(getNotificationAccentColor())
            } catch (_: Throwable) {
                // Accent color styling is optional; safely ignore on older Android / custom ROMs
            }

            try {
                val largeIcon = getNotificationLargeIcon()
                if (largeIcon != null) {
                    builder.setLargeIcon(largeIcon)
                }
            } catch (_: Throwable) {
                // Large icon is optional; fallback safely if unavailable
            }

            if (!bigText.isNullOrBlank()) {
                try {
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                } catch (_: Throwable) {
                    // Ignore style failure
                }
            }

            builder.build()
        } catch (_: Throwable) {
            // Absolute safe fallback notification for legacy Android (API 21-22) / non-standard ROMs
            createSafeFallbackNotification(title, contentText)
        }
    }

    private fun getNotificationAccentColor(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getColor(R.color.terminal_green)
            } else {
                @Suppress("DEPRECATION")
                resources.getColor(R.color.terminal_green)
            }
        } catch (_: Throwable) {
            0xFF00E676.toInt()
        }
    }

    private fun getNotificationLargeIcon(): Bitmap? {
        return try {
            val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getDrawable(R.mipmap.ic_launcher)
            } else {
                @Suppress("DEPRECATION")
                resources.getDrawable(R.mipmap.ic_launcher)
            } ?: return null

            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun createSafeFallbackNotification(title: String, contentText: String): Notification {
        return try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .build()
        } catch (_: Throwable) {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoUSB Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows status of AutoUSB background command service"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        unregisterNetworkBroadcastReceiver()
        ipDiscoveryJob?.cancel()
        _isRunning.value = false
        prefs.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "autousb_service_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.net2software.mobile.autousb.STOP_SERVICE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastExecutionStatus = MutableStateFlow("Service initialized")
        val lastExecutionStatus: StateFlow<String> = _lastExecutionStatus.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, AutoUsbService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AutoUsbService::class.java)
            context.stopService(intent)
        }

        fun triggerExecute(context: Context) {
            val intent = Intent(context, AutoUsbService::class.java)
            context.startService(intent)
        }
    }
}
