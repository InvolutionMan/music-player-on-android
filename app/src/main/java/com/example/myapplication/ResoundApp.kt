package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.myapplication.ui.MainScreen
import com.example.myapplication.ui.components.LocalGlassOpacity
import com.example.myapplication.ui.components.ambientGlow
import com.example.myapplication.player.PlayerState
import com.example.myapplication.player.SettingsState
import kotlinx.coroutines.delay

@Composable
fun ResoundApp(settings: SettingsState) {
    val player = remember { PlayerState() }

    // 播放计时：每秒推进一次进度
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            delay(1000)
            player.tick()
        }
    }

    val track = player.track
    val blurFactor = (settings.blurRadius - 12f) / 30f
    val glowTop = if (settings.coverTint) track.coverColors.first().copy(alpha = 0.30f)
    else Color.White.copy(alpha = 0.05f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .ambientGlow(glowTop, cx = 0.5f, cy = 0.18f, radiusFactor = 1.35f)
            .ambientGlow(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + 0.06f * blurFactor), cx = 0.85f, cy = 0.88f, radiusFactor = 1.1f)
    ) {
        CompositionLocalProvider(LocalGlassOpacity provides settings.glassOpacity.coerceIn(0f, 1f)) {
            MainScreen(player = player, settings = settings)
        }
    }
}
