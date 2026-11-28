package com.union.campus

import com.union.campus.core.AppError
import com.union.campus.core.toAppError
import com.union.campus.core.userFacingMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 容错原则：屏幕提示只展示用户能看懂的说法。
 * 原始异常文案（类名、HTTP 片段、堆栈首行）留在 debugDetail 里，不上屏。
 */
class AppErrorCopyTest {

    @Test
    fun unknownExceptionDoesNotShowRawMessage() {
        val raw = RuntimeException("java.net.UnknownHostException: xxt.chaoxing.com")
        assertEquals("操作失败，请稍后重试", raw.userFacingMessage())
        val mapped = raw.toAppError()
        assertTrue(mapped is AppError.Unknown)
        assertEquals("操作失败，请稍后重试", mapped.userMessage)
        // 原始文案仍保留在 debugDetail，便于排查但不直接展示。
        assertEquals("java.net.UnknownHostException: xxt.chaoxing.com", (mapped as AppError.Unknown).debugDetail)
    }

    @Test
    fun ioExceptionMapsToNetworkCopy() {
        val timeout = java.io.IOException("timeout after 10000ms")
        assertEquals(AppError.Network().userMessage, timeout.userFacingMessage())
        val mapped = timeout.toAppError()
        assertTrue(mapped is AppError.Network)
        assertEquals("timeout after 10000ms", (mapped as AppError.Network).debugDetail)
    }

    @Test
    fun appErrorKeepsItsOwnCopy() {
        val validation = AppError.Validation("请输入手机号")
        assertEquals("请输入手机号", validation.userFacingMessage())
        assertSame(validation, validation.toAppError())

        val expired = AppError.AuthExpired(AppError.Module.JW)
        assertEquals("教务登录失效，请重新登录", expired.userFacingMessage())
    }
}