package com.union.campus.ui.prism

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import kotlinx.coroutines.launch

/**
 * 按压反馈（FlClash PressSurface 复刻，无涟漪）：
 * - ink：内容色 α0.10 覆盖（卡片/列表行）
 * - tint：iOS 式整体提亮——亮色透明度降至 75%，暗色混入 20% 白（按钮）
 */
enum class PressLook { Ink, Tint }

private const val INK_ALPHA = 0.10f
private const val TINT_FADE = 0.25f
private const val TINT_LIFT = 0.2f

val LocalPressLook = compositionLocalOf { PressLook.Ink }
val LocalPrismDark = compositionLocalOf { false }

/** 全局按压指示：主题根部通过 LocalIndication 提供并禁用 ripple。 */
object PrismPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatingNode =
        PressNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 2103
}

private class PressNode(private val interactionSource: InteractionSource) :
    DelegatingNode(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    // Animatable.value 是 snapshot state：draw 中读取会自动触发重绘
    private val animatable = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        animatable.stop()
                        launch { animatable.animateTo(1f, tween(200)) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        animatable.stop()
                        launch { animatable.animateTo(0f, tween(250)) }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val look = currentValueOf(LocalPressLook)
        val onSurface = currentValueOf(androidx.compose.material3.LocalContentColor)
        val dark = currentValueOf(LocalPrismDark)
        val p = animatable.value

        when (look) {
            PressLook.Tint -> {
                if (dark) {
                    drawContent()
                    if (p > 0f) drawRect(Color.White.copy(alpha = TINT_LIFT * p))
                } else {
                    drawWithAlpha(1f - TINT_FADE * p) { drawContent() }
                }
            }
            PressLook.Ink -> {
                drawContent()
                if (p > 0f) drawRect(onSurface.copy(alpha = INK_ALPHA * p))
            }
        }
    }

    private inline fun ContentDrawScope.drawWithAlpha(
        alpha: Float,
        crossinline content: ContentDrawScope.() -> Unit,
    ) {
        if (alpha >= 0.999f) {
            content()
        } else {
            val canvas = drawContext.canvas.nativeCanvas
            val paint = android.graphics.Paint().apply { this.alpha = (255 * alpha).toInt() }
            val save = canvas.saveLayer(0f, 0f, size.width, size.height, paint)
            content()
            canvas.restoreToCount(save)
        }
    }
}

/** 给子树设置按压样式。 */
@Composable
fun WithPressLook(look: PressLook, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPressLook provides look, content = content)
}
