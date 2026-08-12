package com.example.myapplication.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 余音图标库：直接从品牌 SVG 路径数据构建，全为单色可被 Icon(tint) 染色。
 * 不依赖 material-icons-extended，控制体积。
 */
object AppIcons {

    // 圆角实心播放三角：三个顶点用二次贝塞尔(Q)圆化，右尖角明显圆润（Apple Music 风格）
    // 顶点 A(8,5) 顶左、B(8,19) 底左、C(19,12) 右尖；圆角半径 r≈3.5（24dp 时）
    val Play = icon("play", "M8 8.5 L8 15.5 Q8 19 10.95 17.12 L16.05 13.88 Q19 12 16.05 10.12 L10.95 6.88 Q8 5 8 8.5 Z", fill = true)
    // 圆角暂停图标：两条圆头胶囊竖条（与圆角播放三角风格统一）
    val Pause = icon("pause", "M7 7.2 L7 16.8 Q7 18.5 8.7 18.5 L9.7 18.5 Q10.4 18.5 10.4 16.8 L10.4 7.2 Q10.4 5.5 9.7 5.5 L8.7 5.5 Q7 5.5 7 7.2 Z M13.6 7.2 L13.6 16.8 Q13.6 18.5 15.3 18.5 L16.3 18.5 Q17 18.5 17 16.8 L17 7.2 Q17 5.5 16.3 5.5 L15.3 5.5 Q13.6 5.5 13.6 7.2 Z", fill = true)
    // 下一首：两个向右的圆角三角（竖条改为三角，风格统一）
    val SkipNext = icon("skip_next", "M 2.5 7.5 L 2.5 16.5 Q 2.5 18.5 4.09 17.29 L 9.41 13.22 Q 11 12 9.41 10.79 L 4.09 6.72 Q 2.5 5.5 2.5 7.5 Z M 12.5 7.5 L 12.5 16.5 Q 12.5 18.5 14.09 17.29 L 19.41 13.22 Q 21 12 19.41 10.79 L 14.09 6.72 Q 12.5 5.5 12.5 7.5 Z", fill = true)
    // 上一首：两个向左的圆角三角
    val SkipPrev = icon("skip_prev", "M 21.5 7.5 L 21.5 16.5 Q 21.5 18.5 19.91 17.29 L 14.59 13.22 Q 13 12 14.59 10.79 L 19.91 6.72 Q 21.5 5.5 21.5 7.5 Z M 11.5 7.5 L 11.5 16.5 Q 11.5 18.5 9.91 17.29 L 4.59 13.22 Q 3 12 4.59 10.79 L 9.91 6.72 Q 11.5 5.5 11.5 7.5 Z", fill = true)
    val Shuffle = icon("shuffle", "M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5")
    val Repeat = icon("repeat", "M17 2l4 4-4 4M3 11V9a4 4 0 014-4h14M7 22l-4-4 4-4M21 13v2a4 4 0 01-4 4H3")
    val RepeatOne = icon("repeat_one", "M17 2l4 4-4 4M3 11V9a4 4 0 014-4h14M7 22l-4-4 4-4M21 13v2a4 4 0 01-4 4H3M12 9V15")
    val Search = icon("search", "M11 4a7 7 0 100 14 7 7 0 000-14zM19.6 19.6l-3.5-3.5")
    val MoreVert = icon("more", "M12 3.5a1.8 1.8 0 110 3.6 1.8 1.8 0 010-3.6zM12 10.2a1.8 1.8 0 110 3.6 1.8 1.8 0 010-3.6zM12 16.9a1.8 1.8 0 110 3.6 1.8 1.8 0 010-3.6z", fill = true)
    val Lyrics = icon("lyrics", "M4 7h16M4 12h16M4 17h9")
    val MusicNote = icon("music_note", "M9 18V5.6l11-1.8V17M9 18a3 3 0 11-5.6-1.5M20 17a3 3 0 11-5.6-1.5")
    val ArrowBack = icon("arrow_back", "M15 18l-6-6 6-6", strokeWidth = 2.2f)
    val ChevronRight = icon("chevron_right", "M9 18l6-6-6-6", strokeWidth = 2f)
    val Settings = icon("settings",
        "M19.4 15a1.7 1.7 0 00.3 1.9l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.9-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1-1.5 1.7 1.7 0 00-1.9.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.9 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1 1.7 1.7 0 00-.3-1.9l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.9.3h.1a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5h.1a1.7 1.7 0 001.9-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.9v.1a1.7 1.7 0 001.5 1h.1a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z")
    val Folder = icon("folder", "M3 7a2 2 0 012-2h4l2 3h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V7z")
    val Refresh = icon("refresh", "M21 12a9 9 0 11-2.6-6.4M21 3v6h-6")
    val Eye = icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 15a3 3 0 100-6 3 3 0 000 6z")
    val Info = icon("info", "M12 3a9 9 0 100 18 9 9 0 000-18zM12 11v5M12 8h.01")
    val Clock = icon("clock", "M12 3a9 9 0 100 18 9 9 0 000-18zM12 7v5l3.5 2")
    val Waveform = icon("gapless", "M3 12h4l3-8 4 16 3-8h4")
    val Volume = icon("volume", "M11 5L6 9H2v6h4l5 4V5zM15.5 8.5a5 5 0 010 7M18.5 5.5a9 9 0 010 13")
    val Moon = icon("moon", "M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z")
    val Palette = icon("palette", "M12 3a9 9 0 000 18c1.2 0 1.6-1.2.8-2.2-1-1.3-2.6-2.8-1.6-4.6.9-1.6 3.6-1.4 5-1.4 3 0 4.6-1.8 4.6-4C20.8 6.6 16.9 3 12 3z")
    val Shield = icon("shield", "M12 3l9 3v6c0 5-3.8 8.2-9 9-5.2-.8-9-4-9-9V6l9-3zM9 12l2 2 4-4")
    val Home = icon("home", "M12 3l2.2 5.9 6.3.2-4.9 4 1.7 6.1L12 15.8l-5.3 3.4 1.7-6.1-4.9-4 6.3-.2L12 3z", fill = true)
    val Drop = icon("drop", "M12 3c2.8 4 5.5 7.2 5.5 10.2a5.5 5.5 0 11-11 0C6.5 10.2 9.2 7 12 3z")
    val Opacity = icon("opacity", "M12 3a9 9 0 100 18 9 9 0 000-18zM12 3v18")
    val RadioSignal = icon("radio_signal", "M12 12.5a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM8.5 8.5a5 5 0 000 7M5.5 5.5a9 9 0 000 13M15.5 8.5a5 5 0 010 7M18.5 5.5a9 9 0 010 13")
    val ChevronDown = icon("chevron_down", "M6 9l6 6 6-6", strokeWidth = 2.2f)

    private fun icon(
        name: String,
        pathData: String,
        strokeWidth: Float = 1.8f,
        fill: Boolean = false
    ): ImageVector {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            if (fill) {
                addPath(nodes, pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black))
            } else {
                addPath(
                    nodes,
                    pathFillType = PathFillType.NonZero,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()
    }
}
