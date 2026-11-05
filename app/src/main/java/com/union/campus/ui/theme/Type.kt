package com.union.campus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
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

private val Defaults = Typography()

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
