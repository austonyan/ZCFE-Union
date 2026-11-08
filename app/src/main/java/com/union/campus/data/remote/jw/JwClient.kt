package com.union.campus.data.remote.jw

import com.union.campus.core.AppError
import com.union.campus.core.crypto.JwCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * 教务系统客户端：CAS 统一认证 + 喜鹊儿 H5 JSON API。
 * 协议细节依据 allAPI.md §1.2/1.3（郑州财经学院部署）。
 */
class JwClient(
    val cookieJar: UnionCookieJar = UnionCookieJar(),
) {
    val authUrl = "https://authserver.zzife.edu.cn/authserver"
    val jwUrl = "https://jw.zzife.edu.cn"

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    var studentNo: String = ""

    companion object {
        private val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        private val CAS_SERVICE = "https://jw.zzife.edu.cn/caslogin"
    }

    // ---------------- CAS 登录 ----------------

    /** 步骤①：拉登录页，解析 pwdEncryptSalt 与 execution。 */
    private suspend fun fetchLoginPage(): Pair<String, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$authUrl/login?service=${urlEncode(CAS_SERVICE)}")
            .header("User-Agent", UA)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful || body.isEmpty()) throw AppError.Network("登录页响应异常 HTTP ${resp.code}")
            val salt = Regex("id=\"pwdEncryptSalt\"\\s+value=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: Regex("name=\"pwdEncryptSalt\"\\s+value=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            val execution = Regex("id=\"execution\"\\s+value=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: Regex("name=\"execution\"\\s+value=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            if (salt == null || execution == null) {
                // 已登录（带着有效会话访问）时登录页可能被跳转，视为成功
                throw AppError.Upstream("未能在登录页中找到加密参数，可能已处于登录状态")
            }
            salt to execution
        }
    }

    /**
     * 步骤②：是否需要验证码。
     *
     * 学校 CAS 定制主题使用 `checkNeedCaptcha.htl`，返回 JSON `{"isNeed":false}`；
     * 旧版 Apereo 主题的 `needCaptcha.html` 返回纯文本 `true`/`false`。
     * 注意不能对 HTML 页面做 contains("true") 判断：错误页里的 JS 也含 "true"。
     */
    suspend fun needCaptcha(username: String, salt: String = ""): Boolean = withContext(Dispatchers.IO) {
        val jsonUrl = "$authUrl/checkNeedCaptcha.htl".toHttpUrl().newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val jsonBody = runCatching {
            val req = Request.Builder()
                .url(jsonUrl)
                .header("User-Agent", UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/plain, */*")
                .get()
                .build()
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        }.getOrDefault("")
        val isNeed = parseCheckNeedCaptcha(jsonBody)
        if (isNeed != null) return@withContext isNeed

        // 兼容旧版 CAS：纯文本 true/false（只看开头，不扫描整页）
        val legacyUrl = "$authUrl/needCaptcha.html".toHttpUrl().newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("pwdEncrypt2", salt)
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val legacyBody = runCatching {
            val req = Request.Builder().url(legacyUrl).header("User-Agent", UA).get().build()
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        }.getOrDefault("")
        parseLegacyNeedCaptcha(legacyBody)
    }

    /** 拉取验证码图片（需与登录共用 CookieJar）。 */
    suspend fun fetchCaptchaImage(username: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "$authUrl/getCaptcha.htl".toHttpUrl().newBuilder()
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "$authUrl/login")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw AppError.Network("验证码获取失败 HTTP ${resp.code}")
            resp.body?.bytes() ?: throw AppError.Network("验证码获取失败")
        }
    }

    /**
     * 步骤③④：提交登录并建立教务会话。
     * @throws AppError.BadCredentials 密码错误（errormessages 解析）
     * @throws AppError.AuthExpired / Upstream 其它异常
     */
    suspend fun login(username: String, password: String, captcha: String = "") = withContext(Dispatchers.IO) {
        studentNo = username
        val (salt, execution) = try {
            fetchLoginPage()
        } catch (e: AppError.Upstream) {
            // 可能已登录：直接探测 homes
            if (verifySession()) return@withContext
            throw e
        }

        val captchaNeeded = captcha.isBlank() && needCaptcha(username, salt)
        if (captchaNeeded) {
            throw AppError.Validation("需要图形验证码，请输入验证码后重试")
        }

        val form = FormBody.Builder()
            .add("username", username)
            .add("password", JwCrypto.encryptCasPassword(password, salt))
            .add("captcha", captcha)
            .add("_eventId", "submit")
            .add("cllt", "userNameLogin")
            .add("dllt", "generalLogin")
            .add("lt", "")
            .add("execution", execution)
            .add("rememberMe", "true")
            .add("rmShown", "1")
            .build()
        // 必须带 service 参数：CAS 对不带 service 的 POST 直接返回 500
        val postUrl = "$authUrl/login?service=${urlEncode(CAS_SERVICE)}"
        val req = Request.Builder()
            .url(postUrl)
            .header("User-Agent", UA)
            .header("Referer", postUrl)
            .post(form)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw AppError.Upstream("登录请求失败 HTTP ${resp.code}")
            val body = resp.body?.string() ?: ""
            // 密码错误：返回登录页含 errormessages
            val errMsg = Regex("id=\"(?:msg|errorMsg)\"[^>]*>([^<]{2,80})<")
                .find(body)?.groupValues?.get(1)
                ?: Regex("\"errormessages\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            if (errMsg != null && errMsg.isNotBlank() && !errMsg.contains("成功")) {
                throw AppError.BadCredentials(AppError.Module.JW, errMsg)
            }
            // CAS 盐过期会返回新的登录页且无错误文案
            if (body.contains("pwdEncryptSalt") && errMsg == null) {
                throw AppError.AuthExpired(AppError.Module.JW, "CAS execution 过期，请重试")
            }
        }
        if (!verifySession()) {
            throw AppError.BadCredentials(AppError.Module.JW, "登录未能建立教务会话")
        }
    }

    /** 步骤④：验证教务会话（homes.html 含 KINGOSOFT，或任一轻量 H5 接口成功）。 */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$jwUrl/frame/homes.html")
                .header("User-Agent", UA)
                .get()
                .build()
            client.newCall(req).execute().body?.string()?.contains("KINGOSOFT") == true
        }.getOrDefault(false)
    }

    // ---------------- H5 API ----------------

    /**
     * 统一 H5 调用：注入通用参数 → 拼接 qs → of_encrypt/getMd5_2 → POST /wap/{name}.action。
     * 已有键不覆盖；响应做会话失效检测。
     */
    suspend fun h5Call(action: String, params: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val biz = buildMap {
                put("userid", studentNo)
                put("userId", studentNo)
                put("yhzh", studentNo)
                put("usertype", "STU")
                putAll(params)
            }
            val qs = biz.entries.joinToString("&") { "${it.key}=${it.value}" }
            val param = JwCrypto.ofEncrypt(qs)
            val param2 = JwCrypto.getMd5_2(qs)
            val form = FormBody.Builder()
                .add("action", "jw_apply")
                .add("param", param)
                .add("param2", param2)
                .add("timestamp", System.currentTimeMillis().toString())
                .build()
            val req = Request.Builder()
                .url("$jwUrl/wap/$action.action")
                .header("User-Agent", UA)
                .header("Referer", "$jwUrl/h5/index.html")
                .post(form)
                .build()
            val body = try {
                client.newCall(req).execute().use { resp ->
                    resp.body?.string() ?: ""
                }
            } catch (e: Exception) {
                throw AppError.Network(e.message)
            }
            detectSessionInvalid(body)
            json.parseToJsonElement(body)
        }

    /** 会话失效的三种形态（HTTP 恒为 200）。 */
    private fun detectSessionInvalid(body: String) {
        if (body.contains("凭证已失效") || body.contains("无效访问请求") ||
            body.contains("\"type\":\"noLogin\"") || body.contains("\"type\":\"invalidSession\"") ||
            body.contains("\"type\": \"noLogin\"")
        ) {
            throw AppError.AuthExpired(AppError.Module.JW)
        }
    }

    private fun urlEncode(v: String): String =
        java.net.URLEncoder.encode(v, "UTF-8")
}

/** 解析 `checkNeedCaptcha.htl` 的 JSON；不是 JSON 时返回 null（交给旧版文本判断）。 */
internal fun parseCheckNeedCaptcha(body: String): Boolean? = runCatching {
    val obj = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
    obj["isNeed"]?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull()
}.getOrNull()

/**
 * 旧版 `needCaptcha.html` 返回纯文本 `true`/`false`。
 * 只认开头：学校定制主题的 HTML 错误页里也含 `"true"`，不能 contains 判断。
 */
internal fun parseLegacyNeedCaptcha(body: String): Boolean =
    body.trim().lowercase().startsWith("true")

/** 简单内存 CookieJar；JSESSIONID 由 CredentialStore 持久化恢复。 */
class UnionCookieJar : CookieJar {
    private val cookies = java.util.concurrent.CopyOnWriteArrayList<Cookie>()

    fun restore(loaded: List<Cookie>) {
        loaded.forEach(::put)
    }

    fun snapshot(): List<Cookie> = cookies.toList()

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.filter { it.matches(url) }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach(::put)
    }

    fun clear() = cookies.clear()

    /**
     * 按 (name, domain, path) 覆盖。
     *
     * 不能只按 name 去重：authserver 与 jw 会各写一个 JSESSIONID，
     * 只按 name 去重会丢掉教务域上的会话，导致登录后一直提示“未能建立会话”。
     */
    private fun put(cookie: Cookie) {
        cookies.removeAll {
            it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
        }
        cookies.add(cookie)
    }
}

/** 便捷扩展：JsonObject 取字符串。 */
fun JsonObject.str(key: String): String = this[key]?.toString()?.removeSurrounding("\"") ?: ""

internal inline fun <T> Response.useBody(block: (Response) -> T): T = use(block)
