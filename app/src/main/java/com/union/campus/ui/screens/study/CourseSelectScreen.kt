package com.union.campus.ui.screens.study

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
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.core.userFacingMessage
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UiState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
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
            UiState.Failed("选课加载失败：${e.userFacingMessage()}")
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
            Spacer(Modifier.height(12.dp))
        }
    }
}
// ---------------- 课程信息预览 ----------------

/** 课程信息预览；不在这里伪造选课提交。 */
@Composable
fun ConfirmSelectionScreen(
    course: SelectableCourse,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "课程信息",
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
            PrismTextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回课程列表")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
