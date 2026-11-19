package com.union.campus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.prism.PrismSwitch
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
}