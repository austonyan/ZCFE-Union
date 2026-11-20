package com.union.campus.ui.prism

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * 按压反馈（FlClash PressSurface 复刻，无涟漪）：
 * - ink：内容色 α0.10 覆盖（卡片/列表行）
 * - tint：iOS 式整体提亮——亮色透明度降至 75%，暗色混入 20% 白（按钮）
 */
enum class PressLook { Ink, Tint }

private const val HOVER_ALPHA = PrismAlpha.Hover
private const val FOCUS_ALPHA = PrismAlpha.Focus
private const val INK_ALPHA = PrismAlpha.PressInk
private const val TINT_FADE = PrismAlpha.PressTint
private const val TINT_LIFT = PrismAlpha.PressLift

val LocalPressLook = compositionLocalOf { PressLook.Ink }
val LocalPrismDark = compositionLocalOf { false }

/** 全局按压指示：主题根部通过 LocalIndication 设为默认 indication；不屏蔽 ripple，press/hover/focus 三态与 ripple 叠加。 */
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

    // 键盘聚焦 / 指针悬停也要有可见反馈（M3 交互状态层要求）
    private val hover = Animatable(0f)
    private val focus = Animatable(0f)

    private suspend fun updateState(state: Animatable<Float, *>, target: Float, duration: Int) {
        if (PrismMotion.animationsEnabled(currentValueOf(LocalContext))) {
            state.animateTo(target, PrismMotion.tween(duration))
        } else {
            state.snapTo(target)
        }
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        animatable.stop()
                        launch { updateState(animatable, 1f, PrismMotion.Fast) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        animatable.stop()
                        launch { updateState(animatable, 0f, PrismMotion.Standard) }
                    }
                    is HoverInteraction.Enter -> {
                        hover.stop()
                        launch { updateState(hover, 1f, PrismMotion.Fast) }
                    }
                    is HoverInteraction.Exit -> {
                        hover.stop()
                        launch { updateState(hover, 0f, PrismMotion.Standard) }
                    }
                    is FocusInteraction.Focus -> {
                        focus.stop()
                        launch { updateState(focus, 1f, PrismMotion.Fast) }
                    }
                    is FocusInteraction.Unfocus -> {
                        focus.stop()
                        launch { updateState(focus, 0f, PrismMotion.Standard) }
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
        // 悬停/聚焦取较弱的一档，避免盖过按压反馈
        val state = maxOf(hover.value * HOVER_ALPHA, focus.value * FOCUS_ALPHA)

        when (look) {
            PressLook.Tint -> {
                if (dark) {
                    drawContent()
                    if (p > 0f) drawRect(Color.White.withAlpha(TINT_LIFT * p))
                    if (state > 0f) drawRect(Color.White.withAlpha(state))
                } else {
                    drawWithAlpha(1f - TINT_FADE * p) { drawContent() }
                    if (state > 0f) drawRect(onSurface.withAlpha(state))
                }
            }
            PressLook.Ink -> {
                drawContent()
                val alpha = maxOf(INK_ALPHA * p, state)
                if (alpha > 0f) drawRect(onSurface.withAlpha(alpha))
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

/** 按压缩放：按下 0.985、抬起回弹；只走 graphicsLayer，不触发布局。 */
@Composable
fun Modifier.prismPressScale(
    interactionSource: InteractionSource,
    target: Float = 0.985f,
    enabled: Boolean = true,
): Modifier {
    val motion = rememberPrismMotion()
    if (!enabled || !motion.enabled) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) target else 1f,
        animationSpec = motion.spec(if (pressed) PrismSpec.pressDown else PrismSpec.pressUp),
        label = "prism-press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
