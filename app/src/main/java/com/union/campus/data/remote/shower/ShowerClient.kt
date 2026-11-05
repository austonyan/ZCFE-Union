package com.union.campus.data.remote.shower

import com.union.campus.core.AppError
import com.union.campus.core.crypto.ShowerCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

    private var baseUrl: String = "http://lz.hydream.cn/api/v1/"
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
            baseUrl = if (serviceHost.endsWith("/")) serviceHost else "$serviceHost/"
        }
    }

    fun clear() {
        token = ""
        baseUrl = "http://lz.hydream.cn/api/v1/"
    }

    /**
     * 统一调用：构建信封 → POST ztechMsg → 解密 → 错误映射。
     * @return 内层 data.code == 200/100 时的 data 字段（可为空）
     */
    suspend fun call(path: String, data: JsonObject? = null, act: String? = null): JsonObject? =
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

    /** 错误映射：auth=00001/code=90 → 失效；102 → 账密错误；其余按 code+msg。 */
    private fun mapShowerError(path: String, inner: JsonObject): JsonObject? {
        val header = inner["header"]?.jsonObject
        val dataObj = inner["data"]?.jsonObject
        val auth = header?.get("auth")?.toString()?.removeSurrounding("\"") ?: ""
        val code = dataObj?.get("code")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 0
        val msg = dataObj?.get("msg")?.toString()?.removeSurrounding("\"") ?: ""

        if (auth == "00001" || code == 90) throw AppError.AuthExpired(AppError.Module.SHOWER, msg)
        if (code == 102) throw AppError.BadCredentials(AppError.Module.SHOWER, msg)
        if (code == 200 || code == 100) return dataObj?.get("data")?.jsonObject
        throw AppError.Upstream("$path: $msg (code=$code)")
    }

    // ---------------- 具体接口 ----------------

    suspend fun login(mobile: String, pwd: String): JsonObject = call(
        "UserApi/loginWidthPwd",
        data = buildJsonObjectCompat {
            put("mobile", mobile)
            put("pwd", pwd)
        },
    ) ?: throw AppError.BadCredentials(AppError.Module.SHOWER, "登录无返回")

    suspend fun fastLogin(mobile: String, verifyCode: String): JsonObject = call(
        "UserApi/fastLogin",
        data = buildJsonObjectCompat { put("mobile", mobile); put("verifyCode", verifyCode) },
    ) ?: throw AppError.BadCredentials(AppError.Module.SHOWER, "验证码登录无返回")

    suspend fun sendSmsCode(mobile: String, codeType: String = "login") = call(
        "UserApi/sendSMSCode",
        data = buildJsonObjectCompat { put("code_type", codeType); put("mobile", mobile) },
    )

    suspend fun getOrgs(): JsonObject? = call(
        "DeviceAreaApi/getOrgs",
        data = buildJsonObjectCompat { put("org_type", "school"); put("org_name", "") },
    )

    suspend fun getOrgAreas(orgId: String): JsonObject? = call(
        "DeviceAreaApi/getOrgAreas",
        data = buildJsonObjectCompat { put("orgId", orgId) },
    )

    suspend fun getBuildings(orgAreaId: String): JsonObject? = call(
        "DeviceAreaApi/getBuildings",
        data = buildJsonObjectCompat { put("orgAreaId", orgAreaId) },
    )

    suspend fun getDeviceAreas(orgAreaId: String, buildingId: String): JsonObject? = call(
        "DeviceAreaApi/getDeviceArea",
        data = buildJsonObjectCompat { put("orgAreaId", orgAreaId); put("buildingId", buildingId) },
    )

    suspend fun getAreasWithState(orgAreaId: String, deviceTypeKey: String = "faucet"): JsonObject? = call(
        "DeviceAreaApi/selectDeviceAreasWithDeviceState",
        data = buildJsonObjectCompat { put("orgAreaId", orgAreaId); put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun getDevices(deviceAreaId: String, deviceTypeKey: String = "faucet"): JsonObject? = call(
        "DeviceAreaApi/selectDevicesByAreaId",
        data = buildJsonObjectCompat { put("deviceAreaId", deviceAreaId); put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun turnOn(uuid: String, deviceType: String = "faucet"): JsonObject? = call(
        "DeviceApi/turnOn",
        data = buildJsonObjectCompat { put("uuid", uuid); put("deviceType", deviceType) },
    )

    suspend fun turnOff(uuid: String, deviceType: String = "faucet"): JsonObject? = call(
        "DeviceApi/turnOff",
        data = buildJsonObjectCompat { put("uuid", uuid); put("deviceType", deviceType) },
    )

    suspend fun outstandingOrder(): JsonObject? = call(
        "OrderApi/getOutstandingOrder",
        data = buildJsonObjectCompat { },
    )

    suspend fun walletInfo(): JsonObject? = call(
        "UserApi/getWalletInfo",
        data = buildJsonObjectCompat { },
    )

    suspend fun consumptionLog(mobile: String, uid: String, page: Int = 1, limit: Int = 10): JsonObject? = call(
        "ConsumptionApi/consumptionLog",
        data = buildJsonObjectCompat {
            put("mobile", mobile)
            put("uid", uid)
            put("device_type_key", "faucet")
            put("page", page)
            put("limit", limit)
        },
    )

    suspend fun myQueueInfo(deviceTypeKey: String = "faucet"): JsonObject? = call(
        "DeviceAreaApi/getMyQueueInfo",
        data = buildJsonObjectCompat { put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun reserve(uuid: String): JsonObject? = call(
        "DeviceApi/reserve",
        data = buildJsonObjectCompat { put("uuid", uuid); put("version", "55") },
    )

    suspend fun cancelReserve(uuid: String, deviceType: String = "faucet"): JsonObject? = call(
        "DeviceApi/cancelReserve",
        data = buildJsonObjectCompat { put("uuid", uuid); put("deviceType", deviceType) },
    )

    suspend fun queueUp(deviceAreaId: String, deviceTypeKey: String = "faucet"): JsonObject? = call(
        "DeviceAreaApi/queueUp",
        data = buildJsonObjectCompat { put("deviceAreaId", deviceAreaId); put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun cancelQueue(deviceAreaId: String, deviceTypeKey: String = "faucet"): JsonObject? = call(
        "DeviceAreaApi/cancelQueue",
        data = buildJsonObjectCompat { put("deviceAreaId", deviceAreaId); put("deviceTypeKey", deviceTypeKey) },
    )

    suspend fun getUserBaseInfo(mobile: String): JsonObject? = call(
        "MemberApi/getUserBaseInfo",
        data = buildJsonObjectCompat { put("mobile", mobile) },
    )

    /** 写一个小助手避免每处 import buildJsonObject。 */
    private inline fun buildJsonObjectCompat(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        kotlinx.serialization.json.buildJsonObject(block)
}
