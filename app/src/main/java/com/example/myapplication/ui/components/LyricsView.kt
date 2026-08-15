package com.example.myapplication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.LyricLine
import com.example.myapplication.data.LyricWord
import com.example.myapplication.music.LyricFrame
import com.example.myapplication.music.MusicRepository
import com.example.myapplication.player.PlayerState
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 换句过渡弹簧（Apple Music 风格）：列表滚动与行级视觉（颜色/字号/缩放）共用同一条曲线，
 * 行切换时「滑行到位 + 放大提亮」作为一次整体运动完成，而不是滚动先到、文字后动的两段式脱节。
 * stiffness=900（临界阻尼）≈ 0.22s 到位：比原 StiffnessMediumLow(400) 快一倍，
 * 比 StiffnessMedium(1500) 更稳，单行步进的节奏接近 Apple Music。
 */
private val LyricTransitionSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 900f
)
private val LyricTransitionColorSpring = spring<Color>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 900f
)

/** 反光扫过总时长（ms）：Apple Music 式柔和高光，easeInOut，从左到右，非匀速 */
private const val ReflectionSweepMs = 900f

/** 高光扫过前的极短延迟（ms）：等行滑到视觉中心、提亮基本完成后再扫 */
private const val ReflectionDelayMs = 140L

/**
 * 把歌词按 timeMs 归一化（排序 + 同一 timeMs 的连续行合并为一个 Group），
 * 使展示列表与 Rust 时间轴的 index 严格一致：
 * 「两行时间戳相同」的两行在 UI 里就是一个 Group（一个 item），不是两条独立歌词。
 */
private fun normalizeLyrics(lines: List<LyricLine>): List<LyricLine> {
    val sorted = lines.sortedBy { it.timeMs }
    val result = mutableListOf<LyricLine>()
    for (l in sorted) {
        val last = result.lastOrNull()
        if (last != null && last.timeMs == l.timeMs) {
            result[result.lastIndex] = last.copy(
                lines = last.lines + l.lines,
                words = last.words + l.words
            )
        } else {
            result.add(l)
        }
    }
    return result
}

/**
 * 歌词视图（Apple Music 风格高级跟踪）：
 * - LazyColumn 整体作为「连续虚拟 Y 坐标」：行切换时用弹簧驱动 scrollBy，
 *   整个列表连续位移——下一句从下方滑入视觉中心，上一句向上离开，无 snap、无逐句瞬移；
 * - 当前句固定在视口约 48% 处（视觉中心），行级 alpha/scale/字号按「与当前句的相对 index」
 *   动态分层：下一句比上一句略亮（即将进入的层级预告）；
 * - 当前句成为焦点后触发「反光扫过」：一道柔和高光以 easeInOut 从左到右扫过，
 *   高光用同字型渐变 Text 叠加，严格裁剪在文字 glyph 内部（不出现横向高亮条）；
 * - 时间同步（Rust position → index）与视觉动画完全分离：动画永不驱动换句。
 */
@Composable
fun LyricsView(
    player: PlayerState,
    repository: MusicRepository,
    modifier: Modifier = Modifier,
    onShowControls: (Boolean) -> Unit = {}
) {
    val track = player.track
    val listState = rememberLazyListState()

    // 展示歌词：与 Rust 时间轴 index 严格一致（同 timeMs 的两行合并为一个 Group）
    val displayLyrics = remember(track.lyrics) { normalizeLyrics(track.lyrics) }

    // 布局字号：所有歌词行使用同一个固定字号排版（换行结果稳定，不因当前/非当前改变）。
    // 视觉层级只通过 scale/alpha/color 体现，绝不改 layout fontSize/weight。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val layoutFontSize = when {
        screenWidthDp < 360 -> 28f
        screenWidthDp >= 600 -> 32f
        else -> 30f
    }

    // ---- 帧级同步：每帧读播放器位置 → Rust 时间轴计算当前帧（唯一 index 数据源）----
    var frame by remember { mutableStateOf<LyricFrame?>(null) }
    LaunchedEffect(track.title, repository) {
        frame = null
        // 本地曲目：把归一化后的歌词推给 Rust（与展示列表一致，Rust 时间轴二分定位）
        if (!player.neteaseActive) {
            runCatching { repository.setLocalLyric(displayLyrics) }
        }
        while (isActive) {
            withFrameNanos {
                val pos = player.currentPositionMs
                // 仅当 Rust 原生核心不可用（降级模式）时才回退到 Kotlin 本地二分
                frame = repository.getLyricFrame(pos) ?: player.localLyricFrameAt(pos)
            }
        }
    }

    val activeIdx = frame?.currentIndex
        ?.takeIf { displayLyrics.isNotEmpty() }
        ?.coerceIn(0, displayLyrics.lastIndex)
        ?: -1

    // 用户是否正在歌词列表上触摸（区分手动滚动与程序自动滚动）
    var isUserScrolling by remember { mutableStateOf(false) }

    // 滚动方向 → 显示/隐藏控制（前进=上滑=隐藏，回看=下滑=显示；静止/自动滚动不改变状态）
    LaunchedEffect(listState) {
        var lastPos = 0L
        snapshotFlow {
            isUserScrolling to (
                listState.firstVisibleItemIndex.toLong() * 1_000_000L +
                    listState.firstVisibleItemScrollOffset.toLong()
                )
        }.collect { (scrolling, pos) ->
            if (scrolling) {
                if (pos > lastPos) onShowControls(false) else if (pos < lastPos) onShowControls(true)
            }
            lastPos = pos
        }
    }

    // 当前句滚动到视口 48% 处（用户手动滚动时暂停自动跟随）。
    // 手动弹簧驱动 scrollBy：与行级视觉动画共用 LyricTransitionSpring，
    // 滚动与放大/提亮走同一条曲线、同一时刻完成（Apple Music 式整体过渡）。
    // 双行歌词 Group 高度不同：用目标行真实高度定位，滚动完成后做一次微校正保证视觉中心精确。
    LaunchedEffect(activeIdx, track.title) {
        if (activeIdx < 0 || displayLyrics.isEmpty() || isUserScrolling) return@LaunchedEffect
        val info = listState.layoutInfo
        if (info.visibleItemsInfo.isEmpty()) return@LaunchedEffect
        val visible = info.visibleItemsInfo
        val target = visible.firstOrNull { it.index == activeIdx }
        // 目标行高：可见时取真实高度（双行 Group 更高），否则用可见行平均估算
        val itemHeight = (target?.size ?: visible.map { it.size }.average().toInt()).toFloat()
        val viewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val centerOffset = viewportH * 0.48f - itemHeight / 2f
        // 目标行的当前（视口相对）顶边：可见时直接取精确 offset，
        // 不可见时按首可见项 offset + 行高推算。offset 是内容相对坐标，须减去 viewportStartOffset。
        val currentTop = if (target != null) {
            (target.offset - info.viewportStartOffset).toFloat()
        } else {
            val first = visible.first()
            first.offset - info.viewportStartOffset + (activeIdx - first.index) * itemHeight
        }
        // 需要滚动量 = 当前顶边 - 目标顶边（正=向上滚动内容，把行抬高到 centerOffset）
        val delta = currentTop - centerOffset
        if (abs(delta) > 0.5f) {
            listState.scroll(MutatePriority.Default) {
                var last = 0f
                animate(
                    initialValue = 0f,
                    targetValue = delta,
                    animationSpec = LyricTransitionSpring
                ) { value, _ ->
                    val step = value - last
                    last = value
                    scrollBy(step)
                }
            }
        }
        // 校正：非均匀行高下按目标行落点微调（双行 Group 不会停在中心偏下的位置）
        val info2 = listState.layoutInfo
        val t2 = info2.visibleItemsInfo.firstOrNull { it.index == activeIdx }
        if (t2 != null) {
            val err = (t2.offset - info2.viewportStartOffset) - centerOffset
            if (abs(err) > 1f) {
                listState.scroll(MutatePriority.Default) {
                    var last = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = err,
                        animationSpec = LyricTransitionSpring
                    ) { value, _ ->
                        val step = value - last
                        last = value
                        scrollBy(step)
                    }
                }
            }
        }
    }

    // ---- 反光扫过（Reflection Sweep）----
    // 状态值：-1 = 未激活；0..1 = 扫光进度（仅当前句读取，帧驱动不触发列表级重组）
    val reflection = remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(activeIdx, track.title) {
        reflection.floatValue = -1f
        if (activeIdx < 0) return@LaunchedEffect
        // 极短延迟：等行滑到中心、提亮基本完成后再扫（切换瞬间不抢戏）
        delay(ReflectionDelayMs)
        var t = 0f
        var prev = 0L
        while (t < 1f) {
            withFrameNanos { now ->
                if (prev == 0L) prev = now
                val dt = (now - prev) / 1_000_000f
                prev = now
                // 暂停时时间不累计 → 高光停在原地；恢复播放后继续扫（动画绝不驱动换句）
                if (player.isPlaying) {
                    t = (t + dt / ReflectionSweepMs).coerceAtMost(1f)
                }
                reflection.floatValue = t
            }
        }
        reflection.floatValue = -1f
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (displayLyrics.isEmpty()) {
            Text(
                text = "这首歌暂无歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 260.dp),
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
                    }
            ) {
                itemsIndexed(displayLyrics) { i, line ->
                    LyricRow(
                        line = line,
                        // 相对当前句的带符号距离：0=当前；+1=下一句（从下方进入）；-1=上一句（向上离开）
                        rel = if (activeIdx < 0) Int.MAX_VALUE else i - activeIdx,
                        // 只有当前行拿到帧（每帧更新 → 仅当前行重组重绘）
                        frame = if (i == activeIdx) frame else null,
                        // 只有当前行订阅反光扫过状态
                        reflection = if (i == activeIdx) reflection else null,
                        timeMs = line.timeMs,
                        onSeek = { player.seekTo(it / 1000) },
                        fontSize = layoutFontSize,
                        listState = listState,
                        itemIndex = i
                    )
                }
            }
        }
    }
}

/**
 * 歌词行（Apple Music 风格视觉层级，按与当前句的相对 index 动态计算）。
 *
 * 布局稳定原则：所有行的**排版尺寸完全相同**（同一个 fontSize + 同一个 Bold weight + 同一行高 + 同一宽度），
 * 换行结果在「非当前/当前/离开当前」整个生命周期内不变，杜绝"单行变两行"的布局跳动。
 * 视觉层级只通过 scale/alpha/color 体现：当前句 scale=1.0、其余 scale≈0.94~0.97（graphicsLayer 缩放，不改布局）。
 */
@Composable
private fun LyricRow(
    line: LyricLine,
    rel: Int,
    frame: LyricFrame?,
    reflection: State<Float>?,
    timeMs: Int,
    onSeek: (Int) -> Unit,
    fontSize: Float,
    listState: LazyListState,
    itemIndex: Int,
    modifier: Modifier = Modifier
) {
    val isCurrent = rel == 0
    val alphaTarget = when {
        rel == 0 -> 1f
        rel == 1 -> 0.65f   // 下一句（即将从下方进入焦点）
        rel == -1 -> 0.55f  // 上一句（向上离开）
        rel == 2 -> 0.38f
        rel == -2 -> 0.32f
        else -> 0.16f
    }
    val scaleTarget = when {
        rel == 0 -> 1f
        rel == 1 -> 0.97f
        rel == -1 -> 0.96f
        else -> 0.94f
    }
    val color by animateColorAsState(
        targetValue = Color.White.copy(alpha = alphaTarget),
        animationSpec = LyricTransitionColorSpring,
        label = "lyricColor"
    )
    // 视觉缩放（graphicsLayer，不参与文本排版）：当前 1.0，其余略缩——替代 fontSize 变化
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = LyricTransitionSpring,
        label = "lyricScale"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSeek(timeMs) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // 顶部 / 底部边缘渐隐：按行在视口中的位置调整透明度（参考项目）
                val li = listState.layoutInfo
                val item = li.visibleItemsInfo.firstOrNull { it.index == itemIndex }
                if (item != null && li.viewportEndOffset > li.viewportStartOffset) {
                    val viewportH = (li.viewportEndOffset - li.viewportStartOffset).toFloat()
                    val itemCenter = (item.offset - li.viewportStartOffset) + item.size / 2f
                    val fade = 64.dp.toPx()
                    val aTop = (itemCenter / fade).coerceIn(0f, 1f)
                    val aBottom = ((viewportH - itemCenter) / fade).coerceIn(0f, 1f)
                    alpha = minOf(aTop, aBottom)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        if (isCurrent && frame != null) {
            LyricProgressLine(
                line = line,
                frame = frame,
                fontSize = fontSize,
                color = color,
                reflection = reflection
            )
        } else {
            // 双行歌词 Group：两行共享同一固定排版 + 同一 alpha/scale（整体一个单元）
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
            ) {
                val texts = line.lines.ifEmpty { listOf(line.text) }
                texts.forEach { sub ->
                    Text(
                        text = sub,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                        lineHeight = 42.sp
                    )
                }
            }
        }
    }
}

/**
 * 当前歌词 Group 的连续进度渲染（逐字卡拉OK）：
 * - Group 内每行依次排布，逐字时间戳覆盖全组文本（跨行连续）；
 * - 每个字按自己的时间戳逐字高亮：唱到哪个字就高亮哪个字——
 *   高亮从第一行第一个字自然推进到第二行最后一个字，绝不整句/整组同时高亮；
 * - 有真实逐字时间戳（YRC）用真实时间；普通 LRC 由 Rust 按时长均分生成逐字时间戳；
 * - Group 整体滚动/缩放/Alpha 仍是一个单元（同一 timeMs 节点），两行绝不拆开移动。
 *
 * 反光扫过（Reflection Sweep）：对 Group 内每一行用同一个 eased 进度同步扫过，
 * 高光带是「透明→低亮白→亮白→低亮白→透明」的水平渐变，叠加层与基础文字同字型同宽度，
 * 因此高光只落在文字 glyph 内部。
 */
@Composable
private fun LyricProgressLine(
    line: LyricLine,
    frame: LyricFrame,
    fontSize: Float,
    color: Color,
    reflection: State<Float>?,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val style = TextStyle(
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.SansSerif,
        lineHeight = 42.sp
    )
    // 明暗都由行动画色推导：亮=当前行色（提亮到 1.0），暗=其 0.75，换句时连续变化无跳变
    val sung = color.copy(alpha = color.alpha)
    val unsung = color.copy(alpha = color.alpha * 0.75f)
    // 反光进度：-1=未激活；0..1=扫光中
    val sweep = reflection?.value ?: -1f
    // Group 拆分为子行（文本 + 该子行自己的逐字时间轴）
    val subLines = remember(line) { splitLyricGroup(line) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 9.dp)
            .onSizeChanged { widthPx = it.width }
    ) {
        if (widthPx <= 0) return@Box

        Column(modifier = Modifier.fillMaxWidth()) {
            subLines.forEach { sub ->
                if (sub.words.isEmpty()) {
                    // 兜底：无逐字数据时整行填充（正常路径 Rust 已生成逐字时间戳）
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = sub.text,
                            style = style.copy(color = unsung),
                            softWrap = true,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Clip
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .drawWithContent {
                                    clipRect(right = size.width * frame.progress) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                        ) {
                            Text(
                                text = sub.text,
                                style = style.copy(color = sung),
                                softWrap = true,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                } else {
                    // 逐字卡拉OK：唱到哪个字就高亮哪个字
                    WordKaraokeLine(
                        text = sub.text,
                        words = sub.words,
                        style = style,
                        frame = frame,
                        unsung = unsung,
                        sung = sung,
                        widthPx = widthPx
                    )
                }
            }
        }

        // ---- 反光扫过：对 Group 内每一行使用同一 eased 进度同步扫过 ----
        if (sweep > 0f && sweep < 1f) {
            // easeInOut：缓慢加速 → 匀速感 → 缓慢停下（非线性扫描）
            val eased = FastOutSlowInEasing.transform(sweep)
            // 高光带宽度约为文本宽 45%，峰值透明度 0.15：宽、柔、无明显条带
            val bandW = widthPx * 0.45f
            val center = -bandW + (widthPx + bandW * 2f) * eased
            val sweepBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                startX = center - bandW,
                endX = center + bandW
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                subLines.forEach { sub ->
                    Text(
                        text = sub.text,
                        style = style.copy(brush = sweepBrush),
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

/** 一个 Group 内的子行：文本 + 该子行自己的逐字时间轴 */
private data class SubLine(val text: String, val words: List<LyricWord>)

/**
 * 把 Group 拆成子行：全组 words 覆盖两行拼接文本，按文本长度累计拆回各子行，
 * 使「第一行的字先高亮、第二行的字到自己的时间戳后开始」成立（逐字跨行连续）。
 */
private fun splitLyricGroup(line: LyricLine): List<SubLine> {
    val texts = line.lines.ifEmpty { listOf(line.text) }
    if (line.words.isEmpty()) return texts.map { SubLine(it, emptyList()) }
    if (texts.size <= 1) return listOf(SubLine(texts.first(), line.words))
    val result = mutableListOf<SubLine>()
    var idx = 0
    for (t in texts) {
        val bucket = mutableListOf<LyricWord>()
        var remaining = t.length
        while (idx < line.words.size && remaining > 0) {
            val w = line.words[idx]
            bucket.add(w)
            remaining -= w.text.length
            idx++
        }
        result.add(SubLine(t, bucket))
    }
    return result
}

/** 子行级逐字卡拉OK：按该子行自己的时间戳逐字裁剪填充 */
@Composable
private fun WordKaraokeLine(
    text: String,
    words: List<LyricWord>,
    style: TextStyle,
    frame: LyricFrame,
    unsung: Color,
    sung: Color,
    widthPx: Int,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val annotated = remember(text) { buildAnnotatedString { append(text) } }
    val wordRanges = remember(words) {
        val ranges = ArrayList<IntRange>(words.size)
        var offset = 0
        words.forEach { w ->
            ranges.add(offset until offset + w.text.length)
            offset += w.text.length
        }
        ranges
    }
    val layout = textMeasurer.measure(
        annotated,
        style,
        constraints = Constraints(maxWidth = widthPx)
    )
    val heightDp = with(density) { layout.size.height.toDp() }
    Canvas(modifier = Modifier.fillMaxWidth().height(heightDp)) {
        drawText(layout, color = unsung)
        val span = (frame.endTimeMs - frame.startTimeMs).coerceAtLeast(1L)
        val pos = frame.startTimeMs + span * frame.progress
        words.forEachIndexed { i, w ->
            val wordSpan = (w.endMs - w.startMs).coerceAtLeast(1)
            val frac = ((pos - w.startMs) / wordSpan).coerceIn(0f, 1f)
            if (frac <= 0f) return@forEachIndexed
            val range = wordRanges[i]
            val path = layout.getPathForRange(range.first, range.last + 1) ?: return@forEachIndexed
            val bounds = path.getBounds()
            if (bounds.width <= 0f) return@forEachIndexed
            clipRect(
                left = bounds.left,
                top = bounds.top,
                right = bounds.left + bounds.width * frac,
                bottom = bounds.bottom
            ) {
                drawText(layout, color = sung)
            }
        }
    }
}
