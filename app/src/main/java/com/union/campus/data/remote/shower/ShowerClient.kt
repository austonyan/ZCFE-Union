package com.union.campus.data.remote.shower

import com.union.campus.core.AppError
import com.union.campus.core.crypto.ShowerCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 洗浴（大白U帮原生 lz 接口）客户端：DES 信封 + ztechMsg 表单。
 * 协议依据 allAPI.md §2.2（5yoOxt9w / 11111111 / q9KswPoz62jQk12W）。
 */
class ShowerClient {

    private var baseUrl: String = DEFAULT_BASE
    var token: String = ""
        private set

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun updateToken(token: String, serviceHost: String? = null) {
        this.token = token
        if (!serviceHost.isNullOrBlank()) {
            baseUrl = normalizeBase(serviceHost)
        }
    }

    fun clear() {
        token = ""
        baseUrl = DEFAULT_BASE
    }

    /**
     * service_host 可能是 `http://lz.hydream.cn`，也可能已带 `/api/v1/`；
     * 统一补全 scheme、结尾斜杠与 `/api/v1/`，避免把请求打到站点根路径。
     */
    private fun normalizeBase(host: String): String {
        var h = host.trim()
        if (h.isEmpty()) return DEFAULT_BASE
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
        if (!h.endsWith("/")) h += "/"
        if (!h.contains("/api/")) h += "api/v1/"
        return h
    }

    private companion object {
        const val DEFAULT_BASE = "http://lz.hydream.cn/api/v1/"
    }

    /**
     * 统一调用：构建信封 → POST ztechMsg → 解密 → 错误映射。
     * @return `data.data`（列表接口是数组，详情接口是对象；可能为空）
     */
    suspend fun call(path: String, data: JsonObject? = null, act: String? = null): JsonElement? =
        withContext(Dispatchers.IO) {
            val action = act ?: path.substringAfterLast("/")
            val envelope = ShowerCrypto.buildEnvelope(action, data, token)
            val form = FormBody.Builder().add("ztechMsg", envelope).build()
            val req = Request.Builder()
                .url(baseUrl + path)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .post(form)
                .build()
            val respBody = try {
                client.newCall(req).execute().use { resp -> resp.body?.string() ?: "" }
            } catch (e: Exception) {
                throw AppError.Network(e.message)
            }
            if (respBody.isBlank()) throw AppError.Upstream("$path 空响应（HTTP 层异常）")
            val envelopeJson = try {
                json.parseToJsonElement(respBody).jsonObject
            } catch (e: Exception) {
                throw AppError.Upstream("$path 非信封响应：${respBody.take(80)}")
            }
            val inner = try {
                ShowerCrypto.decodeResponse(envelopeJson)
            } catch (e: Exception) {
                throw AppError.Upstream("$path 解密失败：${e.message}")
            }
            mapShowerError(path, inner)
        }

    /** 错误映射：auth=00001/code=90 → 失效；102 → 账密错误；业务文案优先友好展示。 */
    private fun mapShowerError(path: String, inner: JsonObject): JsonElement? {
        val header = inner["header"]?.jsonObject
        val dataObj = inner["data"]?.jsonObject
        val auth = (header?.get("auth") as? JsonPrimitive)?.content ?: ""
        val code = (dataObj?.get("code") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val msg = (dataObj?.get("msg") as? JsonPrimitive)?.content ?: ""

        if (auth == "00001" || code == 90) throw AppError.AuthExpired(AppError.Module.SHOWER, msg)
        if (code == 102) throw AppError.BadCredentials(AppError.Module.SHOWER, msg)
        if (code == 139) throw AppError.Validation("设备区域不存在，请刷新后重试")
        if (code == 298) throw AppError.Validation("校区不存在，请重新选择校区")

        // 非服务时间等业务限制：直接把可读文案返回给用户，不要落入“数据源返回异常”
        showerServiceClosedMessage(msg)?.let { throw AppError.Validation(it) }
        if (code == 200 || code == 100) return dataObj?.get("data")
        if (msg.isNotBlank() &&
            !msg.contains("Undefined", true) &&
            !msg.contains("Exception", true) &&
            !msg.contains("error", true)
        ) {
            throw AppError.Validation(msg)
        }
        throw AppError.Upstream("$path: $msg (code=$code)")
    }

    // ---------------- 具体接口 ----------------

    suspend fun login(mobile: String, pwd: String): JsonObject = call(
        "UserApi/loginWidthPwd",
        data = buildJsonObjectCompat {
            put("mobile", mobile)
            put("pwd", pwd)
        },
    ) as? JsonObject ?: throw AppError.BadCredentials(AppError.Module.SHOWER, "登录无返回")

    suspend fun fastLogin(mobile: String, verifyCode: String): JsonObject = call(
        "UserApi/fastLogin",
        data = buildJsonObjectCompat { put("mobile", mobile); put("verifyCode", verifyCode) },
    ) as? JsonObject ?: throw AppError.BadCredentials(AppError.Module.SHOWER, "验证码登录无返回")

    suspend fun sendSmsCode(mobile: String, codeType: String = "login") = call(
        "UserApi/sendSMSCode",
        data = buildJsonObjectCompat { put("code_type", codeType); put("mobile", mobile) },
    )

    suspend fun getOrgs(): JsonElement? = call(
        "DeviceAreaApi/getOrgs",
        data = buildJsonObjectCompat { put("org_type", "school"); put("org_name", "") },
    )

    suspend fun getOrgAreas(orgId: String): JsonElement? = call(
        "DeviceAreaApi/getOrgAreas",
        data = buildJsonObjectCompat { put("orgId", orgId) },
    )

    suspend fun getBuildings(orgAreaId: String): JsonElement? = call(
        "DeviceAreaApi/getBuildings",
        data = buildJsonObjectCompat { put("orgAreaId", orgAreaId) },
    )

    suspend fun getDeviceAreas(orgAreaId: String, buildingId: String): JsonElement? = call(
        "DeviceAreaApi/getDeviceArea",
        data = buildJsonObjectCompat { put("orgAreaId", orgAreaId); put("buildingId", buildingId) },
    )

    suspend fun getAreasWithState(orgAreaId: String, deviceTypeKey: String = "faucet"): JsonElement? = call(
        "DeviceAreaApi/selectDeviceAreasWithDeviceState",
        data = buildJsonObjectCompat { put("orgAreaId", orgAreaId); put("deviceTypeKey", deviceTypeKey) },
    )

    /** 校区下各区域排队人数。 */
    suspend fun getQueueCounts(orgAreaId: String, deviceTypeKey: String = "faucet"): JsonElement? = call(
        "DeviceAreaApi/selectQueueCountByOrgAreaId",
        data = buildJsonObjectCompat { put("orgAreaId", orgAreaId); put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun getDevices(deviceAreaId: String, deviceTypeKey: String = "faucet"): JsonElement? = call(
        "DeviceAreaApi/selectDevicesByAreaId",
        data = buildJsonObjectCompat { put("deviceAreaId", deviceAreaId); put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun turnOn(uuid: String, deviceType: String = "faucet"): JsonObject? = call(
        "DeviceApi/turnOn",
        data = buildJsonObjectCompat { put("uuid", uuid); put("deviceType", deviceType) },
    ) as? JsonObject

    suspend fun turnOff(uuid: String, deviceType: String = "faucet"): JsonObject? = call(
        "DeviceApi/turnOff",
        data = buildJsonObjectCompat { put("uuid", uuid); put("deviceType", deviceType) },
    ) as? JsonObject

    suspend fun outstandingOrder(): JsonElement? = call(
        "OrderApi/getOutstandingOrder",
        data = buildJsonObjectCompat { },
    )

    suspend fun walletInfo(): JsonObject? = call(
        "UserApi/getWalletInfo",
        data = buildJsonObjectCompat { },
    ) as? JsonObject

    /** 消费明细：只接受 page / pageSize（原生接口实测参数）。 */
    suspend fun consumptionLog(page: Int = 1, pageSize: Int = 10): JsonElement? = call(
        "ConsumptionApi/consumptionLog",
        data = buildJsonObjectCompat {
            put("page", page)
            put("pageSize", pageSize)
        },
    )

    suspend fun myQueueInfo(deviceTypeKey: String = "faucet"): JsonElement? = call(
        "DeviceAreaApi/getMyQueueInfo",
        data = buildJsonObjectCompat { put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun reserve(uuid: String): JsonObject? = call(
        "DeviceApi/reserve",
        data = buildJsonObjectCompat { put("uuid", uuid); put("version", "55") },
    ) as? JsonObject

    suspend fun cancelReserve(uuid: String, deviceType: String = "faucet"): JsonObject? = call(
        "DeviceApi/cancelReserve",
        data = buildJsonObjectCompat { put("uuid", uuid); put("deviceType", deviceType) },
    ) as? JsonObject

    suspend fun queueUp(deviceAreaId: String, deviceTypeKey: String = "faucet"): JsonObject? = call(
        "DeviceAreaApi/queueUp",
        data = buildJsonObjectCompat { put("deviceAreaId", deviceAreaId); put("deviceTypeKey", deviceTypeKey) },
    ) as? JsonObject

    suspend fun cancelQueue(deviceAreaId: String, deviceTypeKey: String = "faucet"): JsonObject? = call(
        "DeviceAreaApi/cancelQueue",
        data = buildJsonObjectCompat { put("deviceAreaId", deviceAreaId); put("deviceTypeKey", deviceTypeKey) },
    ) as? JsonObject

    suspend fun getUserBaseInfo(mobile: String): JsonObject? = call(
        "MemberApi/getUserBaseInfo",
        data = buildJsonObjectCompat { put("mobile", mobile) },
    ) as? JsonObject

    /** 写一个小助手避免每处 import buildJsonObject。 */
    private inline fun buildJsonObjectCompat(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        kotlinx.serialization.json.buildJsonObject(block)
}

/** 非服务时间 / 浴室未开放的文案识别；返回给用户的友好提示。 */
internal fun showerServiceClosedMessage(msg: String): String? {
    if (msg.isBlank()) return null
    val keywords = listOf(
        "非服务时间", "不在服务时间", "服务时间外", "未到服务时间",
        "不在开放时间", "未到开放时间", "开放时间外", "营业时间外",
        "暂停服务", "停止服务", "浴室未开放", "暂未开放", "未开放",
    )
    return if (keywords.any { msg.contains(it) }) {
        "当前不在洗浴服务时间，请按浴室开放时间使用"
    } else {
        null
    }
}
