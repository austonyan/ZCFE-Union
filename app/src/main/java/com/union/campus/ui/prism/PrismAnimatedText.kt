package com.union.campus.ui.prism

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * 数字/短文本变化：上滑淡入，配合等宽数字避免宽度跳动。
 * 不用于每秒计时器，避免持续动画。
 */
@Composable
fun PrismAnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    contentAlignment: Alignment = Alignment.CenterStart,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 3 } + fadeIn(PrismMotion.tween(160))) togetherWith
                (slideOutVertically { -it / 3 } + fadeOut(PrismMotion.tween(160)))
        },
        contentAlignment = contentAlignment,
        label = "prism-animated-text",
    ) { value ->
        Text(
            text = value,
            modifier = modifier,
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
        )
    }
}