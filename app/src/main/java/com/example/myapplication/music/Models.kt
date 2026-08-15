package com.example.myapplication.music

import androidx.compose.runtime.Immutable

/**
 * 与 Rust 层（UniFFI Record）一一对应的 Kotlin 数据模型。
 */

/** 帧级歌词同步结果（Kotlin 每帧传播放位置给 Rust 计算，驱动 UI 连续更新） */
@Immutable
data class LyricFrame(
    val currentIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    /** 行内连续进度 0.0~1.0 */
    val progress: Float,
    val text: String,
)

data class Song(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long,
)

data class SongList(
    val songs: List<Song>,
    val total: Long,
)

/** 「网易云正在播放」匹配结果：歌曲详情 + 歌词 */
data class MatchedSong(
    val song: Song,
    val lyric: Lyric,
)

data class LyricWord(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
)

data class LyricLine(
    val startTimeMs: Long,
    val endTimeMs: Long,
    /** 组内各行文本：双行歌词（同一时间戳节点）为 2 行；普通歌词为 1 行 */
    val lines: List<String>,
    val words: List<LyricWord>,
) {
    /** 兼容：组内文本拼接 */
    val text: String get() = lines.joinToString("")
}

data class Lyric(
    val lines: List<LyricLine>,
)

data class CurrentLyric(
    val currentLineIndex: Int,
    val currentWordIndex: Int,
    val wordProgress: Double,
)