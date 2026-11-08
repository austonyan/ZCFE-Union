package com.union.campus.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.union.campus.data.repo.ElectricRepository
import com.union.campus.data.repo.ShowerRepository
import com.union.campus.data.store.EntityStore
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.CourseCell
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TodoItem
import com.union.campus.ui.common.formatUpdatedAt
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer

/** 今天页：下一节课、今日课程、待办、生活速览。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenTimetable: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onOpenEvaluation: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenElectric: () -> Unit,
    onOpenShower: () -> Unit,
    onOpenLife: () -> Unit,
    onOpenReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entities = ServiceLocator.entities
    val jw = ServiceLocator.jwRepository
    val electric = ServiceLocator.electricRepository
    val shower = ServiceLocator.showerRepository

    var timetable by remember { mutableStateOf<TimetableData?>(null) }
    var sectionTimes by remember { mutableStateOf<List<com.union.campus.domain.SectionTime>?>(null) }
    var todos by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var electricBalance by remember { mutableStateOf<ElectricBalance?>(null) }
    var showerArea by remember { mutableStateOf<ShowerArea?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 每分钟刷新时钟（下一节课倒计时）
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000L)
        }
    }

    LaunchedEffect(Unit) {
        val semesterDm = jw.currentSemesterDm()
        // 缓存优先（按本地估算周次）
        val cachedWeek = SemesterCalc.currentWeek(semesterDm, ServiceLocator.settings.firstWeekStart.first())
        entities.read("jw.timetable.$semesterDm.w$cachedWeek", TimetableData.serializer())?.let {
            timetable = it.data
        }
        // 静默刷新（失败不打断）；不传 week，让教务返回当前周
        if (jw.isBound()) {
            runCatching { timetable = jw.timetable(semesterDm, 0) }
            sectionTimes = runCatching { jw.sectionTimes() }.getOrNull()
        }
        todos = runCatching { ServiceLocator.todoRepository.localTodos() }.getOrDefault(emptyList())
        runCatching {
            val rooms = electric.rooms()
            rooms.firstOrNull()?.let { electricBalance = electric.balance(it.roomVerify) }
        }
        runCatching {
            val orgArea = ServiceLocator.settings.lastShowerOrgArea.first()
            if (orgArea.isNotBlank()) {
                showerArea = shower.areasWithState(orgArea).firstOrNull()
            }
        }
    }

    fun refresh() {
        refreshing = true
        com.union.campus.ui.common.launchUi {
            try {
                val semesterDm = jw.currentSemesterDm()
                if (jw.isBound()) runCatching { timetable = jw.timetable(semesterDm, 0, forceRefresh = true) }
                todos = runCatching { ServiceLocator.todoRepository.localTodos() }.getOrDefault(emptyList())
                runCatching {
                    electric.rooms(forceRefresh = true).firstOrNull()?.let {
                        electricBalance = electric.balance(it.roomVerify, forceRefresh = true)
                    }
                }
                runCatching {
                    val orgArea = ServiceLocator.settings.lastShowerOrgArea.first()
                    if (orgArea.isNotBlank()) showerArea = shower.areasWithState(orgArea, forceRefresh = true).firstOrNull()
                }
            } finally {
                refreshing = false
            }
        }
    }

    // 计算下一节课/正在上课
    val today = SemesterCalc.dayOfWeek(nowTick)
    val todayCells = timetable?.days?.firstOrNull { it.day == today }?.cells.orEmpty()
    val (current, next) = remember(todayCells, sectionTimes, nowTick) {
        computeCurrentNext(todayCells, sectionTimes, nowTick)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(SemesterCalc.dateText(nowTick) + " · 第 " + (timetable?.week ?: 1) + " 周", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenReminders) {
                    Icon(Icons.Rounded.Notifications, contentDescription = "提醒中心")
                }
            }
        }

        // 下一节课 / 正在上
        item {
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenTimetable,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Text(
                    if (current != null) "正在上课" else next?.let { "下一节课" } ?: "今天没有课",
                    style = headlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                val cell = current ?: next
                if (cell != null) {
                    Text(
                        "${cell.name} · ${cell.sections}\n${cell.location} · ${cell.teacher}",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    next?.let { n ->
                        if (current == null) {
                            Spacer(Modifier.height(8.dp))
                            val countdown = countdownText(n, sectionTimes, nowTick)
                            if (countdown != null) {
                                Text("距开始 $countdown", style = footnoteStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                } else {
                    Text("可以去自习或看看生活速览", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // 今日课程
        if (todayCells.isNotEmpty()) {
            item {
                Text("今日课程", style = headlineStyle())
            }
            item {
                PrismGroupedList {
                    todayCells.forEachIndexed { i, cell ->
                        PrismRow(
                            title = cell.name,
                            subtitle = "${cell.sections} · ${cell.location}",
                            index = i, count = todayCells.size,
                            leading = { IconBadge(Icons.Rounded.MenuBook) },
                            onClick = { onOpenCourse(cell.id) },
                        )
                    }
                }
            }
        }

        // 待办
        if (todos.isNotEmpty()) {
            item { Text("待办 · ${todos.size}", style = headlineStyle()) }
            item {
                PrismGroupedList {
                    todos.forEachIndexed { i, todo ->
                        val icon = when (todo.type) {
                            "evaluation" -> Icons.Rounded.RateReview
                            "exam" -> Icons.Rounded.Event
                            "lowElectric" -> Icons.Rounded.WaterDrop
                            else -> Icons.Rounded.Event
                        }
                        PrismRow(
                            title = todo.title,
                            subtitle = todo.subtitle,
                            index = i, count = todos.size,
                            leading = { IconBadge(icon) },
                            onClick = {
                                when (todo.deepLink) {
                                    "evaluation" -> onOpenEvaluation()
                                    "exams" -> onOpenExams()
                                    "electric" -> onOpenElectric()
                                }
                            },
                        )
                    }
                }
            }
        }

        // 生活速览
        item {
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenLife,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Text("生活速览", style = headlineStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.height(4.dp))
                val electricText = electricBalance?.let { "电费 ${it.kwh} 度" } ?: "电费未绑定"
                val showerText = showerArea?.let { "洗浴空闲 ${it.idleNum}/${it.totalNum}" } ?: "洗浴未绑定"
                Text(
                    "$electricText · $showerText\n点此查看",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        item {
            Text("下拉或回到本页时自动刷新", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 计算正在上的课与下一节课。 */
private fun computeCurrentNext(
    cells: List<CourseCell>,
    sectionTimes: List<com.union.campus.domain.SectionTime>?,
    now: Long,
): Pair<CourseCell?, CourseCell?> {
    if (cells.isEmpty()) return null to null
    fun startOf(cell: CourseCell): Long? {
        val st = sectionTimes?.firstOrNull { it.section == cell.startSection }?.start ?: return null
        return SemesterCalc.timeToMillisToday(st, now)
    }
    fun endOf(cell: CourseCell): Long? {
        val et = sectionTimes?.firstOrNull { it.section == cell.endSection }?.end
            ?: sectionTimes?.firstOrNull { it.section == cell.startSection }?.end ?: return null
        return SemesterCalc.timeToMillisToday(et, now)
    }
    val current = cells.firstOrNull { c ->
        val s = startOf(c); val e = endOf(c)
        s != null && e != null && now >= s && now <= e
    }
    val next = cells.filter { c ->
        val s = startOf(c) ?: return@filter false
        s > now
    }.minByOrNull { it.startSection }
    return current to next
}

private fun countdownText(cell: CourseCell, sectionTimes: List<com.union.campus.domain.SectionTime>?, now: Long): String? {
    val start = sectionTimes?.firstOrNull { it.section == cell.startSection }?.start ?: return null
    val startMs = SemesterCalc.timeToMillisToday(start, now) ?: return null
    val diffMin = (startMs - now) / 60000
    return when {
        diffMin >= 60 -> "${diffMin / 60} 小时 ${diffMin % 60} 分钟"
        diffMin > 0 -> "$diffMin 分钟"
        else -> null
    }
}
