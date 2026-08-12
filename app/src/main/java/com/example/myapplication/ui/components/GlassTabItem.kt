package com.example.myapplication.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 单个导航项：选中时图标放大 + 颜色切换 + 文字增亮，spring 弹性动画。
 * 选中背景 pill 由父级 FloatingGlassBottomBar 绘制。
 */
@Composable
fun GlassTabItem(
    tab: FloatingTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 460f),
        label = "tabIconScale"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        label = "tabLabelAlpha"
    )
    val labelScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 460f),
        label = "tabLabelScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.icon,
            contentDescription = tab.label,
            tint = if (selected) primary else idleColor,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = (if (selected) primary else idleColor).copy(alpha = labelAlpha),
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                scaleX = labelScale
                scaleY = labelScale
            }
        )
    }
}
