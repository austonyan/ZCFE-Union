package com.union.campus.ui.screens.life

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerBuilding
import com.union.campus.domain.ShowerDevice
import com.union.campus.domain.ShowerOrg
import com.union.campus.domain.ShowerReservation
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismActionRow
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCheckMark
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.prismTextMuted
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.rememberPrismMotion
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.withAlpha

internal fun buildingLabel(name: String): String {
    val n = name.trim()
    return when {
        n.isBlank() -> "未命名楼栋"
        n.contains("楼") -> n
        n.endsWith("#") -> "${n.dropLast(1)}号楼"
        n.endsWith("号") -> "${n}楼"
        n.all { it.isDigit() } -> "${n}号楼"
        else -> n
    }
}

internal fun areaLabel(area: ShowerArea): String {
    val floor = when {
        area.floor.isBlank() -> ""
        area.floor.contains("层") -> area.floor
        else -> "${area.floor}层"
    }
    val gender = when (area.genderLimit.lowercase()) {
        "male", "1", "男" -> "男"
        "female", "2", "女" -> "女"
        else -> ""
    }
    val prefix = listOf(floor, gender).filter { it.isNotBlank() }.joinToString(" ")
    return if (prefix.isBlank()) area.name else "$prefix ${area.name}"
}

@Composable
internal fun ShowerLocationSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    orgs: List<ShowerOrg>,
    orgAreas: List<ShowerOrg>,
    buildings: List<ShowerBuilding>,
    areas: List<ShowerArea>,
    selectedOrgId: String,
    selectedOrgAreaId: String,
    selectedBuildingId: String,
    selectedAreaId: String,
    onSelectOrg: (String) -> Unit,
    onSelectOrgArea: (String) -> Unit,
    onSelectBuilding: (String) -> Unit,
    onSelectArea: (String) -> Unit,
) {
    val haptics = rememberPrismHaptics()
    PrismSnapSheet(visible = visible, onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("洗浴位置", style = pageTitleStyle())
                Text(
                    "选择学校与校区后，再选楼栋和区域",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (orgs.isNotEmpty()) {
                item { SectionHeader("学校") }
                item {
                    PrismGroupedList {
                        orgs.forEach { org ->
                            LocationOption(
                                title = org.name,
                                selected = org.id == selectedOrgId,
                                onClick = { haptics.select(); onSelectOrg(org.id) },
                            )
                        }
                    }
                }
            }
            if (orgAreas.isNotEmpty()) {
                item { SectionHeader("校区") }
                item {
                    PrismGroupedList {
                        orgAreas.forEach { item ->
                            LocationOption(
                                title = item.name,
                                selected = item.id == selectedOrgAreaId,
                                onClick = { haptics.select(); onSelectOrgArea(item.id) },
                            )
                        }
                    }
                }
            }
            if (buildings.isNotEmpty()) {
                item { SectionHeader("楼栋") }
                item {
                    PrismGroupedList {
                        buildings.forEach { building ->
                            LocationOption(
                                title = buildingLabel(building.name),
                                selected = building.id == selectedBuildingId,
                                onClick = { haptics.select(); onSelectBuilding(building.id) },
                            )
                        }
                    }
                }
            }
            if (areas.isNotEmpty()) {
                item { SectionHeader("区域") }
                item {
                    PrismGroupedList {
                        areas.forEach { area ->
                            LocationOption(
                                title = areaLabel(area),
                                subtitle = "空闲 ${area.idleNum}/${area.totalNum} · 排队 ${area.queueNum} 人",
                                selected = area.id == selectedAreaId,
                                onClick = { haptics.select(); onSelectArea(area.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationOption(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    PrismRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = if (selected) {
            {
                PrismCheckMark(checked = true)
            }
        } else {
            null
        },
    )
}

@Composable
private fun SelectorRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Box {
            PrismTextButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) {
                Text(value.ifBlank { "请选择" }, style = subheadlineStyle())
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "选择$label")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            open = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}
@Composable
internal fun deviceStatusColors(state: Int, reservedByMe: Boolean): Triple<Color, Color, ImageVector> = when {
    reservedByMe || state == 2 -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Rounded.Schedule)
    state == 0 -> Triple(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurface, Icons.Rounded.WaterDrop)
    state == 1 -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Icons.Rounded.WaterDrop)
    else -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Rounded.WarningAmber)
}

@Composable
internal fun DeviceCard(
    device: ShowerDevice,
    reservedByMe: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val (targetBg, targetOnBg, statusIcon) = deviceStatusColors(device.state, reservedByMe)
    val statusText = when {
        reservedByMe -> "已预约 · 等待启动"
        device.state == 0 -> "空闲 · 可预约"
        device.state == 1 -> "使用中 · ${device.remainingText.ifBlank { "计时中" }}"
        device.state == 2 -> "已预约" + if (device.remainingText.isNotBlank()) " · ${device.remainingText}" else ""
        else -> "故障/维护"
    }
    val bg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = motion.tween(PrismMotion.Standard),
        label = "device-bg",
    )
    val onBg by animateColorAsState(
        targetValue = targetOnBg,
        animationSpec = motion.tween(PrismMotion.Standard),
        label = "device-content",
    )
    PrismCard(
        onClick = {
            haptics.tap()
            onClick()
        },
        containerColor = bg,
        corner = if (reservedByMe) 28f else 16f,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
    ) {
        Icon(statusIcon, null, tint = onBg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            device.name,
            style = headlineStyle(),
            color = onBg,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(statusText, style = footnoteStyle(), color = onBg, textAlign = TextAlign.Start)
    }
}

/** 预约剩余秒数：服务端 remainTime 作为起点，本地只负责每秒递减。 */
@Composable
private fun rememberReservationRemaining(expiresAt: Long): Int {
    var now by remember(expiresAt) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= expiresAt) break
            kotlinx.coroutines.delay(1000)
        }
    }
    return ((expiresAt - now) / 1000).toInt().coerceAtLeast(0)
}

/** 预约卡片：显示服务端下发的剩余时间，超时自动取消。 */
@Composable
internal fun ReservationCard(
    reservation: ShowerReservation,
    busy: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onExpired: () -> Unit,
) {
    val remaining = rememberReservationRemaining(reservation.expiresAt)
    LaunchedEffect(remaining) {
        if (remaining <= 0) onExpired()
    }
    val motion = rememberPrismMotion()
    val urgent = remaining <= 60
    val container = if (urgent) PrismStatus.warningContainer() else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (urgent) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSecondaryContainer
    val ringColor by animateColorAsState(
        targetValue = onContainer,
        animationSpec = motion.tween(PrismMotion.Standard),
        label = "reservation-ring",
    )
    val total = reservation.maxWaitSeconds.coerceAtLeast(1)
    val fraction = (remaining.toFloat() / total).coerceIn(0f, 1f)
    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = container,
        corner = 28f,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2f
                    drawArc(
                        color = onContainer.withAlpha(PrismAlpha.Track),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = "%d:%02d".format(remaining / 60, remaining % 60),
                    style = caption2Style().copy(fontFeatureSettings = "tnum"),
                    color = onContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("已预约 ${reservation.deviceName} · 等待启动", style = headlineStyle(), color = onContainer)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (remaining > 0) {
                        "请在倒计时结束前启动设备，超时自动取消 · ${reservation.areaName.ifBlank { "当前区域" }}"
                    } else {
                        "预约已过期，正在自动取消，请重新预约"
                    },
                    style = footnoteStyle(),
                    color = prismTextMuted(onContainer),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "预约不会自动开启设备，需在倒计时内手动启动。",
                    style = footnoteStyle(),
                    color = prismTextMuted(onContainer),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PrismActionRow {
            PrismButton(onClick = onStart, enabled = remaining > 0 && !busy, loading = busy) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("启动设备", maxLines = 1, softWrap = false)
            }
            PrismTextButton(onClick = onCancel, enabled = !busy) {
                Text("取消预约", maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun Modifier.align2(): Modifier = this.then(Modifier)
