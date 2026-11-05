package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.store.SettingsStore
import kotlinx.coroutines.flow.first
import com.union.campus.domain.TodoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 待办聚合：评教 / 教材 / 考试倒计时 / 低电费 / 成绩更新，来源各仓库最近一次拉取结果。
 */
class TodoRepository constructor(
    private val jw: JwRepository,
    private val electric: ElectricRepository,
    private val settings: SettingsStore,
) {
    /** 从缓存聚合本地待办（不主动打网络；各屏刷新时更新来源缓存）。 */
    suspend fun localTodos(): List<TodoItem> {
        val todos = mutableListOf<TodoItem>()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

        // 评教：待评数（来自评教页最近拉取）
        jw.let {
            val pending = try {
                it.pendingEvaluations()
            } catch (_: AppError) {
                emptyList()
            }
            if (pending.isNotEmpty()) {
                todos += TodoItem(
                    id = "evaluations",
                    source = "jw",
                    type = "evaluation",
                    title = "评教还有 ${pending.size} 门",
                    subtitle = "截止以教务为准 · 逐门确认提交",
                    dueText = "",
                    deepLink = "evaluation",
                )
            }
        }

        // 考试倒计时
        runCatching {
            val sem = settings.currentSemesterDm.first()
            if (sem.isNotBlank()) {
                val exams = jw.exams(sem)
                val upcoming = exams.exams.firstOrNull()
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
        runCatching {
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
        return todos
    }
}
