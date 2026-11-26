package com.union.campus.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.union.campus.MainActivity
import com.union.campus.R
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.HomeworkReminder
import com.union.campus.domain.XxtHomeworkRules
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 学习通作业后台同步：每 2 小时串行拉一次，按 24h / 6h / 1h 分档提醒。
 *
 * 与低电费 Worker 一样遵守系统后台限制与免打扰时段：免打扰期间只发 1 小时档的加急提醒。
 * 读接口缓存优先：无绑定或提醒关闭时直接返回，不产生任何网络请求。
 */
class XxtHomeworkWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settings
        if (!settings.remindEnabled.first() || !settings.remindXxtHomework.first()) {
            return Result.success()
        }
        val repo = ServiceLocator.xxtRepository
        if (!repo.isBound()) return Result.success()

        val snapshot = try {
            repo.sync()
        } catch (_: Exception) {
            return Result.success() // 后台失败不打扰，等下一轮
        }
        if (snapshot.items.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val sent = repo.journal().keys.toMutableSet()
        val reminders = buildList {
            addAll(XxtHomeworkRules.newHomeworkReminders(snapshot.pending, now, sent))
            addAll(XxtHomeworkRules.dueReminders(snapshot.pending, now, alreadySent = sent))
        }
        if (reminders.isEmpty()) return Result.success()

        val quiet = settings.quietEnabled.first() && inQuietHours(
            settings.quietStart.first(), settings.quietEnd.first(),
        )
        val delivered = mutableListOf<Pair<String, String>>()
        reminders.forEach { reminder ->
            if (quiet && !reminder.urgent) return@forEach
            if (notify(reminder)) delivered += reminder.key to reminder.tag
        }
        repo.markReminded(delivered)
        return Result.success()
    }

    /** 支持跨零点，例如 23 → 7。 */
    private fun inQuietHours(
        startHour: Int,
        endHour: Int,
        now: Calendar = Calendar.getInstance(),
    ): Boolean = XxtHomeworkRules.inQuietHours(now.get(Calendar.HOUR_OF_DAY), startHour, endHour)

    private fun notify(reminder: HomeworkReminder): Boolean {
        val context = applicationContext
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
        val notification = NotificationCompat.Builder(context, "union_reminders")
            .setSmallIcon(R.drawable.ic_stat_union)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setPriority(if (reminder.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val id = ("xxt:" + reminder.key + "|" + reminder.tag).hashCode()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    companion object {
        private const val UNIQUE_NAME = "union_xxt_homework"

        /** 每 2 小时一次；WorkManager 不保证准时，系统省电时会顺延。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<XxtHomeworkWorker>(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
