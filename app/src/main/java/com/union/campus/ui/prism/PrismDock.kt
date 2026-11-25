package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.union.campus.navigation.Screen

/**
 * Prism 底部 dock：选中胶囊滑动、图标/文字颜色过渡、触感反馈。
 * 与分组列表/卡片共享调和色阶；中等及以上窗口由原生 NavigationRail 接替。
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
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val pill = prismShape(64f)
    val selectedIndex = tabs.indexOf(current).coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = pill,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val itemWidth = maxWidth / tabs.size.toFloat()
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = motion.springSnappy(560f),
                label = "dock-indicator",
            )
            Box(
                Modifier
                    // lambda 重载：偏移量在布局阶段读取，动画每帧只触发布局而非重组。
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .fillMaxHeight()
                    .fillMaxWidth(1f / tabs.size)
                    .clip(pill)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                    ),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                tabs.forEach { tab ->
                    val selected = tab == current
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val focused by interactionSource.collectIsFocusedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed && motion.enabled) 0.94f else 1f,
                        animationSpec = motion.spec(PrismSpec.pressDown),
                        label = "dock-press-scale",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = motion.tween(PrismMotion.Quick),
                        label = "dock-color",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected && motion.enabled) 1.12f else 1f,
                        animationSpec = motion.springSnappy(),
                        label = "dock-icon-scale",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pill)
                            .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, pill) else Modifier)
                            .semantics {
                                if (lifeActive && tab == Screen.Life) stateDescription = "洗浴进行中"
                            }
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) {
                                        haptics.select()
                                        onSelect(tab)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            },
                        ) {
                            Box {
                                Icon(
                                    imageVector = if (selected) {
                                        tab.tabIcon ?: Icons.Rounded.Home
                                    } else {
                                        tab.tabIconOutlined ?: tab.tabIcon ?: Icons.Rounded.Home
                                    },
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(PrismIconSize.Dock).scale(iconScale),
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
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact 保留五项 Dock；Medium+ 改为原生导航轨，共享同一返回栈。 */
@Composable
fun PrismNavigationScaffold(
    tabs: List<Screen>,
    current: Screen?,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    lifeActive: Boolean = false,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        val showNavigation = current in tabs
        Row(Modifier.fillMaxSize()) {
            if (useRail && showNavigation) {
                PrismNavigationRail(tabs, current, onSelect, lifeActive = lifeActive)
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = surfaceColorOf(SurfaceLevel.Page),
                snackbarHost = snackbarHost,
                bottomBar = {
                    if (!useRail && showNavigation) {
                        PrismDock(
                            tabs = tabs,
                            current = current,
                            onSelect = onSelect,
                            lifeActive = lifeActive,
                            modifier = Modifier
                                .testTag("compact-navigation")
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                },
                content = content,
            )
        }
    }
}

@Composable
fun PrismNavigationRail(
    tabs: List<Screen>,
    current: Screen?,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    lifeActive: Boolean = false,
) {
    val haptics = rememberPrismHaptics()
    NavigationRail(
        modifier = modifier
            .testTag("medium-navigation")
            .width(96.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
    ) {
        tabs.forEach { tab ->
            val selected = tab == current
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        haptics.select()
                        onSelect(tab)
                    }
                },
                icon = {
                    Box {
                        Icon(
                            if (selected) tab.tabIcon ?: Icons.Rounded.Home
                            else tab.tabIconOutlined ?: tab.tabIcon ?: Icons.Rounded.Home,
                            contentDescription = null,
                        )
                        if (lifeActive && tab == Screen.Life) {
                            PrismStatusDot(
                                color = PrismStatus.success(),
                                modifier = Modifier.align(Alignment.TopEnd),
                                size = 6,
                            )
                        }
                    }
                },
                label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.semantics {
                    if (lifeActive && tab == Screen.Life) stateDescription = "洗浴进行中"
                },
            )
        }
    }
}
