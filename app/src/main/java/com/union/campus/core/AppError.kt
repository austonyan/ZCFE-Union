package com.union.campus.core

/** 统一错误模型：每类错误给出可解释、可执行的文案。 */
sealed class AppError(val userMessage: String, val debugDetail: String? = null) {
    /** 网络不可达 / 超时 */
    class Network(detail: String? = null) : AppError("网络连接失败，请检查网络后重试", detail)

    /** 凭证失效，需要重新登录（教务会话 / 洗浴 token / 电费学号失效） */
    class AuthExpired(val module: Module, detail: String? = null) :
        AppError("${module.label}登录已失效，请重新登录", detail)

    /** 账号或密码错误 */
    class BadCredentials(val module: Module, detail: String? = null) :
        AppError("${module.label}账号或密码不正确", detail)

    /** 上游返回了无法理解的响应（协议变更等） */
    class Upstream(val detail: String? = null) : AppError("数据源返回异常，请稍后重试", detail)

    /** 输入不合法 */
    class Validation(val message: String) : AppError(message)

    /** 其它未知错误 */
    class Unknown(detail: String? = null) : AppError("出错了，请稍后重试", detail)

    enum class Module(val label: String) {
        JW("教务"), SHOWER("洗浴"), ELECTRIC("电费"),
    }
}

/** suspend 帮助：把异常映射为 AppError。 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    else -> AppError.Unknown(message)
}
