package com.union.campus.data.remote.electric

import com.union.campus.core.AppError
import com.union.campus.core.crypto.ElectricCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 电费（完美校园 智能水电 SWAEServlet）客户端。
 * 明文命令通道：param(JSON) + customercode + method + command；查询重试 3 次。
 * 协议依据 allAPI.md §3.3（本校 7268 / JBSWaterElecService）。
 */
class ElectricClient {

    private val apiBase = "https://xqh5.17wanxiao.com/smartWaterAndElectricityService"

    var customerCode: String = "7268"
    var command: String = "JBSWaterElecService"
    var account: String = ""

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        private val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36 Wanxiao/5.8.34"
        private val HEADERS = mapOf(
            "X-Requested-With" to "com.newcapec.mobile.ncp",
            "Origin" to "https://xqh5.17wanxiao.com",
            "Referer" to "https://xqh5.17wanxiao.com",
        )
    }

    /** SWAEServlet 统一调用：返回内层 body（业务 JSON）。查询类重试 3 次。 */
    private suspend fun call(cmd: String, extra: Map<String, String> = emptyMap(), retryOnFailure: Boolean = true): JsonObject =
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date())
            val payload = buildJsonObject {
                put("cmd", cmd)
                put("account", account)
                put("timestamp", timestamp)
                extra.forEach { (k, v) -> put(k, v) }
            }
            val form = FormBody.Builder()
                .add("param", payload.toString())
                .add("customercode", customerCode)
                .add("method", cmd)
                .add("command", command)
                .build()
            val req = Request.Builder()
                .url("$apiBase/SWAEServlet")
                .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
                .header("User-Agent", UA)
                .post(form)
                .build()

            var lastError: Exception? = null
            val attempts = if (retryOnFailure) 3 else 1
            repeat(attempts) { attempt ->
                try {
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        val outer = json.parseToJsonElement(body).jsonObject
                        val code_ = (outer["code_"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                        if (code_ == 8099) throw AppError.Upstream("学校编码无效（8099），请检查绑定设置")
                        if (code_ != 0) throw AppError.Upstream("网关异常 code_=$code_")
                        return@withContext swaeInnerBody(outer)
                    }
                } catch (e: AppError) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < attempts - 1) Thread.sleep(400L * (attempt + 1))
                }
            }
            throw AppError.Network("电费数据源连接失败（已重试 $attempts 次）：${lastError?.message}")
        }

    /** 内层业务错误映射。 */
    private fun requireOk(body: JsonObject, cmd: String): JsonObject {
        val result = (body["result"] as? JsonPrimitive)?.content
        if (result == "0") return body
        val _result = (body["_result"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
        if (_result == false) {
            val msg = (body["_message"] as? JsonPrimitive)?.content ?: "业务失败"
            throw AppError.Upstream("$cmd: $msg")
        }
        if (result == null && body["_message"] == null) {
            // 无 result 字段但有数据键，视为成功（不同命令返回结构差异）
            return body
        }
        throw AppError.Upstream("$cmd: ${body.msg}")
    }

    private val JsonObject.msg: String
        get() = (this["_message"] as? JsonPrimitive)?.content ?: "业务失败"

    // ---------------- 具体命令 ----------------

    /** 查绑定房间：existflag=2 时含 roomlist[]。 */
    suspend fun getBindRooms(): JsonObject = requireOk(call("getbindroom"), "getbindroom")

    suspend fun getZoneList(): JsonObject = requireOk(call("getzonelist"), "getzonelist")

    suspend fun getBuildList(zoneId: String): JsonObject =
        requireOk(call("getbuildlist", mapOf("zoneid" to zoneId)), "getbuildlist")

    suspend fun getFloorList(buildId: String): JsonObject =
        requireOk(call("getfloorlist", mapOf("buildid" to buildId)), "getfloorlist")

    suspend fun getRoomList(floorId: String): JsonObject =
        requireOk(call("getroomnumlist", mapOf("floorid" to floorId)), "getroomnumlist")

    /** 绑定房间（写，不重试）。 */
    suspend fun bindRoom(zoneId: String, buildId: String, floorId: String, roomNum: String): JsonObject =
        requireOk(
            call(
                "bindroom",
                mapOf("zoneid" to zoneId, "buildid" to buildId, "floorid" to floorId, "roomnum" to roomNum),
                retryOnFailure = false,
            ),
            "bindroom",
        )

    /** 解绑房间（写，不重试）。 */
    suspend fun unbindRooms(roomVerifies: List<String>): JsonObject =
        requireOk(
            call("unbindroom", mapOf("roomverifylist" to roomVerifies.joinToString(",")), retryOnFailure = false),
            "unbindroom",
        )

    /** 余额/今日/本周/月度：modlist[]（odd/todayuse/price/...）。 */
    suspend fun getIndexPage(roomVerify: String = ""): JsonObject =
        requireOk(call("h5_getstuindexpage", mapOf("roomverify" to roomVerify)), "h5_getstuindexpage")

    /** 区间每日用量：sumuse + dayuselist[]。 */
    suspend fun getUsageRange(roomVerify: String, startDate: String, endDate: String): JsonObject =
        requireOk(
            call(
                "gettimeusedetail",
                mapOf(
                    "roomverify" to roomVerify,
                    "businesstype" to "0",
                    "startdate" to startDate,
                    "enddate" to endDate,
                ),
            ),
            "gettimeusedetail",
        )

    /** 月度每日用量：monthuse + dayuselist[]。 */
    suspend fun getUsageMonth(roomVerify: String, yearMonth: String): JsonObject =
        requireOk(
            call(
                "getusedetail",
                mapOf("roomverify" to roomVerify, "businesstype" to "0", "yearmonth" to yearMonth),
            ),
            "getusedetail",
        )

    /** 每日读数/余额：odd/dayuse/sumse/statdate。 */
    suspend fun getStroomDay(roomVerify: String, startDate: String, endDate: String, page: Int = 1, pageSize: Int = 31): JsonObject =
        requireOk(
            call(
                "getstroomday",
                mapOf(
                    "businesstype" to "0",
                    "roomverify" to roomVerify,
                    "starttime" to startDate,
                    "endtime" to endDate,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                ),
            ),
            "getstroomday",
        )

    /** loginCheck：params 换登录态（AES-128-ECB 解密）。 */
    suspend fun loginCheck(params: String): JsonObject = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("data", params).build()
        val req = Request.Builder()
            .url("$apiBase/loginCheck")
            .apply { HEADERS.forEach { (k, v) -> header(k, v) } }
            .header("User-Agent", UA)
            .post(form)
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val outer = json.parseToJsonElement(body).jsonObject
                val resultData = (outer["resultdata"] as? JsonPrimitive)?.content
                    ?: throw AppError.Upstream("loginCheck 无 resultdata")
                val plain = ElectricCrypto.decryptLoginCheck(resultData)
                json.parseToJsonElement(plain).jsonObject
            }
        } catch (e: AppError) {
            throw e
        } catch (e: Exception) {
            throw AppError.Upstream("loginCheck 解密失败：${e.message}")
        }
    }

    /** 未绑定房间探测：roomverify 查询返回 -90004。 */
    fun isRoomNotBound(body: JsonObject): Boolean =
        (body["result"] as? JsonPrimitive)?.content == "-90004"
}

private val SWAE_JSON = Json { ignoreUnknownKeys = true }

/**
 * 网关外层是 `{"code_":0,"body":"{\"result\":\"0\",...}"}`，
 * `body` 是**转义后的 JSON 字符串**。必须用 `JsonPrimitive.content` 取出，
 * 不能用 `toString().removeSurrounding("\"")`，否则内层引号仍是 `\"`，解析必失败。
 */
internal fun swaeInnerBody(outer: JsonObject): JsonObject {
    val bodyStr = (outer["body"] as? JsonPrimitive)?.content
        ?: throw AppError.Upstream("网关响应缺少 body")
    return SWAE_JSON.parseToJsonElement(bodyStr).jsonObject
}
