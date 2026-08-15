package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.myapplication.player.SettingsState
import com.example.myapplication.ui.theme.ResoundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings = remember { SettingsState(this) }

            LaunchedEffect(settings.darkMode) {
                val style = if (settings.darkMode) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            ResoundTheme(darkTheme = settings.darkMode, dynamicColor = false, fontStyle = settings.fontStyle) {
                ResoundApp(settings)
            }
        }
    }
}
