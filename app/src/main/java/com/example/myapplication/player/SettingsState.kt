package com.example.myapplication.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsState(context: Context) {
    var gapless by mutableStateOf(true)
    var resumePosition by mutableStateOf(true)
    var normalizeVolume by mutableStateOf(false)
    var darkMode by mutableStateOf(true)
    var coverTint by mutableStateOf(true)
    var lyricAutoScroll by mutableStateOf(true)
    var showHiddenFiles by mutableStateOf(false)
    var blurRadius by mutableStateOf(30f)
    var glassOpacity by mutableStateOf(0.75f)
    var fontStyle by mutableStateOf(0) // 0=默认, 1=衬线体, 2=圆体

    /**
     * NeteaseCloudMusicApi 网关地址（持久化，真机填电脑局域网 IP，如 http://192.168.x.x:3000；
     * 模拟器默认 http://10.0.2.2:3000）
     */
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var neteaseGatewayUrl: String
        get() = _neteaseGatewayUrl
        set(value) {
            _neteaseGatewayUrl = value
            prefs.edit().putString("netease_gateway_url", value).apply()
        }
    private var _neteaseGatewayUrl by mutableStateOf(
        prefs.getString("netease_gateway_url", "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000"
    )
}
