package com.union.campus.ui.prism

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    val haptics = rememberPrismHaptics()
    val pill = prismShape(64f)
    val selectedIndex = tabs.indexOf(current).coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            // 双层阴影：大范围柔光 + 贴边细影（dark 下由系统自动加深）。
            .shadow(
                elevation = if (LocalPrismDark.current) 16.dp else 24.dp,
                shape = pill,
                clip = false,
                ambientColor = Color.Black.withAlpha(PrismAlpha.Border),
                spotColor = Color.Black.withAlpha(PrismAlpha.Border),
            )
            .shadow(
                elevation = 3.dp,
                shape = pill,
                clip = false,
                ambientColor = Color.Black.withAlpha(0.04f),
                spotColor = Color.Black.withAlpha(0.04f),
            ),
        shape = pill,
        color = surfaceColorOf(SurfaceLevel.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Border)),
        shadowElevation = 0.dp,
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
                    .background(
                        // 选中态降为 tonal 药丸，避免整格实心主色抢视觉。
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (LocalPrismDark.current) 0.18f else 0.12f,
                        ),
                    ),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                tabs.forEach { tab ->
                    val selected = tab == current
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.94f else 1f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
                        label = "dock-press-scale",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = PrismMotion.tween(PrismMotion.Quick),
                        label = "dock-color",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = when {
                            !prismAnimationsEnabled() -> 1f
                            selected -> 1.12f
                            else -> 1f
                        },
                        animationSpec = PrismMotion.springSnappy(),
                        label = "dock-icon-scale",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
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
                                    val transition = rememberInfiniteTransition(label = "dock-live-dot")
                                    val pulse by transition.animateFloat(
                                        initialValue = 0.45f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(900, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse,
                                        ),
                                        label = "dock-live-alpha",
                                    )
                                    PrismStatusDot(
                                        color = PrismStatus.success().withAlpha(
                                            if (prismAnimationsEnabled()) pulse else 1f,
                                        ),
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
                                style = dockLabelStyle(),
                            )
                        }
                    }
                }
            }
        }
    }
}