package com.union.campus.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 学习通（超星）作业模型。
 *
 * 接口只读：只拉课程、作业列表与作业详情，不提交、不答题。
 * 字符串字段保留平台原始值（状态、截止时间文本），解析后的时间另存，
 * 页面与提醒都只依赖解析结果，原始值用于排错与展示兜底。
 */
@Serializable
data class XxtCourse(
    val courseId: String,
    val clazzId: String,
    val cpi: String,
    val name: String,
    val teacher: String = "",
)

@Serializable
data class XxtHomework(
    val key: String,
    val courseId: String,
    val clazzId: String,
    val courseName: String,
    val title: String,
    val status: String,
    val url: String,
    val done: Boolean,
    val deadlineRaw: String = "",
    val deadlineAt: Long = 0L,
    val startRaw: String = "",
    val questionCount: Int = 0,
    val fullScore: String = "",
    /** 上次尝试解析详情的时间；没读出时间也算已尝试，6 小时内不重复请求。 */
    val detailCheckedAt: Long = 0L,
    val firstSeenAt: Long = 0L,
)

/** 学习通通知中心的一条通知（正文保留全文，展示时截断）。 */
@Serializable
data class XxtNotice(
    val noticeId: String,
    val title: String,
    val content: String = "",
    val sender: String = "",
    val publishTime: String = "",
    val read: Boolean = false,
    val firstSeenAt: Long = 0L,
)

/** 一次同步的结果快照。 */
@Serializable
data class XxtHomeworkSnapshot(
    val items: List<XxtHomework> = emptyList(),
    val notices: List<XxtNotice> = emptyList(),
    val syncedAt: Long = 0L,
    val courseCount: Int = 0,
    val errors: List<String> = emptyList(),
) {
    val pending: List<XxtHomework> get() = items.filter { !it.done }
    val pendingCount: Int get() = items.count { !it.done }
    val unreadNotices: List<XxtNotice> get() = notices.filter { !it.read }
}

/** 提醒档位：进入更近的一档才补一次，同一档只响一次。 */
data class HomeworkReminder(
    val key: String,
    val tag: String,
    val title: String,
    val body: String,
    val urgent: Boolean,
)

/**
 * 作业提醒的纯规则：时间补年份、倒计时文案、分档、免打扰。
 * 全部无副作用，单元测试直接覆盖（XxtReminderTest / XxtTimeTest）。
 */
object XxtHomeworkRules {

    /** 视为已完成的状态词（与网页端作业列表一致）。 */
    private val DONE_MARKS = listOf(
        "已完成", "已提交", "待批阅", "已批阅", "已批改", "已交卷", "已评分", "已互评",
    )

    val DEFAULT_THRESHOLDS = listOf(24, 6, 1)

    private val DEADLINE_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    fun isPending(status: String): Boolean = DONE_MARKS.none { status.contains(it) }

    fun workIdFromUrl(url: String): String {
        Regex("[?&]workId=([0-9A-Za-z]+)").find(url)?.let { return it.groupValues[1] }
        return ""
    }

    /** 稳定主键：优先 workId，没有就用课程 + 标题摘要。 */
    fun assignmentKey(courseId: String, clazzId: String, url: String, title: String): String {
        val wid = workIdFromUrl(url)
        if (wid.isNotEmpty()) return "$courseId:$clazzId:$wid"
        return "$courseId:$clazzId:t" + title.hashCode().toUInt().toString(16)
    }

    /**
     * 把只有 "MM-DD HH:MM" 的时间补成完整时间戳。
     * 取去年/今年/明年里离今天最近的那个年份，跨年作业也能排对顺序。
     */
    fun nearestDeadline(raw: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long {
        val m = Regex("(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})").find(raw.trim()) ?: return 0L
        val (mo, day, hh, mm) = m.destructured
        val month = mo.toIntOrNull() ?: return 0L
        val d = day.toIntOrNull() ?: return 0L
        val hour = hh.toIntOrNull() ?: return 0L
        val minute = mm.toIntOrNull() ?: return 0L
        var best: LocalDate? = null
        for (year in listOf(today.year - 1, today.year, today.year + 1)) {
            val candidate = runCatching { LocalDate.of(year, month, d) }.getOrNull() ?: continue
            if (best == null || abs(candidate.toEpochDay() - today.toEpochDay()) <
                abs(best.toEpochDay() - today.toEpochDay())
            ) {
                best = candidate
            }
        }
        val date = best ?: return 0L
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()
    }

    /** "11-28 23:59"，截止在明年时补年份，避免看起来像过去。 */
    fun formatDeadline(deadlineAt: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (deadlineAt <= 0L) return "截止未知"
        val dt = Instant.ofEpochMilli(deadlineAt).atZone(zone).toLocalDateTime()
        val now = LocalDateTime.now(zone)
        val text = DEADLINE_FORMAT.format(dt)
        return if (dt.year != now.year) "${dt.year}-$text" else text
    }

    /** "还有 3 天 5 小时" / "已过期 2 小时"。 */
    fun countdownText(deadlineAt: Long, now: Long = System.currentTimeMillis()): String {
        if (deadlineAt <= 0L) return "截止未知"
        val seconds = (deadlineAt - now) / 1000
        val overdue = seconds < 0
        val total = abs(seconds)
        val days = total / 86400
        val hours = (total % 86400) / 3600
        val minutes = (total % 3600) / 60
        val text = when {
            days >= 1 -> "$days 天" + if (hours > 0) " $hours 小时" else ""
            hours >= 1 -> "$hours 小时" + if (hours < 6 && minutes > 0) " $minutes 分" else ""
            minutes > 0 -> "$minutes 分钟"
            else -> "不到 1 分钟"
        }
        return if (overdue) "已过期 $text" else "还有 $text"
    }

    /** 进入哪一档：返回不小于剩余时间的最小阈值（小时），未进入任何档位返回 null。 */
    fun bucket(remainHours: Double, thresholds: List<Int> = DEFAULT_THRESHOLDS): Int? {
        return thresholds.distinct().sorted().firstOrNull { remainHours <= it }
    }

    /** 剩余小时数；截止未知返回 null。 */
    fun remainHours(deadlineAt: Long, now: Long = System.currentTimeMillis()): Double? =
        if (deadlineAt <= 0L) null else (deadlineAt - now) / 3_600_000.0

    /** 免打扰：支持跨零点，如 23 → 7。 */
    fun inQuietHours(hour: Int, startHour: Int, endHour: Int): Boolean = when {
        startHour == endHour -> false
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    /**
     * 计算这一轮该发的提醒。alreadySent 是 "key|tag" 的已发送集合。
     * 只提醒未完成、有截止时间、还没过期、且进入某个档位的作业。
     */
    fun dueReminders(
        pending: List<XxtHomework>,
        now: Long,
        thresholds: List<Int> = DEFAULT_THRESHOLDS,
        alreadySent: Set<String> = emptySet(),
    ): List<HomeworkReminder> {
        val out = mutableListOf<HomeworkReminder>()
        for (item in pending) {
            if (item.done) continue
            val remain = remainHours(item.deadlineAt, now) ?: continue
            if (remain <= 0) continue
            val bucket = bucket(remain, thresholds) ?: continue
            val tag = "h$bucket"
            if ("${item.key}|$tag" in alreadySent) continue
            out += HomeworkReminder(
                key = item.key,
                tag = tag,
                title = "${bucket} 小时内截止：${item.title}",
                body = buildString {
                    if (item.courseName.isNotBlank()) append(item.courseName).append("\n")
                    append(countdownText(item.deadlineAt, now))
                    append("（").append(formatDeadline(item.deadlineAt)).append("）")
                },
                urgent = bucket <= 1,
            )
        }
        return out
    }

    /** 新通知提醒：只提醒未读、且没发过的。 */
    fun newNoticeReminders(
        notices: List<XxtNotice>,
        alreadySent: Set<String> = emptySet(),
    ): List<HomeworkReminder> = notices
        .filter { !it.read && "${it.noticeId}|notice" !in alreadySent }
        .map { notice ->
            HomeworkReminder(
                key = notice.noticeId,
                tag = "notice",
                title = "学习通通知：${notice.title}",
                body = buildString {
                    if (notice.sender.isNotBlank()) appendLine(notice.sender)
                    append(notice.content.take(120))
                },
                urgent = false,
            )
        }

    /** 新作业提醒：本轮首次出现、且还没发过 new。 */
    fun newHomeworkReminders(
        fresh: List<XxtHomework>,
        now: Long,
        alreadySent: Set<String> = emptySet(),
    ): List<HomeworkReminder> = fresh
        .filter { !it.done && "${it.key}|new" !in alreadySent }
        .map { item ->
            HomeworkReminder(
                key = item.key,
                tag = "new",
                title = "新作业：${item.title}",
                body = buildString {
                    if (item.courseName.isNotBlank()) append(item.courseName).append("\n")
                    if (item.deadlineAt > 0) append(countdownText(item.deadlineAt, now)).append("截止")
                    else append("尚未公布截止时间")
                },
                urgent = false,
            )
        }
}
