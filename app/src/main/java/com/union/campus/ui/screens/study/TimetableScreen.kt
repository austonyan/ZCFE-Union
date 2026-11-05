package com.union.campus.ui.screens.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.CourseCell
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TimetableData
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCorner
import com.union.campus.ui.prism.PrismShape
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer

/** 课表：学期/周次切换、日视图课程列表、调课提醒、找空教室。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenCourse: (CourseCell) -> Unit,
    onOpenGrades: () -> Unit,
    onOpenFreeClassrooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    val entities = ServiceLocator.entities
    val settings = ServiceLocator.settings

    var semesterDm by remember { mutableStateOf("") }
    var week by remember { mutableIntStateOf(1) }
    var timetable by remember { mutableStateOf<TimetableData?>(null) }
    var selectedDay by remember { mutableIntStateOf(SemesterCalc.dayOfWeek()) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        semesterDm = settings.currentSemesterDm.first().ifBlank { SemesterCalc.guessSemesterDm() }
        week = SemesterCalc.currentWeek(semesterDm, settings.firstWeekStart.first())
        selectedDay = SemesterCalc.dayOfWeek()
        entities.read("jw.timetable.$semesterDm.w$week", TimetableData.serializer())?.let { timetable = it.data }
        if (jw.isBound()) runCatching { timetable = jw.timetable(semesterDm, week) }
        runCatching { notice = jw.notices().firstOrNull { it.kind == "change" }?.title }
    }

    fun changeWeek(delta: Int) {
        val newWeek = (week + delta).coerceIn(1, 25)
        week = newWeek
        entities.read("jw.timetable.$semesterDm.w$newWeek", TimetableData.serializer())?.let { timetable = it.data }
        if (jw.isBound()) {
            launchUi { runCatching { timetable = jw.timetable(semesterDm, newWeek) } }
        }
    }

    val cells = timetable?.days?.firstOrNull { it.day == selectedDay }?.cells.orEmpty()

    UnionScreenScaffold(
        title = "课表",
        onBack = null,
        actions = {
            IconButton(onClick = onOpenGrades) { Icon(Icons.Rounded.Grade, contentDescription = "成绩") }
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
            Text("第 $week 周 · ${timetable?.semesterDm ?: semesterDm}", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 周次横向切换
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = false, onClick = { changeWeek(-1) }, label = { Text("上一周") })
                FilterChip(selected = true, onClick = {}, label = { Text("第 $week 周") })
                FilterChip(selected = false, onClick = { changeWeek(1) }, label = { Text("下一周") })
                FilterChip(
                    selected = false,
                    onClick = {
                        launchUi {
                            val start = settings.firstWeekStart.first()
                            week = SemesterCalc.currentWeek(semesterDm, start)
                            runCatching { timetable = jw.timetable(semesterDm, week) }
                        }
                    },
                    label = { Text("回到本周") },
                )
            }

            // 星期选择
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEachIndexed { i, label ->
                    val d = i + 1
                    FilterChip(
                        selected = selectedDay == d,
                        onClick = { selectedDay = d },
                        label = { Text(label) },
                    )
                }
            }

            if (notice != null) {
                PrismCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SwapHoriz, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("调课提醒", style = subheadlineStyle(), fontWeight = FontWeight(600), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(notice ?: "", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            when {
                !jw.isBound() && timetable == null -> NullStatus(
                    icon = Icons.Rounded.MenuBook,
                    title = "登录后查看课表",
                    subtitle = "教务账号未绑定：到「我的 → 凭证中心」登录后即可同步",
                )
                cells.isEmpty() -> NullStatus(
                    icon = Icons.Rounded.MenuBook,
                    title = "今天没有课",
                    subtitle = "试试切换其他星期或周次",
                )
                else -> {
                    cells.forEach { cell ->
                        CourseCellCard(cell) { onOpenCourse(cell) }
                    }
                }
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

/** 课程块卡片（按课程名稳定着色）。 */
@Composable
fun CourseCellCard(cell: CourseCell, onClick: () -> Unit) {
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val idx = (cell.name.hashCode().let { if (it < 0) -it else it }) % colors.size
    val bg = colors[idx]
    val onBg = when (idx) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    PrismCard(
        onClick = onClick,
        containerColor = bg,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Text(cell.name, style = subheadlineStyle(), fontWeight = FontWeight(600), color = onBg, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text("${cell.sections} · ${cell.location}", style = footnoteStyle(), color = onBg)
        Text(cell.teacher, style = footnoteStyle(), color = onBg)
        if (cell.weeks.isNotBlank()) {
            Text(cell.weeks, style = footnoteStyle(), color = onBg.copy(alpha = 0.8f))
        }
    }
}
