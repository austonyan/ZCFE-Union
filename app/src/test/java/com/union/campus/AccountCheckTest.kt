package com.union.campus

import com.union.campus.core.AccountCheck
import com.union.campus.core.AccountCheckState
import com.union.campus.core.AppError
import com.union.campus.core.combineAccountChecks
import com.union.campus.core.toAccountCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 账号体检：区分未绑定、登录失效与网络不可用，并允许多端点互补。 */
class AccountCheckTest {

    @Test
    fun authExpiredMeansLoginExpired() {
        assertEquals(
            AccountCheckState.EXPIRED,
            AppError.AuthExpired(AppError.Module.SHOWER).toAccountCheck().state,
        )
    }

    @Test
    fun badCredentialsMeansLoginExpired() {
        assertEquals(
            AccountCheckState.EXPIRED,
            AppError.BadCredentials(AppError.Module.JW).toAccountCheck().state,
        )
    }

    @Test
    fun networkErrorIsNotReportedAsUnbound() {
        val check = AppError.Network("timeout").toAccountCheck()
        assertEquals(AccountCheckState.UNAVAILABLE, check.state)
        assertEquals(AppError.Network().userMessage, check.detail)
    }

    @Test
    fun upstreamAndUnknownErrorsAreUnavailable() {
        assertEquals(
            AccountCheckState.UNAVAILABLE,
            AppError.Upstream("bad envelope").toAccountCheck().state,
        )
        assertEquals(
            AccountCheckState.UNAVAILABLE,
            IllegalStateException("boom").toAccountCheck().state,
        )
    }

    /** 单个端点因业务限制失败，只要另一个端点成功，账号就算正常。 */
    @Test
    fun oneSuccessfulProbeMakesAccountOk() {
        val combined = combineAccountChecks(
            listOf(
                AccountCheck(AccountCheckState.UNAVAILABLE, "非洗浴服务时间，请按开放时间使用"),
                AccountCheck(AccountCheckState.OK),
            ),
        )
        assertTrue(combined.isOk)
    }

    /** 鉴权失败的结论比网络/上游错误更确定。 */
    @Test
    fun expiredWinsOverUnavailable() {
        val combined = combineAccountChecks(
            listOf(
                AccountCheck(AccountCheckState.EXPIRED),
                AccountCheck(AccountCheckState.UNAVAILABLE, "网络连接失败，请检查网络并重试"),
            ),
        )
        assertEquals(AccountCheckState.EXPIRED, combined.state)
    }

    @Test
    fun unavailableKeepsTheLastReason() {
        val combined = combineAccountChecks(
            listOf(
                AccountCheck(AccountCheckState.UNAVAILABLE, "数据源异常，请稍后重试"),
                AccountCheck(AccountCheckState.UNAVAILABLE, "网络连接失败，请检查网络并重试"),
            ),
        )
        assertEquals(AccountCheckState.UNAVAILABLE, combined.state)
        assertEquals("网络连接失败，请检查网络并重试", combined.detail)
        assertFalse(combined.isOk)
    }
}
