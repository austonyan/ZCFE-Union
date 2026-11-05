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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UiState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.dataOrNull
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel

// ---------------- 选课 ----------------

data class SelectableCourse(
    val id: String,
    val name: String,
    val credit: String,
    val teacher: String,
    val time: String,
)

/** 选课：轮次、可选课程、已选统计与提交。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSelectScreen(
    onBack: () -> Unit,
    onConfirm: (SelectableCourse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var state by remember { mutableStateOf<UiState<List<SelectableCourse>>>(UiState.Loading) }
    var selected by remember { mutableStateOf(setOf<SelectableCourse>()) }

    LaunchedEffect(Unit) {
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
            if (list.isEmpty()) UiState.Empty("当前没有开放的可选课程或选课未开始") else UiState.Ready(list)
        } catch (e: AppError.AuthExpired) {
            UiState.Failed(e.userMessage, isAuth = true)
        } catch (e: AppError) {
            UiState.Failed(e.userMessage)
        } catch (e: Exception) {
            UiState.Failed("选课信息获取失败：${e.message}")
        }
    }

    val totalCredit = selected.sumOf { it.credit.toDoubleOrNull() ?: 0.0 }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("选课", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Rounded.HowToVote, contentDescription = "轮次") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            when (val s = state) {
                is UiState.Loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp))
                is UiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.message, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.isAuth) Text("可到「我的 → 凭证中心」重新登录", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is UiState.Empty -> NullStatus(Icons.Rounded.HowToVote, "选课未开放", s.hint)
                is UiState.Ready -> {
                    Text("可选课程", style = headlineStyle())
                    PrismGroupedList {
                        s.data.forEachIndexed { i, c ->
                            PrismRow(
                                title = c.name,
                                subtitle = "${c.credit} 学分 · ${c.teacher.ifBlank { "—" }} · ${c.time.ifBlank { "时间待定" }}",
                                index = i, count = s.data.size,
                                leading = { IconBadge(when (i % 3) { 0 -> Icons.Rounded.Storage; 1 -> Icons.Rounded.SmartToy; else -> Icons.Rounded.Movie }) },
                                trailing = {
                                    Icon(
                                        Icons.Rounded.Check, null,
                                        tint = if (selected.contains(c)) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.width(24.dp),
                                    )
                                },
                                onClick = {
                                    selected = if (selected.contains(c)) selected - c else selected + c
                                },
                            )
                        }
                    }
                    if (selected.isNotEmpty()) {
                        PrismCard {
                            CardHeader("已选课程")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                selected.joinToString("、") { it.name } + " · 共 ${"%.1f".format(totalCredit)} 学分",
                                style = subheadlineStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    PrismButton(
                        onClick = { onConfirm(selected.firstOrNull() ?: return@PrismButton) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("提交选课")
                    }
                    Text("选课开放状态以教务为准", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 选课确认 ----------------

/** 确认选课：提交前检查（冲突/学分/轮次）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSelectionScreen(
    course: SelectableCourse,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var submitting by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        submitting = true
        launchUi {
            try {
                // 写接口：Tjzxkc_wsxk（结果以教务为准，不自动重试）
                jw.rawCall(
                    "Tjzxkc_wsxk",
                    mapOf("xktype" to "1", "njzy" to "", "sellx" to "", "xh" to ""),
                )
                submitting = false
                result = "选课请求已提交，结果以教务系统为准。请稍后在「已选结果」中确认。"
            } catch (e: AppError) {
                submitting = false
                result = "提交失败：${e.userMessage}"
            } catch (e: Exception) {
                submitting = false
                result = "提交失败：${e.message}"
            }
        }
    }

    if (result != null) {
        PrismAlertDialog(
            title = "提交结果",
            body = result ?: "",
            confirmText = "返回选课",
            dismissText = null,
            onConfirm = { result = null; onSubmitted() },
            onDismiss = { result = null },
        )
    }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("确认选课", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text(course.name, style = com.union.campus.ui.prism.pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("${course.credit} 学分 · ${course.teacher}\n${course.time.ifBlank { "时间待定" }}", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("提交前检查", style = headlineStyle())
            PrismGroupedList {
                PrismRow(title = "课程冲突", subtitle = "以教务校验结果为准", index = 0, count = 3, leading = { IconBadge(Icons.Rounded.Check) })
                PrismRow(title = "学分上限", subtitle = "本门 ${course.credit} 学分", index = 1, count = 3, leading = { IconBadge(Icons.Rounded.Storage) })
                PrismRow(title = "选课轮次", subtitle = "以教务当前开放轮次为准", index = 2, count = 3, leading = { IconBadge(Icons.Rounded.HowToVote) })
            }
            PrismButton(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
            ) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(if (submitting) "提交中…" else "确认提交")
            }
            PrismTextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回修改")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 教学评价 ----------------

/** 教学评价：待评课程、三项评分、主观意见与提交。 */
@OptIn(ExperimentalMaterial3Api::class)
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
            error = "评教信息获取失败：${e.message}"
        }
        loading = false
    }

    if (confirmSubmit) {
        PrismAlertDialog(
            title = "提交评教",
            body = "确认提交对「${current?.courseName ?: "课程"}」的评价？提交后以教务结果为准。",
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
                        result = "已提交，评教结果仅在教务系统生效。"
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
            confirmText = "知道了",
            dismissText = null,
            onConfirm = { result = null },
            onDismiss = { result = null },
        )
    }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("教学评价", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            when {
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp))
                error != null -> Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                pending.isEmpty() -> NullStatus(Icons.Rounded.Star, "当前没有进行中的评教", "评教开放时会显示在这里")
                else -> {
                    Text("待评 ${pending.size} 门 · 截止以教务为准", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pending.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pending.forEach { p ->
                                FilterChip(
                                    selected = current == p,
                                    onClick = { current = p },
                                    label = { Text(p.courseName) },
                                )
                            }
                        }
                    }
                    current?.let { cur ->
                        Text("课程：${cur.courseName} · ${cur.teacher.ifBlank { "教师未标注" }}", style = headlineStyle())
                        SliderQuestion("课堂组织", q1) { q1 = it }
                        SliderQuestion("教学态度", q2) { q2 = it }
                        SliderQuestion("收获程度", q3) { q3 = it }
                        androidx.compose.material3.OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text("主观意见（选填）") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrismButton(
                            onClick = { confirmSubmit = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !submitting,
                        ) {
                            Text(if (submitting) "提交中…" else "提交评教")
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
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}
