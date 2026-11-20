package com.union.campus

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.screens.me.RemindersCenterScreen
import com.union.campus.ui.theme.UnionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemindersSelectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun firstOption_matchesPersistedReminderSettings() {
        val settings = ServiceLocator.settings
        val originalLead = runBlocking { settings.remindClassLead.first() }
        val originalThreshold = runBlocking { settings.lowElectricThreshold.first() }
        try {
            runBlocking {
                settings.setRemindClassLead(5)
                settings.setLowElectricThreshold(10)
            }
            composeRule.setContent {
                UnionTheme { RemindersCenterScreen(onBack = {}) }
            }
            val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            composeRule.onNode(tabRole and hasText("课前 5 分钟"))
                .performScrollTo().assertIsSelected()
            composeRule.onNode(tabRole and hasText("10 度"))
                .performScrollTo().assertIsSelected()
        } finally {
            runBlocking {
                settings.setRemindClassLead(originalLead)
                settings.setLowElectricThreshold(originalThreshold)
            }
        }
    }
}
