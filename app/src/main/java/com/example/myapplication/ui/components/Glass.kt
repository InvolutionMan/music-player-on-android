package com.example.myapplication.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.GlassTint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

/**
 * 毛玻璃透明度（0=完全透明，1=完全不透明）。
 * 由 SettingsState.glassOpacity 提供，全部 glass() 面板统一响应。
 */
val LocalGlassOpacity = compositionLocalOf { 0.75f }

/**
 * 毛玻璃表面：半透明 surface 填充 + 磨砂光泽 + 1dp 描边 + 圆角。
 * 深色下为白色磨砂/描边，浅色下为黑色轻磨砂/描边，随主题切换。
 * 透明度取 [LocalGlassOpacity]：0 完全透明，1 完全不透明（实色面板）。
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp)
): Modifier = composed {
    val o = LocalGlassOpacity.current.coerceIn(0f, 1f)
    if (o <= 0.01f) {
        return@composed this
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val fill = scheme.surface.copy(alpha = o)
    val sheen = if (dark) Color.White.copy(alpha = 0.06f * o) else Color.Black.copy(alpha = 0.035f * o)
    val border = if (dark) Color.White.copy(alpha = 0.10f * o) else Color.Black.copy(alpha = 0.07f * o)
    this
        .background(fill, shape)
        .background(sheen, shape)
        .border(1.dp, border, shape)
}

/** 环境光晕：叠加在屏幕背景上的柔和径向渐变，供玻璃面板透出。 */
fun Modifier.ambientGlow(
    color: Color,
    cx: Float = 0.5f,
    cy: Float = 0.22f,
    radiusFactor: Float = 1.3f
): Modifier = drawBehind {
    val center = Offset(cx * size.width, cy * size.height)
    val radius = size.minDimension * radiusFactor
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color, Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** 余音滚动条：细长玻璃药丸，常显低透明度，滚动时增亮。纯自绘，不依赖已移除的 Scrollbar API。 */

/** 配合 [ScrollState]（Column.verticalScroll / LazyListState）使用。 */
@Composable
fun VerticalGlassScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val values = remember { derivedStateOf { scrollState.value to scrollState.maxValue } }
    VerticalGlassScrollbar(
        scrollOffset = values.value.first,
        maxScrollOffset = values.value.second,
        isScrollInProgress = scrollState.isScrollInProgress,
        modifier = modifier
    )
}

/** 配合 [LazyListState]（歌词 LazyColumn）使用，滚动信息取自 layoutInfo。 */
@Composable
fun VerticalGlassScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val values = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportH = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(0)
            val contentH = info.visibleItemsInfo.lastOrNull()
                ?.let { it.offset + it.size + info.afterContentPadding } ?: 0
            (info.viewportStartOffset.coerceAtLeast(0)) to (contentH - viewportH).coerceAtLeast(0)
        }
    }
    VerticalGlassScrollbar(
        scrollOffset = values.value.first,
        maxScrollOffset = values.value.second,
        isScrollInProgress = listState.isScrollInProgress,
        modifier = modifier
    )
}

@Composable
private fun VerticalGlassScrollbar(
    scrollOffset: Int,
    maxScrollOffset: Int,
    isScrollInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    var viewportH by remember { mutableStateOf(0) }
    val o = LocalGlassOpacity.current.coerceIn(0f, 1f)
    // 与 settings.html 背景模糊滚动条一致：0.08 + (v/60)*0.18 → 0.08~0.26
    val idleAlpha = 0.08f + o * 0.18f   // 0.08 → 0.26
    val scrollAlpha = 0.15f + o * 0.35f  // 0.15 → 0.50
    val alpha by animateFloatAsState(
        targetValue = if (isScrollInProgress) scrollAlpha else idleAlpha,
        animationSpec = tween(180),
        label = "glassScrollbarAlpha"
    )

    Box(
        modifier = modifier.onSizeChanged { viewportH = it.height }
    ) {
        val contentH = (maxScrollOffset + viewportH).coerceAtLeast(1)
        val thumbH = if (viewportH > 0f) viewportH * viewportH / contentH.toFloat() else 0f
        val minThumb = with(LocalDensity.current) { 32.dp.toPx() }
        val finalThumbH = if (viewportH > 0f) thumbH.coerceIn(minThumb, viewportH.toFloat()) else 0f
        val ratio = if (maxScrollOffset > 0) (scrollOffset.toFloat() / maxScrollOffset).coerceIn(0f, 1f) else 0f
        val thumbTop = if (viewportH > 0f) (viewportH - finalThumbH) * ratio else 0f
        val show = maxScrollOffset > 0 && viewportH > 0

        val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (show && finalThumbH > 0f) {
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(0f, thumbTop),
                    size = Size(size.width, finalThumbH),
                    cornerRadius = CornerRadius(size.width / 2f)
                )
            }
        }
    }
}

// =========================================================================
// 滚动边缘渐隐指示器
// =========================================================================

/**
 * 滚动边缘渐隐指示器：在可滚动容器的顶部/底部叠加 32dp 渐变遮罩，
 * 内容滚动时缓慢显现，提示用户还有更多内容。
 * 配合 [ScrollState]（Column.verticalScroll）使用。
 */
@Composable
fun ScrollEdgeFade(
    scrollState: ScrollState,
    fadeHeight: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val values = remember { derivedStateOf { scrollState.value to scrollState.maxValue } }
    ScrollEdgeFadeImpl(
        scrollOffset = values.value.first,
        maxScrollOffset = values.value.second,
        fadeHeight = fadeHeight,
        modifier = modifier
    )
}

/**
 * 配合 [LazyListState]（LazyColumn / LazyRow）使用，滚动信息取自 layoutInfo。
 */
@Composable
fun ScrollEdgeFade(
    listState: LazyListState,
    fadeHeight: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val values = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportH = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(0)
            val contentH = info.visibleItemsInfo.lastOrNull()
                ?.let { it.offset + it.size + info.afterContentPadding } ?: 0
            (info.viewportStartOffset.coerceAtLeast(0)) to (contentH - viewportH).coerceAtLeast(0)
        }
    }
    ScrollEdgeFadeImpl(
        scrollOffset = values.value.first,
        maxScrollOffset = values.value.second,
        fadeHeight = fadeHeight,
        modifier = modifier
    )
}

@Composable
private fun ScrollEdgeFadeImpl(
    scrollOffset: Int,
    maxScrollOffset: Int,
    fadeHeight: Dp,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background
    val topAlpha by animateFloatAsState(
        targetValue = if (scrollOffset > 12) 1f else 0f,
        animationSpec = tween(200),
        label = "scrollFadeTop"
    )
    val botAlpha by animateFloatAsState(
        targetValue = if (maxScrollOffset > 0 && scrollOffset < maxScrollOffset - 12) 1f else 0f,
        animationSpec = tween(200),
        label = "scrollFadeBot"
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (topAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(bg, bg.copy(alpha = 0f))
                        )
                    )
                    .alpha(topAlpha)
            )
        }
        if (botAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(bg.copy(alpha = 0f), bg)
                        )
                    )
                    .alpha(botAlpha)
            )
        }
    }
}

/** 设置行左侧的图标瓷片。 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = Color.Unspecified,
    size: Dp = 34.dp,
    modifier: Modifier = Modifier
) {
    val resolved = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolved.copy(alpha = 0.85f),
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/** 圆形玻璃图标按钮（顶部栏 / 控制行通用）。 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Color.Unspecified,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val idle = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint
    val bg = if (active) MaterialTheme.colorScheme.surfaceVariant else GlassTint
    val color = if (active) MaterialTheme.colorScheme.primary else idle
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}
