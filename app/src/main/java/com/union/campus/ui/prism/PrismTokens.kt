package com.union.campus.ui.prism

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Prism 设计令牌：页面层不再自造字号、透明度与间距。
 *
 * 节奏：页面 16 / 区间 28 / 区内 12；分组头 T24 B8。
 * 透明度五档：分隔线 0.14 / 禁用 0.38 / 提示 0.55 / 次要 0.72 / 主文 1。
 */

object PrismSpace {
    /** 页面左右边距。 */
    val Page = 16.dp

    /** 两个区间之间的留白。 */
    val Section = 28.dp

    /** 区间内部（分组头之后、卡片之间）的间距。 */
    val Item = 12.dp

    /** 卡片内边距。 */
    val Card = 16.dp

    /** 分组头：上 24 / 下 8。 */
    val SectionHeaderTop = 24.dp
    val SectionHeaderBottom = 8.dp

    /** 页面底部留白。 */
    val Bottom = 24.dp

    /** 列表行左右内边距。 */
    val RowHorizontal = 16.dp
}

object PrismAlpha {
    /** 主文。 */
    const val Primary = 1f

    /** 次要文字、卡片脚注。 */
    const val Secondary = 0.72f

    /** 非关键提示、占位说明。 */
    const val Hint = 0.55f

    /** 禁用。 */
    const val Disabled = 0.38f

    /** 分隔线。 */
    const val Divider = 0.14f

    /** 浅色底（chip、徽标容器）。 */
    const val ContainerTint = 0.14f

    /** 描边、极弱分隔。 */
    const val Border = 0.08f

    /** 开关/滑杆未选中轨道。 */
    const val Track = 0.12f

    /** 骨架屏。 */
    const val Skeleton = 0.06f
    const val SkeletonHighlight = 0.11f

    /** 弹层遮罩。 */
    const val ScrimLight = 0.20f
    const val ScrimDark = 0.48f

    /** 弹层把手。 */
    const val Grip = 0.60f

    /** 错误底填充。 */
    const val ErrorFill = 0.24f

    /** 按压 ink / tint（交互层，不是文字层级）。 */
    const val PressInk = 0.10f
    const val PressTint = 0.25f
    const val PressLift = 0.20f
}

object PrismIconSize {
    val Toolbar: Dp = 24.dp
    val Row: Dp = 24.dp
    val Card: Dp = 20.dp
    val Dock: Dp = 28.dp
    val Badge: Dp = 24.dp
    val Chevron: Dp = 18.dp
    val Empty: Dp = 44.dp
    val Meta: Dp = 16.dp
}

/** 页面标题：28/34 w700；PrismPageHeader 与 pageTitleStyle 共用。 */
@Composable
fun pageTitleStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight(700),
)

/** 分组头/卡片头：17/22 w600。 */
@Composable
fun headlineStyle(): TextStyle = MaterialTheme.typography.titleMedium.copy(
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight(600),
)

/** 列表标题：17/22 w400。 */
@Composable
fun bodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp)

/** 列表副标题/正文：15/20。 */
@Composable
fun subheadlineStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** footer：13/18。 */
@Composable
fun footnoteStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** 导航/chip：12/16 w600。 */
@Composable
fun caption1Style(): TextStyle = MaterialTheme.typography.labelMedium.copy(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(600),
)

/** 按钮标签：15/20 w600；按钮内文字统一用这一档，窄按钮也不换行。 */
@Composable
fun buttonLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight(600),
)

/** 最小注记：11/14 w500，不再出现 8/9sp。 */
@Composable
fun caption2Style(): TextStyle = MaterialTheme.typography.labelSmall.copy(
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500),
)

/** 仪表盘大数字：40/44 w700，等宽数字。 */
@Composable
fun displayStyle(): TextStyle = MaterialTheme.typography.displayMedium.copy(
    fontSize = 40.sp,
    lineHeight = 44.sp,
    fontWeight = FontWeight(700),
    fontFeatureSettings = "tnum",
)

/** 指标主数字：34/40 w700，等宽数字。 */
@Composable
fun metricStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    fontSize = 34.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight(700),
    fontFeatureSettings = "tnum",
)

/** 指标次数字（双列卡）：24/28 w700，等宽数字。 */
@Composable
fun metricCompactStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(
    fontSize = 24.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight(700),
    fontFeatureSettings = "tnum",
)

/** 指标小数字（卡片内联）：20/24 w600，等宽数字。 */
@Composable
fun metricSmallStyle(): TextStyle = MaterialTheme.typography.titleMedium.copy(
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight(600),
    fontFeatureSettings = "tnum",
)

/** 页面/卡片使用的形状：参数为 dp，内部按 density 换算，避免再次漏乘。 */
@Composable
fun prismShape(radius: Float, smoothing: Float = PrismSmoothing): PrismShape {
    val density = LocalDensity.current.density
    return remember(radius, smoothing, density) { PrismShape(radius * density, smoothing) }
}

/** 分组列表用的整组形状（首尾圆角由容器裁剪，行本身不再各自裁角）。 */
@Composable
fun prismGroupShape(): PrismShape = prismShape(PrismCorner.Xl)

/** 统一的透明度入口：页面不再直接写 copy(alpha = …)。 */
fun Color.withAlpha(value: Float): Color = copy(alpha = value)

/** 分组头间距（T24 B8）。 */
val PrismSectionHeaderPadding = PaddingValues(
    start = 0.dp,
    end = 0.dp,
    top = PrismSpace.SectionHeaderTop,
    bottom = PrismSpace.SectionHeaderBottom,
)