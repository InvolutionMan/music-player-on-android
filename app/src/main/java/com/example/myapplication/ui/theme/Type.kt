package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 衬线体：Noto Serif CJK SC / Songti SC
val SerifFamily = FontFamily.Serif

// 圆体：Rounded Mplus 1c（系统无原生圆体，用系统字体 + 宽松行距模拟）
val RoundedFamily = FontFamily.Default

private val defaultStyles = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    )
)

fun typographyFor(fontStyle: Int): Typography {
    val family = when (fontStyle) {
        1 -> SerifFamily
        else -> FontFamily.Default
    }
    return Typography(
        displaySmall = defaultStyles.displaySmall.copy(fontFamily = family),
        headlineSmall = defaultStyles.headlineSmall.copy(fontFamily = family),
        titleLarge = defaultStyles.titleLarge.copy(fontFamily = family),
        titleMedium = defaultStyles.titleMedium.copy(fontFamily = family),
        bodyLarge = defaultStyles.bodyLarge.copy(fontFamily = family),
        bodyMedium = defaultStyles.bodyMedium.copy(fontFamily = family),
        labelMedium = defaultStyles.labelMedium.copy(fontFamily = family),
        labelSmall = defaultStyles.labelSmall.copy(fontFamily = family)
    )
}

// 时间等数字体：保持数字列对齐
val TabularStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 12.sp,
    letterSpacing = 0.2.sp,
    fontFeatureSettings = "tnum"
)
