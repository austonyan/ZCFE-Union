package com.union.campus.ui.prism

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 课程色源：课表、今天页主卡与课程详情共用。
 *
 * 色相收敛在 teal→cyan→blue→indigo→violet→slate 这一段，容器明度固定（亮 tone≈92 / 暗 tone≈26），
 * 只靠色相 + 相邻明度区分课程。原来的暖色（橙/粉/棕）会把课表拉成"彩虹墙"，
 * 冷色段既好区分又不会抢主色。
 */
@Composable
fun prismCoursePalette(name: String): Pair<Color, Color> {
    val light = listOf(
        Color(0xFFDDF1EC) to Color(0xFF13544A), // teal
        Color(0xFFDCEFF7) to Color(0xFF12505F), // cyan
        Color(0xFFDEE8FB) to Color(0xFF1D3F73), // blue
        Color(0xFFE3E4FA) to Color(0xFF33357A), // indigo
        Color(0xFFEAE2F8) to Color(0xFF4A3079), // violet
        Color(0xFFE4E8EF) to Color(0xFF37455C), // slate
    )
    val dark = listOf(
        Color(0xFF14352F) to Color(0xFFBDEBE0), // teal
        Color(0xFF123039) to Color(0xFFC3E7F4), // cyan
        Color(0xFF1A2A44) to Color(0xFFCBDCFB), // blue
        Color(0xFF272A4C) to Color(0xFFD5D7FF), // indigo
        Color(0xFF2E2449) to Color(0xFFDCCBFB), // violet
        Color(0xFF252C38) to Color(0xFFD3DCEA), // slate
    )
    val palette = if (LocalPrismDark.current) dark else light
    return palette[abs(name.hashCode()) % palette.size]
}

/** 课程主色（容器底），用于今天页主卡与课程详情头部。 */
@Composable
fun prismCourseContainer(name: String): Color = prismCoursePalette(name).first

/** 课程主色上的文字/图标色。 */
@Composable
fun prismCourseOnContainer(name: String): Color = prismCoursePalette(name).second