package com.union.campus.ui.screens.study

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.CourseCell
import com.union.campus.domain.Exam
import com.union.campus.ui.common.launchUi
import kotlinx.coroutines.flow.first

/** 课表路由包装（直接复用 TimetableScreen）。 */
@Composable
fun TimetableRoute(
    onOpenCourse: (CourseCell) -> Unit,
    onOpenGrades: () -> Unit,
    onOpenFreeClassrooms: () -> Unit,
    onOpenTimetableSettings: () -> Unit,
) {
    TimetableScreen(
        onOpenCourse = onOpenCourse,
        onOpenGrades = onOpenGrades,
        onOpenFreeClassrooms = onOpenFreeClassrooms,
        onOpenTimetableSettings = onOpenTimetableSettings,
    )
}

/** 考试路由包装：负责拉取考试数据并传入 ExamsScreen。 */
@Composable
fun ExamsRoute(
    onOpenExam: (Exam) -> Unit,
    onBack: () -> Unit,
) {
    val jw = ServiceLocator.jwRepository
    var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isAuth by remember { mutableStateOf(false) }

    fun load(force: Boolean) {
        loading = true
        error = null
        launchUi {
            try {
                if (!jw.isBound()) {
                    error = "登录教务后可查看考试安排"
                } else {
                    val sem = jw.currentSemesterDm()
                    exams = jw.exams(sem, force).exams
                    error = null
                }
            } catch (e: AppError.AuthExpired) {
                error = e.userMessage
                isAuth = true
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "考试信息获取失败：${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load(false) }

    ExamsScreen(
        exams = exams,
        loading = loading,
        error = error,
        isAuth = isAuth,
        onRetry = { load(true) },
        onOpenExam = onOpenExam,
        onBack = onBack,
    )
}
