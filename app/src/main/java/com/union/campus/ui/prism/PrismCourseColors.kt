package com.union.campus.ui.prism

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 课程色源：课表、今天页主卡与课程详情共用。
 * 六色按课程名稳定取色，亮/暗两套，避免在页面里各写一份调色板。
 */
@Composable
fun prismCoursePalette(name: String): Pair<Color, Color> {
    val light = listOf(
        Color(0xFFDDF3E4) to Color(0xFF145C3A),
        Color(0xFFE7E2FA) to Color(0xFF3F347F),
        Color(0xFFFCE7D6) to Color(0xFF7A431D),
        Color(0xFFF9DEE8) to Color(0xFF7D2C4E),
        Color(0xFFDDF1FA) to Color(0xFF15546E),
        Color(0xFFF3EAD6) to Color(0xFF5E4A20),
    )
    val dark = listOf(
        Color(0xFF1F3A2B) to Color(0xFFC9F2DC),
        Color(0xFF2E2A4A) to Color(0xFFDCD6FF),
        Color(0xFF3A2A1C) to Color(0xFFF7D9BD),
        Color(0xFF3A2330) to Color(0xFFF7CBDD),
        Color(0xFF1B3440) to Color(0xFFC9ECFA),
        Color(0xFF33301F) to Color(0xFFEFE2BE),
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