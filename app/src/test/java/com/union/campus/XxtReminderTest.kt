package com.union.campus

import com.union.campus.domain.XxtHomework
import com.union.campus.domain.XxtHomeworkRules
import com.union.campus.domain.XxtNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** 提醒规则：分档、去重、新作业与加急标记。 */
class XxtReminderTest {

    private val now = 1_800_000_000_000L

    private fun homework(
        key: String,
        hoursLeft: Double,
        done: Boolean = false,
    ) = XxtHomework(
        key = key,
        courseId = "1",
        clazzId = "2",
        courseName = "高等数学",
        title = "作业 $key",
        status = if (done) "已提交" else "未提交",
        url = "https://mooc1.chaoxing.com/work?workId=$key",
        done = done,
        deadlineRaw = "11-28 23:59",
        deadlineAt = now + (hoursLeft * TimeUnit.HOURS.toMillis(1)).toLong(),
    )

    @Test
    fun onlyNearestBucketFires() {
        // 30 小时：还没进任何档位
        assertTrue(XxtHomeworkRules.dueReminders(listOf(homework("a", 30.0)), now).isEmpty())
        // 20 小时：进 24h 档
        val h24 = XxtHomeworkRules.dueReminders(listOf(homework("a", 20.0)), now)
        assertEquals(1, h24.size)
        assertEquals("h24", h24[0].tag)
        // 5 小时：只补 6h 档（更近的一档），不会再补 24h
        val h6 = XxtHomeworkRules.dueReminders(listOf(homework("a", 5.0)), now)
        assertEquals("h6", h6[0].tag)
        assertEquals(1, h6.size)
    }

    @Test
    fun dedupesByKeyAndTag() {
        val sent = setOf("a|h24")
        assertTrue(XxtHomeworkRules.dueReminders(listOf(homework("a", 20.0)), now, alreadySent = sent).isEmpty())
        // 换一条作业仍然提醒
        assertEquals(1, XxtHomeworkRules.dueReminders(listOf(homework("b", 20.0)), now, alreadySent = sent).size)
    }

    @Test
    fun skipsDoneAndOverdueAndUnknown() {
        val items = listOf(
            homework("done", 2.0, done = true),
            homework("overdue", -1.0),
            homework("unknown", 0.0).copy(deadlineAt = 0L),
        )
        assertTrue(XxtHomeworkRules.dueReminders(items, now).isEmpty())
    }

    @Test
    fun oneHourBucketIsUrgent() {
        val reminder = XxtHomeworkRules.dueReminders(listOf(homework("a", 0.5)), now).single()
        assertEquals("h1", reminder.tag)
        assertTrue(reminder.urgent)
    }

    @Test
    fun newHomeworkWarnsOnce() {
        val items = listOf(homework("a", 100.0), homework("b", 100.0, done = true))
        val first = XxtHomeworkRules.newHomeworkReminders(items, now)
        assertEquals(1, first.size)
        assertEquals("new", first[0].tag)
        val second = XxtHomeworkRules.newHomeworkReminders(items, now, setOf("a|new"))
        assertTrue(second.isEmpty())
    }

    @Test
    fun reminderBodyCarriesCourseAndDeadline() {
        val reminder = XxtHomeworkRules.dueReminders(listOf(homework("a", 20.0)), now).single()
        assertTrue(reminder.title.contains("24 小时内截止"))
        assertTrue(reminder.body.contains("高等数学"))
        assertTrue(reminder.body.contains("还有"))
    }

    @Test
    fun noticeRemindersOnlyForUnreadAndOnce() {
        val notices = listOf(
            XxtNotice(noticeId = "n1", title = "考试安排", content = "本周五确认考场", sender = "教务处"),
            XxtNotice(noticeId = "n2", title = "已读的通知", read = true),
        )
        val first = XxtHomeworkRules.newNoticeReminders(notices)
        assertEquals(1, first.size)
        assertEquals("notice", first[0].tag)
        assertTrue(first[0].title.contains("考试安排"))
        assertTrue(first[0].body.contains("教务处"))
        assertTrue(XxtHomeworkRules.newNoticeReminders(notices, setOf("n1|notice")).isEmpty())
    }
}
