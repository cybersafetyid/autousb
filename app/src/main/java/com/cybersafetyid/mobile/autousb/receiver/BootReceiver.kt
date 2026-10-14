package com.cybersafetyid.mobile.autousb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cybersafetyid.mobile.autousb.MainActivity
import com.cybersafetyid.mobile.autousb.data.AppPreferences
import com.cybersafetyid.mobile.autousb.service.AutoUsbService
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import com.cybersafetyid.mobile.autousb.utils.UsbInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private companion object {
        private const val TAG = "AutoUSB"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "BootReceiver.onReceive action=$action")
        val prefs = AppPreferences(context)

        val isUsbEvent = action == Intent.ACTION_MEDIA_MOUNTED ||
                action == "android.hardware.usb.action.USB_STATE" ||
                action == "android.hardware.usb.action.USB_DEVICE_ATTACHED" ||
                action == "android.hardware.usb.action.USB_ACCESSORY_ATTACHED" ||
                action == Intent.ACTION_POWER_CONNECTED

        val isAutoStartTrigger = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == Intent.ACTION_POWER_CONNECTED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
                action == "android.intent.action.REBOOT" ||
                isUsbEvent

        if (isAutoStartTrigger && prefs.getAutoStartOnBoot()) {
            Log.i(TAG, "BootReceiver triggering AutoUsbService.startService and launchMainActivity...")
            AutoUsbService.startService(context)
            launchMainActivity(context)
        }

        if (isUsbEvent && prefs.getAutoInstallUsbApk()) {
            Log.i(TAG, "BootReceiver triggering UsbInstaller scanAndInstallApksFromUsb...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    UsbInstaller.scanAndInstallApksFromUsb(context, prefs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in UsbInstaller from BootReceiver: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun launchMainActivity(context: Context) {
        try {
            Log.d(TAG, "Attempting standard startActivity for MainActivity...")
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Standard startActivity failed: ${e.message}. Retrying via Root command...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (ShellExecutor.isRootAvailable()) {
                        val pkgName = context.packageName
                        ShellExecutor.executeCommand(
                            "am start -n $pkgName/$pkgName.MainActivity",
                            ExecutionMethod.ROOT
                        )
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Root am start failed: ${ex.message}", ex)
                }
            }
        }
    }
}
