package com.union.campus.ui.screens.me

import com.union.campus.core.AccountCheck
import com.union.campus.core.AccountCheckState
import com.union.campus.di.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 一次账号体检：三套账号并行探测，任一模块异常不影响其它模块。
 *
 * 结论由各仓库的 `checkAccount()` 给出：未绑定、登录失效与网络不可用分开返回，
 * 不再用「异常/未绑定」笼统覆盖。
 */
internal suspend fun checkAllAccounts(): List<Pair<String, AccountCheck>> = coroutineScope {
    // 绑定判断会读取本地加密凭证，统一放到 IO，避免体检时卡住界面
    val jw = async(Dispatchers.IO) { safely { ServiceLocator.jwRepository.checkAccount() } }
    val shower = async(Dispatchers.IO) { safely { ServiceLocator.showerRepository.checkAccount() } }
    val electric = async(Dispatchers.IO) { safely { ServiceLocator.electricRepository.checkAccount() } }
    listOf(
        "教务" to jw.await(),
        "洗浴" to shower.await(),
        "电费" to electric.await(),
    )
}

private suspend fun safely(block: suspend () -> AccountCheck): AccountCheck = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    AccountCheck(AccountCheckState.UNAVAILABLE)
}

/** 体检结论的展示文案；未绑定与登录失效不再共用同一句话。 */
internal val AccountCheck.displayText: String
    get() = when (state) {
        AccountCheckState.OK -> "正常"
        AccountCheckState.UNBOUND -> "未绑定"
        AccountCheckState.EXPIRED -> "登录失效，请重新登录"
        AccountCheckState.UNAVAILABLE -> detail?.takeIf { it.isNotBlank() } ?: "网络或服务异常，请稍后重试"
    }
