package com.union.campus

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import com.union.campus.di.ServiceLocator
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No account or service writes: captures real app destinations for human visual inspection. */
@RunWith(AndroidJUnit4::class)
class AppVisualAuditTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String, dialog: Boolean = false) {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("md3-audit")!!
        val configuration = instrumentation.targetContext.resources.configuration
        // PixelCopy waits for Compose rendering; a raw window screenshot can capture the outgoing tab.
        val node = if (dialog) {
            composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog))
        } else composeRule.onRoot()
        val rendered = node.captureToImage().asAndroidBitmap()
        val bitmap = if (dialog) {
            // The frame barrier above settles rendering; capture the composed window and scrim together.
            val composite = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            rendered.recycle()
            composite
        } else rendered
        val filename = "$name-${configuration.screenWidthDp}dp-${configuration.fontScale}x.png"
        File(directory, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun destinations_areReachable_inBothThemes() {
        val settings = ServiceLocator.settings
        val originalMode = runBlocking { settings.themeMode.first() }
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        try {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodes(hasText("暂不绑定")).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodes(tabRole).fetchSemanticsNodes().isNotEmpty()
            }
            if (composeRule.onAllNodes(hasText("暂不绑定")).fetchSemanticsNodes().isNotEmpty()) {
                capture("welcome")
                composeRule.onNodeWithText("暂不绑定").performScrollTo().performClick()
            }
            listOf("light", "dark").forEach { theme ->
                runBlocking { settings.setThemeMode(theme) }
                // Drain the settings Flow and the theme recomposition before capturing.
                composeRule.waitForIdle()
                listOf("今天", "课表", "教务", "生活", "我的").forEachIndexed { index, label ->
                    composeRule.onNode(tabRole and hasText(label)).performClick().assertIsSelected()
                    val heading = if (label == "课表") "课程表" else label
                    composeRule.waitUntil(15_000) {
                        composeRule.onAllNodes(hasText(heading)).fetchSemanticsNodes().size >=
                            if (heading == label) 2 else 1
                    }
                    if (label == "我的") {
                        composeRule.onNodeWithContentDescription("设置").performScrollTo()
                    }
                    capture("$theme-tab-$index")
                }
                composeRule.onNodeWithContentDescription("设置").performClick()
                composeRule.onNodeWithText("设置").assertIsDisplayed()
                capture("$theme-settings")
                composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                composeRule.onNodeWithText("提醒中心").performScrollTo().performClick()
                composeRule.onNodeWithText("免打扰时段").performScrollTo().performClick()
                val dialog = SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog)
                composeRule.onNode(dialog).assertExists()
                capture("$theme-quiet-hours", dialog = true)
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodes(dialog).fetchSemanticsNodes().isEmpty()
                }
                composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            }
        } finally {
            runBlocking { settings.setThemeMode(originalMode) }
        }
    }
}
