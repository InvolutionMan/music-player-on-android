package com.example.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.myapplication.music.MusicRepository
import com.example.myapplication.player.PlayMode
import com.example.myapplication.player.PlayerState
import com.example.myapplication.ui.icons.AppIcons
import com.example.myapplication.ui.theme.TabularStyle

/**
 * 全屏播放器内容。
 * 动画在 graphicsLayer draw 阶段读取 progress，不触发重组。
 */
@Composable
fun FullScreenPlayer(
    player: PlayerState,
    repository: MusicRepository,
    progress: Animatable<Float, AnimationVector1D>,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = player.track
    var showLyrics by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    // 暂停时封面往中心缩小，播放时恢复（丝滑弹簧过渡）
    val artworkScale = animateFloatAsState(
        targetValue = if (player.isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "artworkScale"
    )

    // 歌词形变动画：封面从居中缩放并平移到左上角
    var contentPos by remember { mutableStateOf(Offset.Zero) }
    var artPos by remember { mutableStateOf(Offset.Zero) }
    var artBoxSize by remember { mutableStateOf(IntSize.Zero) }
    val t = animateFloatAsState(
        targetValue = if (showLyrics) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "lyricsMorph"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress.value.coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = onDragEnd,
                    onVerticalDrag = { change, dragAmount ->
                        // 展开期间双向响应：上滑恢复全屏，下滑收起
                        if (progress.value > 0.01f) {
                            change.consume()
                            onDrag(dragAmount)
                        }
                    }
                )
            }
    ) {
        // 歌词沉浸式背景（全屏、边到边；仅歌词模式显示）
        if (showLyrics) {
            LyricsBackground(
                track = track,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // 顶部栏已移除：返回按钮与「余音 RESOUND」标题取消

            // 中间内容区：封面 / 歌词，封面缩放并平移到左上角（Apple Music 形变动效）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { contentPos = it.positionInWindow() }
            ) {
                // 歌词整体（淡入 + 轻微缩放，Apple Music 入场；规格 #二十五）
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLyrics,
                    enter = fadeIn(tween(320)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(320)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(24.dp))
                        // 头部：左侧留给 morph 到左上角的封面，右侧为歌名/歌手（歌名在上、歌手在下）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 72.dp, end = 8.dp)
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // 歌词列表（位于头部下方，不与其重叠）
                        LyricsView(
                            player = player,
                            repository = repository,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onShowControls = { controlsVisible = it }
                        )
                    }
                }

                // 正常内容（封面 + 歌名/歌手 + 进度；歌词时封面 morph 到左上角，信息淡出）
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(0.85f))

                    // 封面（随过渡缩放淡入；暂停时缩小；歌词时缩放并平移到左上角）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { artBoxSize = it }
                            .onGloballyPositioned { artPos = it.positionInWindow() }
                            .graphicsLayer {
                                // 320dp → 56dp 的缩放（叠加暂停缩放）
                                val s = artworkScale.value * lerp(1f, 0.175f, t.value)
                                val sPx = 320.dp.toPx()
                                val padX = 4.dp.toPx()
                                val padY = 28.dp.toPx() // 顶部给歌词头部留 24dp 呼吸空间
                                // 封面中心（相对内容区）与目标左上角中心
                                val centerX = (artPos.x - contentPos.x) + artBoxSize.width / 2f
                                val centerY = (artPos.y - contentPos.y) + artBoxSize.height / 2f
                                val targetCx = padX + sPx * s / 2f
                                val targetCy = padY + sPx * s / 2f
                                scaleX = s
                                scaleY = s
                                translationX = (targetCx - centerX) * t.value
                                translationY = (targetCy - centerY) * t.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        FullArtwork(track = track, progress = progress)
                    }

                    Spacer(Modifier.height(18.dp))

                    // 歌名/歌手/专辑 + 进度（歌词时淡出并移除，避免拦截歌词触摸）
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !showLyrics,
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(160))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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
                    }

                    Spacer(Modifier.weight(0.15f))
                }
            }

            // 用户手动滑动歌词时隐藏进度条与底部控制按钮；停止后延迟淡入
            androidx.compose.animation.AnimatedVisibility(
                visible = !showLyrics || controlsVisible,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 进度条（封面与歌词模式共用，位置一致；数字在下方）
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppleMusicSlider(
                            value = player.progressSeconds.toFloat(),
                            onValueChange = { player.seekTo(it.toInt()) },
                            valueRange = 0f..track.durationSeconds.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "%d:%02d".format(player.progressSeconds / 60, player.progressSeconds % 60),
                                style = TabularStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val remaining = (track.durationSeconds - player.progressSeconds).coerceAtLeast(0)
                            Text(
                                text = "-%d:%02d".format(remaining / 60, remaining % 60),
                                style = TabularStyle,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 控制按钮（中间一行：上一首 / 播放 / 下一首，按可用宽度自适应并整体居中）
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val gap = 8.dp
                        // 播放约为切歌的 1.2 倍；总宽 = 3.2*skip + 2*gap ≤ 行宽
                        val skip = minOf(104.dp, (maxWidth - gap * 2) / 3.2f)
                        val play = minOf(124.dp, skip * 1.2f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BareIconButton(
                                icon = AppIcons.SkipPrev,
                                contentDescription = "上一首",
                                size = skip,
                                onClick = { player.prev() }
                            )
                            Spacer(Modifier.width(gap))
                            BareIconButton(
                                icon = if (player.isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = if (player.isPlaying) "暂停" else "播放",
                                size = play,
                                onClick = { player.togglePlay() }
                            )
                            Spacer(Modifier.width(gap))
                            BareIconButton(
                                icon = AppIcons.SkipNext,
                                contentDescription = "下一首",
                                size = skip,
                                onClick = { player.next() }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 播放模式 / 显示歌词（下方一行，居中）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayModeButton(player = player)
                        Spacer(Modifier.width(24.dp))
                        BareIconButton(
                            icon = if (showLyrics) AppIcons.MusicNote else AppIcons.Lyrics,
                            contentDescription = if (showLyrics) "返回封面" else "查看歌词",
                            size = 48.dp,
                            onClick = { showLyrics = !showLyrics }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
internal fun PlayModeButton(player: PlayerState) {
    val (icon, label) = when (player.playMode) {
        PlayMode.SEQUENTIAL -> AppIcons.Repeat to "顺序播放"
        PlayMode.SHUFFLE -> AppIcons.Shuffle to "随机播放"
        PlayMode.REPEAT_ONE -> AppIcons.RepeatOne to "单曲循环"
    }
    BareIconButton(
        icon = icon,
        contentDescription = label,
        size = 48.dp,
        tint = MaterialTheme.colorScheme.primary,
        onClick = { player.cyclePlayMode() }
    )
}

/**
 * 无背景的图标按钮：仅图标 + 按下时圆形涟漪。
 * 平时不显示圆圈，只有按下时短暂出现；tint 默认 onSurfaceVariant。
 */
@Composable
private fun BareIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp = 40.dp,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val resolved = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = resolved,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

/**
 * Apple Music 风格播放进度条（Playback Scrubber）：
 * - 8dp 完整胶囊形横向轨道，两端全圆角
 * - 已播放区域纯白，未播放区域半透明白，交界处无缝（clipRect 裁剪）
 * - 默认无 Thumb，播放位置即分界线
 * - 拖动时显示小圆形 Thumb，松手 200ms 淡出
 * - 点击 / 拖动实时 seek
 */
@Composable
private fun AppleMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0f) }
    val trackHeightPx = with(density) { 8.dp.toPx() }
    val range = valueRange.endInclusive - valueRange.start
    val progress = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val newValue = valueRange.start + (offset.x / widthPx) * range
                    onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val newValue = valueRange.start + (change.position.x / widthPx) * range
                        onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
            val trackH = trackHeightPx
            val trackY = (size.height - trackH) / 2f
            val thumbX = progress * size.width
            val corner = CornerRadius(trackH / 2f)

            // 整条胶囊轨道（未播放区域：半透明白）
            drawRoundRect(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = Offset(0f, trackY),
                size = Size(size.width, trackH),
                cornerRadius = corner
            )

            // 已播放区域（纯白，右端带圆角，与胶囊轨道一致）
            if (thumbX > 0f) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(0f, trackY),
                    size = Size(thumbX, trackH),
                    cornerRadius = corner
                )
            }
        }
    }
}