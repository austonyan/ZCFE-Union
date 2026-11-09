package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.navigation.Screen

/**
 * Prism 底部 dock：选中胶囊滑动、图标/文字颜色过渡、触感反馈。
 * 比 M3 NavigationBar 更轻，和分组列表/卡片同一套圆角与色阶。
 */
@Composable
fun PrismDock(
    tabs: List<Screen>,
    current: Screen?,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    lifeActive: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val density = LocalDensity.current
    val haptics = rememberPrismHaptics()
    val pill = remember(density.density) { PrismShape(64f * density.density) }
    val selectedIndex = tabs.indexOf(current).coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = pill,
        color = surfaceColorOf(SurfaceLevel.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val itemWidth = maxWidth / tabs.size.toFloat()
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = PrismMotion.springSnappy(560f),
                label = "dock-indicator",
            )
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .fillMaxHeight()
                    .fillMaxWidth(1f / tabs.size)
                    .clip(pill)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val selected = tab == current
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = PrismMotion.tween(PrismMotion.Quick),
                        label = "dock-color",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (!selected) {
                                    haptics.select()
                                    onSelect(tab)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box {
                                Icon(
                                    imageVector = tab.tabIcon ?: Icons.Rounded.Home,
                                    contentDescription = tab.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(21.dp),
                                )
                                if (lifeActive && tab == Screen.Life) {
                                    PrismStatusDot(
                                        color = PrismStatus.success(),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp),
                                        size = 6,
                                    )
                                }
                            }
                            Text(
                                text = tab.title,
                                color = contentColor,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                style = caption1Style().copy(fontSize = 10.sp, lineHeight = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}