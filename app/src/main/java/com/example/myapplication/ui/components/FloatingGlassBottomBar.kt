package com.example.myapplication.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 一个导航项：未选中用 outline 图标，选中用 filled 图标。
 */
data class FloatingTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * 悬浮毛玻璃底部导航栏。
 *
 * 玻璃观感与第一版一致：沿用应用内统一的 glass() 毛玻璃面板
 * （半透明白 + 1dp 亮色描边 + 圆角），不再使用厚重的胶囊高光/环境光模糊。
 * 保留新版的能力：选中 pill 横向滑动（spring）+ 图标/文字动画。
 */
@Composable
fun FloatingGlassBottomBar(
    selectedIndex: Int,
    tabs: List<FloatingTab>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    topContent: @Composable ColumnScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 迷你播放器等插槽：紧贴导航栏上方，留下 10dp 空隙
            topContent()
            Spacer(Modifier.height(10.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .glass(RoundedCornerShape(24.dp))
            ) {
                val itemWidth = (maxWidth - 12.dp) / tabs.size.coerceAtLeast(1)
                val pillOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                    label = "pillOffset"
                )

                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
                    // 选中背景 pill（横向滑动）
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(56.dp)
                            .offset(x = pillOffset)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        tabs.forEachIndexed { index, tab ->
                            GlassTabItem(
                                tab = tab,
                                selected = index == selectedIndex,
                                onClick = { onTabSelected(index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5 个 Tab 的图标（outline / filled），自绘 ImageVector，不依赖扩展图标库
// ---------------------------------------------------------------------------

private fun ImageVector.Builder.addP(path: String, fill: Boolean, strokeWidth: Float = 1.8f) {
    val nodes = PathParser().parsePathString(path).toNodes()
    if (fill) {
        addPath(nodes, pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black))
    } else {
        addPath(
            nodes,
            pathFillType = PathFillType.NonZero,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
}

private fun tabIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
    val size = 24.dp
    return ImageVector.Builder(name, size, size, 24f, 24f).apply(block).build()
}

val HomeFilled = tabIcon("home_filled") {
    addP("M12 3l2.2 5.9 6.3.2-4.9 4 1.7 6.1L12 15.8l-5.3 3.4 1.7-6.1-4.9-4 6.3-.2L12 3z", fill = true)
}
val HomeOutline = tabIcon("home_outline") {
    addP("M12 3l2.2 5.9 6.3.2-4.9 4 1.7 6.1L12 15.8l-5.3 3.4 1.7-6.1-4.9-4 6.3-.2L12 3z", fill = false)
}

val NewFilled = tabIcon("new_filled") {
    addP("M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5z", fill = true)
}
val NewOutline = tabIcon("new_outline") {
    addP("M12 5v14M5 12h14", fill = false, strokeWidth = 2.2f)
}

val RadioFilled = tabIcon("radio_filled") {
    addP("M7 4h10", fill = false, strokeWidth = 2.2f)
    addP("M4 9a2 2 0 012-2h12a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2V9z", fill = true)
    addP("M8.5 13.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z", fill = true)
    addP("M13 12h4v2.5h-4z", fill = true)
}
val RadioOutline = tabIcon("radio_outline") {
    addP("M7 4h10", fill = false, strokeWidth = 2.2f)
    addP("M4 9a2 2 0 012-2h12a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2V9z", fill = false)
    addP("M8.5 13.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z", fill = false)
    addP("M13 12h4v2.5h-4z", fill = false)
}

val LibraryFilled = tabIcon("library_filled") {
    addP("M4 6h2v14H4z", fill = true)
    addP("M8 4h11a1 1 0 011 1v13a1 1 0 01-1 1H8a1 1 0 01-1-1V5a1 1 0 011-1z", fill = true)
    addP("M10 7h8M10 11h8M10 15h5", fill = false, strokeWidth = 1.6f)
}
val LibraryOutline = tabIcon("library_outline") {
    addP("M4 6h2v14H4z", fill = false)
    addP("M8 4h11a1 1 0 011 1v13a1 1 0 01-1 1H8a1 1 0 01-1-1V5a1 1 0 011-1z", fill = false)
    addP("M10 7h8M10 11h8M10 15h5", fill = false, strokeWidth = 1.6f)
}

val SearchFilled = tabIcon("search_filled") {
    addP("M11 4a7 7 0 100 14 7 7 0 000-14z", fill = true)
    addP("M19.5 19.5l-3.5-3.5", fill = false, strokeWidth = 2.4f)
}
val SearchOutline = tabIcon("search_outline") {
    addP("M11 4a7 7 0 100 14 7 7 0 000-14z", fill = false)
    addP("M19.5 19.5l-3.5-3.5", fill = false, strokeWidth = 2.4f)
}

private const val GEAR_PATH =
    "M19.4 15a1.7 1.7 0 00.3 1.9l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.9-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1-1.5 1.7 1.7 0 00-1.9.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.9 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1 1.7 1.7 0 00-.3-1.9l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.9.3h.1a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5h.1a1.7 1.7 0 001.9-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.9v.1a1.7 1.7 0 001.5 1h.1a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z"

val SettingsFilled = tabIcon("settings_filled") {
    addP(GEAR_PATH, fill = false, strokeWidth = 2.6f)
    addP("M12 12a1.4 1.4 0 110 2.8 1.4 1.4 0 010-2.8z", fill = true)
}
val SettingsOutline = tabIcon("settings_outline") {
    addP(GEAR_PATH, fill = false, strokeWidth = 1.7f)
    addP("M12 12a1.4 1.4 0 110 2.8 1.4 1.4 0 010-2.8z", fill = false)
}
