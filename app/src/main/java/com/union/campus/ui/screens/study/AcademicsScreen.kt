package com.union.campus.ui.screens.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.JwNotice
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TimetableSchedule
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCorner
import com.union.campus.ui.prism.PrismShape
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.prismShape
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
            corner = PrismCorner.Xxl,
            contentPadding = PaddingValues(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    semesterLabel.ifBlank { "当前学期" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = prismShape(PrismCorner.Md),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.School, contentDescription = null)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本周课表与教室",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Text("查看课表", style = footnoteStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        PrismSection(title = "常用功能") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AcademicAction(
                        title = "成绩查询",
                        subtitle = "学分与绩点",
                        icon = Icons.Rounded.Grade,
                        shape = academicActionShape(topStart = PrismCorner.Xl),
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen("grades") },
                    )
                    AcademicAction(
                        title = "考试安排",
                        subtitle = "时间与考场",
                        icon = Icons.Rounded.Event,
                        shape = academicActionShape(topEnd = PrismCorner.Xl),
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen("exams") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AcademicAction(
                        title = "选课",
                        subtitle = "轮次与提交",
                        icon = Icons.Rounded.Assignment,
                        shape = academicActionShape(bottomStart = PrismCorner.Xl),
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen("select") },
                    )
                    AcademicAction(
                        title = "教学评价",
                        subtitle = "待评课程",
                        icon = Icons.Rounded.RateReview,
                        shape = academicActionShape(bottomEnd = PrismCorner.Xl),
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen("evaluation") },
                    )
                }
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

/** 四个入口共用图标底与次级色调；小内角将它们连接成一组，而非四张独立卡片。 */
@Composable
private fun AcademicAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = prismShape(PrismCorner.Md),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = headlineStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Prism 分角 API 使用物理角顺序；入口布局的首尾角随 RTL 一起映射。 */
@Composable
private fun academicActionShape(
    topStart: Float = PrismCorner.Sm,
    topEnd: Float = PrismCorner.Sm,
    bottomEnd: Float = PrismCorner.Sm,
    bottomStart: Float = PrismCorner.Sm,
): Shape {
    val density = LocalDensity.current.density
    val direction = LocalLayoutDirection.current
    return remember(density, direction, topStart, topEnd, bottomEnd, bottomStart) {
        val corners = if (direction == LayoutDirection.Ltr) {
            floatArrayOf(topStart, topEnd, bottomEnd, bottomStart)
        } else {
            floatArrayOf(topEnd, topStart, bottomStart, bottomEnd)
        }
        PrismShape(
            radius = PrismCorner.Md * density,
            corners = FloatArray(4) { corners[it] * density },
        )
    }
}
