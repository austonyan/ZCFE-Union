package com.union.campus

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动冒烟：不依赖网络与账号状态。
 *
 * 首次安装默认落到欢迎页（标题「郑财校园」）；已绑定过的设备直接进今天页
 * （标题「今天」）。两种情况都算启动成功，测试只要求任一页面标题出现、
 * 且 Activity 没有崩溃。
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStart_reachesWelcomeOrToday_withoutCrash() {
        val welcomeOrToday = hasText("郑财校园") or hasText("今天")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(welcomeOrToday).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodes(welcomeOrToday).onFirst().assertExists()
    }
}