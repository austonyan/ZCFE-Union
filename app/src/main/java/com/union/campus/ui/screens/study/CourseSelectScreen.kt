package com.union.campus.ui.screens.study

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UiState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismSlider
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle

// ---------------- 选课 ----------------

data class SelectableCourse(
    val id: String,
    val name: String,
    val credit: String,
    val teacher: String,
    val time: String,
)

/** 选课：单选课程后查看预览，再交由官方系统完成选课。 */
@Composable
fun CourseSelectScreen(
    onBack: () -> Unit,
    onConfirm: (SelectableCourse) -> Unit,
    onOpenAcademicPortal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var state by remember { mutableStateOf<UiState<List<SelectableCourse>>>(UiState.Loading) }
    var selectedCourseId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!jw.isBound()) {
            state = UiState.Empty("登录教务后查看可选课程")
            return@LaunchedEffect
        }
        state = try {
            // 可选课程：Zxkc_wsxk（未开放时为空 → 空态）
            val element = jw.rawCall(
                "Zxkc_wsxk",
                mapOf("xktype" to "1", "njzy" to "", "sellx" to ""),
            )
            val arr = com.union.campus.data.repo.Parse.array(element)
            val list = com.union.campus.data.repo.Parse.objects(arr).mapIndexed { i, c ->
                SelectableCourse(
                    id = com.union.campus.data.repo.Parse.str(c, "kcid", "kcdm", "id").ifBlank { "kc_$i" },
                    name = com.union.campus.data.repo.Parse.str(c, "kcmc", "name").ifBlank { "课程 $i" },
                    credit = com.union.campus.data.repo.Parse.str(c, "xf", "credit"),
                    teacher = com.union.campus.data.repo.Parse.str(c, "jsmc", "teacher"),
                    time = com.union.campus.data.repo.Parse.str(c, "sksj", "time"),
                )
            }
            if (list.isEmpty()) UiState.Empty("当前未获取到可选课程") else UiState.Ready(list)
        } catch (e: AppError.AuthExpired) {
            UiState.Failed(e.userMessage, isAuth = true)
        } catch (e: AppError) {
            UiState.Failed(e.userMessage)
        } catch (e: Exception) {
            UiState.Failed("选课加载失败：${e.message}")
        }
    }

    UnionScreenScaffold(
        title = "选课",
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
            when (val s = state) {
                is UiState.Loading -> PrismLoadingBlock(label = "可选课程加载中")
                is UiState.Failed -> PrismErrorState(
                    message = s.message,
                    hint = "请在「我的 → 凭证中心」重新登录".takeIf { s.isAuth },
                )
                is UiState.Empty -> NullStatus(Icons.Rounded.HowToVote, s.hint)
                is UiState.Ready -> {
                    val selectedCourse = s.data.firstOrNull { it.id == selectedCourseId }
                    PrismSection(title = "选择课程") {
                        PrismGroupedList(modifier = Modifier.selectableGroup()) {
                            s.data.forEachIndexed { i, c ->
                                PrismRow(
                                    title = c.name,
                                    modifier = Modifier.selectable(
                                        selected = c.id == selectedCourseId,
                                        role = Role.RadioButton,
                                        onClick = { selectedCourseId = c.id },
                                    ),
                                    subtitle = "${c.credit} 学分 · ${c.teacher.ifBlank { "—" }} · ${c.time.ifBlank { "时间待定" }}",
                                    leading = { IconBadge(when (i % 3) { 0 -> Icons.Rounded.Storage; 1 -> Icons.Rounded.SmartToy; else -> Icons.Rounded.Movie }) },
                                    trailing = {
                                        RadioButton(
                                            selected = c.id == selectedCourseId,
                                            onClick = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    PrismButton(
                        onClick = { onConfirm(selectedCourse ?: return@PrismButton) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedCourse != null,
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("查看课程")
                    }
                }
            }
            PrismButton(
                onClick = onOpenAcademicPortal,
                filled = state !is UiState.Ready<*>,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("打开教务系统")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 课程预览与官方系统交接 ----------------

/** 课程确认预览；写接口尚未建立课程标识映射，不发送无法对应所选课程的请求。 */
@Composable
fun ConfirmSelectionScreen(
    course: SelectableCourse,
    onBack: () -> Unit,
    onOpenAcademicPortal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "课程预览",
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
                Text(course.name, style = com.union.campus.ui.prism.pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                val details = listOfNotNull(
                    course.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
                    course.teacher.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(listOf(details, course.time.ifBlank { "时间待定" }).filter { it.isNotBlank() }.joinToString("\n"), style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            PrismButton(
                onClick = onOpenAcademicPortal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("打开教务系统")
            }
            Text("请在教务系统核对并完成选课。", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrismTextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回选课")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 教学评价 ----------------

/** 教学评价：待评课程、三项评分、主观意见与提交。 */
@Composable
fun EvaluationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var pending by remember { mutableStateOf<List<com.union.campus.domain.EvaluationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<com.union.campus.domain.EvaluationItem?>(null) }
    var q1 by rememberSaveable { mutableFloatStateOf(0.9f) }
    var q2 by rememberSaveable { mutableFloatStateOf(0.95f) }
    var q3 by rememberSaveable { mutableFloatStateOf(0.85f) }
    var comment by rememberSaveable { mutableStateOf("") }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var confirmSubmit by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            pending = jw.pendingEvaluations()
            current = pending.firstOrNull()
            error = null
        } catch (e: AppError) {
            error = e.userMessage
        } catch (e: Exception) {
            error = "评教加载失败：${e.message}"
        }
        loading = false
    }

    if (confirmSubmit) {
        PrismAlertDialog(
            title = "提交评教",
            body = "确认提交「${current?.courseName ?: "课程"}」评价？结果以教务为准。",
            confirmText = "确认提交",
            onConfirm = {
                confirmSubmit = false
                submitting = true
                launchUi {
                    try {
                        jw.submitEvaluation(
                            mapOf(
                                "hidekey" to (current?.hideKey ?: ""),
                                "xnxq" to (current?.xnxq ?: ""),
                                "score1" to ((q1 * 100).toInt().toString()),
                                "score2" to ((q2 * 100).toInt().toString()),
                                "score3" to ((q3 * 100).toInt().toString()),
                                "comment" to comment,
                            ),
                        )
                        submitting = false
                        result = "已提交，结果仅在教务系统生效。"
                    } catch (e: AppError) {
                        submitting = false
                        result = "提交失败：${e.userMessage}（草稿已保留）"
                    } catch (e: Exception) {
                        submitting = false
                        result = "提交失败：${e.message}"
                    }
                }
            },
            onDismiss = { confirmSubmit = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(
            title = "评教结果",
            body = result ?: "",
            confirmText = "确定",
            dismissText = null,
            onConfirm = { result = null },
            onDismiss = { result = null },
        )
    }

    UnionScreenScaffold(
        title = "教学评价",
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
            when {
                loading -> PrismLoadingBlock(label = "评教加载中")
                error != null -> Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                pending.isEmpty() -> NullStatus(Icons.Rounded.Star, "暂无进行中的评教", "评教开放后显示")
                else -> {
                    Text("待评 ${pending.size} 门 · 截止以教务为准", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pending.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pending.forEach { p ->
                                PrismChip(
                                    label = p.courseName,
                                    selected = current == p,
                                    onClick = { current = p },
                                )
                            }
                        }
                    }
                    current?.let { cur ->
                        Text("课程：${cur.courseName} · ${cur.teacher.ifBlank { "教师未标注" }}", style = headlineStyle())
                        SliderQuestion("课堂组织", q1) { q1 = it }
                        SliderQuestion("教学态度", q2) { q2 = it }
                        SliderQuestion("收获程度", q3) { q3 = it }
                        PrismTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = "主观意见（选填）",
                            singleLine = false,
                            minLines = 3,
                            maxLines = 6,
                        )
                        PrismButton(
                            onClick = { confirmSubmit = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !submitting,
                            loading = submitting,
                        ) {
                            Text("提交评教")
                        }
                        Text("评教结果仅在教务系统生效", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SliderQuestion(title: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = subheadlineStyle())
            Spacer(Modifier.weight(1f))
            Text("${(value * 100).toInt()} 分", style = subheadlineStyle(), color = MaterialTheme.colorScheme.primary)
        }
        PrismSlider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}
