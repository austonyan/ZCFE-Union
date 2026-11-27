package com.union.campus.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TimetableData
import com.union.campus.domain.TimetableSchedule
import com.union.campus.domain.TimetableScheduleConfig
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 上课提醒的系统通知链路：读本地课表缓存，课前 N 分钟发一次通知，点击直达课表页。
 *
 * WorkManager 最小周期 15 分钟且不保证准时；同一条课次按“日期 + 课次 + 上课时间”去重，
 * 迟到最多 3 分钟仍会提示“正在上课”，超过就跳过，避免下一节课前收到上一条。
 */
class ClassReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settings
        if (!settings.remindEnabled.first() || !settings.remindClass.first()) return Result.success()
        if (settings.quietEnabled.first() &&
            UnionNotifications.inQuietHours(
                settings.quietStart.first(),
                settings.quietEnd.first(),
            )
        ) {
            return Result.success()
        }

        val lead = settings.remindClassLead.first().coerceIn(1, 60)
        val semesterDm = settings.timetableSemesterDm.first()
            .ifBlank { settings.currentSemesterDm.first() }
            .ifBlank { SemesterCalc.guessSemesterDm() }
        val week = SemesterCalc.currentWeek(semesterDm, settings.firstWeekStart.first())
        val data = ServiceLocator.entities
            .read("jw.timetable.$semesterDm.w$week", TimetableData.serializer())
            ?.data
            ?: return Result.success()

        val config = TimetableScheduleConfig(
            j1ToJ6Slot = if (settings.timetableJLate.first()) MorningSlot.Late else MorningSlot.Early,
            fallbackGroup = if (settings.timetableFallbackGroup.first() == 2) {
                BuildingGroup.S1ToS6AndJ7
            } else {
                BuildingGroup.J1ToJ6
            },
        )
        val now = System.currentTimeMillis()
        val today = SemesterCalc.dayOfWeek(now)
        val upcoming = data.days.firstOrNull { it.day == today }?.cells.orEmpty()
            .mapNotNull { cell ->
                val start = TimetableSchedule.courseSchedule(cell, config)?.start ?: return@mapNotNull null
                val startAt = SemesterCalc.timeToMillisToday(start, now) ?: return@mapNotNull null
                val minutes = ((startAt - now) / 60_000L).toInt()
                if (minutes in -3..lead) Triple(cell, start, minutes) else null
            }
            .minByOrNull { it.third }
            ?: return Result.success()

        val (cell, start, minutes) = upcoming
        val key = listOf(semesterDm, week, LocalDate.now(), cell.id, start).joinToString("|")
        if (!ReminderDedup.markOnce(applicationContext, key)) return Result.success()

        val title = if (minutes <= 0) "正在上课" else "距上课 $minutes 分钟"
        val text = listOfNotNull(
            cell.name.takeIf { it.isNotBlank() },
            cell.location.takeIf { it.isNotBlank() },
            "$start 开始",
        ).joinToString(" · ")
        UnionNotifications.notify(
            context = applicationContext,
            id = key.hashCode(),
            title = title,
            text = text,
            deepLink = "timetable",
            urgent = minutes <= 5,
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "union_class_reminder"

        /** 每 15 分钟检查一次；系统省电时可能顺延。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClassReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
