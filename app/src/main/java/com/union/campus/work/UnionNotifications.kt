package com.union.campus.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.union.campus.MainActivity
import com.union.campus.R
import java.time.LocalDate
import java.util.Calendar

/**
 * 系统通知统一出口：渠道、权限、点击深链、免打扰与“一天只提醒一次”的去重都收在这里。
 * 应用内横幅（PrismNoticeBanner / PrismMessenger）是另一条链路，这里只负责通知栏。
 */
object UnionNotifications {

    const val CHANNEL_ID = "union_reminders"
    const val EXTRA_DEEP_LINK = "deepLink"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "校园提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "上课、作业截止、低电费与待办提醒"
            },
        )
    }

    /**
     * 系统通知是否可用：应用通知总开关 + Android 13 起的运行时授权。
     * 任一不满足都静默跳过，业务本身不受影响。
     */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String,
        deepLink: String?,
        urgent: Boolean = false,
    ): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (deepLink != null) putExtra(EXTRA_DEEP_LINK, deepLink)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_union)
            .setContentTitle(title)
            .setContentText(text.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(
                if (urgent) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        }.getOrDefault(false)
    }

    /** 支持跨零点，例如 23 → 7。 */
    fun inQuietHours(
        startHour: Int,
        endHour: Int,
        now: Calendar = Calendar.getInstance(),
    ): Boolean {
        if (startHour == endHour) return false
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (startHour < endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }
}

/**
 * 通知去重：同一条提醒（课次 + 日期）只发一次。
 * 用 SharedPreferences 而不是 DataStore，Worker 里读写更直接；跨天自动清空。
 */
internal object ReminderDedup {
    private const val PREFS = "union_reminder_state"
    private const val KEY_DAY = "day"

    fun markOnce(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY, "") != today) {
            prefs.edit().clear().putString(KEY_DAY, today).apply()
        }
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }
}
