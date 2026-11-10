package com.union.campus.ui.prism

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
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

    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    fun <T> tween(durationMillis: Int = Standard): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = StandardEasing)

    fun <T> springSmooth(stiffness: Float = 380f): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)

    fun <T> springSnappy(stiffness: Float = 720f): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = stiffness)
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