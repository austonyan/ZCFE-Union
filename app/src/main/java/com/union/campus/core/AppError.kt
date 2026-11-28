package com.union.campus.core

/** 统一错误模型：每类错误给出可解释、可执行的文案。 */
sealed class AppError(val userMessage: String, val debugDetail: String? = null) : Exception(userMessage) {
    /** 网络不可达 / 超时 */
    class Network(detail: String? = null) : AppError("网络连接失败，请检查网络并重试", detail)

    /** 凭证失效，需要重新登录（教务会话 / 洗浴 token / 电费学号失效） */
    class AuthExpired(val module: Module, detail: String? = null) :
        AppError("${module.label}登录失效，请重新登录", detail)

    /** 账号或密码错误 */
    class BadCredentials(val module: Module, detail: String? = null) :
        AppError("${module.label}账号或密码不正确", detail)

    /** 上游返回了无法理解的响应（协议变更等） */
    class Upstream(val detail: String? = null) : AppError("数据源异常，请稍后重试", detail)

    /** 输入不合法 */
    class Validation(val reason: String) : AppError(reason)

    /** 其它未知错误 */
    class Unknown(detail: String? = null) : AppError("操作失败，请稍后重试", detail)

    enum class Module(val label: String) {
        JW("教务"), SHOWER("洗浴"), ELECTRIC("电费"), XXT("学习通"),
    }
}

/** suspend 帮助：把异常映射为 AppError。原始 message 只留在 debugDetail，不直接展示。 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is java.io.IOException -> AppError.Network(message)
    else -> AppError.Unknown(message)
}

/**
 * 屏幕提示用的文案：AppError 用既定说法，其它异常不把类名/堆栈甩给用户。
 * （容错原则：出错时给用户能看懂的原因，而不是 `java.net.UnknownHostException`。）
 */
fun Throwable.userFacingMessage(): String = when (this) {
    is AppError -> userMessage
    is java.io.IOException -> AppError.Network().userMessage
    else -> AppError.Unknown().userMessage
}
