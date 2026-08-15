package com.example.myapplication.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放引擎：Media3 / ExoPlayer 封装。
 *
 * 架构约束：
 * - Kotlin 负责实际播放（AudioTrack/MediaCodec/MediaSession 等均由 Media3 处理）。
 * - Rust 只提供播放 URL（MusicCore.getPlayUrl）。
 * - 本类是**唯一播放时间源**：歌词 / 进度条都基于 [positionMs]，
 *   由 [updatePosition] 喂给 Rust 歌词引擎，保证时间轴一致。
 */
class ExoPlayerController(context: Context) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    /** 单曲播放结束回调（用于切下一首 / 单曲循环等） */
    var onMediaEnded: (() -> Unit)? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onMediaEnded?.invoke()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                _positionMs.value = player.currentPosition
            }
        })
    }

    /** 播放指定 URL（来自 Rust MusicCore.getPlayUrl） */
    fun play(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun toggle() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun getCurrentPositionMs(): Long = exoPlayer.currentPosition

    fun stop() {
        exoPlayer.stop()
    }

    fun release() {
        exoPlayer.release()
    }
}