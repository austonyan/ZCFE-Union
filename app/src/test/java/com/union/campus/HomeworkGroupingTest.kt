package com.union.campus.ui.screens.homework

import com.union.campus.domain.XxtHomework
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/** 作业列表分组：按剩余时间分桶，已交/未知/过期各自独立，不做装饰性排序。 */
class HomeworkGroupingTest {

    private val now = 1_800_000_000_000L

    private fun hw(key: String, hoursLeft: Double?, done: Boolean = false) = XxtHomework(
        key = key,
        courseId = "1",
        clazzId = "2",
        courseName = "高等数学",
        title = "作业 $key",
        status = if (done) "已提交" else "未提交",
        url = "",
        done = done,
        deadlineAt = hoursLeft?.let { now + (it * TimeUnit.HOURS.toMillis(1)).toLong() } ?: 0L,
    )

    @Test
    fun splitsByRemainingTime() {
        val groups = homeworkGroups(
            listOf(
                hw("a", 5.0),
                hw("b", 30.0),
                hw("c", 100.0),
                hw("d", null),
                hw("e", -2.0),
                hw("f", 5.0, done = true),
            ),
            now,
        )
        assertEquals(
            listOf("24 小时内 · 1", "3 天内 · 1", "更晚 · 1", "截止未知 · 1", "已过期 · 1", "已交 · 1"),
            groups.map { it.title },
        )
        // 每组内按截止时间升序
        assertEquals("作业 a", groups.first().items.single().title)
    }

    @Test
    fun keepsOrderWhenOnlyPendingIsVisible() {
        val groups = homeworkGroups(listOf(hw("a", 5.0), hw("b", 5.0, done = true)), now)
        assertEquals(listOf("24 小时内 · 1", "已交 · 1"), groups.map { it.title })
    }
}
