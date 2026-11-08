package com.union.campus.ui.common

import com.union.campus.core.AppError
import com.union.campus.core.toAppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 主线程协程助手（屏幕一次性动作）。 */
val UiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

fun launchUi(block: suspend () -> Unit): Job = UiScope.launch {
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // 屏幕内自行 catch；这里兜底避免崩溃
        e.printStackTrace()
    }
}

/** 屏幕级 UI 状态：Loading / Ready(带更新时间) / Empty / Failed。 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val data: T, val updatedAt: Long = 0L) : UiState<T>
    data class Empty(val hint: String = "") : UiState<Nothing>
    data class Failed(val message: String, val isAuth: Boolean = false) : UiState<Nothing>
}

fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Ready<T>)?.data

/** 列表型请求 → UiState；空列表 = 正常空态。 */
suspend fun <T> runListUi(block: suspend () -> List<T>): UiState<List<T>> = try {
    val result = block()
    if (result.isEmpty()) UiState.Empty() else UiState.Ready(result)
} catch (e: AppError.AuthExpired) {
    UiState.Failed(e.userMessage, isAuth = true)
} catch (e: AppError) {
    UiState.Failed(e.userMessage)
} catch (e: Exception) {
    UiState.Failed(e.toAppError().userMessage)
}

/** 单值型请求 → UiState；null 结果 = 空态。 */
suspend fun <T> runSingleUi(block: suspend () -> T?): UiState<T> = try {
    val result = block()
    if (result == null) UiState.Empty() else UiState.Ready(result)
} catch (e: AppError.AuthExpired) {
    UiState.Failed(e.userMessage, isAuth = true)
} catch (e: AppError) {
    UiState.Failed(e.userMessage)
} catch (e: Exception) {
    UiState.Failed(e.toAppError().userMessage)
}

/** 时间戳 → "HH:mm 更新"。 */
fun formatUpdatedAt(ms: Long): String = if (ms <= 0) "" else {
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
    "${fmt.format(java.util.Date(ms))} 更新"
}
