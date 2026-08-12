package com.example.myapplication.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.ResoundLibrary
import com.example.myapplication.data.Track
import com.example.myapplication.player.PlayerState
import com.example.myapplication.player.SettingsState
import com.example.myapplication.ui.components.FloatingGlassBottomBar
import com.example.myapplication.ui.components.FloatingTab
import com.example.myapplication.ui.components.GlassIconButton
import com.example.myapplication.ui.components.PlayerContainer
import com.example.myapplication.ui.components.ScrollEdgeFade
import com.example.myapplication.ui.components.VerticalGlassScrollbar
import com.example.myapplication.ui.components.glass
import com.example.myapplication.ui.icons.AppIcons
import com.example.myapplication.ui.theme.TabularStyle

private val AppTabs = listOf(
    FloatingTab("首页", com.example.myapplication.ui.components.HomeOutline, com.example.myapplication.ui.components.HomeFilled),
    FloatingTab("设置", com.example.myapplication.ui.components.SettingsOutline, com.example.myapplication.ui.components.SettingsFilled)
)

/**
 * 应用主壳：Scaffold + 悬浮玻璃底部导航 + Apple Music 风格迷你播放器过渡。
 * 迷你播放器点击/上滑展开全屏播放器，主页随过渡缩放。
 */
@Composable
fun MainScreen(player: PlayerState, settings: SettingsState) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = remember { AppTabs }
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }
    // 内容底部留白 = 手势条 + 导航栏(24+72) + 迷你播放器空隙(10+56) + 呼吸空间
    val bottomPad = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() } + 24.dp + 72.dp + 10.dp + 56.dp + 16.dp
    // 迷你播放器底部偏移 = 手势条 + 导航栏(24+72) + 空隙(10)
    val miniPlayerBottomPad = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() } + 24.dp + 72.dp + 10.dp

    PlayerContainer(
        player = player,
        miniPlayerBottomPadding = miniPlayerBottomPad,
        homeContent = {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    FloatingGlassBottomBar(
                        selectedIndex = selectedTab,
                        tabs = tabs,
                        onTabSelected = { selectedTab = it }
                    )
                }
            ) { contentPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                                ) { it / 6 * dir }
                            ).togetherWith(
                                fadeOut(animationSpec = tween(160, easing = LinearEasing)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    ) { -it / 10 * dir }
                            ).using(SizeTransform(clip = false))
                        },
                        label = "tabContent"
                    ) { tab ->
                        when (tab) {
                            0 -> HomeContent(player, statusTop, bottomPad)
                            else -> SettingsScreen(settings = settings, bottomPadding = bottomPad)
                        }
                    }
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// 首页
// ---------------------------------------------------------------------------

@Composable
private fun HomeContent(
    player: PlayerState,
    statusTop: Dp,
    bottomPad: Dp
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = statusTop + 16.dp, bottom = bottomPad + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(text = "余音", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "下午好，让本地音乐安静地流动", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionTitle("接下来播放") }
            itemsIndexed(ResoundLibrary.tracks) { index, track ->
                TrackRow(
                    track = track,
                    active = index == player.currentIndex,
                    onClick = { player.select(index) }
                )
            }
        }
        // 玻璃滚动条 — 右侧覆盖
        VerticalGlassScrollbar(
            listState = listState,
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .align(Alignment.CenterEnd)
                .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
        )
        // 滚动边缘渐隐指示器
        ScrollEdgeFade(
            listState = listState,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------------------------------------------------------------------------
// 通用行
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun TrackRow(track: Track, active: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
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
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
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
        Text(text = track.durationText, style = TabularStyle, color = if (active) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
    }
}