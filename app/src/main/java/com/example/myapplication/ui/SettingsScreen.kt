package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.myapplication.ui.components.GlassIconButton
import com.example.myapplication.ui.components.IconTile
import com.example.myapplication.ui.components.ScrollEdgeFade
import com.example.myapplication.ui.components.VerticalGlassScrollbar
import com.example.myapplication.ui.components.glass
import com.example.myapplication.ui.icons.AppIcons
import com.example.myapplication.player.SettingsState

@Composable
fun SettingsScreen(
    settings: SettingsState,
    onBack: (() -> Unit)? = null,
    bottomPadding: Dp = 16.dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 顶部（与首页「余音」标题同相对位置：状态栏下 16dp、左 24dp）
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                GlassIconButton(
                    icon = AppIcons.ArrowBack,
                    contentDescription = "返回",
                    size = 40.dp,
                    onClick = onBack
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = bottomPadding)
                ) {
                SettingsGroup("界面") {
                    SwitchRow(AppIcons.Moon, "深色模式", "在浅色与深色之间切换", settings.darkMode) { settings.darkMode = it }
                    RowDivider()
                    SwitchRow(AppIcons.Palette, "跟随封面色调", "背景光晕取自当前专辑封面", settings.coverTint) { settings.coverTint = it }
                    RowDivider()
                    GlassOpacityRow(settings)
                    RowDivider()
                    SwitchRow(AppIcons.Lyrics, "歌词自动滚动", "当前句保持在可视区域中央", settings.lyricAutoScroll) { settings.lyricAutoScroll = it }
                    RowDivider()
                    FontStyleRow(settings.fontStyle) { settings.fontStyle = (settings.fontStyle + 1) % 3 }
                }

                Spacer(Modifier.height(16.dp))
                }
                VerticalGlassScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                )
            }
            ScrollEdgeFade(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
        )
        Column(modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp))) {
            content()
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    sub: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                uncheckedThumbColor = Color.White,
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                disabledUncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassOpacityRow(settings: SettingsState) {
    var textValue by remember { mutableStateOf("${(settings.glassOpacity * 100).toInt()}") }
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 图标 + 文字描述 + 数值输入（同一行，输入框与图标对齐）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(AppIcons.Opacity)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "毛玻璃透明度",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "玻璃面板的透明程度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            // 数值输入框 — 与图标对齐
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .onFocusChanged { isFocused = it.isFocused }
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { newText ->
                        textValue = newText
                        val v = newText.toIntOrNull()
                        if (v != null) {
                            settings.glassOpacity = (v.coerceIn(0, 100) / 100f)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.Transparent),
                    textStyle = MaterialTheme.typography.labelMedium.copy(
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            innerTextField()
                        }
                    }
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.height(4.dp))
        // 第二层：滑块（单独占整行）
        GlassSlider(
            value = settings.glassOpacity,
            onValueChange = {
                settings.glassOpacity = it.coerceIn(0f, 1f)
                textValue = "${(it * 100).toInt()}"
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 自绘滑块，无 ripple、无 shadow，完全避开 Material3 Slider 内部渲染产生的黑边。
 */
@Composable
private fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var sliderWidthPx by remember { mutableStateOf(0f) }
    val thumbRadiusPx = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { sliderWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / sliderWidthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / sliderWidthPx).coerceIn(0f, 1f))
                }
            }
    ) {
        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = Color.White.copy(alpha = 0.16f)

        // 轨道
        Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 10.dp)) {
            val trackH = 4.dp.toPx()
            val trackY = (size.height - trackH) / 2f
            val thumbCenterX = value * size.width

            // 未激活轨道（thumb 右侧）
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(thumbCenterX, trackY),
                size = Size(size.width - thumbCenterX, trackH),
                cornerRadius = CornerRadius(trackH / 2f)
            )
            // 激活轨道（thumb 左侧）
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, trackY),
                size = Size(thumbCenterX, trackH),
                cornerRadius = CornerRadius(trackH / 2f)
            )
        }
        // thumb 圆球 — 纯 Canvas 绘制，无任何 ripple 或 shadow
        val thumbOffset = (value * sliderWidthPx - thumbRadiusPx).coerceAtLeast(0f)
        Canvas(
            modifier = Modifier
                .size(16.dp)
                .offset(x = with(density) { thumbOffset.toDp() })
                .align(Alignment.CenterStart)
        ) {
            drawCircle(Color.White, radius = size.minDimension / 2f)
        }
    }
}

private val fontStyleLabels = listOf("系统默认", "衬线体", "圆体")

@Composable
private fun FontStyleRow(fontStyle: Int, onCycle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCycle() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(AppIcons.Palette)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "字体风格",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = fontStyleLabels.getOrElse(fontStyle) { "系统默认" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "点击切换",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
