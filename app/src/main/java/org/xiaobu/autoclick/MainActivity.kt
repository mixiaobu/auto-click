package org.xiaobu.autoclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.xiaobu.autoclick.ui.screen.AutoClickScreen
import org.xiaobu.autoclick.ui.theme.AutoclickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoclickTheme {
                AutoClickScreen()
            }
        }
    }
}
