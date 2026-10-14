package com.cybersafetyid.mobile.autousb.shell

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {

    fun isShizukuInstalled(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuPermissionGranted(): Boolean {
        return try {
            if (!isShizukuInstalled()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        try {
            if (isShizukuInstalled() && !isShizukuPermissionGranted()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Throwable) {}
    }

    fun execShizukuProcess(
        cmd: Array<String> = arrayOf("sh"),
        env: Array<String>? = null,
        dir: String? = null
    ): Process? {
        return try {
            if (isShizukuPermissionGranted()) {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                method.invoke(null, cmd, env, dir) as? Process
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
