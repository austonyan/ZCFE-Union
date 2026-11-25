package com.union.campus.ui.prism

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp

/**
 * 卡片边缘：1 物理像素描边。
 *
 * 纯 tonal 层级在浅色下只有约 1.2:1 的明度差，没有边界时卡片会"糊"在页面上；
 * 深色下卡片比页面亮，需要一条顶边高光来模拟受光，否则整块发平。
 *
 * @param shape 与卡片相同的连续圆角，描边沿轮廓绘制
 * @param dark 深色主题走 outline 高光，浅色主题走 outlineVariant 压边（均取自 colorScheme）
 */
@Composable
fun Modifier.prismEdge(shape: Shape, dark: Boolean = LocalPrismDark.current): Modifier {
    // 描边从配色方案取色（深色 outline / 浅色 outlineVariant），让动态取色与暗色预设
    // 也能参与，而不是固定白/黑 alpha。
    val edge = if (dark) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
    return drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(width = Dp.Hairline.toPx())
        if (dark) {
            // 深色：整圈弱高光，再给上半圈提亮一档，形成"上面受光"的材质暗示。
            drawOutlineStroke(outline, edge.copy(alpha = 0.10f), stroke)
            clipRect(0f, 0f, size.width, size.height / 2) {
                drawOutlineStroke(outline, edge.copy(alpha = 0.14f), stroke)
            }
        } else {
            drawOutlineStroke(outline, edge.copy(alpha = 0.30f), stroke)
        }
    }
}

/** Outline 没有内置的描边 API，这里按三种形态各画一次。 */
private fun DrawScope.drawOutlineStroke(outline: Outline, color: Color, stroke: Stroke) {
    when (outline) {
        is Outline.Generic -> drawPath(outline.path, color = color, style = stroke)
        is Outline.Rounded -> {
            val r = outline.roundRect
            drawRoundRect(
                color = color,
                topLeft = Offset(r.left, r.top),
                size = Size(r.right - r.left, r.bottom - r.top),
                cornerRadius = r.topLeftCornerRadius,
                style = stroke,
            )
        }
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = stroke,
        )
    }
}