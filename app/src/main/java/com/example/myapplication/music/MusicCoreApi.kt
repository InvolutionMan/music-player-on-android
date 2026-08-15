package com.example.myapplication.music

/**
 * Rust 音乐核心的 Kotlin 侧契约（所有网易云网络请求统一经 Rust，Kotlin 不直接请求网易云）。
 *
 * 对应 Rust `resound_music::MusicCore`（UniFFI）。当 UniFFI bindings 生成后，
 * 用生成的类实现本接口（层级：Kotlin UI → Repository → 本契约 → UniFFI → Rust）。
 */
interface MusicCoreApi {

    suspend fun search(keyword: String): SongList

    suspend fun getSongDetail(id: Long): Song

    /** 播放 URL（不长时间缓存，可能过期） */
    suspend fun getPlayUrl(id: Long): String

    suspend fun getLyric(id: Long): Lyric

    /** 按「歌名 + 歌手」匹配网易云曲库（用于网易云 App 正在播放场景） */
    suspend fun matchSong(title: String, artist: String): MatchedSong

    /**
     * 帧级歌词同步：根据播放位置（毫秒）计算当前歌词帧。
     * Kotlin 每帧（60/120Hz withFrameNanos）调用；Rust 时间轴二分定位（O(log n)），
     * 纯音乐/间奏期间保持当前句，绝不按定时器或上一句时长推进。
     * 无歌词（本地曲目尚未推入 Rust）返回 null。
     */
    fun getLyricFrame(positionMs: Long): LyricFrame?

    /** 本地曲目歌词推入 Rust：由 Rust 时间轴统一同步（Kotlin 不维护另一套歌词 index） */
    fun setLocalLyric(lines: List<com.example.myapplication.data.LyricLine>)

    /** 局域网内自动发现网易云网关（返回地址；未找到抛异常） */
    suspend fun discoverGateway(localIp: String, prefixLen: Int, port: Int, timeoutMs: Int): String

    /** 由 ExoPlayer 驱动：唯一播放时间源（约每 50~100ms 调用） */
    fun updatePosition(positionMs: Long)

    fun getCurrentLyric(): CurrentLyric?

    fun clearCache()
}

/**
 * 音乐数据仓库：UI / Player 的数据访问入口。
 * 优先复用本层——将来 Player 接线（play/seek/歌词同步）都从这里取数。
 */
class MusicRepository(private val core: MusicCoreApi) {

    suspend fun search(keyword: String): SongList = core.search(keyword)

    suspend fun getSongDetail(id: Long): Song = core.getSongDetail(id)

    suspend fun getPlayUrl(id: Long): String = core.getPlayUrl(id)

    suspend fun getLyric(id: Long): Lyric = core.getLyric(id)

    /** 按「歌名 + 歌手」匹配网易云曲库（用于网易云 App 正在播放场景） */
    suspend fun matchSong(title: String, artist: String): MatchedSong = core.matchSong(title, artist)

    /**
     * 帧级歌词同步：根据播放位置（毫秒）计算当前歌词帧。
     * Kotlin 每帧（60/120Hz withFrameNanos）调用；Rust 时间轴二分定位（O(log n)），
     * 纯音乐/间奏期间保持当前句，绝不按定时器或上一句时长推进。
     * 无歌词（本地曲目尚未推入 Rust）返回 null。
     */
    fun getLyricFrame(positionMs: Long): LyricFrame? = core.getLyricFrame(positionMs)

    /** 本地曲目歌词推入 Rust：由 Rust 时间轴统一同步（Kotlin 不维护另一套歌词 index） */
    fun setLocalLyric(lines: List<com.example.myapplication.data.LyricLine>) =
        core.setLocalLyric(lines)

    /** 局域网内自动发现网易云网关（返回地址；未找到抛异常） */
    suspend fun discoverGateway(localIp: String, prefixLen: Int, port: Int, timeoutMs: Int): String =
        core.discoverGateway(localIp, prefixLen, port, timeoutMs)

    fun updatePosition(positionMs: Long) = core.updatePosition(positionMs)

    fun getCurrentLyric(): CurrentLyric? = core.getCurrentLyric()

    fun clearCache() = core.clearCache()
}