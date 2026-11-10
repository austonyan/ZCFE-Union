package com.union.campus

import com.union.campus.data.remote.jw.parseCheckNeedCaptcha
import com.union.campus.data.remote.jw.parseLegacyNeedCaptcha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务验证码探测回归测试。
 *
 * 背景：学校 CAS 定制主题使用 `checkNeedCaptcha.htl` 返回 JSON；
 * 旧实现调用 `needCaptcha.html` 并对整页做 contains("true")，
 * 而错误页里的 JS 含 `isMobile = "true"`，导致任何账号都被判定“需要验证码”，
 * 登录在提交密码前就被中止。
 */
class CasCaptchaTest {

    @Test
    fun jsonEndpoint_noCaptcha() {
        assertEquals(false, parseCheckNeedCaptcha("""{"isNeed":false}"""))
        assertEquals(false, parseCheckNeedCaptcha("""{"isNeed":"false"}"""))
    }

    @Test
    fun jsonEndpoint_needsCaptcha() {
        assertEquals(true, parseCheckNeedCaptcha("""{"isNeed":true}"""))
        assertEquals(true, parseCheckNeedCaptcha("""{"isNeed":"true"}"""))
    }

    @Test
    fun errorHtml_isNotTreatedAsCaptcha() {
        // 真实 needCaptcha.html 错误页片段：含 "true" 但不是验证码要求
        val html = """
            <!DOCTYPE html><html><body>
            <div class="tip_lv2">请求不合法，请联系管理员！</div>
            <script>var isMobile = "true"; var isLogin = "";</script>
            </body></html>
        """.trimIndent()
        assertNull(parseCheckNeedCaptcha(html))
        assertFalse(parseLegacyNeedCaptcha(html))
    }

    @Test
    fun legacyTextEndpoint_isParsed() {
        assertTrue(parseLegacyNeedCaptcha("true"))
        assertTrue(parseLegacyNeedCaptcha("TRUE\n"))
        assertFalse(parseLegacyNeedCaptcha("false"))
        assertFalse(parseLegacyNeedCaptcha(""))
    }
}