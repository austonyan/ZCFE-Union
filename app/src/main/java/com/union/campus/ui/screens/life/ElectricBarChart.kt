package com.union.campus.ui.screens.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.domain.ElectricStats
import com.union.campus.ui.prism.footnoteStyle

/** 柱状图上的一个数据点。 */
data class ElectricBar(val label: String, val value: Double)

/**
 * 轻量柱状图：不依赖图表库，按可用宽度均分柱子。
 * 适合 7 天用量、30/84 天区间和近 6 个月统计。
 */
@Composable
fun ElectricBarChart(
    bars: List<ElectricBar>,
    modifier: Modifier = Modifier,
    valueSuffix: String = "度",
    valueFormat: (Double) -> String = { ElectricStats.formatKwh(it) },
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int? = bars.lastIndex.takeIf { bars.isNotEmpty() },
    showValueLabels: Boolean = bars.size <= 10,
) {
    if (bars.isEmpty()) {
        Text(
            "暂无可绘制的用量数据",
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val maxValue = bars.maxOf { it.value }.takeIf { it > 0.0 } ?: 1.0
    val labelStep = when {
        bars.size <= 7 -> 1
        bars.size <= 14 -> 2
        bars.size <= 31 -> 5
        else -> 7
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    Column(modifier.fillMaxWidth()) {
        Text(
            "峰值 ${valueFormat(maxValue)}$valueSuffix",
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                for (index in 0..3) {
                    val y = size.height * index / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                bars.forEachIndexed { index, bar ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showValueLabels) {
                            Text(
                                if (bar.value > 0.0) valueFormat(bar.value) else "",
                                fontSize = 8.sp,
                                color = valueColor,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val fraction = (bar.value / maxValue).toFloat().coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .fillMaxWidth(0.56f)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (highlightIndex == index) barColor
                                        else barColor.copy(alpha = 0.5f),
                                    ),
                            )
                        }
                        Text(
                            if (index % labelStep == 0 || index == bars.lastIndex) bar.label else "",
                            fontSize = 8.sp,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}