package com.union.campus.ui.prism

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 字号角色已收口到 PrismTokens.kt（pageTitle / headline / body / subheadline /
// footnote / caption1 / caption2 / display / metric / metricCompact / metricSmall）。

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
        // 点按既要有 ink，也要有"被按住"的形变；抬起比按下慢，才有重量感。
        val interaction = remember { MutableInteractionSource() }
        Surface(
            modifier = modifier
                .prismPressScale(interaction, target = 0.985f)
                .prismEdge(shape)
                .clip(shape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                ),
            color = fill,
            shape = shape,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.prismEdge(shape), color = fill, shape = shape) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** 卡片头：图标 18 + 标题 headline/onSurface（不再叠 alpha，避免层级被压灰）。 */
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            title,
            style = headlineStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------- 分组列表（iOS inset grouped） ----------------

/**
 * 分组列表：整组由容器统一裁 24 连续圆角，行之间由 Layout 画一条
 * onSurface·α0.14 的 hairline。行不再手写 index/count，条件增删行
 * 也不会破坏首尾圆角。
 *
 * 放在已带页面左右边距的 Column 里；行内容应是 PrismRow 这类整行组件。
 */
@Composable
fun PrismGroupedList(
    modifier: Modifier = Modifier,
    dividerStart: Dp = PrismSpace.RowHorizontal,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val shape = remember(density.density) { PrismShape(PrismCorner.Xl * density.density) }
    val dividerColor = MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.DividerSoft)
    val dividerStartPx = with(density) { dividerStart.toPx() }
    val dividerWidth = with(density) { Dp.Hairline.toPx() }
    // measure 阶段写入，draw 阶段读取；同一次布局内即可画出分隔线。
    var rowHeights = IntArray(0)
    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .prismEdge(shape)
            .clip(shape)
            .background(surfaceColorOf(SurfaceLevel.Card))
            .drawWithContent {
                drawContent()
                var y = 0f
                for (i in rowHeights.indices) {
                    y += rowHeights[i]
                    if (i < rowHeights.lastIndex) {
                        drawLine(
                            color = dividerColor,
                            start = androidx.compose.ui.geometry.Offset(dividerStartPx, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = dividerWidth,
                        )
                    }
                }
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        rowHeights = placeables.map { it.height }.toIntArray()
        val height = rowHeights.sum()
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

/** 分组行：高 ≥56、水平 16、标题 17/副标题 15、ink 按压。 */
@Composable
fun PrismRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = PrismSpace.RowHorizontal, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leading != null) leading()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = bodyStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Disabled),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 分组分隔线（hairline，默认从行标题端缩进）。 */
@Composable
fun GroupDivider(
    modifier: Modifier = Modifier,
    startPadding: Dp = PrismSpace.RowHorizontal,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.DividerSoft)),
    )
}

/**
 * 分区：分组头 T24 B8 + 内容区间 12。页面只写这一个节奏。
 * 放在已带页面左右边距（16）的 Column / LazyColumn item 里。
 */
@Composable
fun PrismSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title, action = action)
        Column(
            verticalArrangement = Arrangement.spacedBy(PrismSpace.Item),
            content = content,
        )
    }
}

/** 分组头：headline/onSurfaceVariant，T24 B8；action 在右侧（如“查看全部”）。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(PrismSectionHeaderPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = headlineStyle().copy(fontWeight = androidx.compose.ui.text.font.FontWeight(500)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

/** 分组 footer：footnote，T8。 */
@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = footnoteStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp),
    )
}

// ---------------- 统一卡片与提示 ----------------

/**
 * 指标卡：图标 + 标签 + 等宽大数字 + 脚注。
 * 统一替代 LifeMetricCard、电费顶部卡与成绩 GPA 卡。
 */
@Composable
fun PrismMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = surfaceColorOf(LocalSurfaceLevel.current.next()),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.primary,
    compact: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    PrismCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = containerColor,
        contentPadding = PaddingValues(if (compact) 14.dp else 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(if (compact) 28.dp else 40.dp)
                        // 内圆角必须小于外圆角（卡 16 / padding 20），否则视觉上"嵌死"。
                        .clip(prismShape(if (compact) PrismCorner.Sm else 12f))
                        .background(accent.withAlpha(PrismAlpha.ContainerTint)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(if (compact) 17.dp else PrismIconSize.Badge),
                    )
                }
                Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
            }
            Text(
                label,
                style = if (compact) footnoteStyle() else subheadlineStyle(),
                color = contentColor.withAlpha(PrismAlpha.Secondary),
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        PrismAnimatedText(
            text = value,
            style = if (compact) metricCompactStyle() else metricStyle(),
            color = contentColor,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = footnoteStyle(),
                color = contentColor.withAlpha(PrismAlpha.Secondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 入口卡：整行（或双列 compact）可点，统一今天页双列卡、教务常用功能与生活页全宽入口。
 */
@Composable
fun PrismEntryCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badge: String? = null,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    containerColor: Color = surfaceColorOf(LocalSurfaceLevel.current.next()),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    PrismCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = containerColor,
        contentPadding = PaddingValues(if (compact) 14.dp else 16.dp),
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(prismShape(PrismCorner.Sm))
                            .background(accent.withAlpha(PrismAlpha.ContainerTint)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            icon, contentDescription = null, tint = accent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (badge != null) MetaChip(badge, color = accent.withAlpha(PrismAlpha.ContainerTint))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = bodyStyle(), color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = footnoteStyle(),
                    color = contentColor.withAlpha(PrismAlpha.Secondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(prismShape(12f))
                            .background(accent.withAlpha(PrismAlpha.ContainerTint)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            icon, contentDescription = null, tint = accent,
                            modifier = Modifier.size(PrismIconSize.Badge),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = bodyStyle(),
                            fontWeight = androidx.compose.ui.text.font.FontWeight(600),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (badge != null) {
                            Spacer(Modifier.width(8.dp))
                            MetaChip(badge, color = accent.withAlpha(PrismAlpha.ContainerTint))
                        }
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = footnoteStyle(),
                            color = contentColor.withAlpha(PrismAlpha.Secondary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onClick != null) {
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = contentColor.withAlpha(PrismAlpha.Hint),
                        modifier = Modifier.size(PrismIconSize.Chevron),
                    )
                }
            }
        }
    }
}

enum class PrismNoticeTone { Info, Success, Warning, Error }

/** 提示条：统一上课提醒、低余额、洗浴排队、电费警告四种场景。 */
@Composable
fun PrismNoticeBanner(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    tone: PrismNoticeTone = PrismNoticeTone.Info,
    icon: ImageVector? = Icons.Rounded.Notifications,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val container = when (tone) {
        PrismNoticeTone.Info -> PrismStatus.infoContainer()
        PrismNoticeTone.Success -> PrismStatus.successContainer()
        PrismNoticeTone.Warning -> PrismStatus.warningContainer()
        PrismNoticeTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        PrismNoticeTone.Info -> PrismStatus.onInfoContainer()
        PrismNoticeTone.Success -> PrismStatus.onSuccessContainer()
        PrismNoticeTone.Warning -> PrismStatus.onWarningContainer()
        PrismNoticeTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    PrismCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = container,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconBadge(icon, container = content.withAlpha(PrismAlpha.ContainerTint), tint = content)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = bodyStyle(),
                    fontWeight = androidx.compose.ui.text.font.FontWeight(600),
                    color = content,
                )
                if (!message.isNullOrBlank()) {
                    Text(
                        message,
                        style = footnoteStyle(),
                        color = content.withAlpha(PrismAlpha.Secondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                PrismTextButton(onClick = onAction) {
                    Text(actionLabel, color = content)
                }
            }
        }
    }
}

/** 设置开关行：整行可点，开关只作指示，触控目标 ≥48dp。 */
@Composable
fun PrismToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberPrismHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = {
                    haptics.toggle()
                    onCheckedChange(it)
                },
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = PrismSpace.RowHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Secondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PrismSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            interactive = false,
        )
    }
}

/** 工具栏图标按钮：44 视觉 / 48 触控，contentDescription 必填。 */
@Composable
fun PrismIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
) {
    val haptics = rememberPrismHaptics()
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(enabled = enabled) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.withAlpha(PrismAlpha.Disabled),
            modifier = Modifier.size(PrismIconSize.Toolbar),
        )
    }
}

// ---------------- 信息展示 ----------------

/** 带图标容器的列表行 leading（40dp 圆角容器 + 24dp 图标）。 */
@Composable
fun IconBadge(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.primary.withAlpha(PrismAlpha.ContainerTint),
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(prismShape(12f))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon, contentDescription = null,
            tint = tint,
            modifier = Modifier.size(PrismIconSize.Badge),
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
            // 空态不是错误页：给一个品牌色的圆形底座，比一个 55% 灰的大图标更"有人管"。
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary.withAlpha(0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.withAlpha(0.72f),
                    modifier = Modifier.size(32.dp),
                )
            }
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
            style = caption2Style(),
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
        !enabled && filled -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Track)
        !filled -> Color.Transparent
        else -> colors.first
    }
    val interaction = remember { MutableInteractionSource() }
    WithPressLook(PressLook.Tint) {
        Surface(
            modifier = modifier
                .prismPressScale(interaction, target = 0.96f, enabled = enabled && !loading)
                .clip(pill)
                .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp)
                .minimumInteractiveComponentSize()
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled && !loading,
                    role = Role.Button,
                ) {
                    haptics.tap()
                    onClick()
                },
            shape = pill,
            color = resolvedContainer,
            border = if (!filled) androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.takeIf { enabled }
                    ?: MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Track),
            ) else null,
        ) {
            // 加载时保留文字宽度（只把 alpha 压到 0），按钮不会因为换成转圈而抽缩。
            Box {
                Row(
                    Modifier
                        .padding(contentPadding)
                        .graphicsLayer { alpha = if (loading) 0f else 1f },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProvideContentColor(colors.second.takeIf { enabled }
                        ?: MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Disabled)) {
                        CompositionLocalProvider(LocalTextStyle provides buttonLabelStyle()) {
                            content()
                        }
                    }
                }
                if (loading) {
                    PrismLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        size = 18.dp,
                        strokeWidth = 2.dp,
                        color = colors.second,
                    )
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
            modifier = modifier.defaultMinSize(minHeight = 40.dp).minimumInteractiveComponentSize(),
            color = Color.Transparent,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProvideContentColor(MaterialTheme.colorScheme.primary.takeIf { enabled }
                    ?: MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Disabled)) {
                    CompositionLocalProvider(LocalTextStyle provides buttonLabelStyle()) { content() }
                }
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
                        val actionPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        if (dismissText != null) {
                            Box(Modifier.weight(1f)) {
                                PrismButton(
                                    filled = false,
                                    onClick = { close(onDismiss) },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = actionPadding,
                                ) {
                                    Text(dismissText, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
                                contentPadding = actionPadding,
                                containerColor = if (destructive) MaterialTheme.colorScheme.errorContainer else null,
                                contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else null,
                            ) {
                                Text(confirmText, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
