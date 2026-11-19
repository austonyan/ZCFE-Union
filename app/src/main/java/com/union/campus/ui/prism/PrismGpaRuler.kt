package com.union.campus.ui.prism

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/** 绩点尺分档阈值：0–4.0 三等分。 */
object GpaRulerBands {
    const val Max = 4f

    /** 较差 → 中等 的分界。 */
    const val Medium = 1.33f

    /** 中等 → 优秀 的分界。 */
    const val Excellent = 2.67f

    fun labelOf(gpa: Double): String = when {
        gpa >= Excellent -> "优秀"
        gpa >= Medium -> "中等"
        else -> "较差"
    }
}

/**
 * 绩点尺：页面唯一的高光。
 *
 * 左侧 Display 读数、右侧加权平均；下方三段中性色带 + 三档标签，
 * 指针用 primary 圆点与竖线标出当前绩点。无数据时保留色带、隐藏指针。
 */
@Composable
fun PrismGpaRuler(
    gpa: Double?,
    weightedAverage: String,
    modifier: Modifier = Modifier,
) {
    val animations = prismAnimationsEnabled()
    val targetFraction = gpa
        ?.let { (it / GpaRulerBands.Max.toDouble()).coerceIn(0.0, 1.0).toFloat() }
        ?: 0f
    var landing by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(gpa, animations) {
        landing = 0f
        if (gpa != null && animations) {
            // 停在 0 一帧，指针才会真正从起点落位。
            withFrameNanos { }
        }
        landing = targetFraction
    }
    val animated by animateFloatAsState(
        targetValue = landing,
        animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
        label = "gpa-ruler-pointer",
    )
    val pointer = if (animations) animated else targetFraction

    val description = if (gpa == null) {
        "绩点暂无数据"
    } else {
        "绩点 ${formatGpa(gpa)}，满分 4.0，属于${GpaRulerBands.labelOf(gpa)}"
    }

    PrismCard(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        contentPadding = PaddingValues(20.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = gpa?.let { formatGpa(it) } ?: "—",
                style = displayStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = "加权 ${weightedAverage.ifBlank { "—" }}",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .alignByBaseline(),
            )
        }
        Spacer(Modifier.height(10.dp))
        RulerTrack(gpa = gpa, pointer = pointer)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            RulerLabel("较差", Modifier.weight(1f))
            RulerLabel("中等", Modifier.weight(1f))
            RulerLabel("优秀", Modifier.weight(1f))
        }
    }
}

/** 8dp 圆头轨道 + 三段中性色带；指针圆点 12dp、竖线 2dp。 */
@Composable
private fun RulerTrack(gpa: Double?, pointer: Float) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.Center)
                .clip(PrismShape(Float.MAX_VALUE)),
        ) {
            val surface = MaterialTheme.colorScheme.onSurface
            Box(Modifier.weight(1f).fillMaxHeight().background(surface.withAlpha(0.06f)))
            Box(Modifier.weight(1f).fillMaxHeight().background(surface.withAlpha(0.09f)))
            Box(Modifier.weight(1f).fillMaxHeight().background(surface.withAlpha(0.12f)))
        }
        if (gpa != null) {
            Box(
                modifier = Modifier
                    // 圆点右缘与轨道右端对齐，避免 4.0 时指针越界。
                    .offset { IntOffset(((maxWidth - 12.dp).toPx() * pointer).roundToInt(), 0) }
                    .align(Alignment.CenterStart)
                    .width(12.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun RulerLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = caption2Style(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

private fun formatGpa(value: Double): String = String.format(Locale.ROOT, "%.2f", value)