package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.LyricLine
import com.example.myapplication.ui.components.GlassIconButton
import com.example.myapplication.ui.components.VerticalGlassScrollbar
import com.example.myapplication.ui.components.glass
import com.example.myapplication.ui.icons.AppIcons
import com.example.myapplication.ui.theme.TabularStyle
import com.example.myapplication.player.PlayerState

@Composable
fun LyricsScreen(
    state: PlayerState,
    autoScroll: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val track = state.track
    val listState = rememberLazyListState()
    val activeIdx = state.activeLyricIndex()

    LaunchedEffect(activeIdx, track.title) {
        if (!autoScroll || track.lyrics.isEmpty()) return@LaunchedEffect
        // 当前句仍在可视区内 → 不滚动，避免与手动拖动抢控制权、也避免动画频繁重启
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        if (visible.any { it.index == activeIdx }) return@LaunchedEffect
        // 按平均行高估算偏移，把当前句滚动到可视区垂直居中位置
        val avgItem = visible.map { it.size }.average().toInt()
        val centerOffset = (info.viewportEndOffset - info.viewportStartOffset) / 2 - avgItem / 2
        listState.animateScrollToItem(activeIdx, scrollOffset = centerOffset)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // 顶部：返回 / 迷你曲目 / 设置
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = AppIcons.ArrowBack,
                contentDescription = "收起",
                size = 40.dp,
                onClick = onBack
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${track.album.substringBefore(" · ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GlassIconButton(
                icon = AppIcons.Settings,
                contentDescription = "设置",
                size = 40.dp,
                onClick = onOpenSettings
            )
        }

        Spacer(Modifier.height(6.dp))

        // 歌词卡片
        if (track.lyrics.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "这首歌暂无歌词", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 220.dp)
                ) {
                    items(track.lyrics.size) { i ->
                        LyricRow(
                            line = track.lyrics[i],
                            active = i == activeIdx,
                            onClick = { state.seekTo(track.lyrics[i].timeMs / 1000) }
                        )
                    }
                }
                VerticalGlassScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .padding(top = 16.dp, bottom = 16.dp, end = 5.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 迷你控制条
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "%d:%02d".format(state.progressSeconds / 60, state.progressSeconds % 60), style = TabularStyle, color = MaterialTheme.colorScheme.outline)
                Slider(
                    value = state.progressSeconds.toFloat(),
                    onValueChange = { state.seekTo(it.toInt()) },
                    valueRange = 0f..track.durationSeconds.toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Text(text = track.durationText, style = TabularStyle, color = MaterialTheme.colorScheme.outline)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = AppIcons.SkipPrev,
                    contentDescription = "上一句",
                    size = 42.dp,
                    onClick = {
                        val i = activeIdx
                        state.seekTo(if (state.progressSeconds * 1000 - track.lyrics[i].timeMs > 1500) track.lyrics[i].timeMs / 1000 else if (i > 0) track.lyrics[i - 1].timeMs / 1000 else 0)
                    }
                )
                Spacer(Modifier.width(30.dp))
                MiniPlayButton(playing = state.isPlaying, onClick = { state.togglePlay() })
                Spacer(Modifier.width(30.dp))
                GlassIconButton(
                    icon = AppIcons.SkipNext,
                    contentDescription = "下一句",
                    size = 42.dp,
                    onClick = {
                        val i = (activeIdx + 1).coerceAtMost(track.lyrics.lastIndex)
                        state.seekTo(track.lyrics[i].timeMs / 1000)
                    }
                )
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, onClick: () -> Unit) {
    Text(
        text = line.text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        textAlign = TextAlign.Center,
        fontSize = if (active) 20.sp else 16.sp,
        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
        color = when {
            active -> MaterialTheme.colorScheme.onBackground
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
        },
        lineHeight = if (active) 30.sp else 24.sp,
        letterSpacing = if (active) (-0.2).sp else 0.2.sp
    )
}

@Composable
private fun MiniPlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(58.dp)
            .shadow(14.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = if (playing) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (playing) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
