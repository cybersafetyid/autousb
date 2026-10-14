package com.cybersafetyid.mobile.autousb.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {

    private val storageContext: Context = run {
        val appContext = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (appContext.isDeviceProtectedStorage) appContext
                else appContext.createDeviceProtectedStorageContext()
            } catch (_: Exception) {
                appContext
            }
        } else {
            appContext
        }
    }

    private val prefs: SharedPreferences =
        storageContext.getSharedPreferences("autousb_prefs", Context.MODE_PRIVATE)

    private val _autoStartOnBoot = MutableStateFlow(getAutoStartOnBoot())
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _autoExecuteOnStart = MutableStateFlow(getAutoExecuteOnStart())
    val autoExecuteOnStart: StateFlow<Boolean> = _autoExecuteOnStart.asStateFlow()

    private val _autoInstallUsbApk = MutableStateFlow(getAutoInstallUsbApk())
    val autoInstallUsbApk: StateFlow<Boolean> = _autoInstallUsbApk.asStateFlow()

    private val _usbApkFileName = MutableStateFlow(getUsbApkFileName())
    val usbApkFileName: StateFlow<String> = _usbApkFileName.asStateFlow()

    private val _executionMethod = MutableStateFlow(getExecutionMethod())
    val executionMethod: StateFlow<ExecutionMethod> = _executionMethod.asStateFlow()

    private val _autoExecuteScript = MutableStateFlow(getAutoExecuteScript())
    val autoExecuteScript: StateFlow<String> = _autoExecuteScript.asStateFlow()

    private val _adbPort = MutableStateFlow(getAdbPort())
    val adbPort: StateFlow<Int> = _adbPort.asStateFlow()

    private val _showNotification = MutableStateFlow(getShowNotification())
    val showNotification: StateFlow<Boolean> = _showNotification.asStateFlow()

    private val _showIpToast = MutableStateFlow(getShowIpToast())
    val showIpToast: StateFlow<Boolean> = _showIpToast.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(getServiceRunning())
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun getAutoStartOnBoot(): Boolean = prefs.getBoolean(KEY_AUTO_START_BOOT, true)
    fun setAutoStartOnBoot(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_BOOT, value).apply()
        _autoStartOnBoot.value = value
    }

    fun getAutoExecuteOnStart(): Boolean = prefs.getBoolean(KEY_AUTO_EXECUTE_START, true)
    fun setAutoExecuteOnStart(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_EXECUTE_START, value).apply()
        _autoExecuteOnStart.value = value
    }

    fun getAutoInstallUsbApk(): Boolean = prefs.getBoolean(KEY_AUTO_INSTALL_USB_APK, true)
    fun setAutoInstallUsbApk(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_INSTALL_USB_APK, value).apply()
        _autoInstallUsbApk.value = value
    }

    fun getUsbApkFileName(): String = prefs.getString(KEY_USB_APK_FILE_NAME, "autousb.apk") ?: "autousb.apk"
    fun setUsbApkFileName(value: String) {
        prefs.edit().putString(KEY_USB_APK_FILE_NAME, value).apply()
        _usbApkFileName.value = value
    }

    fun getExecutionMethod(): ExecutionMethod {
        val str = prefs.getString(KEY_EXECUTION_METHOD, ExecutionMethod.AUTO.name) ?: ExecutionMethod.AUTO.name
        return ExecutionMethod.fromString(str)
    }

    fun setExecutionMethod(method: ExecutionMethod) {
        prefs.edit().putString(KEY_EXECUTION_METHOD, method.name).apply()
        _executionMethod.value = method
    }

    fun getAutoExecuteScript(): String {
        return prefs.getString(KEY_AUTO_EXECUTE_SCRIPT, ShellExecutor.DEFAULT_ADB_SCRIPT)
            ?: ShellExecutor.DEFAULT_ADB_SCRIPT
    }

    fun setAutoExecuteScript(script: String) {
        prefs.edit().putString(KEY_AUTO_EXECUTE_SCRIPT, script).apply()
        _autoExecuteScript.value = script
    }

    fun getAdbPort(): Int = prefs.getInt(KEY_ADB_PORT, 5555)
    fun setAdbPort(port: Int) {
        prefs.edit().putInt(KEY_ADB_PORT, port).apply()
        _adbPort.value = port
    }

    fun getShowNotification(): Boolean = prefs.getBoolean(KEY_SHOW_NOTIFICATION, true)
    fun setShowNotification(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOTIFICATION, value).apply()
        _showNotification.value = value
    }

    fun getShowIpToast(): Boolean = prefs.getBoolean(KEY_SHOW_IP_TOAST, true)
    fun setShowIpToast(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_IP_TOAST, value).apply()
        _showIpToast.value = value
    }

    fun getServiceRunning(): Boolean = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
    fun setServiceRunning(value: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()
        _isServiceRunning.value = value
    }

    companion object {
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_AUTO_EXECUTE_START = "auto_execute_start"
        private const val KEY_AUTO_INSTALL_USB_APK = "auto_install_usb_apk"
        private const val KEY_USB_APK_FILE_NAME = "usb_apk_file_name"
        private const val KEY_EXECUTION_METHOD = "execution_method"
        private const val KEY_AUTO_EXECUTE_SCRIPT = "auto_execute_script"
        private const val KEY_ADB_PORT = "adb_port"
        private const val KEY_SHOW_NOTIFICATION = "show_notification"
        private const val KEY_SHOW_IP_TOAST = "show_ip_toast"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}
