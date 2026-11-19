package com.union.campus.ui.screens.study

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.union.campus.UnionApplicationHolder
import com.union.campus.domain.CourseCell
import com.union.campus.domain.Exam
import com.union.campus.domain.Grade
import com.union.campus.domain.assessmentText
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCorner
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismNoticeBanner
import com.union.campus.ui.prism.PrismNoticeTone
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismSpec
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.prismCourseContainer
import com.union.campus.ui.prism.prismCourseOnContainer
import com.union.campus.ui.prism.prismShape
import com.union.campus.ui.prism.rememberPrismMotion
import com.union.campus.ui.prism.subheadlineStyle

// ---------------- 课程详情 ----------------

/** 课程详情：课程信息、本周安排、课程说明与操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    cell: CourseCell,
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "课程详情",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        // 只让来源色面板轻微展开，正文与操作保持稳定。
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val motion = rememberPrismMotion()
        val enter by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = motion.spec(PrismSpec.sheetIn),
            label = "course-detail-enter",
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismCard(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    val scale = 0.98f + 0.02f * enter
                    scaleX = scale
                    scaleY = scale
                },
                containerColor = prismCourseContainer(cell.name),
                corner = PrismCorner.Xxl,
                contentPadding = PaddingValues(20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        cell.name,
                        style = pageTitleStyle(),
                        color = prismCourseOnContainer(cell.name),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = prismCourseOnContainer(cell.name),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = prismCourseOnContainer(cell.name),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        cell.weeks.ifBlank { "本学期" },
                        style = subheadlineStyle(),
                        color = prismCourseOnContainer(cell.name),
                    )
                }
            }

            PrismSection(title = "上课安排") {
                PrismGroupedList {
                    PrismRow(
                        title = "上课节次",
                        subtitle = cell.sections.ifBlank { "以教务课表为准" },
                        leading = { CourseDetailIcon(Icons.Rounded.CalendarMonth, cell.name) },
                    )
                    PrismRow(
                        title = "上课地点",
                        subtitle = cell.location.ifBlank { "暂未提供" },
                        leading = { CourseDetailIcon(Icons.Rounded.MeetingRoom, cell.name) },
                    )
                    PrismRow(
                        title = "授课教师",
                        subtitle = cell.teacher.ifBlank { "暂未提供" },
                        leading = { CourseDetailIcon(Icons.Rounded.Person, cell.name) },
                    )
                }
                Text(
                    "来自教务课程表，与课表中的课程颜色对应",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (cell.note.isNotBlank()) {
                PrismCard {
                    CardHeader("课程说明")
                    Spacer(Modifier.height(8.dp))
                    Text(cell.note, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            PrismButton(onClick = onOpenTimetable, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Group, null)
                Spacer(Modifier.width(8.dp))
                Text("查看同班课表")
            }
            PrismButton(
                filled = false,
                onClick = { addToCalendar(cell.name, "${cell.weeks} ${cell.sections} ${cell.location}") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text("加入日历")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CourseDetailIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, courseName: String) {
    Surface(
        shape = prismShape(PrismCorner.Md),
        color = prismCourseContainer(courseName),
        contentColor = prismCourseOnContainer(courseName),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** 系统日历插入（无 WRITE_CALENDAR 权限时跳转日历应用）。 */
fun addToCalendar(title: String, detail: String) {
    val ctx = UnionApplicationHolder.appContext ?: return
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = android.provider.CalendarContract.Events.CONTENT_URI
        putExtra(android.provider.CalendarContract.Events.TITLE, title)
        putExtra(android.provider.CalendarContract.Events.DESCRIPTION, detail)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }
}

// ---------------- 成绩详情 ----------------

/** 成绩详情：单门成绩与绩点解释。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeDetailScreen(
    grade: Grade,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "成绩详情",
        onBack = onBack,
        modifier = modifier,
        actions = {
            PrismIconButton(
                icon = Icons.Outlined.Share,
                contentDescription = "分享",
                onClick = {
                    val text = "${grade.courseName}: ${grade.score} 分 / ${grade.credits} 学分（学号已隐藏）— 来自 郑财校园"
                    UnionApplicationHolder.clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("union", text))
                },
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
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Text(grade.courseName, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${grade.score} 分 · ${grade.gpa.ifBlank { "—" }} 绩点 · ${grade.credits.ifBlank { "?" }} 学分",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            PrismGroupedList {
                PrismRow(title = "课程性质", subtitle = grade.category.ifBlank { "—" })
                PrismRow(title = "考核方式", subtitle = grade.assessmentText().ifBlank { "—" })
            }

            if (grade.score.toDoubleOrNull()?.let { it < 60 } == true) {
                PrismNoticeBanner(
                    title = "该课程低于 60 分",
                    message = "如需补考/重修，请到教务事务中查看申请入口",
                    tone = PrismNoticeTone.Error,
                    icon = Icons.Rounded.Info,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 考试 ----------------

/** 考试：最近考试倒计时、全部考试、冲突提醒、加入日历。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    exams: List<Exam>,
    loading: Boolean,
    error: String?,
    isAuth: Boolean,
    onRetry: () -> Unit,
    onOpenExam: (Exam) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "考试",
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
            val conflict = findExamConflict(exams)
            if (conflict != null) {
                PrismNoticeBanner(
                    title = "冲突提醒",
                    message = "$conflict 请与教务确认",
                    tone = PrismNoticeTone.Warning,
                )
            }

            when {
                loading -> PrismLoadingBlock(label = "正在加载考试安排")
                error != null -> PrismErrorState(
                    message = error,
                    hint = if (isAuth) "可到「我的 → 凭证中心」重新登录" else null,
                    onRetry = onRetry,
                )
                exams.isEmpty() -> com.union.campus.ui.prism.NullStatus(
                    icon = Icons.Rounded.Event,
                    title = "暂未发布考试安排",
                    subtitle = "考试安排通常在考前 2–4 周发布",
                )
                else -> {
                    val first = exams.first()
                    PrismCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    ) {
                        Text(first.courseName, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${first.time}\n${first.location}" + (if (first.seatNo.isNotBlank()) " · 座位 ${first.seatNo}" else ""),
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    PrismSection(title = "全部考试") {
                        PrismGroupedList {
                            exams.forEach { exam ->
                                PrismRow(
                                    title = exam.courseName,
                                    subtitle = "${exam.time} · ${exam.location}",
                                    leading = { IconBadge(Icons.Rounded.Event) },
                                    onClick = { onOpenExam(exam) },
                                )
                            }
                        }
                    }
                    PrismButton(
                        onClick = {
                            exams.forEach { addToCalendar(it.courseName, "${it.time} ${it.location} ${it.seatNo}") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null)
                        Spacer(Modifier.width(8.dp))
                        Text("加入日历")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 检测同日时间重叠的考试。 */
fun findExamConflict(exams: List<Exam>): String? {
    for (i in exams.indices) for (j in i + 1 until exams.size) {
        val a = exams[i]; val b = exams[j]
        if (a.time == b.time && a.time.isNotBlank()) return "「${a.courseName}」与「${b.courseName}」时间重叠，"
    }
    return null
}

/** 考试详情：时间、地点、座位、形式与提醒。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(
    exam: Exam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "考试详情",
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
                Text(exam.courseName, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("${exam.time}\n${exam.location}" + (if (exam.seatNo.isNotBlank()) " · 座位 ${exam.seatNo}" else ""), style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            PrismSection(title = "考试信息") {
                PrismGroupedList {
                    PrismRow(title = "考试地点", subtitle = exam.location, leading = { IconBadge(Icons.Rounded.Info) })
                    PrismRow(title = "考试性质", subtitle = exam.note.ifBlank { "以教务通知为准" }, leading = { IconBadge(Icons.Rounded.Info) })
                    PrismRow(title = "座位号", subtitle = exam.seatNo.ifBlank { "—" }, leading = { IconBadge(Icons.Rounded.Info) })
                }
            }
            PrismCard {
                CardHeader("考试提醒")
                Spacer(Modifier.height(6.dp))
                Text("已设置 7 天 / 1 天 / 2 小时提醒（可在提醒中心关闭）", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrismButton(
                onClick = { addToCalendar(exam.courseName, "${exam.time} ${exam.location} ${exam.seatNo}") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text("加入日历")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
