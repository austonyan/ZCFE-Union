package com.union.campus.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.CourseCell
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TimetableSchedule
import com.union.campus.domain.TimetableScheduleConfig
import com.union.campus.domain.TodoItem
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.MetaChip
import com.union.campus.ui.prism.PrismAnimatedText
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismShape
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel
import com.union.campus.ui.screens.life.estimateDays
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** 今天页：下一节课、今日时间轨、生活速览、待办。 */
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
    val settings = ServiceLocator.settings
    val haptics = rememberPrismHaptics()

    var timetable by remember { mutableStateOf<TimetableData?>(null) }
    var todos by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var electricBalance by remember { mutableStateOf<ElectricBalance?>(null) }
    var showerArea by remember { mutableStateOf<ShowerArea?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        try {
            val semesterDm = jw.currentSemesterDm()
            val cachedWeek = SemesterCalc.currentWeek(semesterDm, settings.firstWeekStart.first())
            entities.read("jw.timetable.$semesterDm.w$cachedWeek", TimetableData.serializer())?.let {
                timetable = it.data
            }
            if (jw.isBound()) {
                runCatching { timetable = jw.timetable(semesterDm, 0) }
            }
            runCatching {
                val rooms = electric.rooms()
                rooms.firstOrNull()?.let { electricBalance = electric.balance(it.roomVerify) }
            }
            runCatching {
                val orgArea = settings.lastShowerOrgArea.first()
                val areaId = settings.lastShowerAreaId.first()
                if (orgArea.isNotBlank()) {
                    val list = shower.areasWithState(orgArea)
                    showerArea = list.firstOrNull { it.id == areaId } ?: list.firstOrNull()
                }
            }
        } finally {
            initialLoading = false
        }
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        launchUi {
            try {
                val semesterDm = jw.currentSemesterDm()
                if (jw.isBound()) {
                    runCatching { timetable = jw.timetable(semesterDm, 0, forceRefresh = true) }
                }
                todos = runCatching { ServiceLocator.todoRepository.localTodos() }.getOrDefault(emptyList())
                runCatching {
                    electric.rooms(forceRefresh = true).firstOrNull()?.let {
                        electricBalance = electric.balance(it.roomVerify, forceRefresh = true)
                    }
                }
                runCatching {
                    val orgArea = settings.lastShowerOrgArea.first()
                    val areaId = settings.lastShowerAreaId.first()
                    if (orgArea.isNotBlank()) {
                        val list = shower.areasWithState(orgArea, forceRefresh = true)
                        showerArea = list.firstOrNull { it.id == areaId } ?: list.firstOrNull()
                    }
                }
                haptics.success()
            } finally {
                refreshing = false
                initialLoading = false
            }
        }
    }

    val jLate by settings.timetableJLate.collectAsState(initial = true)
    val fallbackValue by settings.timetableFallbackGroup.collectAsState(initial = 1)
    val lowElectricThreshold by settings.lowElectricThreshold.collectAsState(initial = 20)
    val remindEnabled by settings.remindEnabled.collectAsState(initial = true)
    val remindClass by settings.remindClass.collectAsState(initial = true)
    val classLead by settings.remindClassLead.collectAsState(initial = 10)
    val remindExam by settings.remindExam.collectAsState(initial = true)
    val remindEvaluation by settings.remindEvaluation.collectAsState(initial = true)
    val remindLowElectric by settings.remindLowElectric.collectAsState(initial = true)
    val scheduleConfig = remember(jLate, fallbackValue) {
        TimetableScheduleConfig(
            j1ToJ6Slot = if (jLate) MorningSlot.Late else MorningSlot.Early,
            fallbackGroup = if (fallbackValue == 2) BuildingGroup.S1ToS6AndJ7 else BuildingGroup.J1ToJ6,
        )
    }
    val today = SemesterCalc.dayOfWeek(nowTick)
    val todayCells = timetable?.days?.firstOrNull { it.day == today }?.cells.orEmpty()
    val (current, next) = remember(todayCells, scheduleConfig, nowTick) {
        computeCurrentNext(todayCells, scheduleConfig, nowTick)
    }
    val minutesToNext = next?.let { cell ->
        val start = TimetableSchedule.courseSchedule(cell, scheduleConfig)?.start ?: return@let null
        val startMs = SemesterCalc.timeToMillisToday(start, nowTick) ?: return@let null
        ((startMs - nowTick) / 60_000L).toInt()
    }
    val showClassReminder = remindEnabled && remindClass && next != null &&
        minutesToNext != null && minutesToNext in 0..classLead

    LaunchedEffect(remindEnabled, remindExam, remindEvaluation, remindLowElectric, lowElectricThreshold) {
        todos = runCatching { ServiceLocator.todoRepository.localTodos() }.getOrDefault(emptyList())
    }

    if (initialLoading) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
        ) {
            PrismSkeletonList(
                modifier = Modifier.fillMaxWidth(),
                rows = 5,
            )
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TodayHeader(
                    nowTick = nowTick,
                    week = timetable?.week ?: 1,
                    onOpenReminders = onOpenReminders,
                )
            }
            if (showClassReminder && next != null && minutesToNext != null) {
                item {
                    ClassReminderBanner(
                        cell = next,
                        minutes = minutesToNext,
                        onClick = onOpenTimetable,
                    )
                }
            }
            item {
                NextClassHero(
                    current = current,
                    next = next,
                    config = scheduleConfig,
                    nowTick = nowTick,
                    onClick = onOpenTimetable,
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("今天", style = headlineStyle())
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (todayCells.isEmpty()) "没有课" else "${todayCells.size} 节课",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (todayCells.isEmpty()) {
                item {
                    PrismCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = surfaceColorOf(SurfaceLevel.Card),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.MenuBook)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("今天没有课", style = bodyStyle())
                                Text("可以去自习，或看看生活速览", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                item {
                    TodayCourseRail(
                        cells = todayCells,
                        config = scheduleConfig,
                        nowTick = nowTick,
                        current = current,
                        next = next,
                        onOpenCourse = onOpenCourse,
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("生活", style = headlineStyle())
                    Spacer(Modifier.weight(1f))
                    Text(
                        "点卡片查看详情",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val days = electricBalance?.let { estimateDays(it) } ?: "—"
                    val low = electricBalance?.kwh?.toDoubleOrNull()?.let { it < lowElectricThreshold } == true
                    LifeMetricCard(
                        label = "电费",
                        value = electricBalance?.let { "${it.kwh} 度" } ?: "未绑定",
                        subtitle = electricBalance?.let { "约 $days 天 · 今日 ${it.todayUse.ifBlank { "—" }} 度" }
                            ?: "绑定后查看余额与用量",
                        icon = Icons.Rounded.Bolt,
                        accent = if (low) PrismStatus.warning() else PrismStatus.info(),
                        container = if (low) PrismStatus.warningContainer() else surfaceColorOf(SurfaceLevel.Card),
                        onContainer = if (low) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSurface,
                        onClick = onOpenElectric,
                        modifier = Modifier.weight(1f),
                    )
                    LifeMetricCard(
                        label = "洗浴",
                        value = showerArea?.let { "${it.idleNum} / ${it.totalNum}" } ?: "未绑定",
                        subtitle = showerArea?.let { "空闲 · 排队 ${it.queueNum} 人" } ?: "登录大白U帮后查看",
                        icon = Icons.Rounded.WaterDrop,
                        accent = PrismStatus.success(),
                        container = surfaceColorOf(SurfaceLevel.Card),
                        onContainer = MaterialTheme.colorScheme.onSurface,
                        onClick = onOpenShower,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (todos.isNotEmpty()) {
                item {
                    Text("待办 · ${todos.size}", style = headlineStyle())
                }
                item {
                    PrismGroupedList {
                        todos.forEachIndexed { index, todo ->
                            val icon = when (todo.type) {
                                "evaluation" -> Icons.Rounded.RateReview
                                "exam" -> Icons.Rounded.Event
                                "lowElectric" -> Icons.Rounded.WaterDrop
                                else -> Icons.Rounded.Event
                            }
                            PrismRow(
                                title = todo.title,
                                subtitle = todo.subtitle,
                                index = index,
                                count = todos.size,
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

            item {
                PrismCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenLife,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("全部生活入口", style = bodyStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                        Text("去生活", style = footnoteStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            item {
                Text(
                    "数据来自教务、完美校园与大白U帮，以官方为准",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayHeader(
    nowTick: Long,
    week: Int,
    onOpenReminders: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "今天",
                style = pageTitleStyle().copy(fontSize = 28.sp, lineHeight = 34.sp),
                fontWeight = FontWeight(700),
            )
            Text(
                "${SemesterCalc.dateText(nowTick)} · 第 $week 周",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenReminders) {
            Icon(Icons.Rounded.Notifications, contentDescription = "提醒中心")
        }
    }
}

@Composable
private fun ClassReminderBanner(
    cell: CourseCell,
    minutes: Int,
    onClick: () -> Unit,
) {
    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Notifications)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (minutes <= 0) "马上上课" else "还有 $minutes 分钟上课",
                    style = bodyStyle(),
                    fontWeight = FontWeight(600),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    listOfNotNull(
                        cell.name,
                        cell.location.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "看课表",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun NextClassHero(
    current: CourseCell?,
    next: CourseCell?,
    config: TimetableScheduleConfig,
    nowTick: Long,
    onClick: () -> Unit,
) {
    val cell = current ?: next
    val info = cell?.let { TimetableSchedule.courseSchedule(it, config) }
    val hasClass = cell != null
    val density = LocalDensity.current
    val railShape = remember(density.density) { PrismShape(2f * density.density) }
    val container = if (hasClass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (hasClass) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = container,
        contentPadding = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(onContainer.copy(alpha = 0.9f)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    current != null -> "正在上课"
                    next != null -> "下一节课"
                    else -> "今天没有课"
                },
                style = footnoteStyle(),
                fontWeight = FontWeight(600),
                color = onContainer.copy(alpha = 0.88f),
            )
            Spacer(Modifier.weight(1f))
            if (current != null) {
                HeroChip("现在", onContainer)
            } else if (next != null) {
                HeroChip("即将开始", onContainer)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = cell?.name ?: "去课表看看本周安排",
            style = headlineStyle().copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = onContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (cell != null) {
                listOfNotNull(
                    info?.let { "${it.start}–${it.end}" },
                    cell.location.takeIf { it.isNotBlank() },
                    cell.teacher.takeIf { it.isNotBlank() },
                    cell.sections.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
            } else {
                "今天暂时没有课程，可以去自习或看看生活速览"
            },
            style = subheadlineStyle(),
            color = onContainer.copy(alpha = 0.82f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (cell != null) {
            Spacer(Modifier.height(14.dp))
            if (next != null && current == null) {
                val countdown = countdownText(next, config, nowTick)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = countdown ?: "即将开始",
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight(700),
                        color = onContainer,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFeatureSettings = "tnum",
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("后开始", style = footnoteStyle(), color = onContainer.copy(alpha = 0.78f))
                }
            } else if (current != null) {
                Text(
                    text = currentRemainingText(current, config, nowTick) ?: "正在计时",
                    style = subheadlineStyle(),
                    fontWeight = FontWeight(600),
                    color = onContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            val progress = classProgress(cell, current != null, config, nowTick)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(railShape)
                    .background(onContainer.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(railShape)
                        .background(onContainer.copy(alpha = 0.9f)),
                )
            }
        }
    }
}

@Composable
private fun HeroChip(text: String, contentColor: Color) {
    val density = LocalDensity.current
    val pill = remember(density.density) { PrismShape(Float.MAX_VALUE) }
    Box(
        Modifier
            .clip(pill)
            .background(contentColor.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, style = footnoteStyle(), fontWeight = FontWeight(600), color = contentColor)
    }
}

@Composable
private fun TodayCourseRail(
    cells: List<CourseCell>,
    config: TimetableScheduleConfig,
    nowTick: Long,
    current: CourseCell?,
    next: CourseCell?,
    onOpenCourse: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            val info = TimetableSchedule.courseSchedule(cell, config)
            val isCurrent = current?.id == cell.id
            val isNext = next?.id == cell.id
            val container = when {
                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                isNext -> MaterialTheme.colorScheme.tertiaryContainer
                else -> surfaceColorOf(SurfaceLevel.Card)
            }
            val onContainer = when {
                isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                isNext -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            val dotColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isNext -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                Column(
                    Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                        .padding(top = 12.dp, end = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        info?.start ?: "—",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight(600),
                        color = if (isCurrent || isNext) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        info?.end.orEmpty(),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    )
                }
                Box(Modifier.width(18.dp).fillMaxHeight()) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 15.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
                PrismCard(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp),
                    onClick = { onOpenCourse(cell.id) },
                    containerColor = container,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            cell.name,
                            style = bodyStyle(),
                            fontWeight = FontWeight(600),
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCurrent) {
                            Spacer(Modifier.width(6.dp))
                            MetaChip("现在")
                        } else if (isNext) {
                            Spacer(Modifier.width(6.dp))
                            MetaChip("下一节")
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            cell.sections.takeIf { it.isNotBlank() },
                            cell.location.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = footnoteStyle(),
                        color = onContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LifeMetricCard(
    label: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    container: Color,
    onContainer: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrismCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = container,
        contentPadding = PaddingValues(14.dp),
    ) {
        val density = LocalDensity.current
        val badgeShape = remember(density.density) { PrismShape(9f * density.density) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(badgeShape)
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = footnoteStyle(), color = onContainer.copy(alpha = 0.72f))
        }
        Spacer(Modifier.height(8.dp))
        PrismAnimatedText(
            text = value,
            style = headlineStyle().copy(fontSize = 20.sp, lineHeight = 24.sp),
            color = onContainer,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = footnoteStyle(),
            color = onContainer.copy(alpha = 0.66f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 计算正在上的课与下一节课；每门课按地点楼号自动选择作息。 */
private fun computeCurrentNext(
    cells: List<CourseCell>,
    config: TimetableScheduleConfig,
    now: Long,
): Pair<CourseCell?, CourseCell?> {
    if (cells.isEmpty()) return null to null
    fun startOf(cell: CourseCell): Long? {
        val start = TimetableSchedule.courseSchedule(cell, config)?.start ?: return null
        return SemesterCalc.timeToMillisToday(start, now)
    }
    fun endOf(cell: CourseCell): Long? {
        val end = TimetableSchedule.courseSchedule(cell, config)?.end ?: return null
        return SemesterCalc.timeToMillisToday(end, now)
    }
    val current = cells.firstOrNull { c ->
        val s = startOf(c)
        val e = endOf(c)
        s != null && e != null && now >= s && now <= e
    }
    val next = cells.filter { c ->
        val s = startOf(c) ?: return@filter false
        s > now
    }.minByOrNull { it.startSection }
    return current to next
}

private fun countdownText(
    cell: CourseCell,
    config: TimetableScheduleConfig,
    now: Long,
): String? {
    val start = TimetableSchedule.courseSchedule(cell, config)?.start ?: return null
    val startMs = SemesterCalc.timeToMillisToday(start, now) ?: return null
    val totalSec = ((startMs - now) / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> "%d 小时 %02d 分".format(hours, minutes)
        minutes > 0 -> "%d 分 %02d 秒".format(minutes, seconds)
        else -> "%d 秒".format(seconds)
    }
}

private fun currentRemainingText(
    cell: CourseCell,
    config: TimetableScheduleConfig,
    now: Long,
): String? {
    val end = TimetableSchedule.courseSchedule(cell, config)?.end ?: return null
    val endMs = SemesterCalc.timeToMillisToday(end, now) ?: return null
    val totalSec = ((endMs - now) / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (minutes > 0) "距下课 %d 分 %02d 秒".format(minutes, seconds) else "距下课 %d 秒".format(seconds)
}

private fun classProgress(
    cell: CourseCell,
    isCurrent: Boolean,
    config: TimetableScheduleConfig,
    now: Long,
): Float {
    val info = TimetableSchedule.courseSchedule(cell, config) ?: return 0.2f
    val startMs = SemesterCalc.timeToMillisToday(info.start, now) ?: return 0.2f
    val endMs = SemesterCalc.timeToMillisToday(info.end, now) ?: return 0.2f
    return if (isCurrent) {
        if (endMs <= startMs) 0.2f else ((now - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
    } else {
        // 下一节课：把“最近一小时”作为倒计时轨道，超过一小时显示 0。
        val diff = (startMs - now).coerceAtLeast(0L)
        (1f - diff / 3_600_000f).coerceIn(0f, 1f)
    }
}