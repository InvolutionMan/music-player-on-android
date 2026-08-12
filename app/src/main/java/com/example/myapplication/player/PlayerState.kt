package com.example.myapplication.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.data.ResoundLibrary
import com.example.myapplication.data.Track

/** 播放模式：顺序 / 随机 / 单曲循环（点击循环切换）。 */
enum class PlayMode { SEQUENTIAL, SHUFFLE, REPEAT_ONE }

/**
 * 播放状态：曲目索引 / 进度 / 播放中 / 播放模式。
 * 由 ResoundApp 的 LaunchedEffect 每秒驱动 tick()。
 */
class PlayerState {

    var currentIndex by mutableStateOf(0)
        private set

    var progressSeconds by mutableStateOf(84)
        private set

    var isPlaying by mutableStateOf(true)
        private set

    /** 播放模式：顺序播放 → 随机播放 → 单曲循环，默认顺序。 */
    var playMode by mutableStateOf(PlayMode.SEQUENTIAL)
        private set

    val track: Track get() = ResoundLibrary.tracks[currentIndex]
    val queueTotalText: String get() = "%d:%02d".format(ResoundLibrary.tracks.sumOf { it.durationSeconds } / 60, ResoundLibrary.tracks.sumOf { it.durationSeconds } % 60)

    fun togglePlay() { isPlaying = !isPlaying }

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
        progressSeconds = 0
        isPlaying = true
    }

    fun seekTo(seconds: Int) {
        progressSeconds = seconds.coerceIn(0, track.durationSeconds)
    }

    fun next() {
        val n = ResoundLibrary.tracks.size
        currentIndex = if (playMode == PlayMode.SHUFFLE) (0 until n).random() else (currentIndex + 1) % n
        progressSeconds = 0
        isPlaying = true
    }

    fun prev() {
        val n = ResoundLibrary.tracks.size
        currentIndex = (currentIndex + n - 1) % n
        progressSeconds = 0
        isPlaying = true
    }

    fun tick() {
        if (!isPlaying) return
        progressSeconds++
        if (progressSeconds >= track.durationSeconds) {
            when (playMode) {
                // 顺序播放：播完队列停止
                PlayMode.SEQUENTIAL -> isPlaying = false
                // 单曲循环：重播当前曲目
                PlayMode.REPEAT_ONE -> progressSeconds = 0
                // 随机播放：自动进入下一首（随机）
                PlayMode.SHUFFLE -> next()
            }
        }
    }

    fun activeLyricIndex(): Int {
        val p = progressSeconds * 1000
        var last = 0
        track.lyrics.forEachIndexed { i, line -> if (line.timeMs <= p) last = i }
        return last
    }
}
