package com.union.campus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.PrismToggleRow
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PrismSwitch 的开关语义：初始关闭、角色为 Switch，
 * 点击后回调收到 true 且状态切换为开启。
 */
@RunWith(AndroidJUnit4::class)
class PrismSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switch_startsOff_thenClickReportsCheckedAndTurnsOn() {
        var checked by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()

        composeRule.setContent {
            UnionTheme {
                PrismSwitch(
                    checked = checked,
                    onCheckedChange = { next ->
                        changes += next
                        checked = next
                    },
                )
            }
        }

        composeRule.onNode(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()

        composeRule.onNode(isToggleable()).assertIsOn()
        composeRule.runOnIdle {
            assertEquals("开关应回调一次且新状态为开启", listOf(true), changes)
        }
    }

    /** 禁用态不该响应点击，也不该回调。 */
    @Test
    fun disabledSwitch_doesNotToggle() {
        var changes = mutableListOf<Boolean>()

        composeRule.setContent {
            UnionTheme {
                PrismSwitch(
                    checked = true,
                    onCheckedChange = { changes += it },
                    enabled = false,
                )
            }
        }

        composeRule.onNode(isToggleable())
            .assertIsNotEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals("禁用态不应回调", emptyList<Boolean>(), changes)
        }
    }

    /**
     * 开关行只暴露一个 role=Switch 的目标：整行可点，右侧指示器不进无障碍树，
     * 避免读屏出现「两个都能点、状态还可能不同步」的节点。
     */
    @Test
    fun toggleRow_exposesSingleSwitchTarget_andRowClickToggles() {
        var checked by mutableStateOf(false)

        composeRule.setContent {
            UnionTheme {
                PrismToggleRow(
                    title = "上课提醒",
                    subtitle = "课前 10 分钟",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }

        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNode(isToggleable()).assertIsOff().performClick()
        composeRule.onNode(isToggleable()).assertIsOn()
    }
}