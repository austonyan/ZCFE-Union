package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 滑动胶囊式分段控件：用于平台选择、用量/费用切换。 */
@Composable
fun PrismSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val density = LocalDensity.current
    val haptics = rememberPrismHaptics()
    val pill = remember(density.density) { PrismShape(40f * density.density) }
    val track = surfaceColorOf(SurfaceLevel.Inset)
    val index = selectedIndex.coerceIn(0, options.lastIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(pill)
            .background(track),
    ) {
        val itemWidth = maxWidth / options.size.toFloat()
        val offset by animateDpAsState(
            targetValue = itemWidth * index,
            animationSpec = PrismMotion.springSnappy(),
            label = "segmented-offset",
        )
        Box(
            Modifier
                .offset(x = offset)
                .fillMaxHeight()
                .fillMaxWidth(1f / options.size)
                .padding(3.dp)
                .clip(pill)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                ),
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { optionIndex, label ->
                    val textColor by animateColorAsState(
                        targetValue = when {
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            optionIndex == index -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = PrismMotion.tween(PrismMotion.Fast),
                        label = "segmented-label-$optionIndex",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = enabled) {
                                if (optionIndex != index) {
                                    haptics.select()
                                    onSelect(optionIndex)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = caption1Style(),
                            textAlign = TextAlign.Center,
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}