package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 52×32 胶囊开关：选中过冲回弹，点按有触感。
 *
 * 触控区固定 52×48，视觉轨道仍是 52×32（上下各留 8dp）。
 * [interactive] = false 时只作指示，由外层 PrismToggleRow 承接整行点击。
 */
@Composable
fun PrismSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
) {
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val pill = prismShape(32f)
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Track)
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = motion.tween(PrismMotion.Standard),
        label = "switch-track",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Disabled)
            checked -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = motion.tween(PrismMotion.Standard),
        label = "switch-thumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = motion.springSnappy(),
        label = "switch-offset",
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 48.dp)
            .then(
                if (interactive) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interactionSource,
                        indication = null,
                    ) { on ->
                        haptics.toggle()
                        onCheckedChange(on)
                    }
                } else {
                    Modifier
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
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(24.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}