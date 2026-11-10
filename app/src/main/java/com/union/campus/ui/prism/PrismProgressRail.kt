package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 引导流程进度轨：段数即步骤数，已完成段填充主色。 */
@Composable
fun PrismProgressRail(
    step: Int,
    total: Int,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(total.coerceAtLeast(1)) { index ->
                val active = index < step
                val color by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    animationSpec = PrismMotion.tween(PrismMotion.Standard),
                    label = "progress-segment",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(color, RoundedCornerShape(2.dp)),
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = footnoteStyle(),
                        color = if (index < step) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 顶部细进度条：用于表单页的轻量步骤提示。 */
@Composable
fun PrismStepBar(step: Int, total: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = step.toFloat() / total.coerceAtLeast(1) + 0.0001f,
        animationSpec = PrismMotion.tween(PrismMotion.Emphasized),
        label = "step-progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

/** 状态色的小圆点。 */
@Composable
fun PrismStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 8,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}