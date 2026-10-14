package com.net2software.mobile.autousb.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.net2software.mobile.autousb.data.AppPreferences
import com.net2software.mobile.autousb.ui.theme.AutousbTheme

@Composable
fun MainScreen(
    prefs: AppPreferences
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        NavigationItem("Info", Icons.Default.Info),
        NavigationItem("Config", Icons.Default.Settings),
        NavigationItem("Terminal", Icons.Default.Code),
        NavigationItem("Logs", Icons.Default.List)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (selectedTab) {
            0 -> InfoScreen(prefs = prefs, modifier = screenModifier)
            1 -> ConfigScreen(prefs = prefs, modifier = screenModifier)
            2 -> TerminalScreen(prefs = prefs, modifier = screenModifier)
            3 -> LogsScreen(modifier = screenModifier)
        }
    }
}

private data class NavigationItem(
    val label: String,
    val icon: ImageVector
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AutousbTheme {
        MainScreen(prefs = AppPreferences(LocalContext.current))
    }
}
