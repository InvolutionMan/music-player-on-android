package com.example.myapplication

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.ui.MainScreen
import com.example.myapplication.ui.components.LocalGlassOpacity
import com.example.myapplication.ui.components.ambientGlow
import com.example.myapplication.data.LyricLine
import com.example.myapplication.data.LyricWord
import com.example.myapplication.music.MusicRepository
import com.example.myapplication.music.NativeMusicCore
import com.example.myapplication.music.UnavailableMusicCore
import com.example.myapplication.netease.NeteaseObserver
import com.example.myapplication.netease.localNetworks
import com.example.myapplication.player.PlayerState
import com.example.myapplication.player.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ResoundApp(settings: SettingsState) {
    val context = LocalContext.current

    // 网易云媒体会话监听（通知使用权授权后生效）
    val neteaseObserver = remember { NeteaseObserver(context) }
    DisposableEffect(neteaseObserver) {
        neteaseObserver.start()
        onDispose { neteaseObserver.release() }
    }

    // 播放状态：本地曲目与网易云正在播放统一入口（前端不变，数据在此填充）
    val player = remember { PlayerState(neteaseObserver) }

    // Rust 音乐核心接线（UniFFI）：搜索 / 歌词 / 匹配网易云曲库统一经仓库。
    // 网关地址来自设置；原生库加载失败时降级。
    val musicRepository = remember(settings.neteaseGatewayUrl) {
        try {
            MusicRepository(NativeMusicCore(settings.neteaseGatewayUrl))
        } catch (e: Throwable) {
            MusicRepository(UnavailableMusicCore(e))
        }
    }

    // 网易云在播：经 Rust 匹配曲库，把歌词/专辑填入播放状态（前端自动展示）
    val np = neteaseObserver.nowPlaying
    val lastDiscoverAt = remember { mutableStateOf(0L) }
    LaunchedEffect(np?.title, np?.artist, musicRepository) {
        val n = np ?: return@LaunchedEffect
        try {
            val matched = musicRepository.matchSong(n.title, n.artist)
            Log.d("ResoundApp", "matchSong 成功: ${matched.song.name} / ${matched.lyric.lines.size} 行歌词")
            player.neteaseAlbum = matched.song.album
            player.neteaseLyrics = matched.lyric.lines.map { l ->
                LyricLine(
                    timeMs = l.startTimeMs.toInt(),
                    // 双行歌词 Group：lines 原样透传（同一时间轴节点整体高亮/移动）
                    lines = l.lines.ifEmpty { listOf(l.text) },
                    words = l.words.map { w ->
                        LyricWord(w.startTimeMs.toInt(), w.endTimeMs.toInt(), w.text)
                    }
                )
            }
        } catch (e: Exception) {
            Log.d("ResoundApp", "matchSong 失败: ${e.message}")
            // 网关可能未配置或不可达：自动扫描一次局域网（60s 内只扫一次）
            val now = SystemClock.elapsedRealtime()
            if (now - lastDiscoverAt.value > 60_000L) {
                lastDiscoverAt.value = now
                val found = withContext(Dispatchers.IO) {
                    var url: String? = null
                    for ((ip, prefix) in localNetworks()) {
                        try {
                            url = musicRepository.discoverGateway(ip, prefix, 3000, 2000)
                            if (!url.isNullOrEmpty()) return@withContext url
                        } catch (_: Exception) {
                            // 该接口子网未找到，继续下一个
                        }
                    }
                    url
                }
                if (!found.isNullOrEmpty()) {
                    Log.d("ResoundApp", "自动发现网关: $found")
                    settings.neteaseGatewayUrl = found
                }
            }
            player.neteaseAlbum = n.album
            player.neteaseLyrics = emptyList()
        }
    }

    // 网易云切歌 / 会话结束：恢复网易云显示（清除手动切回本地的覆盖）
    LaunchedEffect(np?.title) {
        player.onNeteaseTrackChanged()
    }

    // 本地播放心跳：进度条刷新与曲尾处理（粗粒度，与歌词帧级同步无关）
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            delay(500)
            player.heartbeat()
            player.checkTrackEnd()
        }
    }

    val track = player.track
    val blurFactor = (settings.blurRadius - 12f) / 30f
    val glowTop = if (settings.coverTint) track.coverColors.first().copy(alpha = 0.30f)
    else Color.White.copy(alpha = 0.05f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .ambientGlow(glowTop, cx = 0.5f, cy = 0.18f, radiusFactor = 1.35f)
            .ambientGlow(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + 0.06f * blurFactor), cx = 0.85f, cy = 0.88f, radiusFactor = 1.1f)
    ) {
        CompositionLocalProvider(LocalGlassOpacity provides settings.glassOpacity.coerceIn(0f, 1f)) {
            MainScreen(
                player = player,
                settings = settings,
                netease = neteaseObserver,
                musicRepository = musicRepository
            )
        }
    }
}
