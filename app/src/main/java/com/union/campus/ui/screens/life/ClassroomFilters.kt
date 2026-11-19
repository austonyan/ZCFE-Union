package com.union.campus.ui.screens.life

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.union.campus.domain.TeachingBuilding
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCheckMark
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismShape
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.SurfaceLevel
import com.union.campus.ui.prism.caption1Style
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.withAlpha
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------- 筛选栏 ----------------

internal val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private val SectionPresets = listOf(
    "上午" to (1..4).toSet(),
    "下午" to (5..8).toSet(),
    "晚上" to (9..10).toSet(),
    "全天" to (1..10).toSet(),
)

private fun dateChipLabel(date: LocalDate, today: LocalDate): String {
    val weekday = "周" + WeekdayLabels[date.dayOfWeek.value - 1]
    val monthDay = "%02d/%02d".format(date.monthValue, date.dayOfMonth)
    return if (date == today) "今天 · $weekday $monthDay" else "$weekday $monthDay"
}

internal fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
internal fun DateFilterControl(
    weekday: String,
    dateText: String,
    isToday: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember { PrismShape(Float.MAX_VALUE) }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = shape,
        color = surfaceColorOf(SurfaceLevel.Inset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateArrow(Icons.Rounded.ChevronLeft, "前一天", canGoBack, onBack)
            // 日期在上、周几在下；只有"今天"用主色高亮，不再写"今天"两个字。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .clickable(role = Role.Button, onClickLabel = "选择日期", onClick = onClick)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dateText,
                    style = caption1Style(),
                    color = if (isToday) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    weekday,
                    style = caption2Style(),
                    color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            DateArrow(Icons.Rounded.ChevronRight, "后一天", canGoForward, onForward)
        }
    }
}

@Composable
private fun DateArrow(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberPrismHaptics()
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Disabled)
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 实心/描边筛选胶囊；选中态可带 ✕ 直接清空。 */
@Composable
internal fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    fillWidth: Boolean = false,
) {
    val haptics = rememberPrismHaptics()
    val shape = remember { PrismShape(Float.MAX_VALUE) }
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else surfaceColorOf(SurfaceLevel.Inset)
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = shape,
        color = container,
    ) {
        Row(
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = caption1Style(),
                color = content,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = if (fillWidth) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .clickable(role = Role.Button) {
                        haptics.select()
                        onClick()
                    }
                    .padding(
                        start = 14.dp,
                        end = if (onClear != null) 6.dp else 14.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
            )
            if (onClear != null) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "清除筛选") {
                            haptics.tap()
                            onClear()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清除筛选",
                        tint = content,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ---------------- 筛选面板 ----------------

@Composable
internal fun SectionFilterSheet(
    visible: Boolean,
    selected: List<Int>,
    onApply: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    PrismSnapSheet(visible = visible, onDismiss = onDismiss) {
        var draft by remember { mutableStateOf(selected.toSet()) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("选择节次", style = pageTitleStyle())
            Text(
                "所选节次须同时空闲。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionPresets.forEach { (label, sections) ->
                    Box(Modifier.weight(1f)) {
                        FilterPill(
                            label = label,
                            selected = draft == sections,
                            onClick = { draft = if (draft == sections) emptySet() else sections },
                            fillWidth = true,
                        )
                    }
                }
            }
            listOf(1..5, 6..10).forEach { rowSections ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowSections.forEach { section ->
                        Box(Modifier.weight(1f)) {
                            FilterPill(
                                label = section.toString(),
                                selected = section in draft,
                                onClick = {
                                    draft = if (section in draft) draft - section else draft + section
                                },
                                fillWidth = true,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PrismTextButton(onClick = { draft = emptySet() }) { Text("清空") }
                Spacer(Modifier.weight(1f))
                PrismButton(
                    onClick = {
                        onApply(draft.sorted())
                        onDismiss()
                    },
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
internal fun BuildingFilterSheet(
    visible: Boolean,
    buildings: List<TeachingBuilding>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PrismSnapSheet(visible = visible, onDismiss = onDismiss) {
        val current = buildings.firstOrNull { it.dm == selectedId }
        val rest = buildings.filter { it.dm != selectedId }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("选择教学楼", style = pageTitleStyle())
            PrismGroupedList {
                PrismRow(
                    title = "全部楼栋",
                    subtitle = "跨楼栋查询，按楼栋分组",
                    onClick = {
                        onSelect("")
                        onDismiss()
                    },
                    trailing = if (selectedId.isBlank()) {
                        { PrismCheckMark(checked = true) }
                    } else {
                        null
                    },
                )
            }
            if (current != null) {
                PrismGroupedList {
                    PrismRow(
                        title = current.name,
                        subtitle = current.campus.takeIf { it.isNotBlank() },
                        onClick = {
                            onSelect(current.dm)
                            onDismiss()
                        },
                        trailing = { PrismCheckMark(checked = true) },
                    )
                }
            }
            rest.groupBy { it.campus.ifBlank { "其他校区" } }.forEach { (campus, list) ->
                SectionHeader(campus)
                PrismGroupedList {
                    list.forEach { building ->
                        PrismRow(
                            title = building.name,
                            onClick = {
                                onSelect(building.dm)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}
