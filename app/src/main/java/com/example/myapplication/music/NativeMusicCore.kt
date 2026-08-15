package com.example.myapplication.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rust 音乐核心的 Kotlin 适配实现（UniFFI 生成的 bindings）。
 *
 * 层级：Compose UI → MusicRepository → [MusicCoreApi] → 本类 → uniffi.resound_music.MusicCore → Rust。
 * 同步 FFI 调用放到 IO 线程；类型从 Rust（U/Long）映射到 UI 模型。
 * 错误通过 `MusicException` 抛出，UI 可捕获并提示。
 */
class NativeMusicCore(
    private val core: uniffi.resound_music.MusicCore,
) : MusicCoreApi {

    /** baseUrl：NeteaseCloudMusicApi 网关地址（模拟器 http://10.0.2.2:3000） */
    constructor(baseUrl: String = "http://10.0.2.2:3000") : this(uniffi.resound_music.MusicCore(baseUrl))

    override suspend fun search(keyword: String): SongList = withContext(Dispatchers.IO) {
        core.search(keyword).toUi()
    }

    override suspend fun getSongDetail(id: Long): Song = withContext(Dispatchers.IO) {
        core.getSongDetail(id.toULong()).toUi()
    }

    override suspend fun getPlayUrl(id: Long): String = withContext(Dispatchers.IO) {
        core.getPlayUrl(id.toULong())
    }

    override suspend fun getLyric(id: Long): Lyric = withContext(Dispatchers.IO) {
        core.getLyric(id.toULong()).toUi()
    }

    override suspend fun matchSong(title: String, artist: String): MatchedSong = withContext(Dispatchers.IO) {
        core.matchSong(title, artist).toUi()
    }

    /** 帧级调用：纯 CPU 计算（时间轴二分），直接 FFI 不切线程 */
    override fun getLyricFrame(positionMs: Long): LyricFrame? = core.getLyricFrame(positionMs)?.toUi()

    /**
     * 本地曲目歌词推入 Rust：Rust 时间轴成为歌词 index 的唯一数据源。
     * endTimeMs 传 0（占位），由 Rust 按「下一句真实 startTimeMs」补齐。
     */
    override fun setLocalLyric(lines: List<com.example.myapplication.data.LyricLine>) {
        core.setLocalLyric(
            uniffi.resound_music.Lyric(
                lines = lines.map { l ->
                    uniffi.resound_music.LyricLine(
                        startTimeMs = l.timeMs.toULong(),
                        endTimeMs = 0uL,
                        lines = l.lines,
                        words = l.words.map { w ->
                            uniffi.resound_music.LyricWord(
                                startTimeMs = w.startMs.toULong(),
                                endTimeMs = w.endMs.toULong(),
                                text = w.text
                            )
                        }
                    )
                }
            )
        )
    }

    override suspend fun discoverGateway(localIp: String, prefixLen: Int, port: Int, timeoutMs: Int): String =
        withContext(Dispatchers.IO) {
            core.discoverGateway(localIp, prefixLen.toUByte(), port.toUShort(), timeoutMs.toUInt())
        }

    override fun updatePosition(positionMs: Long) {
        core.updatePosition(positionMs.toULong())
    }

    override fun getCurrentLyric(): CurrentLyric? = core.getCurrentLyric()?.toUi()

    override fun clearCache() = core.clearCache()
}

// ---------------------------------------------------------------------------
// Rust 类型 → UI 模型映射（UniFFI 生成的 data class → com.example.myapplication.music.*）
// ---------------------------------------------------------------------------

private fun uniffi.resound_music.Song.toUi() = Song(
    id = id.toLong(),
    name = name,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationMs = durationMs.toLong(),
)

private fun uniffi.resound_music.SongList.toUi() = SongList(
    songs = songs.map { it.toUi() },
    total = total.toLong(),
)

private fun uniffi.resound_music.MatchedSong.toUi() = MatchedSong(
    song = song.toUi(),
    lyric = lyric.toUi(),
)

private fun uniffi.resound_music.LyricFrame.toUi() = LyricFrame(
    currentIndex = currentIndex,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    progress = progress,
    text = text,
)

private fun uniffi.resound_music.Lyric.toUi() = Lyric(
    lines = lines.map { it.toUi() },
)

private fun uniffi.resound_music.LyricLine.toUi() = LyricLine(
    startTimeMs = startTimeMs.toLong(),
    endTimeMs = endTimeMs.toLong(),
    lines = lines,
    words = words.map { it.toUi() },
)

private fun uniffi.resound_music.LyricWord.toUi() = LyricWord(
    startTimeMs = startTimeMs.toLong(),
    endTimeMs = endTimeMs.toLong(),
    text = text,
)

private fun uniffi.resound_music.CurrentLyric.toUi() = CurrentLyric(
    currentLineIndex = currentLineIndex.toInt(),
    currentWordIndex = currentWordIndex.toInt(),
    wordProgress = wordProgress,
)

/**
 * 原生库加载失败时的降级实现（骨架阶段）：避免启动崩溃，调用即抛异常。
 * 接入真实 .so / JNA 正常后，此分支不再触发。
 */
class UnavailableMusicCore(private val cause: Throwable) : MusicCoreApi {
    private fun fail(): Nothing = throw IllegalStateException("Rust 音乐核心不可用", cause)

    override suspend fun search(keyword: String): SongList = fail()
    override suspend fun getSongDetail(id: Long): Song = fail()
    override suspend fun getPlayUrl(id: Long): String = fail()
    override suspend fun getLyric(id: Long): Lyric = fail()
    override suspend fun matchSong(title: String, artist: String): MatchedSong = fail()
    /** 帧级回退：Rust 不可用时返回 null，由 Kotlin 本地歌词计算 */
    override fun getLyricFrame(positionMs: Long): LyricFrame? = null
    /** 原生核心不可用：本地歌词保持 Kotlin 降级路径，无操作 */
    override fun setLocalLyric(lines: List<com.example.myapplication.data.LyricLine>) = Unit
    override suspend fun discoverGateway(localIp: String, prefixLen: Int, port: Int, timeoutMs: Int): String = fail()
    override fun updatePosition(positionMs: Long) = fail()
    override fun getCurrentLyric(): CurrentLyric? = fail()
    override fun clearCache() = fail()
}
