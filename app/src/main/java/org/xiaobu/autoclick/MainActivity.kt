package org.xiaobu.autoclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.xiaobu.autoclick.ui.screen.AutoClickScreen
import org.xiaobu.autoclick.ui.screen.AutoTaskScreen
import org.xiaobu.autoclick.ui.screen.MainScreen
import org.xiaobu.autoclick.ui.screen.TriggerScreen
import org.xiaobu.autoclick.ui.theme.AutoclickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoclickTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            onOpenAutoClick = { navController.navigate("autoClick") },
                            onOpenAutoTask = { navController.navigate("autoTask") },
                            onOpenTrigger = { navController.navigate("trigger") }
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
                }
            }
        }
    }
}
