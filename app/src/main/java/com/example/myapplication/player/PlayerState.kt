package com.example.myapplication.player

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.data.LyricLine
import com.example.myapplication.data.ResoundLibrary
import com.example.myapplication.data.Track
import com.example.myapplication.music.LyricFrame
import com.example.myapplication.netease.NeteaseObserver

/** 播放模式：顺序 / 随机 / 单曲循环（点击循环切换）。 */
enum class PlayMode { SEQUENTIAL, SHUFFLE, REPEAT_ONE }

/**
 * 播放状态：统一本地曲目与网易云正在播放（前端 UI 不变，数据在此填充）。
 *
 * 时间轴（毫秒级连续，帧驱动）：
 * - 网易云模式：媒体会话快照（positionMs + positionUpdatedAtMs）+ 挂钟外推；
 * - 本地模式：seek/切换 时打快照，播放中按挂钟外推，暂停即冻结。
 * 歌词同步不使用秒级 timer：UI 每帧读 [currentPositionMs] 传给 Rust 计算。
 */
class PlayerState(private val netease: NeteaseObserver) {

    var currentIndex by mutableStateOf(0)
        private set

    private var localPositionMs by mutableLongStateOf(84_000L)
    private var localPositionUpdatedAt = SystemClock.elapsedRealtime()
    private var localIsPlaying by mutableStateOf(true)
    private var localPlayMode by mutableStateOf(PlayMode.SEQUENTIAL)

    /** 网易云歌曲的歌词与专辑（由 ResoundApp 经 Rust matchSong 填入） */
    var neteaseLyrics by mutableStateOf<List<LyricLine>>(emptyList())
    var neteaseAlbum by mutableStateOf<String?>(null)

    /** 用户点击本地曲目后暂时切回本地；网易云切歌或会话结束自动恢复 */
    var manualLocalOverride by mutableStateOf(false)
        private set

    /** 最近一次 seek/切歌时间（elapsedRealtime）：歌词 UI 据此决定平滑动画或直接定位 */
    var lastSeekAt by mutableLongStateOf(0L)
        private set

    val neteaseActive: Boolean
        get() {
            val np = netease.nowPlaying ?: return false
            return !manualLocalOverride && np.title.isNotBlank()
        }

    /** 当前曲目：网易云在播时为网易云歌曲，否则为本地曲库曲目 */
    val track: Track
        get() {
            val np = netease.nowPlaying
            if (neteaseActive && np != null) {
                return Track(
                    title = np.title,
                    artist = np.artist,
                    album = neteaseAlbum?.takeIf { it.isNotBlank() } ?: np.album,
                    durationSeconds = (np.durationMs / 1000).toInt().coerceAtLeast(1),
                    coverColors = np.coverColors,
                    lyrics = neteaseLyrics,
                    artwork = np.artwork
                )
            }
            return ResoundLibrary.tracks[currentIndex]
        }

    /**
     * 帧级播放位置（毫秒，连续值）：
     * 网易云 = 媒体会话快照 + 播放中挂钟外推；本地 = 快照 + 播放中挂钟外推（暂停冻结）。
     * UI 每帧读取本值传给 Rust get_lyric_frame，不使用秒级轮询。
     */
    val currentPositionMs: Long
        get() {
            val now = SystemClock.elapsedRealtime()
            val np = netease.nowPlaying
            return if (neteaseActive && np != null) {
                if (np.isPlaying) np.positionMs + (now - np.positionUpdatedAtMs) else np.positionMs
            } else if (localIsPlaying) {
                localPositionMs + (now - localPositionUpdatedAt)
            } else {
                localPositionMs
            }
        }

    /** 粗粒度心跳（进度条/时间文本等非歌词 UI 刷新用；歌词同步不走这里） */
    private var heartbeat by mutableStateOf(0L)

    fun heartbeat() {
        heartbeat++
    }

    /** 粗粒度进度（秒）：进度条 / 时间文本用，随媒体会话轮询或本地心跳更新 */
    val progressSeconds: Int
        get() {
            @Suppress("UNUSED_EXPRESSION")
            heartbeat
            return (currentPositionMs / 1000).toInt()
        }

    /** 唯一播放状态：网易云模式取媒体会话状态 */
    val isPlaying: Boolean
        get() = if (neteaseActive) netease.nowPlaying?.isPlaying == true else localIsPlaying

    /** 播放模式：顺序播放 → 随机播放 → 单曲循环，默认顺序（本地播放逻辑） */
    var playMode by mutableStateOf(PlayMode.SEQUENTIAL)
        private set

    val queueTotalText: String get() = "%d:%02d".format(ResoundLibrary.tracks.sumOf { it.durationSeconds } / 60, ResoundLibrary.tracks.sumOf { it.durationSeconds } % 60)

    /** 播放/暂停：网易云模式控制网易云，否则本地 */
    fun togglePlay() {
        if (neteaseActive) {
            netease.togglePlayPause()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (localIsPlaying) {
            // 暂停：把当前位置冻结进快照
            localPositionMs += now - localPositionUpdatedAt
        }
        localPositionUpdatedAt = now
        localIsPlaying = !localIsPlaying
    }

    /** 切换播放模式：顺序 → 随机 → 单曲循环 → 顺序。 */
    fun cyclePlayMode() {
        playMode = when (playMode) {
            PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SEQUENTIAL
        }
    }

    fun select(index: Int) {
        currentIndex = index.coerceIn(0, ResoundLibrary.tracks.lastIndex)
        localPositionMs = 0
        localPositionUpdatedAt = SystemClock.elapsedRealtime()
        localIsPlaying = true
        lastSeekAt = SystemClock.elapsedRealtime()
        // 用户主动选本地曲目：暂时覆盖网易云显示
        manualLocalOverride = true
    }

    /** 跳转：网易云模式 seek 网易云，否则本地（毫秒级快照重打） */
    fun seekTo(seconds: Int) {
        lastSeekAt = SystemClock.elapsedRealtime()
        if (neteaseActive) {
            netease.seekTo(seconds * 1000L)
        } else {
            localPositionMs = seconds.coerceIn(0, track.durationSeconds) * 1000L
            localPositionUpdatedAt = SystemClock.elapsedRealtime()
        }
    }

    fun next() {
        if (neteaseActive) {
            netease.skipToNext()
            return
        }
        val n = ResoundLibrary.tracks.size
        currentIndex = if (playMode == PlayMode.SHUFFLE) (0 until n).random() else (currentIndex + 1) % n
        localPositionMs = 0
        localPositionUpdatedAt = SystemClock.elapsedRealtime()
        localIsPlaying = true
        lastSeekAt = SystemClock.elapsedRealtime()
    }

    fun prev() {
        if (neteaseActive) {
            netease.skipToPrevious()
            return
        }
        val n = ResoundLibrary.tracks.size
        currentIndex = (currentIndex + n - 1) % n
        localPositionMs = 0
        localPositionUpdatedAt = SystemClock.elapsedRealtime()
        localIsPlaying = true
        lastSeekAt = SystemClock.elapsedRealtime()
    }

    /**
     * 本地播放到曲目结尾时的处理（顺序停止 / 单曲循环 / 随机下一首）。
     * 仅粗粒度心跳调用（500ms），与歌词同步无关。
     */
    fun checkTrackEnd() {
        if (neteaseActive || !localIsPlaying) return
        val durationMs = track.durationSeconds * 1000L
        if (currentPositionMs < durationMs) return
        when (playMode) {
            PlayMode.SEQUENTIAL -> {
                localPositionMs = durationMs
                localPositionUpdatedAt = SystemClock.elapsedRealtime()
                localIsPlaying = false
            }
            PlayMode.REPEAT_ONE -> {
                localPositionMs = 0
                localPositionUpdatedAt = SystemClock.elapsedRealtime()
            }
            PlayMode.SHUFFLE -> next()
        }
    }

    /** 网易云切歌 / 会话变化回调：清除手动切回本地的覆盖 */
    fun onNeteaseTrackChanged() {
        manualLocalOverride = false
    }

    /**
     * 降级回退：仅当 Rust 原生核心不可用时使用（Rust 是歌词 index 的唯一数据源）。
     * 与 Rust 时间轴同一规则：二分定位最后一个 timeMs <= pos，纯音乐/间奏保持当前句；
     * 不使用定时器、不按上一句时长推进。
     */
    fun localLyricFrameAt(positionMs: Long): LyricFrame? {
        val lines = track.lyrics
        if (lines.isEmpty()) return null
        val pos = positionMs.coerceAtLeast(0L)
        // 二分：最后一个 timeMs <= pos
        var lo = 0
        var hi = lines.size - 1
        var idx = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= pos) {
                idx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val line = lines[idx]
        val end = lines.getOrNull(idx + 1)?.timeMs?.toLong() ?: (line.timeMs + 4000L)
        val span = (end - line.timeMs).coerceAtLeast(1L)
        val progress = ((pos - line.timeMs).toFloat() / span).coerceIn(0f, 1f)
        return LyricFrame(
            currentIndex = idx,
            startTimeMs = line.timeMs.toLong(),
            endTimeMs = end,
            progress = progress,
            text = line.text
        )
    }

    /** 兼容旧调用（行级索引，粗粒度；新歌词 UI 不再使用） */
    fun activeLyricIndex(): Int {
        val p = currentPositionMs
        var last = 0
        track.lyrics.forEachIndexed { i, line -> if (line.timeMs <= p) last = i }
        return last
    }
}
