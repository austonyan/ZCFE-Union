package com.union.campus

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.screens.onboarding.WelcomeScreen
import com.union.campus.ui.theme.UnionTheme
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun useSupportedPortraitWindow() {
        // Match MainActivity's manifest constraint, not the emulator launcher's natural orientation.
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeRule.waitUntil(5_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun primaryActions_areVisible_withoutCompletingOptionalScheduleSetup() {
        composeRule.setContent {
            UnionTheme { WelcomeScreen(onStartBinding = {}, onExplore = {}) }
        }
        composeRule.onNodeWithText("开始绑定").assertIsDisplayed()
        composeRule.onNodeWithText("暂不绑定").assertIsDisplayed()
        composeRule.onNodeWithText("J1–J6").assertDoesNotExist()
        composeRule.onNodeWithText("作息设置").performScrollTo().performClick()
        composeRule.onNodeWithText("J1–J6").assertExists()
        composeRule.onNodeWithText("作息设置").performScrollTo().performClick()
        composeRule.onNodeWithText("J1–J6").assertDoesNotExist()
    }
}
