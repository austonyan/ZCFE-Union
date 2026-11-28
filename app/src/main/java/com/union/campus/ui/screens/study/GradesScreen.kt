package com.union.campus.ui.screens.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.Grade
import com.union.campus.domain.GradeBand
import com.union.campus.domain.GpaSummary
import com.union.campus.domain.GradesData
import com.union.campus.domain.Semester
import com.union.campus.domain.computeGpa
import com.union.campus.domain.gradeBandOf
import com.union.campus.ui.common.UiState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.dataOrNull
import com.union.campus.ui.common.formatUpdatedAt
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismCorner
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGpaRuler
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismLoadingIndicator
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.PrismShape
import com.union.campus.ui.prism.PrismSkeleton
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.PrismSpace
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.metricSmallStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.prismShape
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.withAlpha
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 成绩：学期绩点尺、学分账本、按类别分组的课程列表与成绩分布。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    onOpenGrade: (Grade) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    val entities = ServiceLocator.entities

    // 页面状态用 rememberSaveable：从课程详情返回时不会退回默认学期/默认口径。
    var semesters by rememberSaveable(stateSaver = SemesterListSaver) {
        mutableStateOf<List<Semester>>(emptyList())
    }
    var semesterDm by rememberSaveable { mutableStateOf("") }
    var effective by rememberSaveable { mutableStateOf(true) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // 用户手动选过学期后，不再自动跳到"最近一个有成绩的学期"。
    var userPicked by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf<UiState<GradesData>>(UiState.Loading) }
    // 补考标记：只读原始成绩缓存里不及格的课程代码，不额外发网络请求。
    var rawFailCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 已显示内容时，刷新只在顶栏图标转圈，不把整页换成骨架屏。
    val messenger = LocalPrismMessenger.current
    var refreshing by remember { mutableStateOf(false) }

    // 补考标记：每次进入/切换学期都向上游要一次原始成绩，用户不必先手动点「原始成绩」。
    LaunchedEffect(semesterDm, effective) {
        if (!effective || semesterDm.isBlank()) {
            rawFailCodes = emptySet()
            return@LaunchedEffect
        }
        val fromUpstream = if (jw.isBound()) {
            runCatching { jw.grades(semesterDm, effective = false, forceRefresh = true) }.getOrNull()
        } else {
            null
        }
        val raw = fromUpstream ?: entities.read("jw.grades.$semesterDm.0", GradesData.serializer())?.data
        rawFailCodes = raw?.grades
            ?.filter { gradeBandOf(it.score, it.gpa) == GradeBand.Fail }
            ?.map { it.courseCode }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    suspend fun loadInner(force: Boolean) {
        state = if (!jw.isBound()) {
            entities.read("jw.grades.$semesterDm.${if (effective) 1 else 0}", GradesData.serializer())
                ?.let { UiState.Ready(it.data, it.updatedAt) }
                ?: UiState.Empty("在「我的 → 凭证中心」登录教务账号")
        } else {
            try {
                UiState.Ready(jw.grades(semesterDm, effective, forceRefresh = force))
            } catch (e: AppError.AuthExpired) {
                entities.read("jw.grades.$semesterDm.${if (effective) 1 else 0}", GradesData.serializer())
                    ?.let { UiState.Ready(it.data, it.updatedAt) }
                    ?: UiState.Failed(e.userMessage, isAuth = true)
            } catch (e: AppError) {
                UiState.Failed(e.userMessage)
            }
        }
    }

    fun load(force: Boolean = false) {
        state = UiState.Loading
        launchUi {
            loadInner(force)
            // 当前学期还没有成绩时，自动退到最近一个有成绩的学期（只在用户没手动选过时）。
            if (!userPicked && state.dataOrNull()?.grades.isNullOrEmpty() && semesters.size > 1) {
                val from = semesters.indexOfFirst { it.dm == semesterDm }.coerceAtLeast(0)
                var networkTries = 0
                for (candidate in semesters.drop(from + 1)) {
                    val key = "jw.grades.${candidate.dm}.${if (effective) 1 else 0}"
                    val cached = entities.read(key, GradesData.serializer())
                    if (!cached?.data?.grades.isNullOrEmpty()) {
                        semesterDm = candidate.dm
                        state = UiState.Ready(cached.data, cached.updatedAt)
                        // 走到回退学期时如果是用户触发的，同样走一次上游。
                        if (force && jw.isBound()) {
                            runCatching { jw.grades(candidate.dm, effective, forceRefresh = true) }
                                .getOrNull()?.let { state = UiState.Ready(it) }
                        }
                        return@launchUi
                    }
                    if (jw.isBound() && networkTries < 2) {
                        networkTries++
                        val fetched = runCatching { jw.grades(candidate.dm, effective) }.getOrNull()
                        if (!fetched?.grades.isNullOrEmpty()) {
                            semesterDm = candidate.dm
                            state = UiState.Ready(fetched)
                            return@launchUi
                        }
                    }
                }
            }
        }
    }

    /** 顶栏刷新：保留已显示的成绩，只向上游要一次最新数据。 */
    fun refresh() {
        if (refreshing || state is UiState.Loading) return
        refreshing = true
        launchUi {
            try {
                loadInner(force = true)
                when (val current = state) {
                    is UiState.Failed -> messenger.error(current.message)
                    is UiState.Ready -> messenger.success("成绩已刷新")
                    // 空态也要有反馈：未登录说清楚原因，已登录说明这门课还没出分。
                    is UiState.Empty -> messenger.info(
                        if (jw.isBound()) "已刷新，当前学期暂无成绩" else "登录教务后才能同步成绩",
                    )
                    else -> Unit
                }
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (semesterDm.isBlank()) semesterDm = jw.currentSemesterDm()
        if (semesters.isEmpty()) {
            val list = if (jw.isBound()) {
                runCatching { jw.semesterList() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            semesters = if (list.isNotEmpty()) {
                list
            } else {
                listOf(Semester(semesterDm, semesterDm, true))
            }
        }
        if (semesters.none { it.dm == semesterDm }) {
            semesterDm = semesters.firstOrNull { it.isCurrent }?.dm ?: semesters.first().dm
        }
        load(force = false)
    }

    // 首次由上面的 effect 负责；之后每次切学期/切口径都向上游要一次最新数据。
    var firstSelection by remember { mutableStateOf(true) }
    LaunchedEffect(semesterDm, effective) {
        if (semesterDm.isBlank()) return@LaunchedEffect
        if (firstSelection) {
            firstSelection = false
            return@LaunchedEffect
        }
        load(force = true)
    }

    val data = state.dataOrNull()
    val summary = data?.grades?.let { computeGpa(it) }
    val semesterName = semesters.firstOrNull { it.dm == semesterDm }?.name
        ?: semesterDm.ifBlank { "本学期" }

    // 缓存回读时才带更新时间；直接取上游时没有可信时间戳，就不写“xx 更新”。
    val updatedAt = (state as? UiState.Ready)?.updatedAt ?: 0L

    UnionScreenScaffold(
        title = "成绩",
        subtitle = if (updatedAt > 0) "数据 ${formatUpdatedAt(updatedAt)}" else null,
        onBack = onBack,
        modifier = modifier,
        actions = {
            Box {
                PrismIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "刷新成绩",
                    enabled = !refreshing && state !is UiState.Loading,
                    onClick = { refresh() },
                )
                if (refreshing) {
                    PrismLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        size = 18.dp,
                    )
                }
            }
            PrismIconButton(
                icon = Icons.Outlined.Share,
                contentDescription = "分享",
                enabled = !data?.grades.isNullOrEmpty(),
                onClick = { /* 分享：生成脱敏成绩卡片文本 */ launchUi { shareGrades(summary, data) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PrismSpace.Bottom),
            verticalArrangement = Arrangement.spacedBy(PrismSpace.Item),
        ) {
            SemesterRow(
                semesterName = semesterName,
                effective = effective,
                onPickSemester = { if (semesters.isNotEmpty()) sheetOpen = true },
                onToggleMode = {
                    effective = !effective
                    load()
                },
            )

            when (val current = state) {
                UiState.Loading -> {
                    RulerSkeleton()
                    PrismSkeletonList(rows = 3, showHero = false)
                }

                is UiState.Failed -> PrismErrorState(
                    message = current.message,
                    hint = if (current.isAuth) "请在「我的 → 凭证中心」重新登录" else null,
                    onRetry = { load() },
                )

                is UiState.Empty -> NullStatus(
                    icon = Icons.Rounded.Grade,
                    title = "登录教务后查看成绩",
                    subtitle = current.hint,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                is UiState.Ready -> if (current.data.grades.isEmpty()) {
                    NullStatus(
                        icon = Icons.Rounded.Grade,
                        title = "本学期暂无成绩",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    GradesContent(
                        data = current.data,
                        summary = computeGpa(current.data.grades),
                        rawFailCodes = rawFailCodes,
                        onOpenGrade = onOpenGrade,
                    )
                }
            }
        }
    }

    PrismSnapSheet(visible = sheetOpen, onDismiss = { sheetOpen = false }, paneTitle = "选择学期") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("选择学期", style = pageTitleStyle())
            PrismGroupedList {
                semesters.forEach { sem ->
                    PrismRow(
                        title = sem.name,
                        modifier = Modifier.semantics { selected = sem.dm == semesterDm },
                        subtitle = "当前学期".takeIf { sem.isCurrent },
                        onClick = {
                            sheetOpen = false
                            if (sem.dm != semesterDm) {
                                userPicked = true
                                semesterDm = sem.dm
                                load()
                            }
                        },
                        trailing = {
                            if (sem.dm == semesterDm) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 学期选择与成绩口径分开，始终展示两个互斥选项。 */
@Composable
private fun SemesterRow(
    semesterName: String,
    effective: Boolean,
    onPickSemester: () -> Unit,
    onToggleMode: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(prismShape(PrismCorner.Sm))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "选择学期",
                    onClick = onPickSemester,
                )
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = semesterName,
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        PrismSegmented(
            options = listOf("有效成绩", "原始成绩"),
            selectedIndex = if (effective) 0 else 1,
            onSelect = { index -> if ((index == 0) != effective) onToggleMode() },
        )
    }
}

@Composable
private fun RulerSkeleton() {
    PrismSkeleton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(132.dp),
        corner = PrismCorner.Md,
    )
}

@Composable
private fun GradesContent(
    data: GradesData,
    summary: GpaSummary,
    rawFailCodes: Set<String>,
    onOpenGrade: (Grade) -> Unit,
) {
    PrismGpaRuler(
        gpa = summary.gpa.toDoubleOrNull(),
        weightedAverage = summary.weightedAverage,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LedgerRow(summary)
    groupGrades(data.grades).forEach { (title, grades) ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(title) {
                Text(
                    "${grades.size} 门",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrismGroupedList {
                grades.forEach { grade ->
                    GradeRow(
                        grade = grade,
                        makeup = grade.courseCode in rawFailCodes &&
                            gradeBandOf(grade.score, grade.gpa) != GradeBand.Fail,
                        onClick = { onOpenGrade(grade) },
                    )
                }
            }
        }
    }
    GradeDistribution(data.grades)
    Text(
        text = buildString {
            append("成绩为客户端估算，以教务系统为准")
            if (summary.excludedCount > 0) {
                append("\n${summary.excludedCount} 门未计入绩点")
            }
        },
        style = footnoteStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** 学分账本：三列等宽数字 + caption2 标签，不加容器。 */
@Composable
private fun LedgerRow(summary: GpaSummary) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LedgerCell(formatCredits(summary.credits), "学分", Modifier.weight(1f))
        LedgerCell(summary.courseCount.toString(), "门", Modifier.weight(1f))
        LedgerCell(summary.excellentCount.toString(), "门优秀", Modifier.weight(1f))
    }
}

@Composable
private fun LedgerCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = metricSmallStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = caption2Style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** 课程行：3dp 等级色标 + 课名/元信息（可带补考标签）+ 右对齐分数。 */
@Composable
private fun GradeRow(grade: Grade, makeup: Boolean, onClick: () -> Unit) {
    val band = gradeBandOf(grade.score, grade.gpa)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = PrismSpace.RowHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(prismShape(2f))
                .background(bandColor(band)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                grade.courseName,
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    gradeSubtitle(grade, band),
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (makeup) MakeupBadge()
            }
        }
        Text(
            text = grade.score.ifBlank { "—" },
            style = metricSmallStyle(),
            color = if (band == GradeBand.Fail) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/** 补考通过小标签：11dp 主色文字、主色 12% 底、4dp 圆角、左右 5dp 内边距。 */
@Composable
private fun MakeupBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.withAlpha(MakeupBadgeAlpha))
            .clearAndSetSemantics { contentDescription = "补考通过" }
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            "补考",
            style = caption2Style(),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

private const val MakeupBadgeAlpha = 0.12f

/** 成绩分布：按学分加权的堆叠条 + 图例。 */
@Composable
private fun GradeDistribution(grades: List<Grade>) {
    val entries = DistributionBands
        .map { band ->
            band to grades
                .filter { gradeBandOf(it.score, it.gpa) == band }
                .sumOf { it.credits.trim().toDoubleOrNull() ?: 0.0 }
        }
        .filter { it.second > 0.0 }
    if (entries.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        SectionHeader("成绩分布")
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(PrismShape(Float.MAX_VALUE)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEach { (band, credits) ->
                Box(
                    Modifier
                        .weight(credits.toFloat())
                        .fillMaxHeight()
                        .background(bandColor(band)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = entries.joinToString(" · ") { (band, credits) ->
                "${band.label} ${String.format(Locale.ROOT, "%.1f", credits)}"
            },
            style = caption2Style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 等级色：只占语义位，跟着主题预设走。 */
@Composable
private fun bandColor(band: GradeBand): Color = when (band) {
    GradeBand.Excellent -> MaterialTheme.colorScheme.primary
    GradeBand.Good -> MaterialTheme.colorScheme.tertiary
    GradeBand.Medium -> MaterialTheme.colorScheme.onSurfaceVariant
    GradeBand.Pass -> MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Secondary)
    GradeBand.Fail -> MaterialTheme.colorScheme.error
    GradeBand.Unknown -> MaterialTheme.colorScheme.outline
}

private val DistributionBands = listOf(
    GradeBand.Excellent,
    GradeBand.Good,
    GradeBand.Medium,
    GradeBand.Pass,
    GradeBand.Fail,
)

/** 学期列表跨页面返回保存：flat 成 dm/name/isCurrent 三元组。 */
private val SemesterListSaver = listSaver<List<Semester>, String>(
    save = { list -> list.flatMap { listOf(it.dm, it.name, it.isCurrent.toString()) } },
    restore = { flat ->
        flat.chunked(3)
            .filter { it.size == 3 }
            .map { Semester(dm = it[0], name = it[1], isCurrent = it[2].toBoolean()) }
    },
)

private val CategoryOrder = listOf("必修", "选修", "通识", "其他")

/** 按课程类别分组：必修 → 选修 → 通识 → 其他；类别全空时退回单组「全部课程」。 */
private fun groupGrades(grades: List<Grade>): List<Pair<String, List<Grade>>> {
    if (grades.all { it.category.isBlank() }) {
        return listOf("全部课程" to sortGrades(grades))
    }
    return grades
        .groupBy { categoryGroupOf(it.category) }
        .mapValues { (_, list) -> sortGrades(list) }
        .toList()
        .sortedBy { (name, _) ->
            val index = CategoryOrder.indexOf(name)
            if (index >= 0) index else CategoryOrder.lastIndex
        }
}

private fun categoryGroupOf(category: String): String =
    // 教务只有「公共课/任选课」算选修，其余（必修/限选/公共/专业课）统一算必修。
    if (category.contains("任选")) "选修" else "必修"

/** 组内排序：分数降序，同分按学分降序，无法识别的成绩落底。 */
private fun sortGrades(grades: List<Grade>): List<Grade> =
    grades.sortedWith(
        compareByDescending<Grade> { sortScoreOf(it) }
            .thenByDescending { it.credits.trim().toDoubleOrNull() ?: 0.0 },
    )

private fun sortScoreOf(grade: Grade): Double =
    grade.score.trim().toDoubleOrNull() ?: when (gradeBandOf(grade.score, grade.gpa)) {
        GradeBand.Excellent -> 95.0
        GradeBand.Good -> 85.0
        GradeBand.Medium -> 75.0
        GradeBand.Pass -> 65.0
        GradeBand.Fail -> 50.0
        GradeBand.Unknown -> -1.0
    }

/** 元信息：学分 · 绩点 · 等级；空值整段省略。 */
private fun gradeSubtitle(grade: Grade, band: GradeBand): String = buildList {
    grade.credits.trim().takeIf { it.isNotBlank() }?.let { add("$it 学分") }
    grade.gpa.trim().takeIf { it.isNotBlank() }?.let { add("$it 绩点") }
    if (band == GradeBand.Unknown) {
        grade.score.trim().takeIf { it.isNotBlank() }?.let { add(it) }
    } else {
        add(band.label)
    }
}.joinToString(" · ")

private fun formatCredits(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")

private suspend fun shareGrades(summary: GpaSummary?, data: GradesData?) {
    if (data == null) return
    val text = buildString {
        appendLine("学期成绩（学号已隐藏）")
        appendLine("GPA ${summary?.gpa ?: "—"} · 加权平均 ${summary?.weightedAverage ?: "—"}")
        data.grades.forEach { appendLine("${it.courseName}: ${it.score} 分 / ${it.credits} 学分") }
        appendLine("— 来自 郑财校园")
    }
    shareText(text)
}

suspend fun shareText(text: String) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        // 通过剪贴板分享（跨应用分享 intent 需 Activity 上下文，MVP 用剪贴板 + 系统分享入口）
        val clipboard = com.union.campus.UnionApplicationHolder.clipboard
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("union", text))
    }
}
