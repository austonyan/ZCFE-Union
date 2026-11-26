package com.union.campus.data.remote.xxt

import com.union.campus.core.AppError
import com.union.campus.core.crypto.XxtCrypto
import com.union.campus.data.repo.XxtParse
import com.union.campus.domain.XxtCourse
import com.union.campus.domain.XxtNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 学习通会话 Cookie：只服务 *.chaoxing.com，整段导出便于加密持久化。 */
class XxtCookieJar : CookieJar {
    private val store = LinkedHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store.values.toList()

    fun export(): String = store.values.joinToString("; ") { "${it.name}=${it.value}" }

    fun restore(header: String) {
        store.clear()
        header.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=').trim()
            if (name.isEmpty()) return@forEach
            store[name] = Cookie.Builder()
                .name(name)
                .value(value)
                .domain("chaoxing.com")
                .path("/")
                .build()
        }
    }

    fun clear() = store.clear()
    fun isEmpty(): Boolean = store.isEmpty()
}

/**
 * 学习通只读客户端。
 *
 * 协议来自 xxt-assistant 的实测结论：
 * - 登录 POST passport2.chaoxing.com/fanyalogin，uname/password 走 AES-128-CBC；
 * - 作业列表必须带课程中间页的 workEnc 签名与 t / stuenc 伴随参数；
 * - 截止时间只存在于详情页“作答时间: MM-DD HH:mm 至 MM-DD HH:mm”。
 *
 * 只做查询，不做任何提交/答题/刷课请求；请求之间强制限速。
 */
class XxtClient(val cookieJar: XxtCookieJar = XxtCookieJar()) {

    private val json = Json { ignoreUnknownKeys = true }

    private var minGapMs = 600L
    private var lastRequestAt = 0L

    /** 课程页签名与 cookie 绑定，缓存到本次会话结束。 */
    private val signatureCache = mutableMapOf<String, CachedSignatures>()

    data class Signatures(
        val enc: String,
        val openc: String,
        val workEnc: String,
        val examEnc: String,
    )

    private data class CachedSignatures(val at: Long, val value: Signatures)

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val URL_LOGIN = "https://passport2.chaoxing.com/fanyalogin"
        private const val URL_BASE = "https://i.chaoxing.com/base"
        private const val URL_COURSELIST = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata"
        private const val URL_STUMIDDLE = "https://mooc1.chaoxing.com/visit/stucoursemiddle"
        private const val URL_WORKLIST = "https://mooc1.chaoxing.com/mooc2/work/list"
        private const val URL_NOTICES = "https://notice.chaoxing.com/pc/notice/getNoticeList"
        private const val NO_PERMISSION_MARKER = "没有此页面访问权限"
        private val SIGNATURE_TTL_MS = TimeUnit.HOURS.toMillis(6)
    }

    // ---------------------------------------------------------------- 请求基础设施

    private suspend fun throttle() {
        val wait = minGapMs - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        lastRequestAt = System.currentTimeMillis()
    }

    private suspend fun get(url: String, referer: String = "https://i.chaoxing.com/"): Response =
        withContext(Dispatchers.IO) {
            throttle()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", referer)
                .get()
                .build()
            try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw AppError.Network(e.message ?: "学习通连接失败")
            }
        }

    private suspend fun postForm(url: String, form: Map<String, String>, referer: String): Response =
        withContext(Dispatchers.IO) {
            throttle()
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", referer)
                .post(body)
                .build()
            try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw AppError.Network(e.message ?: "学习通连接失败")
            }
        }

    private suspend fun bodyText(url: String, referer: String = "https://i.chaoxing.com/"): String =
        get(url, referer).use { resp ->
            val body = resp.body?.string().orEmpty()
            if (XxtParse.isAntispider(resp.request.url.toString(), body)) {
                throw AppError.Upstream("学习通触发了验证码拦截，请稍后再同步")
            }
            // 同步中途会话过期时，页面会 302 到 passport2/mlogin 并返回 200 —— 不能当成
            // “这门课没有作业”，否则页面会安静地显示成已交完。
            if (!resp.isSuccessful) throw AppError.Network("学习通返回 HTTP ${resp.code}")
            if (XxtParse.looksLikeLogin(resp.request.url.toString(), body) || resp.redirectedToPassport()) {
                throw AppError.AuthExpired(AppError.Module.XXT, "学习通会话已失效")
            }
            body
        }

    // ---------------------------------------------------------------- 登录

    /**
     * 账号密码登录；成功后会话 cookie 留在 jar 里，由仓库负责持久化。
     * @throws AppError.BadCredentials 账号/密码错误（或需要网页端验证码）
     */
    suspend fun login(account: String, password: String) {
        val form = linkedMapOf(
            "fid" to "-1",
            "uname" to XxtCrypto.loginEncrypt(account),
            "password" to XxtCrypto.loginEncrypt(password),
            "refer" to "https%3A%2F%2Fi.chaoxing.com",
            "t" to "true",
            "forbidotherlogin" to "0",
            "validate" to "",
            "doubleFactorLogin" to "0",
            "independentId" to "0",
        )
        val text = postForm(URL_LOGIN, form, "https://passport2.chaoxing.com/login").use { resp ->
            resp.body?.string().orEmpty()
        }
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw AppError.Upstream("登录响应不是 JSON：" + XxtParse.stripTags(text).take(80))
        val status = obj["status"]?.jsonPrimitive?.booleanOrNull
            ?: obj["status"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true)
            ?: false
        if (!status) {
            val msg = obj["msg2"]?.jsonPrimitive?.content
                ?: obj["msg"]?.jsonPrimitive?.content
                ?: "账号或密码错误，也可能需要先在网页端完成验证码"
            throw AppError.BadCredentials(AppError.Module.XXT, msg)
        }
        signatureCache.clear()
        if (cookieJar.isEmpty()) {
            // 少数节点先返回 302 再种 cookie，这里补一次主页访问把会话落全
            runCatching { get(URL_BASE).use { it.body?.string() } }
        }
    }

    /** 登录态是否仍有效（用 i.chaoxing.com/base 探测，未登录会被 302 到 passport2）。 */
    suspend fun checkLogin(): Boolean {
        if (cookieJar.isEmpty()) return false
        return get(URL_BASE).use { resp ->
            val body = resp.body?.string().orEmpty()
            resp.isSuccessful &&
                !XxtParse.looksLikeLogin(resp.request.url.toString(), body) &&
                !resp.redirectedToPassport()
        }
    }

    /** 顺着 priorResponse 检查是否被重定向到 passport2（移动端会跳 mlogin）。 */
    private fun Response.redirectedToPassport(): Boolean {
        var cursor: Response? = this
        while (cursor != null) {
            if (cursor.request.url.host == "passport2.chaoxing.com") return true
            cursor = cursor.priorResponse
        }
        return false
    }

    fun exportCookies(): String = cookieJar.export()

    fun restoreCookies(header: String) {
        cookieJar.restore(header)
        signatureCache.clear()
    }

    fun clearSession() {
        cookieJar.clear()
        signatureCache.clear()
    }

    // ---------------------------------------------------------------- 课程与作业

    /** 个人主页里的 fid（学校标识），课程列表接口需要。 */
    private suspend fun fetchFid(): String {
        val body = bodyText(URL_BASE)
        return Regex("\"fid\"\\s*:\\s*\"?(\\d+)\"?").find(body)?.groupValues?.get(1)
            ?: Regex("var fid = '(\\d+)'").find(body)?.groupValues?.get(1)
            ?: "0"
    }

    suspend fun fetchCourses(): List<XxtCourse> {
        val fid = fetchFid()
        val body = bodyText(
            "$URL_COURSELIST?courseType=1&courseFid=$fid&courseTeacherid=0&courseOrderBy=0",
            referer = URL_COURSELIST,
        )
        return XxtParse.parseCourses(body)
    }

    /** 课程中间页的隐藏签名；workEnc 才是作业列表能用的 enc。 */
    private suspend fun fetchSignatures(course: XxtCourse, refresh: Boolean): Signatures {
        val cacheKey = "${course.courseId}:${course.clazzId}"
        val cached = signatureCache[cacheKey]
        if (!refresh && cached != null && System.currentTimeMillis() - cached.at < SIGNATURE_TTL_MS) {
            return cached.value
        }
        val body = bodyText(
            "$URL_STUMIDDLE?courseid=${course.courseId}&clazzid=${course.clazzId}" +
                "&cpi=${course.cpi}&ismooc2=1&v=2",
        )
        val value = Signatures(
            enc = XxtParse.hiddenValue(body, "enc"),
            openc = XxtParse.hiddenValue(body, "openc"),
            workEnc = XxtParse.hiddenValue(body, "workEnc"),
            examEnc = XxtParse.hiddenValue(body, "examEnc"),
        )
        signatureCache[cacheKey] = CachedSignatures(System.currentTimeMillis(), value)
        return value
    }

    /** 某门课的作业列表；签名失效（页面提示无权限）时重拉课程页再试一次。 */
    suspend fun fetchHomeworkList(course: XxtCourse): XxtParse.WorkPage {
        fetchWorkList(course, refreshSignature = false)?.let { return it }
        return fetchWorkList(course, refreshSignature = true)
            ?: throw AppError.Upstream("${course.name} 的作业签名已失效，稍后再试")
    }

    private suspend fun fetchWorkList(course: XxtCourse, refreshSignature: Boolean): XxtParse.WorkPage? {
        val sig = fetchSignatures(course, refreshSignature)
        if (sig.workEnc.isEmpty()) {
            throw AppError.Upstream("${course.name} 未开放作业（没有作业签名）")
        }
        val body = bodyText(
            "$URL_WORKLIST?courseId=${course.courseId}&classId=${course.clazzId}" +
                "&cpi=${course.cpi}&ut=s&openc=${sig.openc}&enc=${sig.workEnc}" +
                "&t=${System.currentTimeMillis()}&stuenc=${sig.enc}&isdisplaytable=2",
            referer = URL_WORKLIST,
        )
        if (body.contains(NO_PERMISSION_MARKER)) return null
        return XxtParse.parseHomeworkList(body)
    }

    /** 作业详情（题量 / 满分 / 作答起止时间）。 */
    suspend fun fetchHomeworkDetail(url: String): XxtParse.WorkDetail {
        if (!url.startsWith("http")) throw AppError.Upstream("该作业没有可用的详情链接")
        return XxtParse.parseHomeworkDetail(bodyText(url))
    }

    // ---------------------------------------------------------------- 通知

    /** 通知中心列表（游标翻页，只读）。 */
    suspend fun fetchNotices(limit: Int = 20, maxPages: Int = 2): List<XxtNotice> {
        val items = mutableListOf<XxtNotice>()
        var lastId = ""
        repeat(maxPages.coerceAtLeast(1)) {
            val form = linkedMapOf(
                "type" to "0", "notice_type" to "0", "lastValue" to lastId, "sort" to "0",
                "folderUUID" to "", "kw" to "", "startTime" to "", "endTime" to "",
                "gKw" to "", "gName" to "", "year" to "", "tag" to "", "fidsCode" to "",
                "filterSenderPuids" to "", "filterTags" to "",
            )
            val text = postForm(
                URL_NOTICES, form, "https://notice.chaoxing.com/pc/notice/myNotice",
            ).use { it.body?.string().orEmpty() }
            val payload = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: throw AppError.Upstream("通知接口返回的不是 JSON")
            val status = (payload["status"] as? JsonPrimitive)?.booleanOrNull
                ?: ((payload["status"] as? JsonPrimitive)?.contentOrNull == "true")
            if (status == false) throw AppError.Upstream("通知接口返回失败")
            val page = XxtParse.parseNotices(payload)
            items += page.items
            lastId = page.lastId
            if (page.items.isEmpty() || page.lastPage || items.size >= limit) {
                return items.take(limit)
            }
        }
        return items.take(limit)
    }
}
