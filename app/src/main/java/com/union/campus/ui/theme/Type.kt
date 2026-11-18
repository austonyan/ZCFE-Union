package com.union.campus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.union.campus.R

// Roboto Flex 可变字体：仅启用 wght 与 opsz 轴，其余取默认值
private fun flexFont(weight: Int): Font = Font(
    resId = R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", 28f),
    ),
)

private val FlexFamily = FontFamily(
    flexFont(400),
    flexFont(500),
    flexFont(600),
    flexFont(700),
)

// M3 Expressive 的 emphasized 样式 ≈ 标题/按钮/标签用更粗字重（600），正文用常规字重
private fun TextStyle.emphasized(weight: Int = 600) = copy(
    fontFamily = FlexFamily,
    fontWeight = FontWeight(weight),
)

private fun TextStyle.regular() = copy(
    fontFamily = FlexFamily,
    fontWeight = FontWeight(400),
)

/** 统一改写尺寸，避免每个样式函数各自 `.copy(fontSize = …)`。 */
private fun TextStyle.sized(size: Float, lineHeight: Float, weight: Int) = copy(
    fontFamily = FlexFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private val Defaults = Typography()

/**
 * Prism 字阶（iOS Dynamic Type 档 × Roboto Flex）。
 *
 * 尺寸全部只在 [PrismType] 定义一次；`PrismTokens` 里的 `pageTitleStyle` 等只是语义别名，
 * 页面层不再出现 `copy(fontSize = …)`，改字号只改这里。
 */
object PrismType {
    /** 页面标题 28/34 w700 */
    val PageTitle = Defaults.headlineSmall.sized(28f, 34f, 700)

    /** 分组头/卡片头 17/22 w600 */
    val SectionTitle = Defaults.titleMedium.sized(17f, 22f, 600)

    /** 列表标题 17/22 w400 */
    val ListTitle = Defaults.bodyLarge.sized(17f, 22f, 400)

    /** 列表副标题/正文 15/20 w400 */
    val Subhead = Defaults.bodyMedium.sized(15f, 20f, 400)

    /** footer 13/18 w400 */
    val Footnote = Defaults.bodySmall.sized(13f, 18f, 400)

    /** 导航/chip 12/16 w600 */
    val Caption = Defaults.labelMedium.sized(12f, 16f, 600)

    /** 按钮标签 15/20 w600 */
    val ButtonLabel = Defaults.labelLarge.sized(15f, 20f, 600)

    /** 最小注记 11/14 w500 */
    val Caption2 = Defaults.labelSmall.sized(11f, 14f, 500)

    /** 仪表盘大数字 40/44 w700 */
    val Display = Defaults.displayMedium.sized(40f, 44f, 700)

    /** 指标主数字 34/40 w700 */
    val Metric = Defaults.headlineSmall.sized(34f, 40f, 700)

    /** 指标次数字 24/28 w700 */
    val MetricCompact = Defaults.titleLarge.sized(24f, 28f, 700)

    /** 指标小数字 20/24 w600 */
    val MetricSmall = Defaults.titleMedium.sized(20f, 24f, 600)
}

val UnionTypography = Typography(
    displayLarge = Defaults.displayLarge.emphasized(),
    displayMedium = Defaults.displayMedium.emphasized(),
    displaySmall = Defaults.displaySmall.emphasized(),
    headlineLarge = Defaults.headlineLarge.emphasized(),
    headlineMedium = Defaults.headlineMedium.emphasized(),
    headlineSmall = Defaults.headlineSmall.emphasized(),
    titleLarge = Defaults.titleLarge.emphasized(),
    titleMedium = Defaults.titleMedium.emphasized(),
    titleSmall = Defaults.titleSmall.emphasized(),
    bodyLarge = Defaults.bodyLarge.regular(),
    bodyMedium = Defaults.bodyMedium.regular(),
    bodySmall = Defaults.bodySmall.regular(),
    labelLarge = Defaults.labelLarge.emphasized(),
    labelMedium = Defaults.labelMedium.emphasized(500),
    labelSmall = Defaults.labelSmall.emphasized(500),
)
