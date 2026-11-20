package com.union.campus

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrismNativeButtonContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outlinedButton_loadingPreservesLabelAndSize_andBlocksRepeatTouches() {
        var loading by mutableStateOf(false)
        var clicks = 0
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismButton(
                    onClick = { clicks++ },
                    filled = false,
                    loading = loading,
                    modifier = Modifier.testTag("action"),
                ) { Text("重新同步") }
            }
        }
        val button = composeRule.onNodeWithTag("action")
        button.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        val bounds = button.fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { loading = true }
        button.assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performTouchInput { click() }
        composeRule.onNodeWithText("重新同步").assertExists()
        composeRule.onAllNodes(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        )).assertCountEquals(1)
        val loadingBounds = button.fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width, loadingBounds.width, 0.5f)
        assertEquals(bounds.height, loadingBounds.height, 0.5f)
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun textButton_keepsNativeButtonRoleAndTouchTarget_acrossEnabledChanges() {
        var enabled by mutableStateOf(false)
        var clicks = 0
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismTextButton(onClick = { clicks++ }, enabled = enabled) { Text("取消") }
            }
        }
        val button = composeRule.onNodeWithText("取消")
        button.assertIsNotEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            .performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, clicks); enabled = true }
        button.assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
