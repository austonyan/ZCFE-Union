package com.union.campus.ui.prism

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * SnapSheet（FlClash prism_ui 复刻）：
 * - 双 detent：9/16 ≈ 56.25% 与 0.9
 * - 临界阻尼弹簧（mass3/stiffness1000 → spring(1f, 333f)）
 * - 把手 28×4 r2，顶部留白 6
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
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberPrismHaptics()
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

    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            fraction.snapTo(startAt.coerceIn(SnapSheetDefaults.Half, SnapSheetDefaults.Full))
            progress.snapTo(0f)
            progress.animateTo(1f, PrismMotion.tween(PrismMotion.Emphasized, PrismMotion.Decelerate))
        } else if (mounted) {
            // 冻结当前高度：退出过程中不再按 fraction 重新测量，避免吸附与下滑叠加造成的卡顿。
            fraction.stop()
            progress.animateTo(0f, PrismMotion.tween(PrismMotion.Quick, PrismMotion.Accelerate))
            mounted = false
        }
    }

    if (!mounted) return

    BackHandler(enabled = visible, onBack = onDismiss)

    suspend fun snapTo(target: Float) {
        fraction.animateTo(target.coerceIn(SnapSheetDefaults.Half, SnapSheetDefaults.Full), SnapSheetDefaults.SnapSpring)
    }

    val scrollConnection = remember(rootHeight, minPx, maxPx) {
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

    Box(modifier.fillMaxSize()) {
        val scrimColor = MaterialTheme.colorScheme.scrim
            .withAlpha(if (LocalPrismDark.current) PrismAlpha.ScrimDark else PrismAlpha.ScrimLight)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(scrimColor)
                .clickable(enabled = mounted) { if (visible) onDismiss() },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
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
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp)
                        .width(28.dp)
                        .height(4.dp)
                        .clip(handleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Grip)),
                )
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}