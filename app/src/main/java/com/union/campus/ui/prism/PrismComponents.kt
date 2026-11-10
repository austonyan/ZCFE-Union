package com.union.campus.ui.prism

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------- 字号角色（iOS Dynamic Type 档 × Roboto Flex） ----------------

/** 页面大标题 24/30 w600 */
@Composable
fun pageTitleStyle() = MaterialTheme.typography.headlineSmall.copy(
    fontSize = 24.sp, lineHeight = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600),
)

/** 分组头/卡片头 17 w600 */
@Composable
fun headlineStyle() = MaterialTheme.typography.titleMedium.copy(
    fontSize = 17.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600),
)

/** 列表标题 17 w400 */
@Composable
fun bodyStyle() = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp)

/** 列表副标题/正文 15 */
@Composable
fun subheadlineStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** footer 13 */
@Composable
fun footnoteStyle() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** 导航/chip 12 w600 */
@Composable
fun caption1Style() = MaterialTheme.typography.labelMedium.copy(
    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600),
)

/** 仪表盘大数字 40 */
@Composable
fun displayStyle() = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 44.sp)

// ---------------- 卡片 ----------------

/**
 * Prism 卡片：raised surface 填充、连续圆角 16、ink 按压（点击时）、无阴影。
 */
@Composable
fun PrismCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = surfaceColorOf(LocalSurfaceLevel.current.next()),
    corner: Float = PrismCorner.Md,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val shape = remember(corner, density.density) {
        PrismShape(corner * density.density)
    }
    val fill by animateColorAsState(
        targetValue = containerColor,
        animationSpec = PrismMotion.tween(PrismMotion.Standard),
        label = "card-container",
    )
    if (onClick != null) {
        Surface(
            modifier = modifier
                .clip(shape)
                .clickable(onClick = onClick),
            color = fill,
            shape = shape,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, color = fill, shape = shape) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** 卡片头：图标 20 + 标题 headline/onSurfaceVariant·α0.8。 */
@Composable
fun CardHeader(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            title,
            style = headlineStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

// ---------------- 分组列表（iOS inset grouped） ----------------

/**
 * 分组列表：行包在连续面里（首 top24 / 尾 bottom24 / 中 0），
 * 分隔线 hairline onSurface·α0.14 从标题端缩进 14。
 */
@Composable
fun PrismGroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val shape = remember(density.density) { PrismShape(PrismCorner.Xl * density.density) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColorOf(SurfaceLevel.Card)),
        content = content,
    )
}

@Composable
fun groupRowShape(index: Int, count: Int): PrismShape {
    val density = LocalDensity.current
    val d = density.density
    return remember(index, count, d) {
        when {
            count <= 1 -> PrismShape(PrismCorner.Xl * d)
            index == 0 -> PrismShape(0f, corners = floatArrayOf(PrismCorner.Xl * d, PrismCorner.Xl * d, 0f, 0f))
            index == count - 1 -> PrismShape(0f, corners = floatArrayOf(0f, 0f, PrismCorner.Xl * d, PrismCorner.Xl * d))
            else -> PrismShape(0f)
        }
    }
}

/** 分组行：高 ≥56、水平 16、标题 17/副标题 15、ink 按压（index/count 决定圆角）。 */
@Composable
fun PrismRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    index: Int = 0,
    count: Int = 1,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(modifier = modifier.fillMaxWidth()) {
            if (index > 0 && count > 1) GroupDivider(
                startPadding = if (leading != null) 72.dp else 16.dp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(groupRowShape(index, count))
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (leading != null) leading()
                Column(Modifier.weight(1f)) {
                    Text(title, style = bodyStyle(), color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (trailing != null) {
                    trailing()
                } else if (onClick != null) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 分组分隔线（hairline，默认从行标题端缩进）。 */
@Composable
fun GroupDivider(
    modifier: Modifier = Modifier,
    startPadding: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}

/** 分组头：headline/onSurfaceVariant，T24 B8 L16 R16。 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = headlineStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** 分组 footer：footnote，T8。 */
@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = footnoteStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

// ---------------- 信息展示 ----------------

/** 带图标容器的列表行 leading（40dp primaryContainer 圆 + 24dp 图标）。 */
@Composable
fun IconBadge(icon: ImageVector, container: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        modifier = Modifier.size(40.dp).clip(PrismShape(PrismCorner.Md)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * 空状态：插图 + 文案 stagger 入场（60ms 步进 + 12dp 上浮）。
 */
@Composable
fun NullStatus(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StaggerItem(shown, 0) {
            androidx.compose.material3.Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp),
            )
        }
        StaggerItem(shown, 1) {
            Text(title, style = headlineStyle(), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        if (subtitle.isNotBlank()) {
            StaggerItem(shown, 2) {
                Text(
                    subtitle, style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StaggerItem(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    PrismReveal(visible = shown, index = index) { content() }
}

/** 小状态胶囊（MetaChip 风格：raised 底、sm8、caption2）。 */
@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val density = LocalDensity.current
    val shape = remember { PrismShape(PrismCorner.Sm * density.density, 0.4f) }
    Surface(
        modifier = modifier,
        color = color.takeIf { it != Color.Unspecified }
            ?: surfaceColorOf(SurfaceLevel.Inset),
        shape = shape,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ---------------- 按钮（胶囊 + tint 按压，FlClash common_button） ----------------

/**
 * Prism 按钮：胶囊形、minSize 64×40、tint 按压。
 * @param filled true = FilledButton（primary 底）；false = OutlinedButton
 */
@Composable
fun PrismButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptics = rememberPrismHaptics()
    val pill = remember(density.density) { PrismShape(Float.MAX_VALUE) }
    val base = if (filled) {
        Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    } else {
        Pair(Color.Transparent, MaterialTheme.colorScheme.primary)
    }
    val colors = (containerColor ?: base.first) to (contentColor ?: base.second)
    val resolvedContainer = when {
        !enabled && filled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        !filled -> Color.Transparent
        else -> colors.first
    }
    WithPressLook(PressLook.Tint) {
        Surface(
            onClick = {
                haptics.tap()
                onClick()
            },
            enabled = enabled && !loading,
            modifier = modifier
                .clip(pill)
                .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp),
            shape = pill,
            color = resolvedContainer,
            border = if (!filled) androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.takeIf { enabled }
                    ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ) else null,
        ) {
            Row(
                Modifier
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProvideContentColor(colors.second.takeIf { enabled }
                    ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) {
                    if (loading) {
                        PrismLoadingIndicator(size = 18.dp, strokeWidth = 2.dp, color = colors.second)
                    } else {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun ProvideContentColor(color: Color, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides color,
        content = content,
    )
}

/** 文字按钮。 */
@Composable
fun PrismTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberPrismHaptics()
    WithPressLook(PressLook.Tint) {
        Surface(
            onClick = {
                haptics.tap()
                onClick()
            },
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = 40.dp),
            color = Color.Transparent,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProvideContentColor(MaterialTheme.colorScheme.primary.takeIf { enabled }
                    ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) { content() }
            }
        }
    }
}

// ---------------- 对话框（iOS Alert：34dp 连续圆角、348dp、最后一个动作填充） ----------------

@Composable
fun PrismAlertDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = "取消",
    destructive: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open = true }

    // 先播放收回动画，再卸载对话框窗口；按钮/返回键都走这里，退出不再是硬切。
    fun close(action: () -> Unit) {
        if (!open) return
        open = false
        scope.launch {
            delay((PrismMotion.Quick + 20).toLong())
            action()
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = { close(onDismiss) }) {
        val density = LocalDensity.current
        val shape = remember(density.density) { PrismShape(PrismCorner.Alert * density.density) }
        AnimatedVisibility(
            visible = open,
            modifier = modifier.fillMaxWidth(0.92f).widthIn(max = 348.dp),
            enter = fadeIn(PrismMotion.tween(PrismMotion.Fast)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = PrismMotion.springSnappy(),
                ),
            exit = fadeOut(PrismMotion.tween(PrismMotion.Quick, PrismMotion.Accelerate)) +
                scaleOut(
                    targetScale = 0.94f,
                    animationSpec = PrismMotion.tween(PrismMotion.Quick, PrismMotion.Accelerate),
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = surfaceColorOf(SurfaceLevel.Card),
            ) {
                Column(Modifier.padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                    Text(
                        title,
                        style = headlineStyle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        body,
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dismissText != null) {
                            Box(Modifier.weight(1f)) {
                                PrismButton(filled = false, onClick = { close(onDismiss) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(dismissText)
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            PrismButton(
                                onClick = {
                                    close {
                                        onDismiss()
                                        onConfirm()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = if (destructive) MaterialTheme.colorScheme.error else null,
                                contentColor = if (destructive) MaterialTheme.colorScheme.onError else null,
                            ) {
                                Text(confirmText)
                            }
                        }
                    }
                }
            }
        }
    }
}
