package com.union.campus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PrismIconButton 的禁用语义：disabled 时必须暴露禁用状态，
 * 触摸不能触发 onClick；enabled 时恢复按钮语义并正常回调。
 */
@RunWith(AndroidJUnit4::class)
class PrismIconButtonSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledIconButton_isNotEnabled_andIgnoresTouches() {
        var clicks = 0

        composeRule.setContent {
            UnionTheme {
                PrismIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    enabled = false,
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("设置")
            .assertIsNotEnabled()
            // 禁用节点没有 OnClick 语义动作，这里用真实触摸确认不会误触发。
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals("禁用状态点击不应回调", 0, clicks)
        }
    }

    @Test
    fun enabledIconButton_isEnabled_andInvokesClick() {
        var clicks = 0

        composeRule.setContent {
            UnionTheme {
                PrismIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    enabled = true,
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("设置")
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        composeRule.runOnIdle {
            assertEquals("启用状态点击应回调一次", 1, clicks)
        }
    }
}