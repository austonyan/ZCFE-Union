package com.union.campus

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PrismChip 的单选语义回归：TalkBack 必须读作「单选按钮 + 已选中/未选中」，
 * 且已选中的 chip 再次点击不应重复触发回调。
 */
@RunWith(AndroidJUnit4::class)
class PrismChipSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val radioButtonRole =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun chips_exposeRadioButtonAndSelectedSemantics() {
        composeRule.setContent {
            UnionTheme {
                Column {
                    PrismChip(label = "选中项", selected = true, onClick = {})
                    PrismChip(label = "未选中项", selected = false, onClick = {})
                }
            }
        }

        composeRule.onNodeWithText("选中项")
            .assert(radioButtonRole)
            .assertIsSelected()

        composeRule.onNodeWithText("未选中项")
            .assert(radioButtonRole)
            .assertIsNotSelected()
    }

    @Test
    fun clickingUnselectedChip_invokesCallback_butSelectedChipDoesNot() {
        var selectedClicks = 0
        var unselectedClicks = 0

        composeRule.setContent {
            UnionTheme {
                Column {
                    PrismChip(label = "选中项", selected = true, onClick = { selectedClicks++ })
                    PrismChip(label = "未选中项", selected = false, onClick = { unselectedClicks++ })
                }
            }
        }

        composeRule.onNodeWithText("未选中项").performClick()
        composeRule.runOnIdle {
            assertEquals("点击未选中 chip 应回调一次", 1, unselectedClicks)
        }

        composeRule.onNodeWithText("选中项").performClick()
        composeRule.runOnIdle {
            assertEquals("已选中 chip 再次点击不应回调", 0, selectedClicks)
        }
    }
}