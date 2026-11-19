package com.union.campus.ui.prism

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.union.campus.ui.theme.PrismType
import androidx.compose.ui.unit.sp

/**
 * Prism 设计令牌：页面层不再自造字号、透明度与间距。
 *
 * 节奏：页面 16 / 区间 28 / 区内 12；分组头 T24 B8。
 * 透明度只用于非文字元素（分隔线 / 描边 / 容器底 / 骨架 / 图表网格与柱体）。
 * 文字不再叠第二层 alpha：次要文字走 [prismTextMuted]（onSurfaceVariant 1.0），
 * 第三层注记走 [prismTextFaint]（只压一档 0.85，仍 ≥4.5:1）。
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

    /** 旧次要文字档位：文字层请改用 [prismTextMuted]；这里只保留给尚未迁移的非文字调用点。 */
    const val Secondary = 0.72f

    /** 旧提示档位：文字层请改用 [prismTextFaint]；这里保留给图表网格/柱体等非文字用途。 */
    const val Hint = 0.55f

    /**
     * 第三层文字（注记、轴标签）的唯一 alpha：压在 onSurfaceVariant 上的一层。
     * 实测亮色 9 套预设最差 5.21:1（surfaceContainerHigh）/ 6.02:1（纯白），暗色 ≥6.80:1。
     */
    const val Faint = 0.85f

    /** 禁用。 */
    const val Disabled = 0.38f

    /** 分隔线（旧值，渐变/遮罩类仍可用）。 */
    const val Divider = 0.14f

    /** 列表分隔线：深色下 0.14 会发白，统一降到 0.10。 */
    const val DividerSoft = 0.10f

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

    /** 指针悬停 / 键盘聚焦的状态叠加（弱于按压，保证外接键盘与鼠标也有反馈）。 */
    const val Hover = 0.06f
    const val Focus = 0.12f
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

/** 页面标题（28/34 w700）。尺寸定义在 [PrismType.PageTitle]，此处仅作语义别名。 */
@Composable
fun pageTitleStyle(): TextStyle = PrismType.PageTitle

/** 分组头/卡片头（17/22 w600）。 */
@Composable
fun headlineStyle(): TextStyle = PrismType.SectionTitle

/** 列表标题（17/22 w400）。 */
@Composable
fun bodyStyle(): TextStyle = PrismType.ListTitle

/** 列表副标题/正文（15/20）。 */
@Composable
fun subheadlineStyle(): TextStyle = PrismType.Subhead

/** footer（13/18）。 */
@Composable
fun footnoteStyle(): TextStyle = PrismType.Footnote

/** 导航/chip（12/16 w600）。 */
@Composable
fun caption1Style(): TextStyle = PrismType.Caption

/** 按钮标签（15/20 w600）；按钮内文字统一用这一档，窄按钮也不换行。 */
@Composable
fun buttonLabelStyle(): TextStyle = PrismType.ButtonLabel

/** 底部导航栏标签（10/12 w600）：比 caption1 再小一档，5 个 tab 才能并排放下。 */
@Composable
fun dockLabelStyle(): TextStyle = PrismType.Caption.copy(fontSize = 10.sp, lineHeight = 12.sp)

/** 最小注记（11/14 w500），不再出现 8/9sp。 */
@Composable
fun caption2Style(): TextStyle = PrismType.Caption2

/** 仪表盘大数字（40/44 w700，等宽数字）。 */
@Composable
fun displayStyle(): TextStyle = PrismType.Display.copy(fontFeatureSettings = "tnum")

/** 指标主数字（34/40 w700，等宽数字）。 */
@Composable
fun metricStyle(): TextStyle = PrismType.Metric.copy(fontFeatureSettings = "tnum")

/** 指标次数字（双列卡，24/28 w700，等宽数字）。 */
@Composable
fun metricCompactStyle(): TextStyle = PrismType.MetricCompact.copy(fontFeatureSettings = "tnum")

/** 指标小数字（卡片内联，20/24 w600，等宽数字）。 */
@Composable
fun metricSmallStyle(): TextStyle = PrismType.MetricSmall.copy(fontFeatureSettings = "tnum")


/** 页面/卡片使用的形状：参数为 dp，内部按 density 换算，避免再次漏乘。 */
@Composable
fun prismShape(radius: Float, smoothing: Float = PrismSmoothing): PrismShape {
    val density = LocalDensity.current.density
    return remember(radius, smoothing, density) { PrismShape(radius * density, smoothing) }
}

/** 分组列表用的整组形状（首尾圆角由容器裁剪，行本身不再各自裁角）。 */
@Composable
fun prismGroupShape(): PrismShape = prismShape(PrismCorner.Xl)

/**
 * 供 `MaterialTheme(shapes = ...)` 使用：让 M3 原生组件（Button / Card / Chip / Slider / Menu）
 * 与 Prism 使用同一套圆角数值，避免两套形状体系并存。
 *
 * M3 的 [Shapes] 只接受 [CornerBasedShape]，因此这里用 RoundedCornerShape 承载同样的 dp 值；
 * Prism 自绘组件仍走 [prismShape] 的连续圆角。
 */
val PrismMaterialShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(PrismCorner.Sm.dp),
    small = RoundedCornerShape(PrismCorner.Lg.dp),
    medium = RoundedCornerShape(PrismCorner.Md.dp),
    large = RoundedCornerShape(PrismCorner.Xl.dp),
    extraLarge = RoundedCornerShape(PrismCorner.Xxl.dp),
)

/** 统一的透明度入口：页面不再直接写 copy(alpha = …)。 */
fun Color.withAlpha(value: Float): Color = copy(alpha = value)

/**
 * 语义化次要文字色：只用一层，不再叠 alpha。
 *
 * 页面/卡片上直接用 [MaterialTheme.colorScheme.onSurfaceVariant]（1.0）。该令牌本身
 * 已按对比度校准：亮色默认主题在 surfaceContainerHigh(#E1EAEA) 上 9.14:1、
 * surfaceContainerLowest(#FFFFFF) 上 11.19:1；9 套预设最差 7.58 / 9.30。
 * 旧写法再乘 0.72 会掉到 3.81–4.32:1，这就是要修的双重压暗。
 *
 * 彩色容器（primaryContainer 等）传入配套的内容色 [base]（onXContainer）：原样返回。
 * onXContainer/container 已由色板按 ≥4.5:1 配对，再乘 alpha 反而掉档；层级交给
 * 字号与字重，而不是继续压颜色（onSurfaceVariant 放在预设的 tertiaryContainer 上
 * 只有约 2:1，不能替代容器内容色）。
 */
@Composable
fun prismTextMuted(base: Color = MaterialTheme.colorScheme.onSurface): Color =
    if (base == MaterialTheme.colorScheme.onSurface) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        base
    }

/**
 * 第三层文字色（轴标签、占位说明、极弱注记）：onSurfaceVariant 只压一档
 * [PrismAlpha.Faint]（0.85）。
 *
 * 实测（WCAG 2.x）：亮色默认主题 6.08:1（页面）/ 7.08:1（卡片）；9 套预设最差
 * 5.21:1 / 6.02:1；暗色默认主题 9.27 / 8.36，预设最差 8.09 / 6.80 —— 全部 ≥4.5。
 * 彩色容器不适用（onXContainer 再压会掉到约 3.5），此时返回 [base] 原色。
 */
@Composable
fun prismTextFaint(base: Color = MaterialTheme.colorScheme.onSurface): Color =
    if (base == MaterialTheme.colorScheme.onSurface) {
        MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Faint)
    } else {
        base
    }

/** 分组头间距（T24 B8）。 */
val PrismSectionHeaderPadding = PaddingValues(
    start = 0.dp,
    end = 0.dp,
    top = PrismSpace.SectionHeaderTop,
    bottom = PrismSpace.SectionHeaderBottom,
)