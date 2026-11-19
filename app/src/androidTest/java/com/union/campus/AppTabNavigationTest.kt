package com.union.campus

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for all five tabs and the separate settings return path. No credentials required. */
@RunWith(AndroidJUnit4::class)
class AppTabNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fiveTabs_remainReachable_andSettingsReturnsToProfile() {
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText("先逛逛")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(tabRole).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasText("先逛逛")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("先逛逛").performScrollTo().performClick()
        }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(tabRole).fetchSemanticsNodes().size == 5
        }
        listOf("今天", "课表", "教务", "生活", "我的").forEach { label ->
            composeRule.onNode(tabRole and hasText(label))
                .assertIsDisplayed()
                .performClick()
                .assertIsSelected()
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNode(tabRole and hasText("我的")).assertIsSelected()
    }
}
