package com.union.campus

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.ui.common.ExternalLinks
import com.union.campus.ui.common.openExternalLink
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismSnackbarHost
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
    fun coursePreview_handsOffToOfficialSystem_andCanReturn_withoutUnmappedSubmission() {
        var handoffs = 0
        var backs = 0
        var dark by mutableStateOf(false)
        composeRule.setContent {
            UnionTheme(darkTheme = dark) {
                ConfirmSelectionScreen(
                    course = SelectableCourse("test-id", "测试课程", "2", "测试教师", "周一"),
                    onBack = { backs++ },
                    onOpenAcademicPortal = { handoffs++ },
                )
            }
        }
        composeRule.onNodeWithText("测试课程").assertIsDisplayed()
        composeRule.onNodeWithText("打开教务系统").assertIsEnabled().performClick()
        composeRule.onNodeWithText("返回选课").performClick()
        composeRule.runOnIdle {
            assertEquals(1, handoffs)
            assertEquals(1, backs)
        }
        composeRule.onNodeWithText("确认提交").assertDoesNotExist()
        capture("light-course-preview")
        composeRule.runOnIdle { dark = true }
        capture("dark-course-preview")
    }

    @Test
    fun coursePreview_failedHandoff_showsAccessibleFeedback_andRemainsRetryable() {
        var attempts = 0
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) {
                attempts++
                throw ActivityNotFoundException("fixture only")
            }
        }
        val message = "无法打开教务系统，请检查浏览器"
        composeRule.setContent {
            UnionTheme(darkTheme = false) {
                val messenger = LocalPrismMessenger.current
                Box(Modifier.fillMaxSize()) {
                    ConfirmSelectionScreen(
                        course = SelectableCourse("test-id", "测试课程", "2", "测试教师", "周一"),
                        onBack = {},
                        onOpenAcademicPortal = {
                            openExternalLink(ExternalLinks.AcademicPortal, context) { messenger.error(message) }
                        },
                    )
                    PrismSnackbarHost(messenger, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        composeRule.onNodeWithText("打开教务系统").performClick()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
        composeRule.onNodeWithText("测试课程").assertIsDisplayed()
        composeRule.onNodeWithText("打开教务系统").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(2, attempts) }
        capture("failed-course-handoff")
    }
}
