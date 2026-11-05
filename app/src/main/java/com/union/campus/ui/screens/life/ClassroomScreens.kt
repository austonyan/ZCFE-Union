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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel
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
    var buildings by remember { mutableStateOf<List<String>>(emptyList()) }
    var building by remember { mutableStateOf<String?>(null) }
    var dateOffset by remember { mutableStateOf(0) } // 0 今天 / 1 明天 / 2 后天
    var rooms by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val dateText = fmt.format(Date(System.currentTimeMillis() + dateOffset.toLong() * 24 * 3600_000))

    LaunchedEffect(Unit) {
        runCatching { buildings = jw.teachingBuildings() }
        if (buildings.isEmpty()) buildings = listOf("A 楼", "B 楼", "C 楼")
        building = buildings.firstOrNull()
    }
    LaunchedEffect(building, dateOffset) {
        val b = building ?: return@LaunchedEffect
        loading = true
        error = null
        rooms = emptyList()
        try {
            rooms = if (jw.isBound()) jw.freeClassrooms(dateText, b) else emptyList()
        } catch (e: AppError) {
            error = e.userMessage
        } catch (e: Exception) {
            error = "教室查询失败：${e.message}"
        }
        loading = false
    }

    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("空闲教室", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
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
                    FilterChip(selected = dateOffset == i, onClick = { dateOffset = i }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buildings.forEach { b ->
                    FilterChip(selected = building == b, onClick = { building = b }, label = { Text(b) })
                }
            }

            when {
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp))
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("登录教务后可查询真实空闲教室", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rooms.isEmpty() -> NullStatus(
                    Icons.Rounded.MeetingRoom,
                    "暂无空闲教室数据",
                    if (!jw.isBound()) "登录教务后按日期/教学楼查询" else "该楼栋当前没有返回空闲教室",
                )
                else -> PrismGroupedList {
                    rooms.forEachIndexed { i, room ->
                        PrismRow(
                            title = room,
                            subtitle = "今日空闲 · 点击查看详情",
                            index = i, count = rooms.size,
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
    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("教室详情", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text(room, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("容量与设备信息以教务数据为准", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("今日空闲时段", style = headlineStyle())
            // 时段以教务 getJsxx 返回为准；上游未返回明细时不编造数据
            NullStatus(
                Icons.Rounded.CalendarMonth,
                "暂无分时段数据",
                "空闲时段以上游返回为准；可在空闲教室页按日期/教学楼重新查询",
            )
            PrismCard {
                CardHeader("说明")
                Spacer(Modifier.height(6.dp))
                Text("教室容量、设备与分时段空闲情况均以教务系统为准。", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
