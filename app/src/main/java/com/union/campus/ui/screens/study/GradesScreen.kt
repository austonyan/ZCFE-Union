package com.union.campus.ui.screens.study

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.Grade
import com.union.campus.domain.GradesData
import com.union.campus.domain.Semester
import com.union.campus.ui.common.StateContent
import com.union.campus.ui.common.UiState
import com.union.campus.ui.common.dataOrNull
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismMetricCard
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.footnoteStyle
import kotlinx.coroutines.flow.first

/** 成绩：学期 GPA、有效/原始切换、学分进度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    onOpenGrade: (Grade) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    val entities = ServiceLocator.entities

    var semesters by remember { mutableStateOf<List<Semester>>(emptyList()) }
    var semesterDm by remember { mutableStateOf("") }
    var effective by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf<UiState<GradesData>>(UiState.Loading) }

    suspend fun loadInner() {
        state = if (!jw.isBound()) {
            entities.read("jw.grades.$semesterDm.${if (effective) 1 else 0}", GradesData.serializer())
                ?.let { UiState.Ready(it.data, it.updatedAt) }
                ?: UiState.Empty("登录教务后可查看成绩")
        } else {
            try {
                UiState.Ready(jw.grades(semesterDm, effective))
            } catch (e: AppError.AuthExpired) {
                entities.read("jw.grades.$semesterDm.${if (effective) 1 else 0}", GradesData.serializer())
                    ?.let { UiState.Ready(it.data, it.updatedAt) }
                    ?: UiState.Failed(e.userMessage, isAuth = true)
            } catch (e: AppError) {
                UiState.Failed(e.userMessage)
            }
        }
    }

    fun load() {
        state = UiState.Loading
        launchUi { loadInner() }
    }

    LaunchedEffect(Unit) {
        semesterDm = jw.currentSemesterDm()
        if (jw.isBound()) {
            val list = runCatching { jw.semesterList() }.getOrDefault(emptyList())
            semesters = if (list.isNotEmpty()) list else listOf(Semester(semesterDm, semesterDm, true))
            if (semesters.none { it.dm == semesterDm }) {
                semesterDm = semesters.firstOrNull { it.isCurrent }?.dm ?: semesters.first().dm
            }
        } else {
            semesters = listOf(Semester(semesterDm, semesterDm, true))
        }
        load()
    }

    val data = state.dataOrNull()
    val gpa = data?.grades?.let { computeGpa(it) }

    UnionScreenScaffold(
        title = "成绩",
        onBack = onBack,
        actions = {
            PrismIconButton(
                icon = Icons.Outlined.Share,
                contentDescription = "分享",
                onClick = { /* 分享：生成脱敏成绩卡片文本 */ launchUi { shareGrades(gpa, data) } },
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
            if (semesters.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    semesters.take(8).forEach { sem ->
                        PrismChip(
                            label = sem.shortLabel,
                            selected = semesterDm == sem.dm,
                            onClick = { semesterDm = sem.dm; load() },
                        )
                    }
                }
            }

            PrismMetricCard(
                label = "GPA",
                value = gpa?.first ?: "—",
                subtitle = "加权平均 ${gpa?.second ?: "—"} · 已修 ${data?.grades?.size ?: 0} 门课程\n按学校规则估算，以教务为准",
                icon = Icons.Rounded.Grade,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                accent = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrismChip(label = "有效成绩", selected = effective, onClick = { effective = true; load() })
                PrismChip(label = "原始成绩", selected = !effective, onClick = { effective = false; load() })
            }

            StateContent(
                state = state,
                emptyIcon = Icons.Rounded.Grade,
                onRetry = { load() },
            ) { d ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrismGroupedList {
                        d.grades.forEach { g ->
                            PrismRow(
                                title = g.courseName,
                                subtitle = "${g.score} 分 · ${g.gpa.ifBlank { "— " }}绩点 · ${g.credits.ifBlank { "?" }} 学分",
                                leading = { IconBadge(Icons.Rounded.Grade) },
                                onClick = { onOpenGrade(g) },
                            )
                        }
                    }
                    Text(
                        "成绩为客户端估算展示，最终以教务系统为准",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private suspend fun shareGrades(gpa: Pair<String, String>?, data: GradesData?) {
    if (data == null) return
    val text = buildString {
        appendLine("我的学期成绩（学号已隐藏）")
        appendLine("GPA ${gpa?.first} · 加权平均 ${gpa?.second}")
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

/** GPA 计算：4.0 制，绩点按分数段（90+→4.0, 85+→3.7, 82+→3.3, 78+→3.0, 75+→2.7, 72+→2.3, 68+→2.0, 64+→1.5, 60+→1.0）。 */
fun computeGpa(grades: List<Grade>): Pair<String, String> {
    var totalCredit = 0.0
    var weightedPoints = 0.0
    var weightedScore = 0.0
    var validScore = 0.0
    var scoreCredits = 0.0
    grades.forEach { g ->
        val credit = g.credits.toDoubleOrNull() ?: return@forEach
        val score = g.score.toDoubleOrNull()
        val gp = g.gpa.toDoubleOrNull() ?: score?.let { scoreToGp(it) } ?: return@forEach
        totalCredit += credit
        weightedPoints += credit * gp
        if (score != null) {
            validScore += credit * score
            scoreCredits += credit
        }
    }
    val gpa = if (totalCredit > 0) "%.2f".format(weightedPoints / totalCredit) else "—"
    val avg = if (scoreCredits > 0) "%.1f".format(validScore / scoreCredits) else "—"
    return gpa to avg
}

private fun scoreToGp(score: Double): Double = when {
    score >= 90 -> 4.0
    score >= 85 -> 3.7
    score >= 82 -> 3.3
    score >= 78 -> 3.0
    score >= 75 -> 2.7
    score >= 72 -> 2.3
    score >= 68 -> 2.0
    score >= 64 -> 1.5
    score >= 60 -> 1.0
    else -> 0.0
}
