package com.example.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * 播放器展开/收起过渡动画参数。
 */
@Stable
class PlayerTransitionState(
    initialProgress: Float = 0f
) {
    /** 过渡进度 0.0=迷你播放器, 1.0=全屏播放器 */
    val progress = Animatable(initialProgress)

    /** 是否正在动画中 */
    val isRunning: Boolean get() = progress.isRunning

    /** 是否展开 */
    val isExpanded: Boolean get() = progress.value > 0.5f

    /** 动画规格 */
    val expandSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val collapseSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 展开 */
    suspend fun expand() {
        progress.animateTo(1f, expandSpring)
    }

    /** 收起 */
    suspend fun collapse() {
        progress.animateTo(0f, collapseSpring)
    }

    /** 根据进度决定展开或收起 */
    suspend fun snapOrAnimate() {
        val target = if (progress.value > 0.5f) 1f else 0f
        if (target > 0.5f) {
            progress.animateTo(1f, expandSpring)
        } else {
            progress.animateTo(0f, collapseSpring)
        }
    }
}

/**
 * 共享元素位置信息。
 */
@Stable
class SharedArtworkBounds {
    /** 迷你播放器中 artwork 的全局位置和大小 */
    var miniRect by mutableStateOf(Rect.Zero)
        internal set

    /** 全屏播放器中 artwork 的全局位置和大小 */
    var fullRect by mutableStateOf(Rect.Zero)
        internal set

    /** 根据进度插值计算 artwork 的位置和大小 */
    fun lerp(progress: Float): Rect {
        val from = miniRect
        val to = fullRect
        if (from == Rect.Zero && to == Rect.Zero) return Rect.Zero
        if (from == Rect.Zero) return to
        if (to == Rect.Zero) return from
        val t = progress.coerceIn(0f, 1f)
        return Rect(
            left = from.left + (to.left - from.left) * t,
            top = from.top + (to.top - from.top) * t,
            right = from.right + (to.right - from.right) * t,
            bottom = from.bottom + (to.bottom - from.bottom) * t
        )
    }
}

/**
 * 创建共享 artwork 位置状态。
 */
@Composable
fun rememberSharedArtworkBounds(): SharedArtworkBounds {
    return remember { SharedArtworkBounds() }
}

/**
 * 创建过渡状态。
 */
@Composable
fun rememberPlayerTransitionState(
    initialProgress: Float = 0f
): PlayerTransitionState {
    return remember { PlayerTransitionState(initialProgress) }
}