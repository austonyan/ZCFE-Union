package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.store.SettingsStore
import kotlinx.coroutines.flow.first
import com.union.campus.domain.TodoItem
import com.union.campus.domain.XxtHomeworkRules
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * 待办聚合：评教 / 教材 / 考试倒计时 / 低电费 / 成绩更新，来源各仓库最近一次拉取结果。
 */
class TodoRepository constructor(
    private val jw: JwRepository,
    private val electric: ElectricRepository,
    private val xxt: XxtRepository,
    private val settings: SettingsStore,
) {
    /** 从缓存聚合本地待办（不主动打网络；各屏刷新时更新来源缓存）。 */
    suspend fun localTodos(): List<TodoItem> {
        if (!settings.remindEnabled.first()) return emptyList()
        val todos = mutableListOf<TodoItem>()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val today = LocalDate.now()

        // 评教：待评数（来自评教页最近拉取）
        if (settings.remindEvaluation.first()) jw.let {
            val pending = try {
                it.pendingEvaluations()
            } catch (_: AppError) {
                emptyList()
            }
            val pendingCount = pending.count { it.isPending }
            if (pendingCount > 0) {
                todos += TodoItem(
                    id = "evaluations",
                    source = "jw",
                    type = "evaluation",
                    title = "待评 $pendingCount 门",
                    subtitle = "截止以教务为准 · 请逐门确认提交",
                    dueText = "",
                    deepLink = "evaluation",
                )
            }
        }

        // 考试倒计时
        if (settings.remindExam.first()) runCatching {
            val sem = jw.currentSemesterDm()
            if (sem.isNotBlank()) {
                val exams = jw.exams(sem)
                // 只保留今天及以后的考试；没有日期的保留（无法判断）
                val upcoming = exams.exams
                    .filter { examDate(it.time)?.isBefore(today) != true }
                    .minByOrNull { examDate(it.time) ?: LocalDate.MAX }
                if (upcoming != null) {
                    todos += TodoItem(
                        id = "exam_countdown",
                        source = "jw",
                        type = "exam",
                        title = "考试倒计时",
                        subtitle = "${upcoming.courseName} · ${upcoming.time}",
                        deepLink = "exams",
                    )
                }
            }
        }

        // 低电费
        if (settings.remindLowElectric.first()) runCatching {
            val threshold = settings.lowElectricThreshold.first()
            val rooms = electric.rooms()
            rooms.firstOrNull()?.let { room ->
                val balance = electric.balance(room.roomVerify)
                val kwh = balance?.kwh?.toDoubleOrNull()
                if (kwh != null && kwh < threshold) {
                    todos += TodoItem(
                        id = "low_electric",
                        source = "electric",
                        type = "lowElectric",
                        title = "电费低于 $threshold 度",
                        subtitle = "${room.fullName} · 剩余 ${balance?.kwh ?: "?"} 度 · ${fmt.format(Date())}",
                        deepLink = "electric",
                    )
                }
            }
        }
        // 学习通作业：未交且三天内要交的，折叠成一条待办（明细在作业页）
        if (settings.remindXxtHomework.first()) runCatching {
            val now = System.currentTimeMillis()
            val pending = xxt.pendingHomework().filter { it.deadlineAt > now }
            val soon = pending.filter { it.deadlineAt <= now + 3 * 24 * 3600_000L }
            val nearest = (if (soon.isNotEmpty()) soon else pending).minByOrNull { it.deadlineAt }
            if (nearest != null) {
                todos += TodoItem(
                    id = "xxt_homework",
                    source = "xxt",
                    type = "homework",
                    title = if (soon.size > 1) "学习通 ${soon.size} 项作业待交"
                    else "学习通作业：${nearest.title}",
                    subtitle = "${nearest.courseName} · ${XxtHomeworkRules.countdownText(nearest.deadlineAt)}",
                    dueText = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                        .format(Date(nearest.deadlineAt)),
                    deepLink = "homework",
                )
            }
        }
        return todos.filterNot { isTodoExpired(it, today) }
    }
}

/** 待办是否已过期：dueText 或考试日期早于今天。 */
internal fun isTodoExpired(todo: TodoItem, today: LocalDate = LocalDate.now()): Boolean {
    val due = parseDate(todo.dueText)
    val exam = if (todo.type == "exam") parseDate(todo.subtitle) else null
    return (due != null && due.isBefore(today)) || (exam != null && exam.isBefore(today))
}

/** 从文本中提取 yyyy-MM-dd；解析不到返回 null（不误删无日期待办）。 */
private fun parseDate(text: String): LocalDate? {
    val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(text) ?: return null
    return runCatching {
        LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }.getOrNull()
}

private fun examDate(text: String): LocalDate? = parseDate(text)
