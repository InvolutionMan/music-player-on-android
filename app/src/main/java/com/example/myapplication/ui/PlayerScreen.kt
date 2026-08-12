package com.example.myapplication.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.myapplication.data.ResoundLibrary
import com.example.myapplication.ui.components.GlassIconButton
import com.example.myapplication.ui.components.PlayModeButton
import com.example.myapplication.ui.components.glass
import com.example.myapplication.ui.icons.AppIcons
import com.example.myapplication.ui.theme.TabularStyle
import com.example.myapplication.player.PlayerState

@Composable
fun PlayerScreen(state: PlayerState, onOpenLyrics: () -> Unit, onExit: (() -> Unit)? = null) {
    val track = state.track
    val glow = track.coverColors.first().copy(alpha = 0.55f)
    val dragAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .offset(y = with(density) { dragAnim.value.toDp() })
            .graphicsLayer {
                val progress = (dragAnim.value / 1200f).coerceIn(0f, 1f)
                scaleX = 1f - progress * 0.06f
                scaleY = 1f - progress * 0.06f
                alpha = 1f - progress * 0.35f
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 可拖拽区域（顶部内容，非滚动区域）
            Box(
                modifier = Modifier
                    .pointerInput(onExit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val screenH = size.height.toFloat()
                                if (dragAnim.value > screenH * 0.3f) {
                                    scope.launch {
                                        dragAnim.animateTo(
                                            screenH,
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        )
                                        onExit?.invoke()
                                    }
                                } else {
                                    scope.launch {
                                        dragAnim.animateTo(0f, animationSpec = spring())
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val raw = (dragAnim.value + dragAmount).coerceAtLeast(0f)
                                val resisted = if (raw > 200f) 200f + (raw - 200f) * 0.35f else raw
                                scope.launch { dragAnim.snapTo(resisted) }
                            }
                        )
                    }
            ) {
                Column {
                    PlayerTopBar(onOpenLyrics, onExit)
                    Spacer(Modifier.height(14.dp))

                    // 封面
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val coverSize = minOf(300.dp, maxWidth)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(coverSize)
                                .shadow(34.dp, RoundedCornerShape(30.dp), spotColor = glow)
                                .background(Brush.linearGradient(track.coverColors), RoundedCornerShape(30.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                                        )
                                    )
                            )
                            Icon(
                                imageVector = AppIcons.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(84.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))

                    // 曲目信息
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    // 控制
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayModeButton(player = state)
                        GlassIconButton(
                            icon = AppIcons.SkipPrev,
                            contentDescription = "上一首",
                            size = 44.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { state.prev() }
                        )
                        PlayButton(playing = state.isPlaying, onClick = { state.togglePlay() })
                        GlassIconButton(
                            icon = AppIcons.SkipNext,
                            contentDescription = "下一首",
                            size = 44.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { state.next() }
                        )
                        GlassIconButton(
                            icon = AppIcons.Lyrics,
                            contentDescription = "查看歌词",
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onOpenLyrics
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // 进度
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
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            )
                        )
                        Text(text = track.durationText, style = TabularStyle, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.height(8.dp))

                    // 接下来播放
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "接下来播放",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${ResoundLibrary.tracks.size} 首 · ${state.queueTotalText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 队列（可滚动，不受拖拽手势影响）
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(20.dp)),
                contentPadding = PaddingValues(6.dp)
            ) {
                itemsIndexed(ResoundLibrary.tracks) { index, tr ->
                    QueueRow(
                        track = tr,
                        active = index == state.currentIndex,
                        onClick = { state.select(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(onOpenLyrics: () -> Unit, onExit: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onExit != null) {
            GlassIconButton(
                icon = AppIcons.ChevronDown,
                contentDescription = "收起播放器",
                size = 40.dp,
                onClick = onExit
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = "余音", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "RESOUND",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.weight(1f))
        GlassIconButton(
            icon = AppIcons.Lyrics,
            contentDescription = "查看歌词",
            size = 40.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onOpenLyrics
        )
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(74.dp)
            .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = if (playing) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (playing) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun QueueRow(
    track: com.example.myapplication.data.Track,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(track.coverColors))
        ) {
            Icon(
                imageVector = AppIcons.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (active) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = track.durationText, style = TabularStyle, color = if (active) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
    }
}