package com.net2software.mobile.autousb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.net2software.mobile.autousb.MainActivity
import com.net2software.mobile.autousb.data.AppPreferences
import com.net2software.mobile.autousb.service.AutoUsbService
import com.net2software.mobile.autousb.shell.ExecutionMethod
import com.net2software.mobile.autousb.shell.ShellExecutor
import com.net2software.mobile.autousb.utils.UsbInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = AppPreferences(context)

        val isUsbEvent = action == Intent.ACTION_MEDIA_MOUNTED ||
                action == "android.hardware.usb.action.USB_DEVICE_ATTACHED"

        val isAutoStartTrigger = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == Intent.ACTION_POWER_CONNECTED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
                action == "android.intent.action.REBOOT" ||
                isUsbEvent

        if (isAutoStartTrigger && prefs.getAutoStartOnBoot()) {
            AutoUsbService.startService(context)
            launchMainActivity(context)
        }

        if (isUsbEvent && prefs.getAutoInstallUsbApk()) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    UsbInstaller.scanAndInstallApksFromUsb(context, prefs)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun launchMainActivity(context: Context) {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        } catch (_: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (ShellExecutor.isRootAvailable()) {
                        val pkgName = context.packageName
                        ShellExecutor.executeCommand(
                            "am start -n $pkgName/$pkgName.MainActivity",
                            ExecutionMethod.ROOT
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }
}

