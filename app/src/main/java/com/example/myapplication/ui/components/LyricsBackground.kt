package com.example.myapplication.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.example.myapplication.data.Track

/**
 * 歌词页沉浸式背景：模糊专辑封面 + 暗色渐变遮罩。
 *
 * 结构（Apple Music 风格）：
 *   Box
 *   ├── 专辑封面（放大铺满全屏，ContentScale.Crop 语义）
 *   ├── Gaussian Blur（API 31+ 用 RenderEffect 实时模糊；低版本降级为无模糊，渐变本身平滑）
 *   ├── 暗色渐变遮罩（顶部较暗 → 中间适中 → 底部明显变暗）
 *   └── 歌词层（由外部叠加）
 *
 * 切歌时新封面从旧封面上交叉淡入淡出（300~600ms，Ease-In-Out）。
 * 当前 App 的专辑封面用 coverColors 渐变表示；若替换为真实图片（URL），
 * 只需把渐变 Box 换成 Coil AsyncImage 即可，其余结构不变。
 */
@Composable
fun LyricsBackground(
    track: Track,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 专辑封面背景（放大铺满，切歌交叉淡入淡出）
        Crossfade(
            targetState = track,
            animationSpec = tween(450, easing = FastOutSlowInEasing),
            label = "lyricsBgCrossfade"
        ) { currentTrack ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 整体降低亮度 / 透明度
                        alpha = 0.72f
                        // 明显高斯模糊（仅 API 31+）
                        if (Build.VERSION.SDK_INT >= 31) {
                            renderEffect = AndroidRenderEffect.createBlurEffect(
                                48f, 48f, Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    }
                    .background(Brush.linearGradient(currentTrack.coverColors))
            )
        }

        // 暗色渐变遮罩：顶部较暗 → 中间适中 → 底部明显变暗
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.62f)
                        )
                    )
                )
        )
    }
}