package com.example.myapplication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.LyricLine
import com.example.myapplication.player.PlayerState

/**
 * Apple Music 风格同步歌词视图：
 * - 当前句固定在视口约 48% 的视觉中心，平滑自动滚动跟随播放进度
 * - 当前句高亮（颜色 / 字号 / 缩放 / 透明度平滑过渡），其余句较弱
 * - 固定行高，字号变化不引起列表跳动
 * - 用户手动滚动时暂停自动跟随，可一键回到当前句
 */
@Composable
fun LyricsView(
    player: PlayerState,
    modifier: Modifier = Modifier,
    onShowControls: (Boolean) -> Unit = {}
) {
    val track = player.track
    val listState = rememberLazyListState()
    val activeIdx = player.activeLyricIndex()

    // 用户是否正在歌词列表上触摸（区分手动滚动与程序自动滚动）
    var isUserScrolling by remember { mutableStateOf(false) }

    // 只有向上滚动时显示控制按钮，向下滚动隐藏；自动滚动 / 静止不改变状态
    LaunchedEffect(listState) {
        var lastPos = 0L
        snapshotFlow {
            isUserScrolling to (listState.firstVisibleItemIndex.toLong() * 1_000_000L + listState.firstVisibleItemScrollOffset.toLong())
        }.collect { (scrolling, pos) ->
            if (scrolling) {
                if (pos > lastPos) onShowControls(false)   // 向下：隐藏
                else if (pos < lastPos) onShowControls(true) // 向上：显示
            }
            lastPos = pos
        }
    }

    // 平滑滚动：仅在 currentLyricIndex 变化时把当前句滚动到视口 48% 处（用户手动滚动时暂停）
    LaunchedEffect(activeIdx, track.title) {
        if (track.lyrics.isEmpty() || isUserScrolling) return@LaunchedEffect
        val info = listState.layoutInfo
        if (info.visibleItemsInfo.isEmpty()) return@LaunchedEffect
        val avgItem = info.visibleItemsInfo.map { it.size }.average().toFloat()
        val viewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val centerOffset = (viewportH * 0.48f - avgItem / 2f).toInt()
        listState.animateScrollToItem(activeIdx, scrollOffset = centerOffset)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (track.lyrics.isEmpty()) {
            Text(
                text = "这首歌暂无歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            isUserScrolling = true
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                pressed = event.changes.any { it.pressed }
                            }
                            isUserScrolling = false
                        }
                    },
                contentPadding = PaddingValues(vertical = 260.dp)
            ) {
                items(track.lyrics.size) { i ->
                    LyricRow(
                        line = track.lyrics[i],
                        active = i == activeIdx,
                        onClick = { player.seekTo(track.lyrics[i].timeMs / 1000) },
                        modifier = Modifier.graphicsLayer {
                            // 顶部 / 底部边缘淡出：按行在视口中的位置调整透明度
                            val li = listState.layoutInfo
                            val item = li.visibleItemsInfo.firstOrNull { it.index == i }
                            if (item != null && li.viewportEndOffset > li.viewportStartOffset) {
                                val viewportH = (li.viewportEndOffset - li.viewportStartOffset).toFloat()
                                val itemCenter = (item.offset - li.viewportStartOffset) + item.size / 2f
                                val fade = 64.dp.toPx()
                                val aTop = (itemCenter / fade).coerceIn(0f, 1f)
                                val aBottom = ((viewportH - itemCenter) / fade).coerceIn(0f, 1f)
                                alpha = minOf(aTop, aBottom)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = if (active)
            Color.White
        else
            Color.White.copy(alpha = 0.55f),
        animationSpec = tween(450),
        label = "lyricColor"
    )
    val fontSize by animateFloatAsState(
        targetValue = if (active) 30f else 28f,
        animationSpec = tween(450),
        label = "lyricSize"
    )
    // 轻微缩放：当前句 1.0，其余 0.96（graphicsLayer 不影响布局）
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.96f,
        animationSpec = tween(450),
        label = "lyricScale"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = line.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            textAlign = TextAlign.Start,
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = color,
            // 固定行高：字号变化不改变行高，整个列表不跳动
            lineHeight = 42.sp
        )
    }
}