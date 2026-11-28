package com.union.campus

import com.union.campus.core.AccountCheck
import com.union.campus.core.AccountCheckState
import com.union.campus.ui.screens.me.AccountCheckUi
import com.union.campus.ui.screens.me.AccountPlatform
import com.union.campus.ui.screens.me.accountCheckSummary
import com.union.campus.ui.screens.me.accountStatusLabel
import com.union.campus.ui.screens.me.toUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 账号体系文案：四个平台、一个状态位置。
 *
 * 这层测试保护两件事：
 * 1. 学习通正式进入账号体系（标签与顺序固定，不能再退回「三套账号」）；
 * 2. 徽标在同一位置先表达绑定状态、体检后表达结论，不会两套说法并存。
 */
class AccountStatusCopyTest {

    @Test
    fun fourPlatformsKeepLearningAndLifeOrder() {
        assertEquals(
            listOf("教务", "学习通", "洗浴", "电费"),
            AccountPlatform.All.map { it.label },
        )
    }

    @Test
    fun badgeShowsBindingStateBeforeAnyCheck() {
        assertEquals("已保存", accountStatusLabel(bound = true, state = AccountCheckUi.Idle))
        assertEquals("未绑定", accountStatusLabel(bound = false, state = AccountCheckUi.Idle))
        assertEquals("检查中", accountStatusLabel(bound = true, state = AccountCheckUi.Checking))
    }

    @Test
    fun checkResultReplacesBindingState() {
        assertEquals("正常", accountStatusLabel(bound = true, state = AccountCheckUi.Ok))
        assertEquals("登录失效", accountStatusLabel(bound = true, state = AccountCheckUi.Expired))
        assertEquals("暂不可用", accountStatusLabel(bound = true, state = AccountCheckUi.Unavailable))
        // 检查后未绑定的平台也统一显示「未绑定」，不显示「已保存」
        assertEquals("未绑定", accountStatusLabel(bound = false, state = AccountCheckUi.Unbound))
    }

    @Test
    fun accountCheckMapsToOneUiState() {
        assertEquals(AccountCheckUi.Ok, AccountCheck(AccountCheckState.OK).toUiState())
        assertEquals(AccountCheckUi.Unbound, AccountCheck(AccountCheckState.UNBOUND).toUiState())
        assertEquals(AccountCheckUi.Expired, AccountCheck(AccountCheckState.EXPIRED).toUiState())
        assertEquals(
            AccountCheckUi.Unavailable,
            AccountCheck(AccountCheckState.UNAVAILABLE, "网络连接失败").toUiState(),
        )
    }

    @Test
    fun summaryNamesEveryFailingPlatform() {
        val results = listOf(
            AccountPlatform.Jw to AccountCheck(AccountCheckState.OK),
            AccountPlatform.Xxt to AccountCheck(AccountCheckState.EXPIRED),
            AccountPlatform.Shower to AccountCheck(AccountCheckState.UNAVAILABLE, "网络连接失败"),
            AccountPlatform.Electric to AccountCheck(AccountCheckState.UNBOUND),
        )
        val summary = accountCheckSummary(results)
        assertEquals(true, summary.contains("学习通：登录失效，请重新登录"))
        assertEquals(true, summary.contains("洗浴：网络连接失败"))
        assertEquals(true, summary.contains("电费：未绑定"))
        assertEquals(false, summary.contains("教务："))
    }

    @Test
    fun allHealthySummaryIsOneSentence() {
        val results = AccountPlatform.All.map { it to AccountCheck(AccountCheckState.OK) }
        assertEquals("四个平台凭证正常", accountCheckSummary(results))
    }
}