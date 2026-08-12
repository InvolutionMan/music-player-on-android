package com.example.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.myapplication.player.PlayerState
import com.example.myapplication.ui.icons.AppIcons

/**
 * 迷你播放器。
 * 位于底部，展示当前曲目信息，点击/上滑展开全屏播放器。
 * 动画在 graphicsLayer draw 阶段读取 progress，不触发重组。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    player: PlayerState,
    progress: Animatable<Float, AnimationVector1D>,
    onTap: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = player.track

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                val t = progress.value.coerceIn(0f, 1f)
                alpha = (1f - t).coerceIn(0f, 1f)
                translationY = t * 40f
            }
            .clickable(onClick = onTap)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = onDragEnd,
                    onVerticalDrag = { change, dragAmount ->
                        // 双向：上滑展开，下滑收起
                        change.consume()
                        onDrag(-dragAmount)
                    }
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp)
        ) {
            MiniArtwork(track = track, progress = progress)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee()
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee()
                )
            }

            Icon(
                imageVector = if (player.isPlaying) AppIcons.Pause else AppIcons.Play,
                contentDescription = if (player.isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = AppIcons.SkipNext,
                contentDescription = "下一首",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { player.next() }
            )
        }
    }
}