package com.cybersafetyid.mobile.autousb

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.cybersafetyid.mobile.autousb.data.AppPreferences
import com.cybersafetyid.mobile.autousb.service.AutoUsbService
import com.cybersafetyid.mobile.autousb.ui.screens.MainScreen
import com.cybersafetyid.mobile.autousb.ui.theme.AutousbTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission result handled, proceed to start service if configured
            checkAndStartService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        enableEdgeToEdge()

        requestPermissionsIfNeeded()
        checkAndStartService()

        setContent {
            AutousbTheme {
                MainScreen(prefs = prefs)
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAndStartService() {
        if (prefs.getAutoStartOnBoot() && !AutoUsbService.isRunning.value) {
            AutoUsbService.startService(this)
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun MainActivityPreview() {
    AutousbTheme {
        MainScreen(prefs = AppPreferences(LocalContext.current))
    }
}

