package com.net2software.mobile.autousb.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.net2software.mobile.autousb.data.AppPreferences
import com.net2software.mobile.autousb.data.LogRepository
import com.net2software.mobile.autousb.shell.CommandResult
import com.net2software.mobile.autousb.shell.ExecutionMethod
import com.net2software.mobile.autousb.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UsbInstaller {

    data class ApkMetadata(
        val file: File,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val isTargetApp: Boolean,
        val isVersionChangedOrNewer: Boolean
    )

    suspend fun scanAndInstallApksFromUsb(
        context: Context,
        prefs: AppPreferences
    ): String = withContext(Dispatchers.IO) {
        if (!prefs.getAutoInstallUsbApk()) {
            return@withContext "Auto USB APK install is disabled in settings."
        }

        val targetPattern = prefs.getUsbApkFileName().lowercase()
        val mountPaths = listOf(
            File("/storage"),
            File("/mnt/media_rw"),
            File("/mnt/usb"),
            File("/storage/usbdrive"),
            File("/storage/usbdisk"),
            File("/storage/udisk"),
            Environment.getExternalStorageDirectory()
        )

        val foundApkFiles = mutableListOf<File>()

        for (baseDir in mountPaths) {
            if (baseDir.exists() && baseDir.isDirectory) {
                // Search base directory directly
                foundApkFiles.addAll(searchApksInDir(baseDir, targetPattern))

                val subDirs = baseDir.listFiles() ?: continue
                for (sub in subDirs) {
                    if (sub.isDirectory && sub.name != "emulated" && sub.name != "self") {
                        foundApkFiles.addAll(searchApksInDir(sub, targetPattern))
                    }
                }
            }
        }

        val uniqueApks = foundApkFiles.distinctBy { it.absolutePath }

        if (uniqueApks.isEmpty()) {
            return@withContext "No APK files found on USB drive matching pattern '$targetPattern'."
        }

        val currentPackageInfo = getCurrentPackageInfo(context)
        val currentVersionCode = getVersionCode(currentPackageInfo)
        val currentVersionName = currentPackageInfo?.versionName ?: "unknown"

        val metadataList = uniqueApks.mapNotNull { apkFile ->
            getApkMetadata(context, apkFile, currentVersionName, currentVersionCode, targetPattern)
        }

        if (metadataList.isEmpty()) {
            return@withContext "Found ${uniqueApks.size} APKs on USB, but none match target version criteria."
        }

        val isRoot = ShellExecutor.isRootAvailable()
        val resultsSummary = StringBuilder()

        for (meta in metadataList) {
            val apkFile = meta.file
            val apkPath = apkFile.absolutePath

            val versionStatus = if (meta.isVersionChangedOrNewer) {
                "NEW VERSION DETECTED (v${meta.versionName} code ${meta.versionCode} vs installed v${currentVersionName} code ${currentVersionCode})"
            } else {
                "RE-INSTALL VERSION (v${meta.versionName})"
            }

            if (isRoot) {
                val installCmd = "pm install -r \"$apkPath\""
                val res = ShellExecutor.executeCommand(installCmd, ExecutionMethod.ROOT)

                val logEntry = CommandResult(
                    command = "Auto-Install/Update APK: ${apkFile.name} [$versionStatus]",
                    exitCode = res.exitCode,
                    stdout = res.stdout,
                    stderr = res.stderr,
                    executionMethodUsed = ExecutionMethod.ROOT,
                    durationMs = res.durationMs
                )
                LogRepository.addLog(logEntry)

                if (res.isSuccess) {
                    resultsSummary.append("Successfully Auto-Installed/Updated via Root: ${apkFile.name} [$versionStatus]\n")
                } else {
                    resultsSummary.append("Root install failed for ${apkFile.name}: ${res.stderr}\n")
                }
            } else {
                withContext(Dispatchers.Main) {
                    launchSystemInstaller(context, apkFile)
                }
                resultsSummary.append("Launched Installer for ${apkFile.name} [$versionStatus]\n")
            }
        }

        return@withContext resultsSummary.toString().trim()
    }

    private fun getApkMetadata(
        context: Context,
        apkFile: File,
        currentVersionName: String,
        currentVersionCode: Long,
        targetPattern: String
    ): ApkMetadata? {
        val pm = context.packageManager
        val archiveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        } catch (_: Exception) {
            null
        } ?: return null

        val apkPackageName = archiveInfo.packageName ?: context.packageName
        val apkVersionName = archiveInfo.versionName ?: ""
        val apkVersionCode = getVersionCode(archiveInfo)

        val isMyPackage = apkPackageName.equals(context.packageName, ignoreCase = true)
        val isNameMatch = targetPattern == "*" ||
                targetPattern == "*.apk" ||
                apkFile.name.lowercase().contains("autousb") ||
                apkFile.name.lowercase() == targetPattern

        if (!isMyPackage && !isNameMatch) {
            return null
        }

        val isVersionChangedOrNewer = apkVersionCode > currentVersionCode ||
                (!apkVersionName.equals(currentVersionName, ignoreCase = true) && apkVersionName.isNotBlank())

        return ApkMetadata(
            file = apkFile,
            packageName = apkPackageName,
            versionName = apkVersionName,
            versionCode = apkVersionCode,
            isTargetApp = true,
            isVersionChangedOrNewer = isVersionChangedOrNewer
        )
    }

    private fun getCurrentPackageInfo(context: Context): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getVersionCode(packageInfo: PackageInfo?): Long {
        if (packageInfo == null) return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun searchApksInDir(dir: File, targetPattern: String): List<File> {
        val list = mutableListOf<File>()
        try {
            val files = dir.listFiles() ?: return list
            for (f in files) {
                if (f.isFile && f.name.lowercase().endsWith(".apk")) {
                    val nameLower = f.name.lowercase()
                    if (targetPattern == "*" ||
                        targetPattern == "*.apk" ||
                        nameLower.contains("autousb") ||
                        nameLower == targetPattern
                    ) {
                        list.add(f)
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun launchSystemInstaller(context: Context, apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
