package com.example.myapplication.netease

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/** 网易云音乐包名 */
const val NETEASE_PACKAGE = "com.netease.cloudmusic"

/** 承载媒体会话监听所需的通知使用权（服务本身无逻辑） */
class NeteaseListenerService : NotificationListenerService()

/** 网易云 App 正在播放的歌曲快照 */
data class NeteaseNowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: ImageBitmap?,
    val durationMs: Long,
    val positionMs: Long,
    /** 位置快照时间（elapsedRealtime）：UI 帧驱动时用它外推出连续位置 */
    val positionUpdatedAtMs: Long,
    val isPlaying: Boolean,
    /** 由封面主色派生的渐变（供前端 coverColors 使用） */
    val coverColors: List<Color>,
)

/**
 * 监听网易云 App 的媒体会话（通知使用权授权后生效）：
 * - 曲目元数据（歌名 / 歌手 / 专辑 / 封面）
 * - 播放进度与状态（轮询 + 播放中线性外推，保证歌词平滑滚动）
 */
class NeteaseObserver(private val context: Context) {

    var nowPlaying by mutableStateOf<NeteaseNowPlaying?>(null)
        private set

    var permissionGranted by mutableStateOf(hasNotificationAccess(context))
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, NeteaseListenerService::class.java)
    private var controller: MediaController? = null
    private var started = false

    // 封面只在元数据变化时重新解码（getBitmap 开销较大）
    private var lastMetaSignature: String? = null
    // 封面主色派生的渐变色缓存（随元数据一起更新）
    private var coverColorsCache: List<Color> = NETEASE_FALLBACK_GRADIENT

    private val sessionsListener = object : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            controller = controllers?.firstOrNull { it.packageName == NETEASE_PACKAGE }
            lastMetaSignature = null
            if (controller == null) nowPlaying = null
        }
    }

    fun start() {
        permissionGranted = hasNotificationAccess(context)
        if (!permissionGranted) return
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component)
            sessionsListener.onActiveSessionsChanged(sessionManager.getActiveSessions(component))
        }.onFailure {
            permissionGranted = false
            return
        }
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                runCatching { poll() }
                delay(150)
            }
        }
    }

    /** 重新读取通知使用权状态（用户从系统设置返回后调用） */
    fun refreshPermission() {
        permissionGranted = hasNotificationAccess(context)
        if (permissionGranted) start()
    }

    // ------------------------------------------------------------------
    // 播放控制（通过媒体会话 transport，对网易云 App 生效）
    // ------------------------------------------------------------------

    /** 播放 / 暂停 */
    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    /** 下一首 */
    fun skipToNext() {
        controller?.transportControls?.skipToNext()
    }

    /** 上一首 */
    fun skipToPrevious() {
        controller?.transportControls?.skipToPrevious()
    }

    /** 跳转到指定位置（点击歌词行） */
    fun seekTo(positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs)
    }

    private fun poll() {
        val c = controller ?: run {
            if (nowPlaying != null) nowPlaying = null
            return
        }
        val md = c.metadata
        if (md == null) {
            lastMetaSignature = null
            if (nowPlaying != null) nowPlaying = null
            return
        }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION)

        val signature = "$title|$artist|$album|$duration"
        val artwork = if (signature != lastMetaSignature) {
            lastMetaSignature = signature
            val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            Log.d("NeteaseObserver", "metadata keys=${md.keySet()}, artwork=${bmp != null}")
            coverColorsCache = gradientFromBitmap(bmp)
            bmp?.asImageBitmap()
        } else {
            nowPlaying?.artwork
        }

        val ps = c.playbackState
        val playing = ps?.state == PlaybackState.STATE_PLAYING
        // 原始快照 + 快照时间：连续位置由 UI 帧驱动时外推（见 PlayerState.currentPositionMs）
        val position = ps?.position ?: nowPlaying?.positionMs ?: 0L
        val snapshotAt = SystemClock.elapsedRealtime()

        val current = nowPlaying
        if (current == null || current.title != title || current.artist != artist ||
            current.album != album || current.artwork !== artwork ||
            current.durationMs != duration ||
            current.isPlaying != playing || current.positionMs != position
        ) {
            nowPlaying = NeteaseNowPlaying(
                title, artist, album, artwork, duration, position, snapshotAt, playing, coverColorsCache
            )
        }
    }

    fun release() {
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller = null
        nowPlaying = null
    }

    companion object {
        /** 是否已授予通知使用权 */
        fun hasNotificationAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return enabled.contains(componentFlattened(context))
        }

        private fun componentFlattened(context: Context): String =
            ComponentName(context, NeteaseListenerService::class.java).flattenToString()
    }
}

/** 无封面 / 解码失败时的兜底渐变（网易云红） */
private val NETEASE_FALLBACK_GRADIENT = listOf(Color(0xFFE03A3A), Color(0xFF8C1F1F), Color(0xFF470F0F))

/** 封面缩至 1x1 取主色，派生出「主色 → 深色」三段渐变 */
private fun gradientFromBitmap(bmp: Bitmap?): List<Color> {
    if (bmp == null) return NETEASE_FALLBACK_GRADIENT
    val scaled = Bitmap.createScaledBitmap(bmp, 1, 1, true)
    val pixel = scaled.getPixel(0, 0)
    if (!scaled.isRecycled) scaled.recycle()
    val base = Color(pixel)
    fun darken(f: Float) = Color(base.red * f, base.green * f, base.blue * f, 1f)
    return listOf(base, darken(0.55f), darken(0.3f))
}

/** 枚举本机所有活跃 IPv4 网络接口（Wi-Fi 优先），供网关扫描使用。 */
fun localNetworks(): List<Pair<String, Int>> {
    val entries = mutableListOf<Triple<Int, String, Int>>()
    for (iface in NetworkInterface.getNetworkInterfaces() ?: return emptyList()) {
        if (!iface.isUp) continue
        val wifi = if (iface.name.lowercase().let { it.contains("wlan") || it.contains("wifi") }) 0 else 1
        for (addr in iface.inetAddresses) {
            if (addr !is Inet4Address || addr.isLoopbackAddress) continue
            val ip = addr.hostAddress ?: continue
            val prefix = iface.interfaceAddresses
                .firstOrNull { it.address == addr }
                ?.networkPrefixLength?.toInt() ?: 24
            entries.add(Triple(wifi, ip, prefix))
        }
    }
    return entries.sortedBy { it.first }.map { it.second to it.third }
}
