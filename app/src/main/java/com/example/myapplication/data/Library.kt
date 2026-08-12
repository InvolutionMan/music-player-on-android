package com.example.myapplication.data

import androidx.compose.ui.graphics.Color

data class LyricLine(val timeMs: Int, val text: String)

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val coverColors: List<Color>,
    val lyrics: List<LyricLine>
) {
    val durationText: String
        get() = "%d:%02d".format(durationSeconds / 60, durationSeconds % 60)
}

object ResoundLibrary {
    val tracks = listOf(
        Track(
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美 · 2003",
            durationSeconds = 217,
            coverColors = listOf(Color(0xFF7EC4F5), Color(0xFF3B6FD4), Color(0xFF2A4FAE)),
            lyrics = listOf(
                LyricLine(0, "故事的小黄花"),
                LyricLine(6000, "从出生那年就飘着"),
                LyricLine(13000, "童年的荡秋千"),
                LyricLine(19000, "随记忆一直晃到现在"),
                LyricLine(26000, "Re So So Si Do Si La"),
                LyricLine(33000, "So La Si Si Si Si La Si La So"),
                LyricLine(40000, "吹着前奏望着天空的"),
                LyricLine(47000, "我想起花瓣试着掉落"),
                LyricLine(54000, "为你翘课的那一天"),
                LyricLine(62000, "花落的那一天"),
                LyricLine(70000, "教室的那一间"),
                LyricLine(78000, "我怎么看不见"),
                LyricLine(86000, "消失的下雨天"),
                LyricLine(94000, "我好想再淋一遍"),
                LyricLine(102000, "没想到失去的勇气我还留着"),
                LyricLine(112000, "好想再问一遍"),
                LyricLine(122000, "你会等待还是离开"),
                LyricLine(132000, "刮风这天 我试过握着你手"),
                LyricLine(142000, "但偏偏 雨渐渐 大到我看你不见"),
                LyricLine(152000, "还要多久 我才能在你身边"),
                LyricLine(162000, "等到放晴的那天 也许我会比较好一点")
            )
        ),
        Track(
            title = "富士山下",
            artist = "陈奕迅",
            album = "认了吧 · 2007",
            durationSeconds = 259,
            coverColors = listOf(Color(0xFFCDD6DE), Color(0xFF8A97A5), Color(0xFF5D6A78)),
            lyrics = listOf(
                LyricLine(0, "拦路雨偏似雪花"),
                LyricLine(8000, "饮泣的你冻吗"),
                LyricLine(16000, "这风褛我给你磨到有襟花"),
                LyricLine(24000, "连调了职也不怕"),
                LyricLine(32000, "怎么始终牵挂"),
                LyricLine(40000, "苦心选中今天想车你回家")
            )
        ),
        Track(
            title = "平凡之路",
            artist = "朴树",
            album = "猎户星座 · 2017",
            durationSeconds = 303,
            coverColors = listOf(Color(0xFFE8C996), Color(0xFFC0945F), Color(0xFF8A6A3F)),
            lyrics = listOf(
                LyricLine(0, "我曾经跨过山和大海"),
                LyricLine(9000, "也穿过人山人海"),
                LyricLine(18000, "我曾经拥有着的一切"),
                LyricLine(27000, "转眼都飘散如烟"),
                LyricLine(36000, "我曾经失落失望失掉所有方向"),
                LyricLine(45000, "直到看见平凡才是唯一的答案")
            )
        ),
        Track(
            title = "红豆",
            artist = "王菲",
            album = "唱游 · 1998",
            durationSeconds = 255,
            coverColors = listOf(Color(0xFFF27A84), Color(0xFFD94A5A), Color(0xFFA32B3D)),
            lyrics = listOf(
                LyricLine(0, "还没好好地感受"),
                LyricLine(9000, "雪花绽放的气候"),
                LyricLine(18000, "我们一起颤抖"),
                LyricLine(27000, "会更明白 什么是温柔"),
                LyricLine(36000, "还没跟你牵着手"),
                LyricLine(45000, "走过荒芜的沙丘")
            )
        )
    )
}
