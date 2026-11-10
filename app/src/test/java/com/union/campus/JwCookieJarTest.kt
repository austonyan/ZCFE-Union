package com.union.campus

import com.union.campus.data.remote.jw.UnionCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 教务 CookieJar 回归测试。
 *
 * authserver 与 jw 各写一个 JSESSIONID；旧实现按 name 去重并共用同一个 key，
 * 会把教务域的会话丢掉，导致登录成功后仍然“未能建立会话”。
 */
class JwCookieJarTest {

    private fun jsession(domain: String, value: String) = Cookie.Builder()
        .name("JSESSIONID")
        .value(value)
        .domain(domain)
        .path("/")
        .build()

    @Test
    fun keepsAuthserverAndJwSessionsSeparately() {
        val jar = UnionCookieJar()
        jar.saveFromResponse(
            "https://authserver.zzife.edu.cn/".toHttpUrl(),
            listOf(jsession("authserver.zzife.edu.cn", "auth-session")),
        )
        jar.saveFromResponse(
            "https://jw.zzife.edu.cn/".toHttpUrl(),
            listOf(jsession("jw.zzife.edu.cn", "jw-session")),
        )

        val forJw = jar.loadForRequest("https://jw.zzife.edu.cn/frame/homes.html".toHttpUrl())
        assertEquals(listOf("jw-session"), forJw.map { it.value })
        assertEquals(2, jar.snapshot().size)
    }

    @Test
    fun replacesSameNameDomainAndPath() {
        val jar = UnionCookieJar()
        jar.saveFromResponse("https://jw.zzife.edu.cn/".toHttpUrl(), listOf(jsession("jw.zzife.edu.cn", "old")))
        jar.saveFromResponse("https://jw.zzife.edu.cn/".toHttpUrl(), listOf(jsession("jw.zzife.edu.cn", "new")))

        assertEquals(
            listOf("new"),
            jar.loadForRequest("https://jw.zzife.edu.cn/".toHttpUrl()).map { it.value },
        )
    }
}