package com.union.campus.ui.screens.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.data.repo.decodeEvaluationMultiAnswer
import com.union.campus.data.repo.EvaluationSubmissionRejected
import com.union.campus.data.repo.encodeEvaluationMultiAnswer
import com.union.campus.data.repo.validateEvaluationAnswers
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.EvaluationCategory
import com.union.campus.domain.EvaluationForm
import com.union.campus.domain.EvaluationItem
import com.union.campus.domain.EvaluationQuestion
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.IconBadge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle

@Composable
fun EvaluationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isBound: () -> Boolean = { ServiceLocator.jwRepository.isBound() },
    loadCourses: suspend () -> List<EvaluationItem> = { ServiceLocator.jwRepository.pendingEvaluations() },
    loadForm: suspend (EvaluationItem) -> EvaluationForm = { ServiceLocator.jwRepository.evaluationQuestions(it) },
    submit: suspend (EvaluationItem, EvaluationForm, Map<String, String>) -> Unit = { item, form, answers ->
        ServiceLocator.jwRepository.submitEvaluation(item, form, answers)
    },
) {
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf<List<EvaluationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var listError by remember { mutableStateOf<String?>(null) }
    var currentCourse by remember { mutableStateOf<EvaluationItem?>(null) }
    var form by remember { mutableStateOf<EvaluationForm?>(null) }
    var formReloadNonce by remember { mutableIntStateOf(0) }
    var formLoading by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var answerDrafts by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submissionUncertain by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<String?>(null) }

    fun refreshCourses() {
        loading = true
        listError = null
        scope.launch {
            try {
                if (!isBound()) {
                    courses = emptyList()
                    listError = "请先绑定教务账号"
                } else {
                    courses = loadCourses()
                    submissionUncertain = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                listError = e.userMessage
            } catch (e: Exception) {
                listError = "评教列表加载失败：${e.message ?: "请重试"}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshCourses() }

    LaunchedEffect(currentCourse?.courseKey, formReloadNonce) {
        val item = currentCourse ?: run {
            form = null
            formError = null
            formLoading = false
            return@LaunchedEffect
        }
        form = null
        formError = null
        formLoading = true
        try {
            form = loadForm(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppError) {
            formError = e.userMessage
        } catch (e: Exception) {
            formError = "问卷加载失败：${e.message ?: "请重试"}"
        } finally {
            formLoading = false
        }
    }

    if (confirmSubmit) {
        val item = currentCourse
        PrismAlertDialog(
            title = "提交前确认",
            body = "提交「${item?.courseName.orEmpty()}」的评价？提交后通常无法修改。",
            confirmText = "确认提交",
            onConfirm = {
                confirmSubmit = false
                val currentForm = form
                val course = currentCourse
                if (currentForm != null && course != null && !submitting && !submissionUncertain) {
                    submitting = true
                    scope.launch {
                        try {
                            submit(
                                course,
                                currentForm,
                                answerDraftsFor(course, currentForm, answerDrafts),
                            )
                            answerDrafts = answerDrafts.filterKeys { !it.startsWith("${course.courseKey}\u001e") }
                            courses = courses.map { if (it == course) it.copy(courseStatusCode = "1") else it }
                            currentCourse = null
                            submissionUncertain = false
                            submitResult = try {
                                courses = loadCourses()
                                "评价已提交，教务已返回成功结果。"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                "评价已提交；列表刷新失败，请重新打开评教列表核对状态。"
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: AppError.Network) {
                            submissionUncertain = true
                            submitResult = "提交状态暂无法确认。请刷新待评列表核对，避免直接重复提交。"
                        } catch (e: EvaluationSubmissionRejected) {
                            submitResult = e.message ?: "教务未接受本次评价"
                        } catch (e: AppError.Validation) {
                            submitResult = e.userMessage
                        } catch (e: AppError) {
                            submissionUncertain = true
                            submitResult = "提交状态暂无法确认（${e.userMessage}）。请刷新待评列表核对，避免直接重复提交。"
                        } catch (e: Exception) {
                            submissionUncertain = true
                            submitResult = "提交状态暂无法确认。请刷新待评列表核对，避免直接重复提交。"
                        } finally {
                            submitting = false
                        }
                    }
                }
            },
            onDismiss = { confirmSubmit = false },
        )
    }

    submitResult?.let { message ->
        PrismAlertDialog(
            title = "评教结果",
            body = message,
            confirmText = "确定",
            dismissText = null,
            onConfirm = { submitResult = null },
            onDismiss = { submitResult = null },
        )
    }

    fun goBack() {
        if (submitting) return
        if (currentCourse == null) onBack() else {
            currentCourse = null
            if (submissionUncertain) refreshCourses()
        }
    }
    BackHandler(enabled = currentCourse != null || submitting) { goBack() }
    UnionScreenScaffold(
        title = "教学评价",
        onBack = ::goBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(key(currentCourse?.courseKey) { rememberScrollState() })
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val item = currentCourse
            if (item == null) {
                when {
                    loading -> PrismLoadingBlock(label = "评教列表加载中")
                    listError != null -> {
                        PrismErrorState(message = listError ?: "评教列表加载失败")
                        PrismButton(onClick = ::refreshCourses, modifier = Modifier.fillMaxWidth()) { Text("重新加载") }
                    }
                    courses.isEmpty() -> NullStatus(Icons.Rounded.Star, "暂无评教课程", "当前没有可评或可查看的课程")
                    else -> {
                        Text(
                            "待评 ${courses.count { it.isPending }} 门 · 共 ${courses.size} 门",
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        courses.groupBy { it.roundKey }.values.forEach { roundCourses ->
                            val round = roundCourses.first()
                            val heading = listOf(round.semesterName, round.roundName)
                                .filter(String::isNotBlank).joinToString(" · ").ifBlank { "教学评价" }
                            PrismSection(title = heading) {
                                if (round.startsAt.isNotBlank() || round.endsAt.isNotBlank()) {
                                    Text(
                                        listOf(round.startsAt, round.endsAt).filter(String::isNotBlank).joinToString(" — "),
                                        style = subheadlineStyle(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                PrismGroupedList {
                                    roundCourses.sortedBy { it.courseStatusCode }.forEach { course ->
                                        val status = if (course.isPending) "评价" else "查看"
                                        val subtitle = listOf(
                                            course.teacher,
                                            course.credits.takeIf(String::isNotBlank)?.let { "$it 学分" },
                                            course.evaluationType,
                                        ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
                                        PrismRow(
                                            title = course.courseName,
                                            subtitle = subtitle,
                                            leading = { IconBadge(Icons.Rounded.AssignmentTurnedIn) },
                                            trailing = { Text(status, color = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                form = null
                                                formError = null
                                                formLoading = true
                                                currentCourse = course
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                PrismTextButton(
                    onClick = ::goBack,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("返回评教列表") }

                PrismCard(modifier = Modifier.fillMaxWidth()) {
                    Text(item.courseName, style = pageTitleStyle())
                    val metadata = listOfNotNull(
                        item.credits.takeIf(String::isNotBlank)?.let { "$it 学分" },
                        item.evaluationType.takeIf(String::isNotBlank),
                        item.teacher.takeIf(String::isNotBlank),
                    ).joinToString(" · ")
                    if (metadata.isNotBlank()) Text(metadata, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOf(item.semesterName, item.roundName).filter(String::isNotBlank).joinToString(" · "),
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.startsAt.isNotBlank() || item.endsAt.isNotBlank()) {
                        Text(
                            listOf(item.startsAt, item.endsAt).filter(String::isNotBlank).joinToString(" — "),
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when {
                    formLoading -> PrismLoadingBlock(label = "评价问卷加载中")
                    formError != null -> {
                        PrismErrorState(message = formError ?: "问卷加载失败")
                        PrismButton(
                            onClick = { formReloadNonce++ },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("重新加载问卷") }
                    }
                    form == null || form?.formats.isNullOrEmpty() ->
                        NullStatus(Icons.Rounded.Star, "教务未返回评价题目", "请稍后刷新评教列表")
                    else -> {
                        val currentForm = form!!
                        val answers = answerDraftsFor(item, currentForm, answerDrafts)
                        val editable = item.isPending && currentForm.statusCode == "0"
                        currentForm.formats.forEach { format ->
                            if (format.title.isNotBlank()) Text(format.title, style = headlineStyle())
                            format.categories.forEach { category ->
                                EvaluationCategoryEditor(
                                    category = category,
                                    answers = answers,
                                    enabled = editable && !submitting,
                                    onAnswerChange = { question, answer ->
                                        answerDrafts = answerDrafts + (answerKey(item, question) to answer)
                                    },
                                )
                            }
                        }
                        if (!editable) {
                            Text("此课程已提交或当前不可修改。", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val validation = validateEvaluationAnswers(currentForm, answers)
                            if (validation != null) {
                                Text(validation, style = footnoteStyle(), color = MaterialTheme.colorScheme.error)
                            }
                            if (submissionUncertain) {
                                Text(
                                    "上次提交结果未确认，请先回到列表刷新状态，不要直接重复提交。",
                                    style = footnoteStyle(),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                PrismButton(
                                    onClick = {
                                        currentCourse = null
                                        refreshCourses()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("刷新待评列表") }
                            } else {
                                PrismButton(
                                    onClick = { confirmSubmit = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !submitting && validation == null,
                                    loading = submitting,
                                ) {
                                    Text("提交评价")
                                }
                            }
                            Text("提交后通常无法修改，请核对全部选项。", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EvaluationCategoryEditor(
    category: EvaluationCategory,
    answers: Map<String, String>,
    enabled: Boolean,
    onAnswerChange: (EvaluationQuestion, String) -> Unit,
) {
    PrismSection(title = category.title.ifBlank { "评价项目" }) {
        category.questions.forEachIndexed { index, question ->
            EvaluationQuestionEditor(
                question = question,
                answer = answers[question.key] ?: defaultEvaluationAnswer(question),
                enabled = enabled,
                position = index + 1,
                onAnswer = { onAnswerChange(question, it) },
            )
        }
    }
}

@Composable
internal fun EvaluationQuestionEditor(
    question: EvaluationQuestion,
    answer: String,
    enabled: Boolean,
    position: Int? = null,
    onAnswer: (String) -> Unit,
) {
    PrismCard(modifier = Modifier.fillMaxWidth()) {
        Text(if (position != null) "$position、${question.title}" else question.title, style = subheadlineStyle())
        Spacer(Modifier.height(8.dp))
        when (question.typeCode) {
            "1" -> {
                Text("单选题 · 必答", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    question.options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = answer == option.code,
                                    enabled = enabled,
                                    role = Role.RadioButton,
                                    onClick = { onAnswer(option.code) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = answer == option.code, onClick = null, enabled = enabled)
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            "2" -> {
                val range = listOf(question.minimum, question.maximum).filter(String::isNotBlank).joinToString("–")
                PrismTextField(
                    value = answer,
                    onValueChange = onAnswer,
                    label = if (range.isBlank()) "分值 · 必答" else "分值 $range · 必答",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = enabled,
                )
            }
            "3" -> {
                Text("多选题 · 必答", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val selected = decodeEvaluationMultiAnswer(answer).toSet()
                Column(Modifier.fillMaxWidth()) {
                    question.options.forEach { option ->
                        val checked = option.code in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = checked,
                                    enabled = enabled,
                                    role = Role.Checkbox,
                                    onValueChange = { next ->
                                        val values = if (next) selected + option.code else selected - option.code
                                        onAnswer(encodeEvaluationMultiAnswer(question.options.map { it.code }.filter { it in values }))
                                    },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            "4" -> {
                Text("问答题 · 必答", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrismTextField(
                    value = answer,
                    onValueChange = onAnswer,
                    label = "请输入评价内容",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 8,
                    enabled = enabled,
                )
            }
            else -> Text(
                "暂不支持的题型（${question.typeCode.ifBlank { "未知" }}），为避免丢题，已禁用提交。",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun answerKey(item: EvaluationItem, question: EvaluationQuestion): String = "${item.courseKey}\u001e${question.key}"

private fun answerDraftsFor(
    item: EvaluationItem,
    form: EvaluationForm,
    drafts: Map<String, String>,
): Map<String, String> = form.formats
    .flatMap { it.categories }
    .flatMap { it.questions }
    .associate { question ->
        question.key to (drafts[answerKey(item, question)] ?: defaultEvaluationAnswer(question))
    }

private fun defaultEvaluationAnswer(question: EvaluationQuestion): String = when (question.typeCode) {
    "3" -> encodeEvaluationMultiAnswer(question.initialAnswers)
    else -> question.initialAnswers.firstOrNull().orEmpty()
}
