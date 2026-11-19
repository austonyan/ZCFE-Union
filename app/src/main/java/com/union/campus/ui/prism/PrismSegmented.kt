package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Connected single-select controls: 24dp outside / 6dp inside, with a 2dp seam.
 * Selection changes shape as well as tone; every segment retains a 48dp touch target.
 * Minimum rather than fixed height allows the label to grow with the system font scale.
 */
@Composable
fun PrismSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val index = selectedIndex.coerceIn(0, options.lastIndex)

    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { optionIndex, label ->
            val selected = optionIndex == index
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val corner by animateFloatAsState(
                targetValue = when {
                    pressed && enabled -> PrismShapeRole.PressedControl
                    selected -> PrismShapeRole.SelectedControl
                    else -> PrismShapeRole.GroupInner
                },
                animationSpec = motion.springSnappy(),
                label = "segment-shape-$optionIndex",
            )
            val outer by animateFloatAsState(
                targetValue = when {
                    pressed && enabled -> PrismShapeRole.PressedControl
                    selected -> PrismShapeRole.SelectedControl
                    else -> PrismShapeRole.GroupOuter
                },
                animationSpec = motion.springSnappy(),
                label = "segment-outer-$optionIndex",
            )
            val shape = RoundedCornerShape(
                topStart = (if (optionIndex == 0) outer else corner).dp,
                bottomStart = (if (optionIndex == 0) outer else corner).dp,
                topEnd = (if (optionIndex == options.lastIndex) outer else corner).dp,
                bottomEnd = (if (optionIndex == options.lastIndex) outer else corner).dp,
            )
            val container by animateColorAsState(
                targetValue = if (selected && enabled) MaterialTheme.colorScheme.secondaryContainer
                    else surfaceColorOf(SurfaceLevel.Inset),
                animationSpec = motion.tween(PrismMotion.Fast),
                label = "segment-container-$optionIndex",
            )
            val foreground by animateColorAsState(
                targetValue = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Disabled)
                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = motion.tween(PrismMotion.Fast),
                label = "segment-label-$optionIndex",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .prismPressScale(interaction, target = 0.98f, enabled = enabled)
                    .clip(shape)
                    .background(container)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = {
                            if (!selected) {
                                haptics.select()
                                onSelect(optionIndex)
                            }
                        },
                    )
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = caption1Style(),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = foreground,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
