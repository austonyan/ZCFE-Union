package com.union.campus.ui.screens.study

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.CourseCell
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.Semester
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TimetableSchedule
import com.union.campus.domain.TimetableScheduleConfig
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismShape
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SurfaceLevel
import com.union.campus.ui.prism.caption1Style
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.prismCoursePalette
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.withAlpha
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 课表：页头、周次切换、网格课表；作息在右上角设置，空教室入口留在生活与教务页。 */
@Suppress("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenCourse: (CourseCell) -> Unit,
    onOpenGrades: () -> Unit,
    onOpenTimetableSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    val entities = ServiceLocator.entities
    val settings = ServiceLocator.settings
    val haptics = rememberPrismHaptics()

    var semesters by remember { mutableStateOf<List<Semester>>(emptyList()) }
    var semesterDm by remember { mutableStateOf("") }
    var week by remember { mutableIntStateOf(1) }
    var maxWeek by remember { mutableIntStateOf(25) }
    var timetable by remember { mutableStateOf<TimetableData?>(null) }
    var showWeekend by remember { mutableStateOf(false) }
    var weekDirection by remember { mutableIntStateOf(0) }
    var currentWeek by remember { mutableIntStateOf(1) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val jLate by settings.timetableJLate.collectAsState(initial = true)
    val fallbackValue by settings.timetableFallbackGroup.collectAsState(initial = 1)
    val scheduleConfig = TimetableScheduleConfig(
        j1ToJ6Slot = if (jLate) MorningSlot.Late else MorningSlot.Early,
        fallbackGroup = if (fallbackValue == 2) BuildingGroup.S1ToS6AndJ7 else BuildingGroup.J1ToJ6,
    )

    suspend fun loadSemester(dm: String, force: Boolean = false) {
        if (dm.isBlank()) return
        if (!force && dm == semesterDm && timetable != null) return
        semesterDm = dm
        weekDirection = 0
        currentWeek = SemesterCalc.currentWeek(dm, settings.firstWeekStart.first())
        val data = if (jw.isBound()) {
            runCatching { jw.timetable(dm, 0) }.getOrNull()
        } else {
            null
        }
        if (data != null) {
            timetable = data
            week = data.week
            maxWeek = data.maxWeek
        } else {
            week = currentWeek
            entities.read("jw.timetable.$dm.w$week", TimetableData.serializer())?.let { timetable = it.data }
        }
    }

    LaunchedEffect(Unit) {
        if (jw.isBound()) {
            semesters = runCatching { jw.semesterList() }.getOrDefault(emptyList())
        }
        val saved = settings.timetableSemesterDm.first()
        loadSemester(saved.ifBlank { jw.currentSemesterDm() }, force = true)
        // 学期选择在「课程表设置」里完成；设置保存后这里立刻跟着换学期。
        settings.timetableSemesterDm.collect { next ->
            val target = next.ifBlank { jw.currentSemesterDm() }
            if (target.isNotBlank() && target != semesterDm) loadSemester(target)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    fun changeWeek(delta: Int) {
        val newWeek = (week + delta).coerceIn(1, maxWeek)
        if (newWeek == week) return
        weekDirection = delta
        haptics.select()
        week = newWeek
        entities.read("jw.timetable.$semesterDm.w$newWeek", TimetableData.serializer())?.let { timetable = it.data }
        if (jw.isBound()) {
            launchUi { runCatching { timetable = jw.timetable(semesterDm, newWeek, forceRefresh = true) } }
        }
    }

    val weekCourseCount = timetable?.days?.sumOf { day ->
        day.cells.count { it.startSection <= TimetableSchedule.MAX_SECTION && it.endSection >= 1 }
    } ?: 0
    val weekendCount = timetable?.days?.filter { it.day >= 6 }?.sumOf { day ->
        day.cells.count { it.startSection <= TimetableSchedule.MAX_SECTION && it.endSection >= 1 }
    } ?: 0
    val semesterLabel = timetable?.semesterName?.takeIf { it.isNotBlank() }
        ?: semesters.firstOrNull { it.dm == semesterDm }?.name
        ?: semesterDm
    val weekStartDate = runCatching { LocalDate.parse(timetable?.weekStart.orEmpty()) }.getOrNull()
    val weekRange = weekStartDate?.let {
        val fmt = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)
        "${it.format(fmt)} - ${it.plusDays(4).format(fmt)}"
    }.orEmpty()

    fun goThisWeek() {
        weekDirection = 0
        haptics.select()
        launchUi {
            runCatching { jw.timetable(semesterDm, 0, forceRefresh = true) }
                .getOrNull()?.let { data ->
                    timetable = data
                    week = data.week
                    maxWeek = data.maxWeek
                }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PrismPageHeader(
            title = "课程表",
            subtitle = listOfNotNull(
                weekRange.takeIf { it.isNotBlank() },
                semesterLabel.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            actions = {
                PrismIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = "课程表设置",
                    onClick = onOpenTimetableSettings,
                )
                PrismIconButton(
                    icon = Icons.Outlined.Grade,
                    contentDescription = "成绩",
                    onClick = onOpenGrades,
                )
            },
        )

        WeekSwitcher(
            week = week,
            maxWeek = maxWeek,
            currentWeek = currentWeek,
            onPrevious = { changeWeek(-1) },
            onNext = { changeWeek(1) },
            onThisWeek = { goThisWeek() },
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (weekCourseCount > 0) "本周 $weekCourseCount 节课" else "本周暂无课程",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            PrismTextButton(onClick = { showWeekend = !showWeekend }) {
                Text(
                    if (showWeekend) "隐藏周末"
                    else if (weekendCount > 0) "显示周末 · $weekendCount"
                    else "显示周末",
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!jw.isBound() && timetable == null) {
                NullStatus(
                    icon = Icons.Rounded.MenuBook,
                    title = "登录后查看课表",
                    subtitle = "教务账号未绑定：到「我的 → 凭证中心」登录后即可同步",
                )
            } else {
                AnimatedContent(
                    targetState = week,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (weekDirection == 0) {
                            fadeIn(PrismMotion.tween(PrismMotion.Quick)) togetherWith
                                fadeOut(PrismMotion.tween(PrismMotion.Fast))
                        } else {
                            val direction = if (weekDirection > 0) 1 else -1
                            (slideInHorizontally(PrismMotion.tween(PrismMotion.Emphasized)) { it / 2 * direction } +
                                fadeIn(PrismMotion.tween(PrismMotion.Quick))) togetherWith
                                (slideOutHorizontally(PrismMotion.tween(PrismMotion.Emphasized)) { -it / 2 * direction } +
                                    fadeOut(PrismMotion.tween(PrismMotion.Fast)))
                        }
                    },
                    label = "timetable-week",
                ) { _ ->
                    TimetableGrid(
                        timetable = timetable,
                        dayCount = if (showWeekend) 7 else 5,
                        config = scheduleConfig,
                        nowTick = nowTick,
                        isCurrentWeek = week == currentWeek,
                        onOpenCourse = onOpenCourse,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** 周切换：胶囊里左右翻周，右侧「回到本周」只在离开本周后可用。 */
@Composable
private fun WeekSwitcher(
    week: Int,
    maxWeek: Int,
    currentWeek: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    val density = LocalDensity.current
    val pill = remember(density.density) { PrismShape(64f * density.density) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = pill, color = surfaceColorOf(SurfaceLevel.Inset)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrismIconButton(
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "上一周",
                    onClick = onPrevious,
                    enabled = week > 1,
                )
                Text(
                    text = "第 $week 周",
                    style = caption1Style(),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 52.dp),
                )
                PrismIconButton(
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "下一周",
                    onClick = onNext,
                    enabled = week < maxWeek,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrismTextButton(onClick = onThisWeek, enabled = week != currentWeek) {
            Text("回到本周")
        }
    }
}

@Composable
private fun TimetableGrid(
    timetable: TimetableData?,
    dayCount: Int,
    config: TimetableScheduleConfig,
    nowTick: Long,
    isCurrentWeek: Boolean,
    onOpenCourse: (CourseCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (timetable == null) {
        Box(modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            PrismLoadingBlock(label = "课表加载中")
        }
        return
    }

    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceIn(0.8f, 1.4f)
    val dayShape = remember(density.density) { PrismShape(8f * density.density) }
    val blockShape = remember(density.density) { PrismShape(10f * density.density) }
    val tagShape = remember(density.density) { PrismShape(4f * density.density) }
    // 时间栏用无字距小字：10sp 下「S 10:40」在 52dp 内完整显示，也不会把三行撑出行高。
    val timeStyle = caption2Style().copy(
        fontSize = 10.sp,
        lineHeight = 11.sp,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val sectionStyle = footnoteStyle().copy(
        fontSize = 12.sp,
        lineHeight = 14.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    val today = SemesterCalc.dayOfWeek(nowTick)
    val weekStart = runCatching { LocalDate.parse(timetable.weekStart) }.getOrNull()
    val dateFmt = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)
    val nowMinutes = minutesOfDay(nowTick)
    val maxSection = TimetableSchedule.MAX_SECTION

    BoxWithConstraints(modifier.fillMaxSize()) {
        val displayedDays = timetable.days.filter { it.day in 1..dayCount }
        val allCells = displayedDays.flatMap { it.cells }
            .filter { it.startSection <= maxSection && it.endSection >= 1 }
        val hasEvening = allCells.any { it.endSection >= TimetableSchedule.EVENING_START_SECTION }
        val visibleEnd = if (hasEvening) maxSection else TimetableSchedule.DINNER_AFTER_SECTION
        val todayCells = displayedDays.firstOrNull { it.day == today }?.cells.orEmpty()
        val nowSlot = TimetableSchedule.slotForCurrentOrNext(todayCells, config, nowTick)
        val times = TimetableSchedule.sectionTimes(nowSlot).associateBy { it.section }
        val sTimes = TimetableSchedule.sectionTimes(config.slotFor(BuildingGroup.S1ToS6AndJ7))
            .associateBy { it.section }
        val jTimes = TimetableSchedule.sectionTimes(config.slotFor(BuildingGroup.J1ToJ6))
            .associateBy { it.section }

        val breakHeight = 18.dp * fontScale
        val headerHeight = 40.dp * fontScale
        val gridPadding = 8.dp
        val timeWidth = 52.dp * fontScale
        val breakCount = (if (visibleEnd > TimetableSchedule.LUNCH_AFTER_SECTION) 1 else 0) +
            (if (visibleEnd > TimetableSchedule.DINNER_AFTER_SECTION) 1 else 0)
        val rowsRoom = maxHeight - gridPadding * 2 - headerHeight - 4.dp - breakHeight * breakCount
        // 8 节以内按可用高度均分；有 9、10 节或大字号时保底行高，卡片内部滚动。
        val minRowHeight = 42.dp * fontScale
        val rowHeight = (rowsRoom / visibleEnd).coerceIn(minRowHeight, 56.dp * fontScale)

        fun isBreakAfter(section: Int): Boolean =
            section == TimetableSchedule.LUNCH_AFTER_SECTION ||
                section == TimetableSchedule.DINNER_AFTER_SECTION

        fun sectionTop(section: Int): Dp {
            var y = 0.dp
            for (i in 1 until section) {
                y += rowHeight
                if (isBreakAfter(i)) y += breakHeight
            }
            return y
        }

        fun blockHeight(start: Int, end: Int): Dp {
            var h = 0.dp
            for (i in start..end) {
                h += rowHeight
                if (i < end && isBreakAfter(i)) h += breakHeight
            }
            return maxOf(h - 2.dp, 1.dp)
        }

        val totalHeight = sectionTop(visibleEnd) + rowHeight
        var nowY: Dp? = null
        if (isCurrentWeek) {
            for (section in 1..visibleEnd) {
                val time = times[section] ?: continue
                val start = parseMinutesOfDay(time.start) ?: continue
                val end = parseMinutesOfDay(time.end) ?: continue
                if (nowMinutes in start..end) {
                    val fraction = (nowMinutes - start).toFloat() / (end - start).coerceAtLeast(1)
                    nowY = sectionTop(section) + rowHeight * fraction
                    break
                }
                if (nowMinutes < start) {
                    nowY = sectionTop(section)
                    break
                }
            }
        }

        PrismCard(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = gridPadding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(timeWidth))
                (1..dayCount).forEach { day ->
                    val isToday = day == today
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(dayShape)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primaryContainer.withAlpha(PrismAlpha.ContainerTint)
                                else Color.Transparent,
                            )
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            dayName(day),
                            style = footnoteStyle(),
                            fontWeight = if (isToday) FontWeight(700) else FontWeight(500),
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val date = weekStart?.plusDays((day - 1).toLong())
                        if (date != null) {
                            Text(
                                date.format(dateFmt),
                                style = footnoteStyle(),
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            Box(Modifier.fillMaxWidth().height(totalHeight)) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(timeWidth).fillMaxHeight()) {
                        for (section in 1..visibleEnd) {
                            Column(
                                Modifier
                                    .offset(y = sectionTop(section))
                                    .height(rowHeight)
                                    .fillMaxWidth()
                                    .padding(horizontal = 1.dp)
                                    .clearAndSetSemantics { },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "$section",
                                    style = sectionStyle,
                                    fontWeight = FontWeight(700),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                if (section == 3 || section == 4) {
                                    Text(
                                        "S ${sTimes[section]?.start.orEmpty()}",
                                        style = timeStyle,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        "J ${jTimes[section]?.start.orEmpty()}",
                                        style = timeStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                } else {
                                    times[section]?.let { time ->
                                        Text(
                                            time.start,
                                            style = timeStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Secondary),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                        Text(
                                            time.end,
                                            style = timeStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Hint),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(Modifier.weight(1f).fillMaxHeight()) {
                        (1..dayCount).forEach { day ->
                            val cells = displayedDays.firstOrNull { it.day == day }?.cells.orEmpty()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            day == today -> MaterialTheme.colorScheme.primaryContainer.withAlpha(PrismAlpha.Track)
                                            day % 2 == 0 -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Border)
                                            else -> Color.Transparent
                                        },
                                    ),
                            ) {
                                for (section in 1..visibleEnd) {
                                    Box(
                                        Modifier
                                            .offset(y = sectionTop(section))
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.withAlpha(PrismAlpha.Divider)),
                                    )
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outlineVariant.withAlpha(PrismAlpha.Border)),
                                )
                                cells.forEach { cell ->
                                    if (cell.startSection > maxSection || cell.endSection < 1) return@forEach
                                    if (cell.startSection > visibleEnd) return@forEach
                                    val start = cell.startSection.coerceIn(1, visibleEnd)
                                    val end = cell.endSection.coerceIn(start, visibleEnd)
                                    val blockH = blockHeight(start, end)
                                    val (bg, onBg) = prismCoursePalette(cell.name)
                                    val scheduleInfo = TimetableSchedule.courseSchedule(cell, config)
                                    val sectionLabel = if (end > start) "第 $start–$end 节" else "第 $start 节"
                                    val shownLocation = displayLocation(cell.location)
                                    // TalkBack 一句读完：课名、节次、地点、时间。
                                    val spoken = listOfNotNull(
                                        cell.name.takeIf { it.isNotBlank() },
                                        sectionLabel,
                                        shownLocation.takeIf { it.isNotBlank() }?.let { "地点 $it" },
                                        scheduleInfo?.let { "${it.start} 至 ${it.end}" },
                                    ).joinToString("，")
                                    Box(
                                        Modifier
                                            .offset(y = sectionTop(start))
                                            .height(blockH)
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp, vertical = 1.dp)
                                            .clip(blockShape)
                                            .background(bg)
                                            // 先清空内部所有语义，再挂一条完整描述；触摸仍由 clickable 处理。
                                            .clearAndSetSemantics {
                                                contentDescription = spoken
                                                onClick(label = "查看课程") {
                                                    onOpenCourse(cell)
                                                    true
                                                }
                                            }
                                            .clickable { onOpenCourse(cell) },
                                    ) {
                                        Row(Modifier.fillMaxSize()) {
                                            Box(
                                                Modifier
                                                    .width(3.dp)
                                                    .fillMaxHeight()
                                                    .background(onBg.withAlpha(PrismAlpha.ContainerTint)),
                                            )
                                            Column(Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
                                                Text(
                                                    cell.name,
                                                    style = caption1Style(),
                                                    color = onBg,
                                                    maxLines = if (blockH >= 52.dp) 2 else 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (shownLocation.isNotBlank() && blockH >= 36.dp) {
                                                    Text(
                                                        "@$shownLocation",
                                                        style = caption2Style(),
                                                        color = onBg.withAlpha(PrismAlpha.Secondary),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                if (cell.teacher.isNotBlank() && blockH >= 66.dp) {
                                                    Text(
                                                        cell.teacher,
                                                        style = caption2Style(),
                                                        color = onBg.withAlpha(PrismAlpha.Secondary),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                if (scheduleInfo != null && blockH >= 80.dp) {
                                                    Text(
                                                        "${scheduleInfo.start}–${scheduleInfo.end}",
                                                        style = caption2Style(),
                                                        color = onBg.withAlpha(PrismAlpha.Hint),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (visibleEnd > TimetableSchedule.LUNCH_AFTER_SECTION) {
                    val s4 = sTimes[TimetableSchedule.LUNCH_AFTER_SECTION]?.end.orEmpty()
                    val j4 = jTimes[TimetableSchedule.LUNCH_AFTER_SECTION]?.end.orEmpty()
                    BreakBand(
                        label = if (s4 == j4) "午休 $s4 起" else "午休 $s4 / $j4 起",
                        y = sectionTop(TimetableSchedule.LUNCH_AFTER_SECTION) + rowHeight,
                        height = breakHeight,
                    )
                }
                if (visibleEnd > TimetableSchedule.DINNER_AFTER_SECTION) {
                    val (start, end) = TimetableSchedule.dinnerRange()
                    BreakBand(
                        label = "晚休 $start–$end",
                        y = sectionTop(TimetableSchedule.DINNER_AFTER_SECTION) + rowHeight,
                        height = breakHeight,
                    )
                }

                nowY?.let { y ->
                    Box(
                        Modifier
                            .offset(y = y)
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Box(
                        Modifier
                            .offset(x = timeWidth - 4.dp, y = y - 3.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Box(
                        Modifier
                            .offset(x = timeWidth + 3.dp, y = y - 9.dp)
                            .clip(tagShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "现在",
                            style = caption2Style(),
                            fontWeight = FontWeight(700),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun BreakBand(label: String, y: Dp, height: Dp) {
    Box(
        Modifier
            .offset(y = y)
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = caption2Style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun dayName(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(day - 1) { "" }

/** 课表格子里只显示教务地点方括号内的部分，例如「知会楼(S1)[S1-301]」→「S1-301」。 */
private val locationBracketRegex = Regex("""\[([^\]]*)]""")

private fun displayLocation(location: String): String {
    val inside = locationBracketRegex.find(location)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    return inside.ifBlank { location.trim() }
}

private fun minutesOfDay(now: Long): Int {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
}

private fun parseMinutesOfDay(text: String): Int? {
    val parts = text.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return hour * 60 + minute
}