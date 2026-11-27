package com.union.campus.work

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.union.campus.domain.HomeworkReminder

/**
 * 学习通作业提醒的本地通知：与其它提醒共用 [UnionNotifications] 的渠道与深链，
 * 设备端测试仍可直接调用这里点亮一条通知。
 */
object XxtNotifications {

    const val CHANNEL_ID = UnionNotifications.CHANNEL_ID

    fun notify(context: Context, reminder: HomeworkReminder): Boolean {
        val key = "xxt:" + reminder.key + "|" + reminder.tag
        return UnionNotifications.notify(
            context = context,
            id = key.hashCode(),
            title = reminder.title,
            text = reminder.body,
            deepLink = "homework",
            urgent = reminder.urgent,
        )
    }

    /** 测试用：清掉本模块发出的通知，避免打扰后续用例。 */
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** 通知文案在系统里的标题，测试断言用它。 */
    fun titleOf(notification: Notification): String? =
        notification.extras.getString(Notification.EXTRA_TITLE)
}
