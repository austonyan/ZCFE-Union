package com.union.campus.ui.prism

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon

/**
 * 圆角 token（对齐 FlClash prism_ui AppCorner）：
 * sm 8 / md 16（默认卡）/ lg 20 / xl 24（分组首尾行）/ alert 34（对话框）/ sheet 38 / full 胶囊。
 */
object PrismCorner {
    const val None = 0f
    const val Xs = 4f
    const val Sm = 8f
    const val Md = 16f
    const val Lg = 20f
    const val Xl = 24f
    const val Xxl = 28f
    const val Alert = 34f
    const val Sheet = 38f
}

/** 平滑度：iOS 视感 ≈ Figma 0.6；胶囊形用 0。 */
const val PrismSmoothing = 0.6f

/**
 * 连续圆角形状（RoundedSuperellipse 等价实现，基于 androidx.graphics-shapes，
 * 与 M3 Expressive / Flutter RoundedSuperellipseBorder 同源）。
 *
 * @param radius 圆角半径 dp（<=0 为直角；Float.MAX_VALUE 视作胶囊）
 * @param smoothing 0..1，0 为标准圆角，越大越"连续"
 * @param corners 四角半径（tl, tr, br, bl），为 null 时统一用 radius
 */
class PrismShape(
    private val radius: Float,
    private val smoothing: Float = PrismSmoothing,
    private val corners: FloatArray? = null,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        val pillRadius = minOf(w, h) / 2f
        val isPill = radius >= pillRadius - 0.5f
        val perCorner = corners
        if (radius <= 0f && perCorner == null) return Outline.Rectangle(Rect(0f, 0f, w, h))

        val smoothingValue = if (isPill) 0f else smoothing
        val roundings: List<CornerRounding> = perCorner?.map {
            if (it <= 0f) CornerRounding.Unrounded else CornerRounding(it, smoothingValue)
        } ?: run {
            val r = radius.coerceAtMost(pillRadius)
            List(4) { CornerRounding(r, smoothingValue) }
        }
        val vertices = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        return try {
            val polygon = RoundedPolygon(
                vertices = vertices,
                rounding = roundings.first(),
                perVertexRounding = roundings,
                centerX = w / 2f,
                centerY = h / 2f,
            )
            Outline.Generic(polygon.toComposePath(w, h))
        } catch (_: Exception) {
            // 退化：极小尺寸下多边形构建失败时回退直角
            Outline.Rectangle(Rect(0f, 0f, w, h))
        }
    }
}

private fun RoundedPolygon.toComposePath(w: Float, h: Float): Path {
    val path = Path()
    val cubics = cubics
    if (cubics.isEmpty()) return path
    path.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
    for (c in cubics) {
        path.cubicTo(
            c.control0X, c.control0Y,
            c.control1X, c.control1Y,
            c.anchor1X, c.anchor1Y,
        )
    }
    path.close()
    return path
}

// 常用形状统一走 PrismTokens.prismShape(dp) / prismGroupShape()，不再保留第二套工厂。
