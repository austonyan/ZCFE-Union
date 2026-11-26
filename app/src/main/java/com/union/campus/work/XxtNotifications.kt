package com.union.campus.work

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.union.campus.MainActivity
import com.union.campus.R
import com.union.campus.domain.HomeworkReminder

/**
 * 学习通提醒的本地通知：与后台 Worker 共用同一段构建逻辑，设备端测试可直接点亮。
 * 点击通知进入 App 的作业页（deepLink=homework）。
 */
object XxtNotifications {

    const val CHANNEL_ID = "union_reminders"

    fun notify(context: Context, reminder: HomeworkReminder): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("deepLink", "homework")
        }
        val pending = PendingIntent.getActivity(
            context,
            reminder.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_union)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setPriority(
                if (reminder.urgent) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val id = ("xxt:" + reminder.key + "|" + reminder.tag).hashCode()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    /** 测试用：清掉本模块发出的通知，避免打扰后续用例。 */
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** 通知文案在系统里的标题，测试断言用它。 */
    fun titleOf(notification: Notification): String? =
        notification.extras.getString(Notification.EXTRA_TITLE)
}
