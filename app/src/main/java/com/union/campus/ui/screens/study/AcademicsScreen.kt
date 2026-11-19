package com.union.campus.ui.screens.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.JwNotice
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TimetableSchedule
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismEntryCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.prismTextMuted
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.flow.first

/** 教务首页：常用功能、教务事务与最近通知。 */
@Composable
fun AcademicsScreen(
    onOpen: (String) -> Unit,
    onOpenNotice: (JwNotice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    val settings = ServiceLocator.settings
    val entities = ServiceLocator.entities

    var semesterLabel by remember { mutableStateOf("") }
    var week by remember { mutableIntStateOf(1) }
    var courseCount by remember { mutableIntStateOf(0) }
    var notices by remember { mutableStateOf<List<JwNotice>>(emptyList()) }

    LaunchedEffect(Unit) {
        val dm = jw.currentSemesterDm()
        semesterLabel = runCatching { jw.semesterList() }
            .getOrDefault(emptyList())
            .firstOrNull { it.dm == dm }?.name
            ?: dm
        week = SemesterCalc.currentWeek(dm, settings.firstWeekStart.first())
        courseCount = entities.read("jw.timetable.$dm.w$week", TimetableData.serializer())
            ?.data?.days?.sumOf { day ->
                day.cells.count {
                    it.startSection <= TimetableSchedule.MAX_SECTION && it.endSection >= 1
                }
            } ?: 0
        if (jw.isBound()) {
            notices = runCatching { jw.notices() }.getOrDefault(emptyList()).take(3)
        }
    }

    Column(
        modifier
            .pageWidth()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrismPageHeader(
            title = "教务",
            subtitle = listOfNotNull(
                "第 $week 周",
                "本周 $courseCount 节课".takeIf { courseCount > 0 },
            ).joinToString(" · "),
        )

        PrismCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpen("timetable") },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentPadding = PaddingValues(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        semesterLabel.ifBlank { "当前学期" },
                        style = headlineStyle(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "本周课表、教室与调课提醒",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    "看课表",
                    style = footnoteStyle(),
                    color = prismTextMuted(MaterialTheme.colorScheme.onPrimaryContainer),
                )
            }
        }

        PrismSection(title = "常用功能") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrismEntryCard(
                    title = "成绩查询",
                    subtitle = "学分与绩点",
                    icon = Icons.Rounded.Grade,
                    compact = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen("grades") },
                )
                PrismEntryCard(
                    title = "考试安排",
                    subtitle = "时间与考场",
                    icon = Icons.Rounded.Event,
                    compact = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen("exams") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrismEntryCard(
                    title = "选课",
                    subtitle = "轮次与提交",
                    icon = Icons.Rounded.Assignment,
                    compact = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen("select") },
                )
                PrismEntryCard(
                    title = "教学评价",
                    subtitle = "待评课程",
                    icon = Icons.Rounded.RateReview,
                    compact = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen("evaluation") },
                )
            }
        }

        PrismSection(title = "教务事务") {
            PrismGroupedList {
                PrismRow(
                    title = "学籍档案",
                    subtitle = "姓名、院系、专业与学籍状态",
                    leading = { IconBadge(Icons.Rounded.Badge) },
                    onClick = { onOpen("record") },
                )
                PrismRow(
                    title = "教材确认",
                    subtitle = "待确认教材与教材对账",
                    leading = { IconBadge(Icons.Rounded.MenuBook) },
                    onClick = { onOpen("textbooks") },
                )
                PrismRow(
                    title = "空闲教室",
                    subtitle = "按教学楼查询可自习教室",
                    leading = { IconBadge(Icons.Rounded.MeetingRoom) },
                    onClick = { onOpen("freeClassrooms") },
                )
                PrismRow(
                    title = "教务通知",
                    subtitle = "新闻、公告、调课与停课",
                    leading = { IconBadge(Icons.Rounded.Campaign) },
                    onClick = { onOpen("notices") },
                )
                PrismRow(
                    title = "全部教务事务",
                    subtitle = "异动、免修、预约与问卷",
                    leading = { IconBadge(Icons.Rounded.ListAlt) },
                    onClick = { onOpen("affairs") },
                )
            }
        }

        if (notices.isNotEmpty()) {
            PrismSection(
                title = "最近通知",
                action = {
                    PrismTextButton(onClick = { onOpen("notices") }) { Text("全部") }
                },
            ) {
                PrismGroupedList {
                    notices.forEach { notice ->
                        PrismRow(
                            title = notice.title,
                            subtitle = "${notice.date} · ${notice.source}",
                            leading = {
                                IconBadge(
                                    when (notice.kind) {
                                        "change" -> Icons.Rounded.SwapHoriz
                                        "news" -> Icons.Rounded.Campaign
                                        else -> Icons.Rounded.Assignment
                                    },
                                )
                            },
                            onClick = { onOpenNotice(notice) },
                        )
                    }
                }
            }
        }

        Text(
            "成绩、考试与通知以教务系统为准",
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}