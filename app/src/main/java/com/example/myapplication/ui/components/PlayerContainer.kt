package com.example.myapplication.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.music.MusicRepository
import com.example.myapplication.player.PlayerState
import kotlinx.coroutines.launch

/**
 * Apple Music 风格的 Mini Player → Full Screen Player 过渡容器。
 *
 * 动画优化：所有动画值在 graphicsLayer 的 draw 阶段读取（零重组），
 * 条件渲染阈值用 derivedStateOf 缓存，模糊用 RenderEffect 硬件加速。
 */
@Composable
fun PlayerContainer(
    player: PlayerState,
    musicRepository: MusicRepository,
    miniPlayerBottomPadding: Dp = 24.dp,
    homeContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val expandSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val collapseSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // 阈值判定用 derivedStateOf 缓存，动画帧不触发重组，仅跨阈值时重组一次
    val showFullScreen by remember { derivedStateOf { progress.value > 0.01f } }
    val showMiniPlayer by remember { derivedStateOf { progress.value < 0.99f } }

    fun onDragEnd() {
        scope.launch {
            if (progress.value > 0.4f) {
                progress.animateTo(1f, expandSpring)
            } else {
                progress.animateTo(0f, collapseSpring)
            }
        }
    }

    fun onDrag(amount: Float) {
        scope.launch {
            val raw = (progress.value + amount / 800f).coerceIn(0f, 1f)
            val resisted = when {
                raw < 0.2f -> raw * 0.6f
                raw > 0.8f -> 0.8f + (raw - 0.8f) * 0.4f
                else -> raw
            }
            progress.snapTo(resisted)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ============================================================
        // Layer 1: 主页内容（缩放 + 淡出 + 模糊，全部在 draw 阶段更新）
        // ============================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = progress.value
                    val scale = 1f - t * 0.06f
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - t * 0.5f
                    renderEffect = if (Build.VERSION.SDK_INT >= 31 && t > 0.01f) {
                        AndroidRenderEffect.createBlurEffect(24f * t, 24f * t, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    } else {
                        null
                    }
                }
        ) {
            homeContent()
        }

        // ============================================================
        // Layer 2: 全屏播放器（仅展开时渲染，避免折叠状态拦截主页触摸）
        // ============================================================
        if (showFullScreen) {
            Box(modifier = Modifier.fillMaxSize()) {
                FullScreenPlayer(
                    player = player,
                    repository = musicRepository,
                    progress = progress,
                    onDrag = { amount -> onDrag(-amount) },
                    onDragEnd = { onDragEnd() }
                )
            }
        }

        // ============================================================
        // Layer 3: 迷你播放器（底部悬浮；完全展开后移除避免拦截）
        // ============================================================
        if (showMiniPlayer) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = miniPlayerBottomPadding)
            ) {
                MiniPlayer(
                    player = player,
                    progress = progress,
                    onTap = {
                        scope.launch {
                            progress.animateTo(1f, expandSpring)
                        }
                    },
                    onDrag = { amount -> onDrag(amount) },
                    onDragEnd = { onDragEnd() }
                )
            }
        }
    }
}