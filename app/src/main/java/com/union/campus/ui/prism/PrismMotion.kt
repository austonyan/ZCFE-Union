package com.union.campus.ui.prism

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /**
     * 非组合入口：读取系统「动画时长缩放」与「过渡动画缩放」。
     * 任一为 0 即视为用户关闭动画（开发者选项 / 无障碍减少动效）。
     * 组件内优先使用 [rememberPrismMotion]，以便同一处读取、统一退化。
     */
    fun animationsEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        val animator = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val transition = Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        return animator > 0f && transition > 0f
    }
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

    /** 入场强调：轻微过冲的落位（引导完成勾选等），比 [pressDown] 软。 */
    val entranceBouncy: FiniteAnimationSpec<Float> =
        spring<Float>(dampingRatio = 0.7f, stiffness = 320f)
}

/**
 * 组合内动效配置：系统关闭动画时，所有 spec 统一退化为 [snap]。
 *
 * 方案说明：`PrismMotion.tween/spring*` 是普通函数，读系统设置需要 Context，无法直接感知开关；
 * 把开关写进全局 `MutableState` 又会把可变状态塞进 Theme（且 Theme.kt 不在本次改动范围）。
 * 因此由组件在组合内调用 [rememberPrismMotion] 显式取得配置，再把 spec 交给动画 API——
 * [prismAnimationsEnabled] 通过 ContentObserver 监听设置变化，开关切换后随重组自动生效，
 * 也不引入任何新依赖。
 */
@Immutable
class PrismMotionConfig internal constructor(
    /** 系统是否允许播放动画；为 false 时所有 spec 均为 [snap]。 */
    val enabled: Boolean,
) {
    /** 包装一个已有 spec：关闭动画时直接 snap，否则原样返回。 */
    fun <T> spec(normalSpec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (enabled) normalSpec else snap()

    fun <T> tween(
        durationMillis: Int = PrismMotion.Standard,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> =
        if (enabled) PrismMotion.tween(durationMillis, delayMillis) else snap()

    fun <T> springSmooth(stiffness: Float = 380f): FiniteAnimationSpec<T> =
        if (enabled) PrismMotion.springSmooth(stiffness) else snap()

    fun <T> springSnappy(stiffness: Float = 720f): FiniteAnimationSpec<T> =
        if (enabled) PrismMotion.springSnappy(stiffness) else snap()
}

/** 组件内统一入口：取得系统动画开关对应的 spec 工厂，装饰动效与状态过渡都从这里取。 */
@Composable
fun rememberPrismMotion(): PrismMotionConfig {
    val enabled = prismAnimationsEnabled()
    return remember(enabled) { PrismMotionConfig(enabled) }
}

/**
 * 系统关闭动画时，骨架/呼吸点等装饰动效应停下。
 *
 * 用户可能在应用存活期间打开「移除动画」（无障碍/开发者选项），
 * 因此注册 ContentObserver 让开关即时生效，而不是只读一次。
 */
@Composable
fun prismAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) {
        mutableStateOf(PrismMotion.animationsEnabled(context))
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                enabled = PrismMotion.animationsEnabled(context)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}