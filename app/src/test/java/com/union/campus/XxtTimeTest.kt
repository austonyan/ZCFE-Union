package com.union.campus

import com.union.campus.domain.XxtHomeworkRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 学习通只有 "MM-DD HH:MM" 的时间补年份、倒计时与分档规则。 */
class XxtTimeTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 27)

    private fun asDateTime(millis: Long): LocalDateTime =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    @Test
    fun picksNearestYearAcrossNewYear() {
        // 12-01 距今天 4 天 → 今年
        assertEquals(
            LocalDateTime.of(2026, 12, 1, 9, 30),
            asDateTime(XxtHomeworkRules.nearestDeadline("12-01 09:30", today, zone)),
        )
        // 01-05 距今年 326 天、距明年 39 天 → 明年
        assertEquals(
            LocalDateTime.of(2027, 1, 5, 8, 0),
            asDateTime(XxtHomeworkRules.nearestDeadline("01-05 08:00", today, zone)),
        )
    }

    @Test
    fun unknownDeadlineStaysZero() {
        assertEquals(0L, XxtHomeworkRules.nearestDeadline("截止未知", today, zone))
        assertEquals(0L, XxtHomeworkRules.nearestDeadline("", today, zone))
    }

    @Test
    fun bucketsByThreshold() {
        assertEquals(24, XxtHomeworkRules.bucket(23.5))
        assertEquals(6, XxtHomeworkRules.bucket(6.0))
        assertEquals(1, XxtHomeworkRules.bucket(0.4))
        assertNull(XxtHomeworkRules.bucket(30.0))
    }

    @Test
    fun countdownTextInChinese() {
        val now = 1_800_000_000_000L
        assertEquals("还有 3 天 5 小时", XxtHomeworkRules.countdownText(now + 3 * 86400_000L + 5 * 3600_000L, now))
        assertEquals("还有 45 分钟", XxtHomeworkRules.countdownText(now + 45 * 60_000L, now))
        assertTrue(XxtHomeworkRules.countdownText(now - 2 * 3600_000L, now).startsWith("已过期"))
        assertEquals("截止未知", XxtHomeworkRules.countdownText(0L, now))
    }

    @Test
    fun quietHoursAcrossMidnight() {
        assertTrue(XxtHomeworkRules.inQuietHours(23, 23, 7))
        assertTrue(XxtHomeworkRules.inQuietHours(6, 23, 7))
        assertTrue(!XxtHomeworkRules.inQuietHours(12, 23, 7))
        assertTrue(!XxtHomeworkRules.inQuietHours(23, 7, 7))
    }

    @Test
    fun statusMarksCountAsDone() {
        assertTrue(XxtHomeworkRules.isPending("未提交"))
        assertTrue(XxtHomeworkRules.isPending(""))
        assertTrue(!XxtHomeworkRules.isPending("已提交"))
        assertTrue(!XxtHomeworkRules.isPending("待批阅"))
        assertTrue(!XxtHomeworkRules.isPending("已互评"))
    }

    @Test
    fun assignmentKeyPrefersWorkId() {
        val withId = XxtHomeworkRules.assignmentKey(
            "1", "2", "https://mooc1.chaoxing.com/work/doHomeWorkNew?workId=998877", "作业",
        )
        assertEquals("1:2:998877", withId)
        val withoutId = XxtHomeworkRules.assignmentKey("1", "2", "https://x/other", "作业")
        assertTrue(withoutId.startsWith("1:2:t"))
    }
}
