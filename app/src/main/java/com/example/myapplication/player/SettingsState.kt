package com.example.myapplication.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsState {
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
}
