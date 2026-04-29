package org.xiaobu.autoclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.xiaobu.autoclick.ui.screen.AutoClickScreen
import org.xiaobu.autoclick.ui.screen.AutoTaskScreen
import org.xiaobu.autoclick.ui.screen.MainScreen
import org.xiaobu.autoclick.ui.screen.SettingsScreen
import org.xiaobu.autoclick.ui.screen.TriggerScreen
import org.xiaobu.autoclick.ui.theme.AutoclickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AutoClickApp
        setContent {
            var selectedThemeId by remember { mutableStateOf(app.appSettingsStore.getThemeId()) }
            AutoclickTheme(selectedThemeId = selectedThemeId) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            onOpenAutoClick = { navController.navigate("autoClick") },
                            onOpenAutoTask = { navController.navigate("autoTask") },
                            onOpenTrigger = { navController.navigate("trigger") },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("autoClick") {
                        AutoClickScreen(onBack = { navController.popBackStack() })
                    }
                    composable("autoTask") {
                        AutoTaskScreen(onBack = { navController.popBackStack() })
                    }
                    composable("trigger") {
                        TriggerScreen(onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            selectedThemeId = selectedThemeId,
                            onThemeSelected = { themeId ->
                                app.appSettingsStore.saveThemeId(themeId)
                                selectedThemeId = themeId
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
