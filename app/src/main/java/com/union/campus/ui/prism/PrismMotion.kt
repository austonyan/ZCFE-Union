package com.union.campus.ui.prism

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 统一动效 token：时长、缓动与弹簧。 */
object PrismMotion {
    const val Instant = 90
    const val Fast = 140
    const val Quick = 180
    const val Standard = 220
    const val Emphasized = 300
    const val Slow = 500
    const val Shimmer = 1500

    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    fun <T> tween(
        durationMillis: Int = Standard,
        delayMillis: Int = 0,
    ): TweenSpec<T> =
        tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = StandardEasing)

    /** 指定缓动的补间：进场用 Decelerate，退场用 Accelerate。 */
    fun <T> tween(
        durationMillis: Int,
        easing: Easing,
    ): TweenSpec<T> =
        androidx.compose.animation.core.tween(durationMillis = durationMillis, easing = easing)

    fun <T> springSmooth(stiffness: Float = 380f): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)

    fun <T> springSnappy(stiffness: Float = 720f): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = stiffness)
}

/**
 * 语义化动效规格：页面/标签/面板/按压统一从这里取，
 * 避免各处手写时长与缓动导致节奏漂移。
 */
object PrismSpec {
    /** 标签页：fade-through + 0.98→1 微缩放，退场略微放大避免画面塌陷。 */
    val tabEnter: EnterTransition =
        fadeIn(PrismMotion.tween(210, PrismMotion.Decelerate)) +
            scaleIn(
                initialScale = 0.98f,
                animationSpec = PrismMotion.tween(210, PrismMotion.Decelerate),
            )
    val tabExit: ExitTransition =
        fadeOut(PrismMotion.tween(90, PrismMotion.Accelerate)) +
            scaleOut(
                targetScale = 1.01f,
                animationSpec = PrismMotion.tween(90, PrismMotion.Accelerate),
            )

    /** 详情页 push：约 24dp 位移，不再满屏横滑。 */
    val pushEnter: EnterTransition =
        slideInHorizontally(animationSpec = spring(1f, 420f)) { it / 12 } +
            fadeIn(PrismMotion.tween(240))
    val pushExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = PrismMotion.tween(240, PrismMotion.Decelerate),
        ) { -it / 24 } + fadeOut(PrismMotion.tween(240))

    /** pop：push 的对称方向。 */
    val popEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = PrismMotion.tween(240, PrismMotion.Decelerate),
        ) { -it / 24 } + fadeIn(PrismMotion.tween(240))
    val popExit: ExitTransition =
        slideOutHorizontally(animationSpec = spring(1f, 420f)) { it / 12 } +
            fadeOut(PrismMotion.tween(240))

    /** 面板：临界阻尼，避免过冲露出底部缝隙。 */
    val sheetIn: FiniteAnimationSpec<Float> = spring<Float>(dampingRatio = 1f, stiffness = 420f)

    /** 按压：按下快、回弹略慢。 */
    val pressDown: FiniteAnimationSpec<Float> = spring<Float>(dampingRatio = 0.7f, stiffness = 900f)
    val pressUp: FiniteAnimationSpec<Float> = spring(0.9f, 500f)
}

/** 系统关闭动画时，骨架/呼吸点等装饰动效应停下。 */
@Composable
fun prismAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val animator = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val transition = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        animator > 0f && transition > 0f
    }
}