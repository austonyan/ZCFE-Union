package com.union.campus.ui.prism

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一的胶囊筛选芯片：未选中为 Inset 底，选中为主色底；
 * 颜色走 Fast 过渡，点按有触感，和 dock / segmented 使用同一套 Spring。
 */
@Composable
fun PrismChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val pill = remember(density.density) { PrismShape(Float.MAX_VALUE) }
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> surfaceColorOf(SurfaceLevel.Inset).withAlpha(PrismAlpha.Hint)
            selected -> MaterialTheme.colorScheme.primary
            else -> surfaceColorOf(SurfaceLevel.Inset)
        },
        animationSpec = motion.tween(PrismMotion.Fast),
        label = "chip-container",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Disabled)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.tween(PrismMotion.Fast),
        label = "chip-content",
    )
    WithPressLook(PressLook.Tint) {
        Surface(
            modifier = modifier
                .minimumInteractiveComponentSize()
                // 单选筛选语义：TalkBack 读作「单选按钮，已选中/未选中」。
                // 调用方在外层 Row 上补 Modifier.selectableGroup() 即可获得分组语义。
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = {
                        // 保持原行为：已选中的 chip 再次点击不重复触发。
                        if (enabled && !selected) {
                            haptics.select()
                            onClick()
                        }
                    },
                ),
            shape = pill,
            color = container,
        ) {
            Text(
                text = label,
                style = caption1Style(),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Prism 滑杆：主色轨道 + 等宽数值，松手时给一次轻触感。 */
@Composable
fun PrismSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val haptics = rememberPrismHaptics()
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = { haptics.tap() },
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            activeTickColor = MaterialTheme.colorScheme.onPrimary.withAlpha(PrismAlpha.Grip),
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            inactiveTickColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** 轻量加载指示：所有页面用同一线宽与主色，替代零散的 M3 进度圈。 */
@Composable
fun PrismLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
    )
}

@Composable
fun PrismLoadingBlock(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrismLoadingIndicator()
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 统一错误态：图标 + 说明 + 次要重试按钮，替代各页面自行拼的文字块。 */
@Composable
fun PrismErrorState(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    retryText: String = "重试",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Hint),
            modifier = Modifier.size(38.dp),
        )
        Text(
            text = message,
            style = subheadlineStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            PrismButton(filled = false, onClick = onRetry) { Text(retryText) }
        }
    }
}

/** 内容入场：60ms 步进 + 轻微上浮；系统关闭动画时直接展示。 */
@Composable
fun PrismReveal(
    visible: Boolean,
    index: Int = 0,
    content: @Composable () -> Unit,
) {
    if (!prismAnimationsEnabled()) {
        content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(PrismMotion.tween(PrismMotion.Standard, delayMillis = 50 * index)) +
            slideInVertically(PrismMotion.tween(PrismMotion.Standard, delayMillis = 50 * index)) { it / 3 },
        exit = fadeOut(PrismMotion.tween(PrismMotion.Fast)),
    ) {
        content()
    }
}

/** 带选中勾的顶部下滑选择面板，用于锁定延迟、免打扰等单值设置。 */
data class PrismOption(
    val key: String,
    val label: String,
    val subtitle: String? = null,
)

@Composable
fun PrismOptionSheet(
    visible: Boolean,
    title: String,
    options: List<PrismOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val haptics = rememberPrismHaptics()
    PrismSnapSheet(visible = visible, onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = pageTitleStyle())
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrismGroupedList {
                options.forEach { option ->
                    PrismRow(
                        title = option.label,
                        subtitle = option.subtitle,
                        onClick = {
                            haptics.select()
                            onSelect(option.key)
                            onDismiss()
                        },
                        trailing = if (option.key == selectedKey) {
                            {
                                PrismCheckMark(checked = true)
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            Text(
                "选择后立即保存",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Prism 下拉字段：外观与 PrismTextField 一致，点整块打开选项。 */
@Composable
fun PrismDropdownField(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        PrismTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            label = label,
            leadingIcon = leadingIcon,
            supportingText = supportingText,
            enabled = enabled,
            trailingIcon = {
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            },
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    enabled = enabled && options.isNotEmpty(),
                    role = Role.DropdownList,
                    onClickLabel = "选择$label",
                ) { expanded = true },
        )
        DropdownMenu(
            expanded = expanded && options.isNotEmpty() && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                )
            }
        }
    }
}

/** 选中态的小勾：缩放淡入，避免列表里状态切换显得生硬。 */
@Composable
fun PrismCheckMark(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = rememberPrismMotion()
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.72f,
        animationSpec = motion.springSnappy(),
        label = "check-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0.28f,
        animationSpec = motion.tween(PrismMotion.Fast),
        label = "check-alpha",
    )
    Icon(
        Icons.Rounded.Check,
        contentDescription = if (checked) "已选择" else null,
        tint = MaterialTheme.colorScheme.primary.withAlpha(alpha),
        modifier = modifier.size(20.dp).scale(scale),
    )
}
