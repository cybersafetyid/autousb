package com.cybersafetyid.mobile.autousb.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import java.io.File
import java.io.FileOutputStream

object BundledApkInstaller {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val BUNDLED_SHIZUKU_ASSET = "shizuku.apk"

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun extractBundledShizukuApk(context: Context): File? {
        return try {
            val assetStream = context.assets.open(BUNDLED_SHIZUKU_ASSET)
            val outFile = File(context.cacheDir, "shizuku_bundled.apk")
            FileOutputStream(outFile).use { fos ->
                assetStream.copyTo(fos)
            }
            assetStream.close()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun installBundledShizuku(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "Shizuku requires Android 6.0 (API 23) or higher. It is not supported on Android 5.0 Lollipop."
        }

        val apkFile = extractBundledShizukuApk(context)
            ?: return "Failed to extract bundled Shizuku APK from assets."

        val isRoot = ShellExecutor.isRootAvailable()
        if (isRoot) {
            val result = ShellExecutor.executeCommand(
                "pm install -r \"${apkFile.absolutePath}\"",
                ExecutionMethod.ROOT
            )
            return if (result.isSuccess) {
                "Shizuku APK installed successfully via Root!"
            } else {
                "Root install failed: ${result.stderr}"
            }
        } else {
            return try {
                val contentUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Prompting Android System Package Installer for Shizuku..."
            } catch (e: Exception) {
                "Failed to launch installer: ${e.localizedMessage}"
            }
        }
    }
}
