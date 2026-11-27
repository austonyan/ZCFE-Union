package com.union.campus.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.union.campus.di.ServiceLocator
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 低电费每日检查（默认 20:00 前后触发）：低于阈值时发系统通知。
 * 后台提醒受系统限制（power.md §4.4.2），不保证准时。
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settings
        val remindEnabled = settings.remindEnabled.first()
        val lowElectricOn = settings.remindLowElectric.first()
        if (!remindEnabled || !lowElectricOn) return Result.success()

        // 免打扰：静音时段内不发系统通知（应用内待办不受影响）
        if (settings.quietEnabled.first() &&
            UnionNotifications.inQuietHours(settings.quietStart.first(), settings.quietEnd.first())
        ) {
            return Result.success()
        }

        val electric = ServiceLocator.electricRepository
        if (!electric.isBound()) return Result.success()
        val threshold = settings.lowElectricThreshold.first()

        return try {
            val rooms = electric.rooms()
            val room = rooms.firstOrNull() ?: return Result.success()
            val balance = electric.balance(room.roomVerify) ?: return Result.success()
            val kwh = balance.kwh.toDoubleOrNull()
            if (kwh != null && kwh < threshold) {
                UnionNotifications.notify(
                    context = applicationContext,
                    id = 2001,
                    title = "电费低于 $threshold 度",
                    text = "${room.fullName} 剩余 ${balance.kwh} 度，建议充值",
                    deepLink = "electric",
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.success() // 后台检查失败不打扰
        }
    }

    companion object {
        /** 每日一次；尽量落在 20:00 前后（WorkManager 不保证准时）。 */
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val target = now.clone() as Calendar
            target.set(Calendar.HOUR_OF_DAY, 20)
            target.set(Calendar.MINUTE, 0)
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            val delay = target.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "union_daily_reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
