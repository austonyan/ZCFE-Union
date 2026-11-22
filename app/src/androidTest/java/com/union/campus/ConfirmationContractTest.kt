package com.union.campus

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.screens.study.ConfirmSelectionScreen
import com.union.campus.ui.screens.study.SelectableCourse
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConfirmationContractTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = context.resources.configuration
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(context.getExternalFilesDir("handoff-audit"), "$name-${configuration.screenWidthDp}dp-${configuration.fontScale}x.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun confirm_readsTarget_beforeDismissClearsCallerState() {
        var target by mutableStateOf<String?>("教材 A")
        val events = mutableListOf<String>()
        composeRule.setContent {
            UnionTheme {
                if (target != null) {
                    PrismAlertDialog(
                        title = "核对教材",
                        body = target.orEmpty(),
                        confirmText = "确认领取",
                        onConfirm = { events += "confirm:$target" },
                        onDismiss = { events += "dismiss"; target = null },
                    )
                }
            }
        }
        composeRule.onNodeWithText("确认领取").performClick()
        composeRule.waitUntil(5_000) { events.size == 2 }
        composeRule.runOnIdle { assertEquals(listOf("confirm:教材 A", "dismiss"), events) }
        composeRule.onNodeWithText("核对教材").assertDoesNotExist()
    }

    @Test
    fun coursePreview_containsNoExternalHandoff_andCanReturn_withoutFakeSubmission() {
        var backs = 0
        var dark by mutableStateOf(false)
        composeRule.setContent {
            UnionTheme(darkTheme = dark) {
                ConfirmSelectionScreen(
                    course = SelectableCourse("test-id", "测试课程", "2", "测试教师", "周一"),
                    onBack = { backs++ },
                )
            }
        }
        composeRule.onNodeWithText("测试课程").assertIsDisplayed()
        composeRule.onNodeWithText("返回课程列表").performClick()
        composeRule.onNodeWithText("打开教务系统").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, backs)
        }
        composeRule.onNodeWithText("确认提交").assertDoesNotExist()
        capture("light-course-information")
        composeRule.runOnIdle { dark = true }
        capture("dark-course-information")
    }
}
