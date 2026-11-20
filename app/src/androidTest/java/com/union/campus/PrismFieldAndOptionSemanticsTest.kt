package com.union.campus

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.prism.PrismDropdownField
import com.union.campus.ui.prism.PrismOption
import com.union.campus.ui.prism.PrismOptionSheet
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrismFieldAndOptionSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dropdown_hasOneReadOnlyAnchor_andAnnouncesSelectedMenuItem() {
        val options = listOf("春季", "秋季")
        var selected by mutableStateOf(options.first())
        val selections = mutableListOf<Int>()
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismDropdownField(
                    label = "学期",
                    options = options,
                    selected = selected,
                    onSelected = { selections += it; selected = options[it] },
                )
            }
        }
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onAllNodes(isFocusable(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).assertCountEquals(0)
        val anchor = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
        composeRule.onNode(anchor).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNode(hasText("春季") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertIsSelected()
        composeRule.onNodeWithText("秋季").assertIsNotSelected().performClick()
        composeRule.runOnIdle { assertEquals(listOf(1), selections) }
        composeRule.onNode(anchor).assert(hasText("秋季"))
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun dropdown_disabledAndEmptyFields_offerNoMenuActionOrEditing() {
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                Column {
                    PrismDropdownField(
                        label = "不可用",
                        options = listOf("选项"),
                        selected = "选项",
                        onSelected = { error("Disabled field must not select") },
                        enabled = false,
                    )
                    PrismDropdownField(
                        label = "暂无选项",
                        options = emptyList(),
                        selected = null,
                        onSelected = { error("Empty field must not select") },
                    )
                }
            }
        }
        composeRule.onNodeWithText("不可用").assertIsNotEnabled().performTouchInput { click() }
        composeRule.onNodeWithText("暂无选项").assertIsNotEnabled().performTouchInput { click() }
        // Disabled native text fields retain a no-op click semantics action, but no menu anchor.
        composeRule.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(0)
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assertCountEquals(0)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun optionSheet_exposesOneRadioGroup_andSelectsAndDismissesExactlyOnce() {
        var visible by mutableStateOf(true)
        val selectedKeys = mutableListOf<String>()
        var dismissals = 0
        composeRule.setContent {
            UnionTheme(darkTheme = true, dynamicColor = false) {
                PrismOptionSheet(
                    visible = visible,
                    title = "外观",
                    options = listOf(PrismOption("system", "跟随系统"), PrismOption("dark", "深色")),
                    selectedKey = "system",
                    onSelect = { selectedKeys += it },
                    onDismiss = { dismissals++; visible = false },
                )
            }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule.onAllNodes(radio).assertCountEquals(2)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertCountEquals(1)
        composeRule.onNodeWithText("跟随系统").assertIsSelected().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("深色").assertIsNotSelected().assert(radio).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("dark"), selectedKeys)
            assertEquals(1, dismissals)
        }
        composeRule.onAllNodes(radio).assertCountEquals(0)
    }
}
