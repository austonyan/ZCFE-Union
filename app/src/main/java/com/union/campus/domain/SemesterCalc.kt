package com.union.campus.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 学期/周次计算：优先用户设置的开学日，否则按学期代码估算。 */
object SemesterCalc {

    /** 解析学期代码：支持 5 位（20261）与 9 位（202620261 = 学年拼接）。 */
    fun parseSemester(semesterDm: String): Pair<Int, Int> {
        return when (semesterDm.length) {
            9 -> (semesterDm.take(4).toIntOrNull() ?: currentYear()) to (semesterDm.takeLast(1).toIntOrNull() ?: 1)
            5 -> (semesterDm.take(4).toIntOrNull() ?: currentYear()) to (semesterDm.takeLast(1).toIntOrNull() ?: 1)
            else -> currentYear() to 1
        }
    }

    /** 学期第一周的周一（估算：秋季学期 9 月 1 日、春季学期 3 月 1 日所在周）。 */
    fun estimateStart(semesterDm: String): Long {
        val (year, termRaw) = parseSemester(semesterDm)
        val term = termRaw
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, if (term == 1) Calendar.SEPTEMBER else Calendar.MARCH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        // 回退到周一
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_MONTH, -6)
            else -> cal.add(Calendar.DAY_OF_MONTH, Calendar.MONDAY - cal.get(Calendar.DAY_OF_WEEK))
        }
        return cal.timeInMillis
    }

    fun currentWeek(semesterDm: String, firstWeekStartMs: Long, now: Long = System.currentTimeMillis()): Int {
        if (semesterDm.isBlank()) return 1
        val start = if (firstWeekStartMs > 0) firstWeekStartMs else estimateStart(semesterDm)
        val days = ((now - start) / (24 * 3600_000L)).toInt()
        return (days / 7 + 1).coerceIn(1, 25)
    }

    /** 当前学期代码（按月份估算：8-12 月为秋季，1-7 月为春季）。格式：学年(4位)+学期(1位)。 */
    fun guessSemesterDm(now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return if (month >= 8) "${year}1" else "${year - 1}2"
    }

    /** 今天是星期几（1=周一 … 7=周日）。 */
    fun dayOfWeek(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val d = cal.get(Calendar.DAY_OF_WEEK)
        return if (d == Calendar.SUNDAY) 7 else d - 1
    }

    fun dateText(now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val week = listOf("一", "二", "三", "四", "五", "六", "日")[dayOfWeek(now) - 1]
        return "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 星期$week"
    }

    /** "HH:mm" → 当天毫秒时间。 */
    fun timeToMillisToday(hhmm: String, now: Long = System.currentTimeMillis()): Long? {
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    fun todayText(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(now))
}
