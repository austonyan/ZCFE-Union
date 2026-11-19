package com.union.campus.ui.screens.life

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.FreeClassroom
import com.union.campus.domain.TeachingBuilding
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 空闲教室：日期、时段、教学楼筛选与可用教室列表。 */
@Composable
fun FreeClassroomsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var buildings by remember { mutableStateOf<List<TeachingBuilding>>(emptyList()) }
    var building by remember { mutableStateOf<TeachingBuilding?>(null) }
    // 楼栋对象不能直接存，保存 id；日期偏移用 rememberSaveable 保证从详情返回后不重置。
    var buildingId by rememberSaveable { mutableStateOf("") }
    var dateOffset by rememberSaveable { mutableIntStateOf(0) } // 0 今天 / 1 明天 / 2 后天
    var rooms by remember { mutableStateOf<List<FreeClassroom>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val dateText = fmt.format(Date(System.currentTimeMillis() + dateOffset.toLong() * 24 * 3600_000))

    LaunchedEffect(Unit) {
        buildings = runCatching { jw.teachingBuildings() }.getOrDefault(emptyList())
        // 返回本页时按保存的 id 反查楼栋；找不到再回落到惠济校区或第一栋。
        building = buildings.firstOrNull { it.dm == buildingId }
            ?: buildings.firstOrNull { it.campus.startsWith("惠济") }
            ?: buildings.firstOrNull()
        buildingId = building?.dm.orEmpty()
        if (building == null) {
            loading = false
            if (jw.isBound()) error = "未获取到教学楼列表，请稍后重试"
        }
    }
    LaunchedEffect(building?.dm, dateOffset) {
        val b = building ?: return@LaunchedEffect
        loading = true
        error = null
        rooms = emptyList()
        try {
            rooms = if (jw.isBound()) jw.freeClassrooms(dateText, b.dm) else emptyList()
        } catch (e: AppError) {
            error = e.userMessage
        } catch (e: Exception) {
            error = "教室查询失败：${e.message}"
        }
        loading = false
    }

    UnionScreenScaffold(
        title = "空闲教室",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val dayLabels = listOf("今天", "明天", "后天")
            Text("${dayLabels.getOrElse(dateOffset) { "今天" }} · ${dateText}", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayLabels.forEachIndexed { i, label ->
                    PrismChip(
                        label = label,
                        selected = dateOffset == i,
                        onClick = { dateOffset = i },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buildings.forEach { b ->
                    PrismChip(
                        label = b.name,
                        selected = building?.dm == b.dm,
                        onClick = {
                            building = b
                            buildingId = b.dm
                        },
                    )
                }
            }

            when {
                loading -> PrismLoadingBlock(label = "正在查询空闲教室")
                error != null -> PrismErrorState(
                    message = error ?: "",
                    hint = if (!jw.isBound()) "登录教务后可查询真实空闲教室" else null,
                )
                rooms.isEmpty() -> NullStatus(
                    Icons.Rounded.MeetingRoom,
                    "暂无空闲教室数据",
                    if (!jw.isBound()) "登录教务后按日期/教学楼查询" else "该楼栋当前没有返回空闲教室",
                )
                else -> PrismGroupedList {
                    rooms.forEach { room ->
                        val detail = buildString {
                            if (room.type.isNotBlank()) append(room.type)
                            if (room.capacity.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("容量 ${room.capacity}")
                            }
                            if (room.sectionCount > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("空闲 ${room.freeCount}/${room.sectionCount} 节")
                            }
                        }
                        PrismRow(
                            title = room.name,
                            subtitle = detail.ifBlank { "空闲教室" },
                            leading = { IconBadge(Icons.Rounded.MeetingRoom) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 教室详情：容量、设备与今日空闲时段。 */
@Composable
fun ClassroomDetailScreen(
    room: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "教室详情",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Text(room, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("容量与设备信息以教务数据为准", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            PrismSection(title = "今日空闲时段") {
                // 时段以教务 getJsxx 返回为准；上游未返回明细时不编造数据
                NullStatus(
                    Icons.Rounded.CalendarMonth,
                    "暂无分时段数据",
                    "空闲时段以上游返回为准；可在空闲教室页按日期/教学楼重新查询",
                )
            }
            PrismCard {
                CardHeader("说明")
                Spacer(Modifier.height(6.dp))
                Text("教室容量、设备与分时段空闲情况均以教务系统为准。", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
