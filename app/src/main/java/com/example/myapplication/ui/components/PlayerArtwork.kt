package com.example.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Track
import com.example.myapplication.ui.icons.AppIcons

/**
 * 专辑封面内容（纯视觉，无定位）。
 * 有真实封面图（网易云）时显示图片，否则显示渐变 + 音符图标。
 * 由外部控制大小和透明度。
 */
@Composable
fun ArtworkContent(
    track: Track,
    size: Dp,
    cornerRadius: Dp = 12.dp,
    shadowElevation: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .shadow(shadowElevation, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(track.coverColors))
    ) {
        val art = track.artwork
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val iconSize = (size * 0.28f).coerceIn(20.dp, 84.dp)
            Icon(
                imageVector = AppIcons.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * 迷你播放器中的 artwork（40dp，随过渡缩放淡出）。
 * 动画在 graphicsLayer draw 阶段读取 progress，不触发重组。
 */
@Composable
fun MiniArtwork(
    track: Track,
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier
) {
    ArtworkContent(
        track = track,
        size = 40.dp,
        cornerRadius = 10.dp,
        modifier = modifier.graphicsLayer {
            val t = progress.value.coerceIn(0f, 1f)
            scaleX = 1f - t * 0.3f
            scaleY = 1f - t * 0.3f
            alpha = 1f - t
        }
    )
}

/**
 * 全屏播放器中的 artwork（320dp，随过渡缩放淡入）。
 * 动画在 graphicsLayer draw 阶段读取 progress，不触发重组。
 */
@Composable
fun FullArtwork(
    track: Track,
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier
) {
    ArtworkContent(
        track = track,
        size = 320.dp,
        cornerRadius = 30.dp,
        shadowElevation = 34.dp,
        modifier = modifier.graphicsLayer {
            val t = progress.value.coerceIn(0f, 1f)
            scaleX = t.coerceAtLeast(0.01f)
            scaleY = t.coerceAtLeast(0.01f)
            alpha = t
        }
    )
}