package com.union.campus

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.data.repo.decodeEvaluationMultiAnswer
import com.union.campus.domain.EvaluationCategory
import com.union.campus.domain.EvaluationFormat
import com.union.campus.domain.EvaluationForm
import com.union.campus.domain.EvaluationItem
import com.union.campus.domain.EvaluationOption
import com.union.campus.domain.EvaluationQuestion
import com.union.campus.ui.screens.study.EvaluationQuestionEditor
import com.union.campus.ui.screens.study.EvaluationScreen
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EvaluationQuestionEditorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun singleChoice_rendersRealOptions_andEmitsSelectedCode() {
        var answer by mutableStateOf("")
        composeRule.setContent {
            EvaluationEditorHost {
                EvaluationQuestionEditor(
                    question = question("1", options = listOf(EvaluationOption("A", "选项A"), EvaluationOption("B", "选项B"))),
                    answer = answer,
                    enabled = true,
                    onAnswer = { answer = it },
                )
            }
        }
        composeRule.onNodeWithText("单选题 · 必答").assertIsDisplayed()
        composeRule.onNodeWithText("选项B").performClick()
        composeRule.runOnIdle { assertEquals("B", answer) }
    }

    @Test
    fun multiChoice_togglesEachOption_andKeepsSelectionAsValues() {
        var answer by mutableStateOf("[]")
        composeRule.setContent {
            EvaluationEditorHost {
                EvaluationQuestionEditor(
                    question = question("3", options = listOf(EvaluationOption("A", "选项A"), EvaluationOption("B", "选项B"))),
                    answer = answer,
                    enabled = true,
                    onAnswer = { answer = it },
                )
            }
        }
        composeRule.onNodeWithText("选项A").performClick()
        composeRule.runOnIdle { assertEquals(listOf("A"), decodeEvaluationMultiAnswer(answer)) }
        composeRule.onNodeWithText("选项B").performClick()
        composeRule.runOnIdle { assertEquals(listOf("A", "B"), decodeEvaluationMultiAnswer(answer)) }
    }

    @Test
    fun numericAndTextQuestions_acceptInput_withoutInventingDefaults() {
        var score by mutableStateOf("")
        var comment by mutableStateOf("")
        composeRule.setContent {
            EvaluationEditorHost {
                EvaluationQuestionEditor(
                    question = question("2", minimum = "60", maximum = "100"),
                    answer = score,
                    enabled = true,
                    onAnswer = { score = it },
                )
                EvaluationQuestionEditor(
                    question = question("4"),
                    answer = comment,
                    enabled = true,
                    onAnswer = { comment = it },
                )
            }
        }
        composeRule.onNodeWithText("分值 60–100 · 必答").performTextInput("88")
        composeRule.onNodeWithText("请输入评价内容").performTextInput("建议增加案例")
        composeRule.runOnIdle {
            assertEquals("88", score)
            assertEquals("建议增加案例", comment)
        }
    }

    @Test
    fun officialEvaluationFlow_loadsRoundAndCourse_thenConfirmsExactDynamicAnswersOnce() {
        val writes = AtomicInteger(0)
        val captured = AtomicReference<Map<String, String>>(emptyMap())
        val question = EvaluationQuestion(
            key = "F1|Y1|Q1",
            code = "Q1",
            title = "课堂目标清晰",
            typeCode = "1",
            options = listOf(EvaluationOption("A", "选项A"), EvaluationOption("B", "选项B")),
        )
        val item = EvaluationItem(
            xnxq = "20260",
            evaluationRoundCode = "R1",
            courseCode = "C1",
            teacherCode = "T1",
            evaluationTypeCode = "P1",
            evaluationStatusCode = "S1",
            courseStatusCode = "0",
            courseName = "测试课程",
            teacher = "测试教师",
            credits = "3",
            evaluationType = "讲解",
            semesterName = "2025-2026学年第一学期",
            roundName = "学生评教",
            startsAt = "2026-11-02 00:00",
            endsAt = "2026-11-27 23:59",
        )
        val form = EvaluationForm("0", listOf(EvaluationFormat("F1", "总体评价", listOf(
            EvaluationCategory("Y1", "教师授课情况", listOf(question)),
        ))))
        composeRule.setContent {
            UnionTheme {
                EvaluationScreen(
                    onBack = {},
                    isBound = { true },
                    loadCourses = { listOf(item) },
                    loadForm = { form },
                    submit = { _, _, answers ->
                        writes.incrementAndGet()
                        captured.set(answers)
                    },
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("测试课程").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("2026-11-02 00:00 — 2026-11-27 23:59").assertIsDisplayed()
        composeRule.onNodeWithText("测试课程").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("课堂目标清晰", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("选项B").performClick()
        composeRule.onNodeWithText("提交评价").assertIsEnabled().performClick()
        composeRule.onNodeWithText("提交前确认").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, writes.get()) }
        composeRule.onNodeWithText("确认提交").performClick()
        composeRule.waitUntil(5_000) { writes.get() == 1 }
        composeRule.runOnIdle { assertEquals(mapOf(question.key to "B"), captured.get()) }
        composeRule.onNodeWithText("打开教务系统").assertDoesNotExist()
    }

    private fun question(
        type: String,
        options: List<EvaluationOption> = emptyList(),
        minimum: String = "",
        maximum: String = "",
    ) = EvaluationQuestion(
        key = "format|category|question-$type",
        code = "question-$type",
        title = "题目类型 $type",
        typeCode = type,
        options = options,
        minimum = minimum,
        maximum = maximum,
    )

    @Composable
    private fun EvaluationEditorHost(content: @Composable () -> Unit) {
        UnionTheme { Column { content() } }
    }
}
