package com.union.campus.ui.screens.life

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.domain.FreeClassroom
import com.union.campus.domain.TeachingBuilding
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismSkeleton
import com.union.campus.ui.prism.PrismSpace
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.prismShape
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.withAlpha

// ---------------- 列表与状态 ----------------

private const val ClassroomColumns = 10

/**
 * 节次方块配色与尺寸集中在这里：
 * 空闲 = 白底 + 描边、有课 = onSurface 28% 灰、我的冲突 = 主色、未知 = 描边空心。
 */
private object ClassroomCellStyle {
    val Size: Dp = 10.dp
    val Gap: Dp = 2.dp
    val Corner: Dp = 2.dp
    val BorderWidth: Dp = 1.dp
    const val OccupiedAlpha = 0.28f
}

private enum class ClassroomCellState { Free, Occupied, Mine, Unknown }

@Composable
internal fun ClassroomLegend(
    showFree: Boolean,
    showOccupied: Boolean,
    showMine: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showFree) LegendItem(ClassroomCellState.Free, "空闲")
        if (showOccupied) LegendItem(ClassroomCellState.Occupied, "有课")
        if (showMine) LegendItem(ClassroomCellState.Mine, "我的冲突")
    }
}

@Composable
private fun LegendItem(state: ClassroomCellState, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ClassroomCell(state)
        Text(label, style = caption2Style(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 节次表头：1…10 与下方方块同宽、居中。 */
@Composable
internal fun SectionNumbersHeader(modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale.coerceIn(0.8f, 1.4f)
    val numberStyle = caption2Style().copy(
        fontSize = (8f / fontScale).sp,
        lineHeight = (10f / fontScale).sp,
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(ClassroomCellStyle.Gap)) {
            for (section in 1..ClassroomColumns) {
                Box(
                    Modifier.width(ClassroomCellStyle.Size),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        section.toString(),
                        style = numberStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClassroomList(
    groups: List<Pair<TeachingBuilding, List<FreeClassroom>>>,
    singleBuilding: Boolean,
    filtered: Boolean,
    filterLabel: String,
    mySections: Set<Int>,
    failureCount: Int,
    onRetryFailures: () -> Unit,
    onCellClick: (FreeClassroom, Int) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PrismSpace.Bottom),
    ) {
        groups.forEach { (building, rooms) ->
            if (!singleBuilding) {
                item(key = "building-${building.dm}") {
                    SectionHeader(building.name, Modifier.padding(horizontal = 16.dp))
                }
            }
            rooms.groupBy { it.type.ifBlank { "其他教室" } }.forEach { (type, list) ->
                item(key = "type-${building.dm}-$type") {
                    if (singleBuilding) {
                        SectionHeader(type, Modifier.padding(horizontal = 16.dp))
                    } else {
                        TypeHeader(type)
                    }
                }
                val sorted = list.sortedBy { it.name }
                itemsIndexed(sorted, key = { index, room -> "${building.dm}-$type-$index-${room.name}" }) { index, room ->
                    // item 里只能有一个根节点，行和分隔线要包在 Column 里。
                    Column(Modifier.fillMaxWidth()) {
                        ClassroomRow(
                            room = room,
                            filtered = filtered,
                            filterLabel = filterLabel,
                            mySections = mySections,
                            onCellClick = { section -> onCellClick(room, section) },
                        )
                        if (index < sorted.lastIndex) RoomDivider()
                    }
                }
            }
        }
        if (failureCount > 0) {
            item(key = "failures") {
                FailureRow(count = failureCount, onRetry = onRetryFailures)
            }
        }
    }
}

/** 房间之间的细分隔线：从文字端缩进，和分组列表的观感一致。 */
@Composable
private fun RoomDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(Dp.Hairline)
            .background(MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.DividerSoft)),
    )
}

@Composable
private fun TypeHeader(title: String) {
    Text(
        title,
        style = subheadlineStyle().copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ClassroomRow(
    room: FreeClassroom,
    filtered: Boolean,
    filterLabel: String,
    mySections: Set<Int>,
    onCellClick: (Int) -> Unit,
) {
    if (filtered) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(prismShape(2f))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = bodyStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        seatText(room).takeIf { it.isNotBlank() },
                        "${filterLabel}可用",
                    ).joinToString(" · "),
                    style = caption2Style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = bodyStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    seatText(room),
                    style = caption2Style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            SectionCells(
                sections = room.sections,
                mySections = mySections,
                onCellClick = onCellClick,
            )
        }
    }
}

internal fun seatText(room: FreeClassroom): String =
    room.capacity.trim().takeIf { it.isNotBlank() }?.let { "$it 座" } ?: ""

/** 10 个节次方块；整条可点，按横坐标定位节次（9dp 单块不适合做触控目标）。 */
@Composable
private fun SectionCells(
    sections: List<Boolean?>,
    mySections: Set<Int>,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPrismHaptics()
    val density = LocalDensity.current
    val stepPx = with(density) { (ClassroomCellStyle.Size + ClassroomCellStyle.Gap).toPx() }
    val currentClick by rememberUpdatedState(onCellClick)
    var pressedSection by remember { mutableIntStateOf(0) }
    val cellsWidth = ClassroomCellStyle.Size * ClassroomColumns +
        ClassroomCellStyle.Gap * (ClassroomColumns - 1)
    Box(
        modifier = modifier
            .width(cellsWidth)
            .height(40.dp)
            .pointerInput(ClassroomColumns) {
                detectTapGestures(
                    onPress = { offset ->
                        val section = (offset.x / stepPx).toInt() + 1
                        if (section in 1..ClassroomColumns) {
                            pressedSection = section
                            val released = tryAwaitRelease()
                            pressedSection = 0
                            if (released) {
                                haptics.tap()
                                currentClick(section)
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ClassroomCellStyle.Gap)) {
            for (section in 1..ClassroomColumns) {
                val free = sections.getOrNull(section - 1)
                val state = when {
                    free == null -> ClassroomCellState.Unknown
                    free && section in mySections -> ClassroomCellState.Mine
                    free -> ClassroomCellState.Free
                    else -> ClassroomCellState.Occupied
                }
                val scale by animateFloatAsState(
                    targetValue = if (pressedSection == section) 0.98f else 1f,
                    animationSpec = PrismMotion.springSnappy(),
                    label = "classroom-cell-press",
                )
                ClassroomCell(state, Modifier.scale(scale))
            }
        }
    }
}

@Composable
private fun ClassroomCell(state: ClassroomCellState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ClassroomCellStyle.Corner)
    val base = Modifier
        .size(ClassroomCellStyle.Size)
        .then(modifier)
    val outline = MaterialTheme.colorScheme.outlineVariant
    when (state) {
        ClassroomCellState.Free -> Box(
            base
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(ClassroomCellStyle.BorderWidth, outline, shape),
        )

        ClassroomCellState.Occupied -> Box(
            base
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.withAlpha(ClassroomCellStyle.OccupiedAlpha)),
        )

        ClassroomCellState.Mine -> Box(
            base
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        )

        ClassroomCellState.Unknown -> Box(
            base.drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(ClassroomCellStyle.Corner.toPx()),
                    style = Stroke(
                        width = ClassroomCellStyle.BorderWidth.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(2.dp.toPx(), 1.5.dp.toPx()),
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
private fun FailureRow(count: Int, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count 栋未查询成功",
            style = caption2Style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        PrismTextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
internal fun FilteredEmptyState(onClear: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NullStatus(
            icon = Icons.Rounded.MeetingRoom,
            title = "没有符合条件的教室",
            subtitle = "试试第 5–6 节或换栋楼",
        )
        PrismButton(filled = false, onClick = onClear) { Text("清空筛选") }
    }
}

@Composable
internal fun NoClassroomsState(
    allBuildings: Boolean,
    failureCount: Int,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NullStatus(
            icon = Icons.Rounded.CalendarMonth,
            title = if (allBuildings) "今天这些楼没有排教室" else "今天这栋楼没有排教室",
            subtitle = "换一天或换栋楼看看",
        )
        if (failureCount > 0) FailureRow(count = failureCount, onRetry = onRetry)
    }
}

@Composable
internal fun AllFreeCard(
    roomCount: Int,
    allBuildings: Boolean,
    onShowDetails: () -> Unit,
) {
    PrismCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        CardHeader("全天空闲")
        Spacer(Modifier.height(6.dp))
        Text(
            "${if (allBuildings) "已查询的" else "本楼"} $roomCount 间教室今天全天空闲",
            style = subheadlineStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PrismTextButton(onClick = onShowDetails) { Text("查看详情") }
    }
}

@Composable
internal fun ClassroomSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        SectionNumbersHeader()
        repeat(3) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PrismSkeleton(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .height(14.dp),
                        corner = 6f,
                    )
                    PrismSkeleton(
                        Modifier
                            .fillMaxWidth(0.28f)
                            .height(11.dp),
                        corner = 4f,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(ClassroomCellStyle.Gap)) {
                    repeat(ClassroomColumns) {
                        PrismSkeleton(
                            Modifier.size(ClassroomCellStyle.Size),
                            corner = 2f,
                        )
                    }
                }
            }
        }
    }
}
