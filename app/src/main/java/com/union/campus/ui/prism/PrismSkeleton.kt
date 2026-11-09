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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** 轻量骨架块：默认 1200ms 线性扫光，系统关闭动画时退化为静态灰块。 */
@Composable
fun PrismSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val enabled = prismAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "prism-skeleton")
    val shimmerState = if (enabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "prism-shimmer",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    val shimmer = shimmerState.value
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
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

/** 页面级骨架：一张主卡 + 若干列表行。 */
@Composable
fun PrismSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    showHero: Boolean = true,
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
                shape = RoundedCornerShape(18.dp),
            )
        }
        repeat(rows.coerceAtLeast(1)) {
            PrismSkeleton(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}