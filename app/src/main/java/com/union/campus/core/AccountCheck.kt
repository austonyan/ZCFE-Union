package com.union.campus.core

/**
 * 账号检查结果。
 *
 * 以前三套账号的体检只返回一个布尔值，导致「未绑定」「登录失效」「网络连不上」
 * 都被显示成「异常/未绑定」；洗浴还额外依赖内存里的会话状态，容易在进程恢复后
 * 把已保存的凭证误判为未绑定。这里把结论拆开，UI 才能给出可执行的原因。
 */
enum class AccountCheckState {
    /** 凭证可用 */
    OK,

    /** 本地没有保存凭证 */
    UNBOUND,

    /** 已保存凭证但服务端判定登录失效，需要重新登录 */
    EXPIRED,

    /** 凭证可能有效，但当前网络/服务不可用，无法验证 */
    UNAVAILABLE,
}

data class AccountCheck(
    val state: AccountCheckState,
    /** 失败时的可读原因，例如「网络连接失败，请检查网络并重试」。 */
    val detail: String? = null,
) {
    val isOk: Boolean get() = state == AccountCheckState.OK
}

/** 把探测异常映射为检查结论：鉴权错误才算登录失效，其余一律按“暂时无法验证”处理。 */
internal fun Throwable.toAccountCheck(): AccountCheck = when (this) {
    is AppError.AuthExpired -> AccountCheck(AccountCheckState.EXPIRED)
    is AppError.BadCredentials -> AccountCheck(AccountCheckState.EXPIRED, userMessage)
    is AppError -> AccountCheck(AccountCheckState.UNAVAILABLE, userMessage)
    else -> AccountCheck(AccountCheckState.UNAVAILABLE)
}

/**
 * 合并同一账号多个探测端点的结果。
 *
 * - 任一端点成功即视为账号可用（单个接口的业务限制不代表登录失效）；
 * - 都没有成功时，登录失效比网络/上游异常更确定，优先返回；
 * - 都不可用时取最后一个失败原因（调用方把更可信的端点放在后面）。
 */
internal fun combineAccountChecks(checks: List<AccountCheck>): AccountCheck {
    if (checks.isEmpty()) return AccountCheck(AccountCheckState.UNAVAILABLE)
    checks.firstOrNull { it.isOk }?.let { return it }
    checks.firstOrNull { it.state == AccountCheckState.EXPIRED }?.let { return it }
    return checks.lastOrNull { it.detail != null } ?: checks.last()
}
