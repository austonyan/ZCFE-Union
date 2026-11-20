package com.union.campus.ui.prism

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

/**
 * SnapSheet（FlClash prism_ui 复刻）：
 * - 双 detent：9/16 ≈ 56.25% 与 0.9
 * - 临界阻尼弹簧（mass3/stiffness1000 → spring(1f, 333f)）
 * - 把手 32×4 r2，顶部留白 6
 * - 内容上滑先撑满 sheet 再滚动、下滑到顶再收回（NestedScrollConnection）
 * - 进出场共用一条进度：遮罩与面板同时淡入/下落，退出时先冻结高度再下滑
 */
object SnapSheetDefaults {
    const val Half = 0.5625f
    const val Full = 0.9f

    // mass 3 / stiffness 1000：ω = sqrt(k/m) = 18.26 → stiffness = ω² (mass=1) ≈ 333.3
    val SnapSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 333f)
}

@Composable
fun PrismSnapSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    startAt: Float = SnapSheetDefaults.Half,
    paneTitle: String = "选项面板",
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val rootHeight = LocalWindowInfo.current.containerSize.height
    val minPx = rootHeight * SnapSheetDefaults.Half
    val maxPx = rootHeight * SnapSheetDefaults.Full

    val fraction = remember {
        Animatable(startAt.coerceIn(SnapSheetDefaults.Half, SnapSheetDefaults.Full))
    }
    // 0 = 完全收起，1 = 完全展开；遮罩与面板共用同一条进度，进出场严格同步。
    val progress = remember { Animatable(0f) }
    // 退出动画播放期间保持组合，播完再真正移除。
    var mounted by remember { mutableStateOf(visible) }

    LaunchedEffect(visible, motion.enabled) {
        if (visible) {
            if (!mounted) fraction.snapTo(startAt.coerceIn(SnapSheetDefaults.Half, SnapSheetDefaults.Full))
            mounted = true
            if (motion.enabled) progress.animateTo(1f, PrismSpec.sheetIn)
            else progress.snapTo(1f)
        } else if (mounted) {
            // 冻结当前高度：退出过程中不再按 fraction 重新测量，避免吸附与下滑叠加造成的卡顿。
            fraction.stop()
            if (motion.enabled) {
                progress.animateTo(0f, PrismMotion.tween(PrismMotion.Quick, PrismMotion.Accelerate))
            } else {
                progress.snapTo(0f)
            }
            mounted = false
        }
    }

    if (!mounted) return

    suspend fun snapTo(target: Float) {
        val destination = target.coerceIn(SnapSheetDefaults.Half, SnapSheetDefaults.Full)
        if (motion.enabled) fraction.animateTo(destination, SnapSheetDefaults.SnapSpring)
        else fraction.snapTo(destination)
    }

    val scrollConnection = remember(rootHeight, minPx, maxPx, motion.enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0 && fraction.value < SnapSheetDefaults.Full) {
                    val remain = fraction.value * rootHeight - maxPx
                    val consumed = dy.coerceAtLeast(remain)
                    scope.launch { snapTo((fraction.value * rootHeight - consumed) / rootHeight) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy > 0 && fraction.value > SnapSheetDefaults.Half) {
                    val remain = minPx - fraction.value * rootHeight
                    val give = dy.coerceAtMost(-remain)
                    scope.launch { snapTo((fraction.value * rootHeight + give) / rootHeight) }
                    return Offset(0f, give)
                }
                return Offset.Zero
            }
        }
    }

    val topShape = remember(density.density) {
        PrismShape(
            0f,
            corners = floatArrayOf(
                PrismCorner.Sheet * density.density,
                PrismCorner.Sheet * density.density,
                0f, 0f,
            ),
        )
    }
    val handleShape = remember(density.density) { PrismShape(2f * density.density) }
    val sheetHeightDp = with(density) { (fraction.value * rootHeight).toDp() }

    PrismSheetWindow(visible, onDismiss, modifier) {
        val scrimColor = MaterialTheme.colorScheme.scrim
            .withAlpha(if (LocalPrismDark.current) PrismAlpha.ScrimDark else PrismAlpha.ScrimLight)
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .graphicsLayer { alpha = progress.value }
                .background(scrimColor)
                .pointerInput(visible) {
                    detectTapGestures { if (visible) onDismiss() }
                },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .semantics {
                    this.paneTitle = paneTitle
                    dismiss {
                        if (visible) {
                            onDismiss()
                            true
                        } else false
                    }
                }
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            fraction.snapTo(
                                ((fraction.value * rootHeight - delta) / rootHeight)
                                    .coerceIn(SnapSheetDefaults.Half, SnapSheetDefaults.Full),
                            )
                        }
                    },
                    onDragStopped = { velocity ->
                        val v = velocity / rootHeight
                        if (v > 1.2f) {
                            haptics.tap()
                            onDismiss()
                        } else {
                            val target = if (v < -0.05f ||
                                fraction.value > (SnapSheetDefaults.Half + SnapSheetDefaults.Full) / 2
                            ) SnapSheetDefaults.Full else SnapSheetDefaults.Half
                            haptics.select()
                            scope.launch { snapTo(target) }
                        }
                    },
                )
                .clip(topShape)
                .background(surfaceColorOf(SurfaceLevel.Page))
                .nestedScroll(scrollConnection),
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .clip(handleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Grip)),
                )
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

/** The platform window owns modal keyboard/accessibility focus; the content keeps Prism's detents. */
@Composable
private fun PrismSheetWindow(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (visible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = visible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Keep the authored scrim, without a second platform dim layer.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val dark = LocalPrismDark.current
        DisposableEffect(window, dark) {
            window?.let {
                it.setDimAmount(0f)
                // Only the Prism progress owns entry/exit, not a second platform window fade.
                it.setWindowAnimations(0)
                val controller = WindowCompat.getInsetsController(it, it.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            onDispose { }
        }
        Box(modifier.fillMaxSize(), content = content)
    }
}
