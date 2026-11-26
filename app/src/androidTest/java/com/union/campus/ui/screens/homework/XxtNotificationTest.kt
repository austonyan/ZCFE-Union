package com.union.campus.ui.screens.homework

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.domain.HomeworkReminder
import com.union.campus.work.XxtNotifications
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设备端通知回归：走 Worker 用的同一段构建逻辑发一条作业提醒，
 * 再从系统里把它查回来——证明“提醒”真的能到达通知栏。
 */
@RunWith(AndroidJUnit4::class)
class XxtNotificationTest {

    @Test
    fun homeworkReminderReachesNotificationShade() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reminder = HomeworkReminder(
            key = "instrumented-homework",
            tag = "h6",
            title = "6 小时内截止：单元测试作业",
            body = "高等数学\n还有 5 小时（11-27 23:59）",
            urgent = false,
        )

        assertTrue(XxtNotifications.notify(context, reminder))

        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.any { sbn ->
            XxtNotifications.titleOf(sbn.notification) == reminder.title
        }
        assertTrue("通知没有出现在通知栏", posted)
    }
}
