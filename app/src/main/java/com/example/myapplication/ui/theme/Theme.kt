package com.example.myapplication.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ResoundColorScheme = darkColorScheme(
    primary = ResoundAccent,
    onPrimary = Color.White,
    primaryContainer = ResoundAccent.copy(alpha = 0.18f),
    onPrimaryContainer = ResoundAccent,
    background = ResoundBg,
    onBackground = ResoundInk,
    surface = ResoundBg,
    onSurface = ResoundInk,
    surfaceVariant = ResoundSurface,
    onSurfaceVariant = ResoundMuted,
    outline = ResoundFaint,
    outlineVariant = GlassLine,
    error = Color(0xFFFF6B7A),
    onError = Color.White
)

private val ResoundLightColorScheme = lightColorScheme(
    primary = ResoundAccentLight,
    onPrimary = Color.White,
    primaryContainer = ResoundAccentLight.copy(alpha = 0.15f),
    onPrimaryContainer = ResoundAccentLight,
    background = ResoundBgLight,
    onBackground = ResoundInkLight,
    surface = ResoundSurfaceLight,
    onSurface = ResoundInkLight,
    surfaceVariant = Color(0xFFF1F0EE),
    onSurfaceVariant = ResoundMutedLight,
    outline = ResoundFaintLight,
    outlineVariant = Color(0xFFE4E2DE),
    error = Color(0xFFB3261E),
    onError = Color.White
)

/**
 * 余音主题。
 * @param darkTheme 深色 / 浅色模式（由设置中的「深色模式」开关控制）。
 * @param dynamicColor Android 12+ 启用 Material You 动态颜色（跟随壁纸主题色）。
 */
@Composable
fun ResoundTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    fontStyle: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ResoundColorScheme
        else -> ResoundLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typographyFor(fontStyle),
        content = content
    )
}
