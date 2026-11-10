package com.union.campus

import com.union.campus.data.repo.isTodoExpired
import com.union.campus.domain.TodoItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 待办过期校验：早于今天的考试/截止日期不再显示。 */
class TodoFilterTest {

    private val today = LocalDate.of(2026, 11, 9)

    @Test
    fun pastExamIsExpired() {
        val todo = TodoItem(
            id = "exam",
            source = "jw",
            type = "exam",
            title = "考试倒计时",
            subtitle = "软件工程 · 2026-06-27(16周 星期六)13:30-15:30",
            deepLink = "exams",
        )
        assertTrue(isTodoExpired(todo, today))
    }

    @Test
    fun futureExamIsNotExpired() {
        val todo = TodoItem(
            id = "exam",
            source = "jw",
            type = "exam",
            title = "考试倒计时",
            subtitle = "软件工程 · 2027-01-05(18周 星期二)09:00-11:00",
            deepLink = "exams",
        )
        assertFalse(isTodoExpired(todo, today))
    }

    @Test
    fun undatedTodoIsKept() {
        val todo = TodoItem(
            id = "eval",
            source = "jw",
            type = "evaluation",
            title = "评教还有 2 门",
            subtitle = "截止以教务为准",
            deepLink = "evaluation",
        )
        assertFalse(isTodoExpired(todo, today))
    }

    @Test
    fun pastDueTextIsExpired() {
        val todo = TodoItem(
            id = "local",
            source = "local",
            type = "task",
            title = "交材料",
            subtitle = "学院办公室",
            dueText = "2026-11-01 17:00",
            deepLink = "notices",
        )
        assertTrue(isTodoExpired(todo, today))
    }
}