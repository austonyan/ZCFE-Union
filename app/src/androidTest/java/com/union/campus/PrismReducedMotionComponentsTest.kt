package com.union.campus

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.ui.prism.PrismLoadingIndicator
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.PrismMessenger
import com.union.campus.ui.prism.PrismMessageTone
import com.union.campus.ui.prism.PrismSnackbarHost
import com.union.campus.ui.prism.prismPressScale
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrismReducedMotionComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingIndicator_staysIndeterminateButDoesNotAnimate_whenMotionIsRemoved() = withoutAnimations("animator_duration_scale") {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismLoadingIndicator(modifier = Modifier.testTag("loading"))
            }
        }
        val indicator = composeRule.onNodeWithTag("loading")
        indicator.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        ))
        val before = indicator.captureToImage().toPixelMap()
        composeRule.mainClock.advanceTimeBy(1_000)
        val after = indicator.captureToImage().toPixelMap()
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        for (y in 0 until before.height) {
            for (x in 0 until before.width) {
                assertEquals("Loading pixels must stay static at ($x, $y)", before[x, y], after[x, y])
            }
        }
    }

    @Test
    fun snapSheet_entersAndUnmountsWithoutWaitingForAnimationFrames_whenMotionIsRemoved() = withoutAnimations("transition_animation_scale") {
        var visible by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismSnapSheet(visible = visible, onDismiss = { visible = false }) { Text("面板内容") }
            }
        }
        // A snap can settle on the next frame, not after a spring or a timed exit.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val top = composeRule.onNodeWithText("面板内容").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(top, composeRule.onNodeWithText("面板内容").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        composeRule.runOnIdle { visible = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("面板内容").assertDoesNotExist()
    }

    @Test
    fun pressScale_keepsGeometryUnchanged_whenTransitionMotionIsRemoved() = withoutAnimations("transition_animation_scale") {
        val interactions = MutableInteractionSource()
        composeRule.setContent {
            UnionTheme {
                Box(Modifier.size(100.dp).prismPressScale(interactions, target = 0.8f).testTag("press"))
            }
        }
        val before = composeRule.onNodeWithTag("press").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { interactions.tryEmit(PressInteraction.Press(Offset.Zero)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        val after = composeRule.onNodeWithTag("press").fetchSemanticsNode().boundsInRoot
        assertEquals(before.width, after.width, 0.5f)
        assertEquals(before.height, after.height, 0.5f)
    }

    @Test
    fun snackbar_hasNoPositionOrSizeAnimation_whenTransitionMotionIsRemoved() = withoutAnimations("transition_animation_scale") {
        val messenger = PrismMessenger()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UnionTheme { PrismSnackbarHost(messenger, Modifier.testTag("snackbar")) }
        }
        composeRule.runOnIdle { messenger.show("打开失败", PrismMessageTone.Error, "重试", {}) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("打开失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
        val before = composeRule.onNodeWithTag("snackbar").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(500)
        val after = composeRule.onNodeWithTag("snackbar").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 0.5f)
        assertEquals(before.height, after.height, 0.5f)
    }

    @Test
    fun snackbar_respectsRecommendedAccessibilityTimeout_forTextAndActions() = withoutAnimations("transition_animation_scale") {
        val messenger = PrismMessenger()
        val requests = mutableListOf<List<Any>>()
        val accessibility = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long,
                containsIcons: Boolean,
                containsText: Boolean,
                containsControls: Boolean,
            ): Long {
                requests += listOf(originalTimeoutMillis, containsIcons, containsText, containsControls)
                return if (containsControls) 10_000L else 6_000L
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides accessibility) {
                UnionTheme { PrismSnackbarHost(messenger) }
            }
        }
        composeRule.runOnIdle { messenger.error("打开失败") }
        composeRule.mainClock.advanceTimeBy(3_600)
        composeRule.onNodeWithText("打开失败").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.onNodeWithText("打开失败").assertDoesNotExist()
        composeRule.runOnIdle { messenger.show("再次失败", PrismMessageTone.Error, "重试", {}) }
        composeRule.mainClock.advanceTimeBy(5_600)
        composeRule.onNodeWithText("重试").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithText("重试").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(
                listOf<Any>(3_200L, true, true, false),
                listOf<Any>(5_000L, true, true, true),
            ), requests)
        }
    }

    /** Change real system settings, then restore their exact original values even if assertions fail. */
    private fun withoutAnimations(disabledScale: String, test: () -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        val keys = listOf("animator_duration_scale", "transition_animation_scale")
        val previous = keys.associateWith { shell("settings get global $it") }
        try {
            // Keep the other scale enabled: either Prism setting alone must remove motion.
            keys.forEach { key -> shell("settings put global $key ${if (key == disabledScale) 0 else 1}") }
            test()
        } finally {
            previous.forEach { (key, value) ->
                if (value == "null") shell("settings delete global $key")
                else shell("settings put global $key $value")
            }
        }
    }
}
