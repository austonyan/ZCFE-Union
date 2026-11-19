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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.CourseCell
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricLowState
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TimetableSchedule
import com.union.campus.domain.TimetableScheduleConfig
import com.union.campus.domain.TodoItem
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCorner
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismMetricCard
import com.union.campus.ui.prism.PrismNoticeBanner
import com.union.campus.ui.prism.PrismNoticeTone
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.caption1Style
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.metricStyle
import com.union.campus.ui.prism.prismCourseContainer
import com.union.campus.ui.prism.prismCourseOnContainer
import com.union.campus.ui.prism.prismShape
import com.union.campus.ui.prism.prismTextFaint
import com.union.campus.ui.prism.prismTextMuted
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.withAlpha
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
    val classReminder = if (remindEnabled && remindClass) {
        next?.let { cell ->
            val start = TimetableSchedule.courseSchedule(cell, scheduleConfig)?.start
            val startMs = start?.let { SemesterCalc.timeToMillisToday(it, nowTick) }
            val minutes = startMs?.let { ((it - nowTick) / 60_000L).toInt() }
            if (minutes != null && minutes in 0..classLead) cell to minutes else null
        }
    } else {
        null
    }

    LaunchedEffect(remindEnabled, remindExam, remindEvaluation, remindLowElectric, lowElectricThreshold) {
        todos = runCatching { ServiceLocator.todoRepository.localTodos() }.getOrDefault(emptyList())
    }

    if (initialLoading) {
        Column(
            Modifier
                .pageWidth()
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
        // 限宽只加在列表内容上；PullToRefreshBox 仍保持整屏，不用限宽容器包住刷新容器。
        LazyColumn(
            modifier = Modifier
                .pageWidth()
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PrismPageHeader(
                    title = "今天",
                    subtitle = "${SemesterCalc.dateText(nowTick)} · 第 ${timetable?.week ?: 1} 周",
                    actions = {
                        PrismIconButton(
                            icon = Icons.Outlined.Notifications,
                            contentDescription = "提醒中心",
                            onClick = onOpenReminders,
                        )
                    },
                )
            }
            classReminder?.let { (cell, minutes) ->
                item {
                    PrismNoticeBanner(
                        title = if (minutes <= 0) "马上上课" else "还有 $minutes 分钟上课",
                        message = listOfNotNull(
                            cell.name,
                            cell.location.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        tone = PrismNoticeTone.Info,
                        icon = Icons.Rounded.Notifications,
                        actionLabel = "看课表",
                        onAction = onOpenTimetable,
                        onClick = onOpenTimetable,
                    )
                }
            }
            item {
                NextClassHero(
                    current = current,
                    next = next,
                    hasClassesToday = todayCells.isNotEmpty(),
                    config = scheduleConfig,
                    nowTick = nowTick,
                    onClick = onOpenTimetable,
                )
            }
            item {
                PrismSection(
                    title = "今日课程",
                    action = {
                        Text(
                            if (todayCells.isEmpty()) "没有课" else "${todayCells.size} 节课",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    if (todayCells.isEmpty()) {
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
                    } else {
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
            }

            item {
                PrismSection(
                    title = "生活",
                    action = {
                        Text(
                            "点卡片查看详情",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val days = electricBalance?.let { estimateDays(it) } ?: "—"
                        val low = ElectricLowState.isLow(electricBalance, lowElectricThreshold)
                        PrismMetricCard(
                            label = if (low) "电费 · 偏低" else "电费",
                            value = electricBalance?.let { "${it.kwh} 度" } ?: "未绑定",
                            subtitle = electricBalance?.let { "约 $days 天 · 今日 ${it.todayUse.ifBlank { "—" }} 度" }
                                ?: "绑定后查看余额与用量",
                            icon = Icons.Rounded.Bolt,
                            accent = if (low) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = if (low) PrismStatus.warningContainer() else surfaceColorOf(SurfaceLevel.Card),
                            contentColor = if (low) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSurface,
                            compact = true,
                            onClick = onOpenElectric,
                            modifier = Modifier.weight(1f),
                        )
                        PrismMetricCard(
                            label = "洗浴",
                            value = showerArea?.let { "${it.idleNum} / ${it.totalNum}" } ?: "未绑定",
                            subtitle = showerArea?.let { "空闲 · 排队 ${it.queueNum} 人" } ?: "登录大白U帮后查看",
                            icon = Icons.Rounded.WaterDrop,
                            accent = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = surfaceColorOf(SurfaceLevel.Card),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            compact = true,
                            onClick = onOpenShower,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (todos.isNotEmpty()) {
                item {
                    PrismSection(title = "待办 · ${todos.size}") {
                        PrismGroupedList {
                            todos.forEach { todo ->
                                val icon = when (todo.type) {
                                    "evaluation" -> Icons.Rounded.RateReview
                                    "exam" -> Icons.Rounded.Event
                                    "lowElectric" -> Icons.Rounded.WaterDrop
                                    else -> Icons.Rounded.Event
                                }
                                PrismRow(
                                    title = todo.title,
                                    subtitle = todo.subtitle,
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
            }

            item {
                PrismCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenLife,
                    containerColor = surfaceColorOf(SurfaceLevel.Card),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("全部生活入口", style = bodyStyle(), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text("去生活", style = footnoteStyle(), color = MaterialTheme.colorScheme.primary)
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
private fun NextClassHero(
    current: CourseCell?,
    next: CourseCell?,
    hasClassesToday: Boolean,
    config: TimetableScheduleConfig,
    nowTick: Long,
    onClick: () -> Unit,
) {
    val cell = current ?: next
    val info = cell?.let { TimetableSchedule.courseSchedule(it, config) }
    val container = cell?.let { prismCourseContainer(it.name) } ?: surfaceColorOf(SurfaceLevel.Card)
    val onContainer = cell?.let { prismCourseOnContainer(it.name) } ?: MaterialTheme.colorScheme.onSurface
    val status = when {
        current != null -> "正在上课"
        next != null -> "下一节 · 未开始"
        hasClassesToday -> "今日课程已结束"
        else -> "今天没有课"
    }

    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = container,
        corner = PrismCorner.Xxl,
        contentPadding = PaddingValues(if (cell == null) 16.dp else 20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Text(
                text = cell?.name ?: if (hasClassesToday) "今天的课，上完了" else "留一点时间给自己",
                style = if (cell == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = onContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 静态状态几何，不靠循环动效表达「正在上课」。
            Box(
                Modifier
                    .size(48.dp)
                    .clip(if (current != null) prismShape(PrismCorner.Md) else CircleShape)
                    .background(onContainer.withAlpha(PrismAlpha.ContainerTint)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        current != null -> Icons.Rounded.MenuBook
                        next != null -> Icons.Rounded.Schedule
                        hasClassesToday -> Icons.Rounded.Check
                        else -> Icons.Rounded.MenuBook
                    },
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HeroChip(status, onContainer)
        if (cell != null) {
            Spacer(Modifier.height(24.dp))
            if (cell.location.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = onContainer, modifier = Modifier.size(20.dp))
                    Text(cell.location, style = MaterialTheme.typography.titleLarge, color = onContainer, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            val detail = listOfNotNull(
                cell.teacher.takeIf { it.isNotBlank() },
                cell.sections.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(detail, style = footnoteStyle(), color = prismTextMuted(onContainer), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(20.dp))
            info?.let {
                Text(
                    text = "${it.start}–${it.end}",
                    style = metricStyle(),
                    color = onContainer,
                )
            }
            Text(
                text = if (current != null) currentRemainingText(current, config, nowTick) ?: "正在上课"
                else countdownText(cell, config, nowTick)?.let { "$it 后开始" } ?: "即将开始",
                style = subheadlineStyle(),
                color = prismTextMuted(onContainer),
            )
            if (current != null) {
                Spacer(Modifier.height(12.dp))
                val progress = classProgress(cell, true, config, nowTick)
                val railShape = prismShape(PrismCorner.Sm)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(railShape)
                        .background(onContainer.withAlpha(PrismAlpha.ContainerTint)),
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(railShape).background(onContainer))
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                if (hasClassesToday) "歇一会儿，明天再战" else "今天暂时没有课程，可以去自习或看看生活速览",
                style = subheadlineStyle(),
                color = prismTextMuted(onContainer),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("查看本周课表", style = footnoteStyle(), fontWeight = FontWeight.SemiBold, color = onContainer)
    }
}

@Composable
private fun HeroChip(text: String, contentColor: Color) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(contentColor.withAlpha(PrismAlpha.ContainerTint))
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
        cells.forEachIndexed { index, cell ->
            val info = TimetableSchedule.courseSchedule(cell, config)
            val isCurrent = current?.id == cell.id
            val isNext = next?.id == cell.id
            // 与主卡保持同一课程色，只有当前焦点突出；不把整条时间轨变成彩虹。
            val isFocus = isCurrent || (current == null && isNext)
            val isFinished = info?.end?.let { SemesterCalc.timeToMillisToday(it, nowTick) }?.let { it < nowTick } == true
            val container = if (isFocus) prismCourseContainer(cell.name) else surfaceColorOf(SurfaceLevel.Card)
            val onContainer = if (isFocus) prismCourseOnContainer(cell.name) else MaterialTheme.colorScheme.onSurface
            val dotColor = if (isFocus) onContainer else MaterialTheme.colorScheme.outlineVariant
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                Column(
                    Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .padding(top = 12.dp, end = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        info?.start ?: "—",
                        style = caption1Style().copy(fontFeatureSettings = "tnum"),
                        fontWeight = if (isFocus) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        info?.end.orEmpty(),
                        style = caption2Style(),
                        color = prismTextFaint(),
                    )
                }
                Box(Modifier.width(18.dp).fillMaxHeight()) {
                    if (index < cells.lastIndex) Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .padding(top = 19.dp)
                            .width(2.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    if (index > 0) Box(
                        Modifier.align(Alignment.TopCenter).height(19.dp).width(2.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (isFocus) 13.dp else 15.dp)
                            .size(if (isFocus) 12.dp else 8.dp)
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
                    Text(
                        cell.name,
                        style = bodyStyle(),
                        fontWeight = FontWeight(600),
                        color = onContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            cell.sections.takeIf { it.isNotBlank() },
                            cell.location.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = footnoteStyle(),
                        color = prismTextMuted(onContainer),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrent || isNext || isFinished) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                isCurrent -> "正在上课"
                                isNext -> "下一节"
                                else -> "已结束"
                            },
                            style = caption1Style(),
                            color = prismTextMuted(onContainer),
                        )
                    }
                }
            }
        }
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
    return when {
        hours > 0 -> "%d 小时 %02d 分".format(hours, minutes)
        minutes > 0 -> "%d 分钟".format(minutes)
        else -> "不到 1 分钟"
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
    return if (minutes > 0) "距下课 %d 分钟".format(minutes) else "距下课不到 1 分钟"
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
