package com.union.campus.ui.screens.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.CourseCell
import com.union.campus.domain.MorningGroup
import com.union.campus.domain.Semester
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TimetableSchedule
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** 课表：学期/周次切换、网格课表、调课提醒、找空教室。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenCourse: (CourseCell) -> Unit,
    onOpenGrades: () -> Unit,
    onOpenFreeClassrooms: () -> Unit,
    onOpenTimetableSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    val entities = ServiceLocator.entities
    val settings = ServiceLocator.settings

    var semesters by remember { mutableStateOf<List<Semester>>(emptyList()) }
    var semesterDm by remember { mutableStateOf("") }
    var week by remember { mutableIntStateOf(1) }
    var maxWeek by remember { mutableIntStateOf(25) }
    var timetable by remember { mutableStateOf<TimetableData?>(null) }
    var showWeekend by remember { mutableStateOf(false) }

    val morningGroupValue by settings.timetableMorningGroup
        .collectAsState(initial = MorningGroup.GroupOne.storedValue)
    val morningGroup = MorningGroup.fromStored(morningGroupValue)
    val sectionTimes = remember(morningGroup) { TimetableSchedule.sectionTimes(morningGroup) }

    LaunchedEffect(Unit) {
        semesterDm = jw.currentSemesterDm()
        if (jw.isBound()) {
            semesters = runCatching { jw.semesterList() }.getOrDefault(emptyList())
            val data = runCatching { jw.timetable(semesterDm, 0) }.getOrNull()
            if (data != null) {
                timetable = data
                week = data.week
                maxWeek = data.maxWeek
            } else {
                week = SemesterCalc.currentWeek(semesterDm, settings.firstWeekStart.first())
                entities.read("jw.timetable.$semesterDm.w$week", TimetableData.serializer())?.let { timetable = it.data }
            }
        }
    }

    fun openSemester(dm: String) {
        if (dm == semesterDm) return
        semesterDm = dm
        launchUi {
            runCatching { jw.timetable(dm, 0) }.getOrNull()?.let { data ->
                timetable = data
                week = data.week
                maxWeek = data.maxWeek
            }
        }
    }

    fun changeWeek(delta: Int) {
        val newWeek = (week + delta).coerceIn(1, maxWeek)
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
        launchUi {
            runCatching { jw.timetable(semesterDm, 0, forceRefresh = true) }
                .getOrNull()?.let { data ->
                    timetable = data
                    week = data.week
                    maxWeek = data.maxWeek
                }
        }
    }

    UnionScreenScaffold(
        title = "课程表",
        onBack = null,
        actions = {
            IconButton(onClick = { changeWeek(-1) }, enabled = week > 1) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一周")
            }
            IconButton(onClick = { changeWeek(1) }, enabled = week < maxWeek) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下一周")
            }
            IconButton(onClick = onOpenTimetableSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "课表设置")
            }
            IconButton(onClick = onOpenGrades) { Icon(Icons.Rounded.Grade, contentDescription = "成绩") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("第 $week 周", style = pageTitleStyle(), fontWeight = FontWeight(700))
                    Text(
                        listOfNotNull(semesterLabel, weekRange.ifBlank { null }).joinToString(" · "),
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { goThisWeek() }) {
                    Icon(Icons.Rounded.CalendarToday, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("本周")
                }
            }

            if (semesters.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    semesters.take(8).forEach { s ->
                        FilterChip(
                            selected = semesterDm == s.dm,
                            onClick = { openSemester(s.dm) },
                            label = { Text(s.shortLabel) },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (weekCourseCount > 0) "本周 $weekCourseCount 节课" else "本周暂无课程",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showWeekend = !showWeekend }) {
                    Text(
                        if (showWeekend) "隐藏周末"
                        else if (weekendCount > 0) "显示周末 · $weekendCount"
                        else "显示周末",
                    )
                }
            }

            MorningGroupCard(
                group = morningGroup,
                sectionTimes = sectionTimes,
                onClick = onOpenTimetableSettings,
            )

            if (!jw.isBound() && timetable == null) {
                NullStatus(
                    icon = Icons.Rounded.MenuBook,
                    title = "登录后查看课表",
                    subtitle = "教务账号未绑定：到「我的 → 凭证中心」登录后即可同步",
                )
            } else {
                TimetableGrid(
                    timetable = timetable,
                    dayCount = if (showWeekend) 7 else 5,
                    morningGroup = morningGroup,
                    onOpenCourse = onOpenCourse,
                )
            }

            androidx.compose.material3.Button(
                onClick = onOpenFreeClassrooms,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.MeetingRoom, null)
                Spacer(Modifier.width(8.dp))
                Text("找空教室自习")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 上午作息提示：让用户随时知道当前分组，并可直接进入课表设置。 */
@Composable
private fun MorningGroupCard(
    group: MorningGroup,
    sectionTimes: List<com.union.campus.domain.SectionTime>,
    onClick: () -> Unit,
) {
    val third = sectionTimes.firstOrNull { it.section == 3 }
    val fourth = sectionTimes.firstOrNull { it.section == 4 }
    PrismCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "上午作息 · ${group.label}（${group.classCodes}）",
                    style = footnoteStyle(),
                    fontWeight = FontWeight(600),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (third != null && fourth != null) {
                    Text(
                        "第 3 节 ${third.start}–${third.end} · 第 4 节 ${fourth.start}–${fourth.end}",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Text(
                "调整",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TimetableGrid(
    timetable: TimetableData?,
    dayCount: Int,
    morningGroup: MorningGroup,
    onOpenCourse: (CourseCell) -> Unit,
) {
    if (timetable == null) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("课表加载中…", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxSection = TimetableSchedule.MAX_SECTION
    val displayedDays = timetable.days.filter { it.day in 1..dayCount }
    val allCells = displayedDays.flatMap { it.cells }
        .filter { it.startSection <= maxSection && it.endSection >= 1 }
    val hasEvening = allCells.any { it.endSection >= TimetableSchedule.EVENING_START_SECTION }
    val visibleEnd = if (hasEvening) maxSection else TimetableSchedule.DINNER_AFTER_SECTION
    val times = TimetableSchedule.sectionTimes(morningGroup).associateBy { it.section }

    val rowHeight = 58.dp
    val breakHeight = 26.dp
    val timeWidth = 44.dp

    fun isBreakAfter(section: Int): Boolean =
        section == TimetableSchedule.LUNCH_AFTER_SECTION || section == TimetableSchedule.DINNER_AFTER_SECTION

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
    val weekStart = runCatching { LocalDate.parse(timetable.weekStart) }.getOrNull()
    val today = SemesterCalc.dayOfWeek()
    val dateFmt = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)

    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(timeWidth))
            (1..dayCount).forEach { day ->
                val isToday = day == today
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else Color.Transparent,
                        )
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        dayName(day),
                        style = footnoteStyle(),
                        fontWeight = if (isToday) FontWeight(700) else FontWeight(500),
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    val date = weekStart?.plusDays((day - 1).toLong())
                    if (date != null) {
                        Text(
                            date.format(dateFmt),
                            style = footnoteStyle(),
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .padding(horizontal = 2.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "$section",
                                fontSize = 13.sp,
                                fontWeight = FontWeight(700),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            times[section]?.let { time ->
                                Text(
                                    time.start,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                )
                                Text(
                                    time.end,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
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
                                        day == today -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                        day % 2 == 0 -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f)
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
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            )
                            cells.forEach { cell ->
                                if (cell.startSection > maxSection || cell.endSection < 1) return@forEach
                                if (cell.startSection > visibleEnd) return@forEach
                                val start = cell.startSection.coerceIn(1, visibleEnd)
                                val end = cell.endSection.coerceIn(start, visibleEnd)
                                val (bg, onBg) = coursePalette(cell.name)
                                Box(
                                    Modifier
                                        .offset(y = sectionTop(start))
                                        .height(blockHeight(start, end))
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .clickable { onOpenCourse(cell) },
                                ) {
                                    Row(Modifier.fillMaxSize()) {
                                        Box(
                                            Modifier
                                                .width(3.dp)
                                                .fillMaxHeight()
                                                .background(onBg.copy(alpha = 0.20f)),
                                        )
                                        Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {
                                            Text(
                                                cell.name,
                                                fontSize = 12.sp,
                                                lineHeight = 14.sp,
                                                fontWeight = FontWeight(600),
                                                color = onBg,
                                                maxLines = if (end == start) 2 else 3,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (cell.location.isNotBlank()) {
                                                Text(
                                                    "@${cell.location}",
                                                    fontSize = 9.sp,
                                                    lineHeight = 11.sp,
                                                    color = onBg.copy(alpha = 0.82f),
                                                    maxLines = if (end == start) 1 else 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (cell.teacher.isNotBlank() && end > start) {
                                                Text(
                                                    cell.teacher,
                                                    fontSize = 9.sp,
                                                    lineHeight = 11.sp,
                                                    color = onBg.copy(alpha = 0.82f),
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
                val (start, end) = TimetableSchedule.lunchRange(morningGroup)
                BreakBand(
                    label = "午休 $start–$end",
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dayName(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(day - 1) { "" }

@Composable
private fun coursePalette(name: String): Pair<Color, Color> {
    val light = listOf(
        Color(0xFFDDF3E4) to Color(0xFF145C3A),
        Color(0xFFE7E2FA) to Color(0xFF3F347F),
        Color(0xFFFCE7D6) to Color(0xFF7A431D),
        Color(0xFFF9DEE8) to Color(0xFF7D2C4E),
        Color(0xFFDDF1FA) to Color(0xFF15546E),
        Color(0xFFF3EAD6) to Color(0xFF5E4A20),
    )
    val dark = listOf(
        Color(0xFF1F3A2B) to Color(0xFFC9F2DC),
        Color(0xFF2E2A4A) to Color(0xFFDCD6FF),
        Color(0xFF3A2A1C) to Color(0xFFF7D9BD),
        Color(0xFF3A2330) to Color(0xFFF7CBDD),
        Color(0xFF1B3440) to Color(0xFFC9ECFA),
        Color(0xFF33301F) to Color(0xFFEFE2BE),
    )
    val palette = if (isSystemInDarkTheme()) dark else light
    return palette[abs(name.hashCode()) % palette.size]
}