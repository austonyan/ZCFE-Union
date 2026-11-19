package com.union.campus.ui.prism

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Surface 四级层（FlClash surface.dart 亮暗不对称规则）：
 * 亮色 page=灰、card=纯白（最亮）；暗色 page=更暗、card=tone15（更亮一档）。
 *
 * 亮色下 Page(#EFEDF0) 与 Card(#FFFFFF) 的对比约 1.22:1，深色下同样是两级明度差；
 * 相邻层级低于 1.2:1 时边界不可辨，卡片会退化成"贴纸"。
 */
enum class SurfaceLevel {
    Page, Card, Block, Inset,
}

private fun ColorScheme.colorOf(level: SurfaceLevel, dark: Boolean): Color = when (level) {
    // 实测：surfaceContainer 对纯白只有 1.17:1，卡片边界仍然发虚；
    // 再压一档到 surfaceContainerHigh（约 1.22:1）才过 1.2 的可辨阈值。
    SurfaceLevel.Page -> if (dark) surfaceDim else surfaceContainerHigh
    SurfaceLevel.Card -> if (dark) surfaceContainer else surfaceContainerLowest
    SurfaceLevel.Block -> if (dark) surfaceContainerHigh else surfaceContainer
    SurfaceLevel.Inset -> surfaceContainerHighest
}

/** 当前所处 surface 层级（SurfaceScope 等价物）。 */
val LocalSurfaceLevel = staticCompositionLocalOf { SurfaceLevel.Page }

/** 取当前层级再往上一级的颜色（卡片放在 page 上时自动升一级）。 */
@Composable
fun raisedSurfaceColor(base: SurfaceLevel = LocalSurfaceLevel.current): Color =
    MaterialTheme.colorScheme.colorOf(base.next(), MaterialTheme.colorScheme.isDark())

internal fun SurfaceLevel.next(): SurfaceLevel = when (this) {
    SurfaceLevel.Page -> SurfaceLevel.Card
    SurfaceLevel.Card -> SurfaceLevel.Block
    SurfaceLevel.Block -> SurfaceLevel.Inset
    SurfaceLevel.Inset -> SurfaceLevel.Inset
}

private fun ColorScheme.isDark(): Boolean = surface.luminanceLessThan(surfaceContainer)

private fun Color.luminanceLessThan(other: Color): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) <
        (0.299f * other.red + 0.587f * other.green + 0.114f * other.blue)

/** 组合局部：切到某 surface 层级的子树。 */
@Composable
fun SurfaceScope(
    level: SurfaceLevel,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSurfaceLevel provides level, content = content)
}

/** 直接取色（供非组合位置使用时请用重载版本）。 */
@Composable
fun surfaceColorOf(level: SurfaceLevel): Color =
    MaterialTheme.colorScheme.colorOf(level, MaterialTheme.colorScheme.isDark())
