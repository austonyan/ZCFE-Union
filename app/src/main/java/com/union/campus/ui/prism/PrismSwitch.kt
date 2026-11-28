package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 52×32 胶囊开关，对齐 material3 `Switch` 的尺寸与状态语义：
 *
 * - 轨道 52×32、滑块 24，四周留 4dp；按下时滑块临时长到 28（M3 的 pressed handle）。
 * - 选中：轨道 primary / 滑块 onPrimary / 图标 onPrimaryContainer；
 *   未选中：轨道 surfaceContainerHighest / 滑块 outline；
 *   禁用：轨道 12%、滑块 38%（onSurface），与 M3 disabled 一致。
 * - 选中时滑块内画一个 16dp 对勾（M3「带图标开关」），只画图形、不参与读屏。
 * - 触控区固定 52×48，视觉轨道仍是 52×32（上下各留 8dp）。
 * - [interactive] = false 时只作指示，由外层 PrismToggleRow 承接整行点击，
 *   同时清空自身语义，避免读屏出现两个指向同一状态的节点。
 *
 * 唯一的刻意偏差：禁用且选中时轨道保留 38% 的 primary，而不是变成中性灰——
 * 否则「总开关关掉」后子开关全部长成未选中的样子，用户看不出哪些本来是开的。
 */
@Composable
fun PrismSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
    thumbIcon: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val showPressed = pressed && enabled
    val pill = prismShape(32f)

    val trackColor by animateColorAsState(
        targetValue = when {
            checked && !enabled -> MaterialTheme.colorScheme.primary.withAlpha(PrismAlpha.Disabled)
            checked -> MaterialTheme.colorScheme.primary
            !enabled -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Track)
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = motion.tween(PrismMotion.Standard),
        label = "switch-track",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            checked && !enabled -> MaterialTheme.colorScheme.onPrimary.withAlpha(0.70f)
            checked -> MaterialTheme.colorScheme.onPrimary
            !enabled -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Disabled)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = motion.tween(PrismMotion.Standard),
        label = "switch-thumb",
    )
    val iconColor = when {
        !enabled && checked -> MaterialTheme.colorScheme.onPrimaryContainer.withAlpha(0.60f)
        checked -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // M3：按下时 handle 24 → 28，右内边距始终 4dp。
    val thumbSize by animateDpAsState(
        targetValue = if (showPressed) 28.dp else 24.dp,
        animationSpec = motion.springSnappy(),
        label = "switch-thumb-size",
    )
    val density = LocalDensity.current
    val thumbOffsetPx = with(density) { (52.dp - thumbSize - 4.dp).roundToPx() }
    val verticalOffsetPx = with(density) { ((32.dp - thumbSize) / 2).roundToPx() }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 48.dp)
            .then(
                if (interactive) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = source,
                        indication = null,
                    ) { on ->
                        haptics.toggle()
                        onCheckedChange(on)
                    }
                } else {
                    // 整行已经有 role=Switch；这里只画状态，不进无障碍树。
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(pill)
                .background(trackColor),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset { IntOffset(thumbOffsetPx, verticalOffsetPx) }
                    .size(thumbSize)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(thumbColor),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbIcon && checked) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}