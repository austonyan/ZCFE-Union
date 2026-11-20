package com.union.campus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.navigation.Screen
import com.union.campus.ui.prism.PrismNavigationScaffold
import com.union.campus.ui.theme.UnionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun resize_preservesSelection_andSwitchesAt600dp() {
        var width by mutableStateOf(599.dp)
        var current by mutableStateOf<Screen?>(Screen.Today)
        composeRule.setContent {
            // Keep the synthetic window inside the physical device, regardless of device density.
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                UnionTheme {
                    Box(Modifier.requiredSize(width, 600.dp)) {
                        PrismNavigationScaffold(
                            tabs = Screen.Tabs,
                            current = current,
                            onSelect = { current = it },
                        ) { padding -> Text("页面内容", Modifier.padding(padding)) }
                    }
                }
            }
        }
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeRule.onNodeWithTag("compact-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("medium-navigation").assertDoesNotExist()
        composeRule.onNode(tabRole and hasText("教务")).performClick().assertIsSelected()
        composeRule.runOnIdle { width = 600.dp }
        composeRule.onNodeWithTag("compact-navigation").assertDoesNotExist()
        composeRule.onNodeWithTag("medium-navigation").assertIsDisplayed()
        composeRule.onNode(tabRole and hasText("教务")).assertIsSelected()
        composeRule.onNode(tabRole and hasText("我的")).performClick().assertIsSelected()
        composeRule.runOnIdle { width = 599.dp }
        composeRule.onNode(tabRole and hasText("我的")).assertIsSelected()
        composeRule.onNodeWithText("页面内容").assertIsDisplayed()
        composeRule.runOnIdle { current = Screen.Settings }
        composeRule.onNodeWithTag("compact-navigation").assertDoesNotExist()
        composeRule.onNodeWithTag("medium-navigation").assertDoesNotExist()
    }

    @Test
    fun rail_announcesActiveShowerSession() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                UnionTheme {
                    Box(Modifier.requiredSize(700.dp, 600.dp)) {
                        PrismNavigationScaffold(
                            tabs = Screen.Tabs,
                            current = Screen.Life,
                            onSelect = {},
                            lifeActive = true,
                        ) { }
                    }
                }
            }
        }
        composeRule.onNode(
            hasText("生活") and SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, "洗浴进行中",
            ),
        ).assertIsDisplayed().assertIsSelected()
    }
}
