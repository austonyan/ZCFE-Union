package com.union.campus.ui.prism

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** 轻量骨架块：默认 1500ms 线性扫光，系统关闭动画时退化为静态灰块。 */
@Composable
fun PrismSkeleton(
    modifier: Modifier = Modifier,
    corner: Float = PrismCorner.Sm,
) {
    val density = LocalDensity.current
    val shape = remember(density.density, corner) { PrismShape(corner * density.density) }
    val enabled = rememberPrismMotion().enabled
    val transition = rememberInfiniteTransition(label = "prism-skeleton")
    val shimmerState = if (enabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(PrismMotion.Shimmer, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "prism-shimmer",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    val shimmer = shimmerState.value
    val base = MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Skeleton)
    val highlight = MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.SkeletonHighlight)
    val brush: Brush = if (enabled) {
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(shimmer * 1200f - 600f, 0f),
            end = Offset(shimmer * 1200f, 0f),
        )
    } else {
        SolidColor(base)
    }
    androidx.compose.foundation.layout.Box(modifier.clip(shape).background(brush))
}

/**
 * 页面级骨架：按真实页面形状排（主卡 → 双列指标 → 列表行）。
 *
 * 统一的大方块会让加载态看起来"卡住了"；骨架的形状越接近内容，
 * 加载完成时布局越不跳。
 */
@Composable
fun PrismSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    showHero: Boolean = true,
    showMetricRow: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showHero) {
            PrismSkeleton(
                modifier = Modifier.fillMaxWidth().height(132.dp),
                corner = PrismShapeRole.Hero,
            )
        }
        if (showMetricRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrismSkeleton(
                    modifier = Modifier.weight(1f).height(96.dp),
                    corner = PrismShapeRole.Card,
                )
                PrismSkeleton(
                    modifier = Modifier.weight(1f).height(96.dp),
                    corner = PrismShapeRole.Card,
                )
            }
        }
        repeat(rows.coerceAtLeast(1)) {
            PrismSkeleton(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                corner = PrismShapeRole.Card,
            )
        }
    }
}
