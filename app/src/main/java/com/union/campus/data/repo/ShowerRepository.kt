package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.remote.shower.ShowerClient
import com.union.campus.data.store.CacheTtl
import com.union.campus.data.store.CredentialStore
import com.union.campus.data.store.EntityStore
import com.union.campus.data.store.SettingsStore
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerBuilding
import com.union.campus.domain.ShowerDevice
import com.union.campus.domain.ShowerOrg
import com.union.campus.domain.ShowerOrder
import com.union.campus.domain.ShowerSessionState
import com.union.campus.domain.ShowerWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/**
 * 洗浴仓库：登录/组织/区域/设备/开关/订单/钱包 + 会话恢复。
 */
class ShowerRepository constructor(
    private val client: ShowerClient,
    private val store: EntityStore,
    private val credentials: CredentialStore,
    private val settings: SettingsStore,
) {
    private val parse = Parse

    private val _sessionState = MutableStateFlow(SessionState.NOT_BOUND)
    val sessionState: StateFlow<SessionState> = _sessionState

    enum class SessionState { NOT_BOUND, VALID, EXPIRED }

    /** 活跃洗浴会话（重启恢复用）。 */
    private val _activeSession = MutableStateFlow<ShowerSessionState?>(null)
    val activeSession: StateFlow<ShowerSessionState?> = _activeSession

    val sessionFileKey = "shower.activeSession"

    fun restore() {
        val cred = credentials.load()
        if (cred.showerToken.isNotBlank()) {
            client.updateToken(cred.showerToken, cred.showerServiceHost.takeIf { it.isNotBlank() })
            _sessionState.value = SessionState.VALID
        } else {
            _sessionState.value = SessionState.NOT_BOUND
        }
        store.read(sessionFileKey, ShowerSessionState.serializer())?.let {
            _activeSession.value = it.data
        }
    }

    fun isBound(): Boolean = _sessionState.value == SessionState.VALID

    fun maskedMobile(): String {
        val m = credentials.load().showerMobile
        return if (m.length == 11) "${m.take(3)}****${m.takeLast(4)}" else "未绑定"
    }

    suspend fun login(mobile: String, password: String) {
        val data = client.login(mobile, password)
        // LoginEntity：token/id/mobile/service_host...
        val token = parse.str(data, "token", "TOKEN_ID")
        val uid = parse.str(data, "id", "uid")
        val serviceHost = parse.str(data, "service_host", "serviceHost")
        if (token.isBlank()) throw AppError.BadCredentials(AppError.Module.SHOWER, "登录响应缺少 token")
        client.updateToken(token, serviceHost.takeIf { it.isNotBlank() })
        val cred = credentials.load().copy(
            showerMobile = mobile,
            showerToken = token,
            showerServiceHost = serviceHost,
            showerUid = uid,
            showerPassword = password,
        )
        credentials.save(cred)
        _sessionState.value = SessionState.VALID
    }

    /** 短信验证码登录。 */
    suspend fun clientSendSms(mobile: String) {
        client.sendSmsCode(mobile)
    }

    suspend fun clientFastLogin(mobile: String, code: String) {
        val data = client.fastLogin(mobile, code)
        val token = parse.str(data, "token", "TOKEN_ID")
        val uid = parse.str(data, "id", "uid")
        val serviceHost = parse.str(data, "service_host", "serviceHost")
        if (token.isBlank()) throw AppError.BadCredentials(AppError.Module.SHOWER, "验证码登录响应缺少 token")
        client.updateToken(token, serviceHost.takeIf { it.isNotBlank() })
        credentials.save(
            credentials.load().copy(showerMobile = mobile, showerToken = token, showerServiceHost = serviceHost, showerUid = uid),
        )
        _sessionState.value = SessionState.VALID
    }

    suspend fun logout() {
        client.clear()
        val cred = credentials.load().copy(showerMobile = "", showerToken = "", showerServiceHost = "", showerUid = "")
        credentials.save(cred)
        _sessionState.value = SessionState.NOT_BOUND
        _activeSession.value = null
        store.remove(sessionFileKey)
    }

    suspend fun probe(): Boolean = try {
        orgs()
        true
    } catch (_: AppError) {
        false
    }

    suspend fun orgs(): List<ShowerOrg> {
        val data = client.getOrgs() ?: return emptyList()
        return parse.objects(parse.array(data, "data", "list", "orgs")).map {
            ShowerOrg(
                id = parse.str(it, "id", "org_id", "orgId"),
                name = parse.str(it, "name", "org_name", "orgName").ifBlank { "校区" },
            )
        }
    }

    suspend fun buildings(orgAreaId: String): List<ShowerBuilding> {
        val data = client.getBuildings(orgAreaId) ?: return emptyList()
        return parse.objects(parse.array(data, "data", "list")).map {
            ShowerBuilding(
                id = parse.str(it, "id", "building_id", "buildingId"),
                name = parse.str(it, "name", "building_name", "buildingName").ifBlank { "楼栋" },
            )
        }
    }

    /** 区域 + 空闲统计（缓存 30s）。 */
    suspend fun areasWithState(orgAreaId: String, forceRefresh: Boolean = false): List<ShowerArea> {
        val cacheKey = "shower.areas.$orgAreaId"
        if (!forceRefresh) {
            store.read(cacheKey, ListSerializer(ShowerArea.serializer()))?.let { return it.data }
        }
        val data = client.getAreasWithState(orgAreaId) ?: return emptyList()
        val list = parse.objects(data["data"] as? JsonArray ?: parse.array(data)).map { a ->
            ShowerArea(
                id = parse.str(a, "id", "device_area_id", "deviceAreaId", "area_id"),
                name = parse.str(a, "name", "device_area_name", "deviceAreaName", "area_name").ifBlank { "区域" },
                idleNum = parse.int(a, "idle_num", "idleNum", "free_num"),
                totalNum = parse.int(a, "total_num", "totalNum", "device_num"),
                queueNum = parse.int(a, "queue_num", "queueNum", "wait_num"),
                serviceTime = parse.str(a, "service_time", "serviceTime", "open_time"),
                maintenance = parse.str(a, "maintenance", "repair", "stop_desc"),
            )
        }
        if (list.isNotEmpty()) {
            store.write(cacheKey, ListSerializer(ShowerArea.serializer()), list)
        }
        return list
    }

    suspend fun devices(deviceAreaId: String, forceRefresh: Boolean = false): List<ShowerDevice> {
        val cacheKey = "shower.devices.$deviceAreaId"
        if (!forceRefresh) {
            store.read(cacheKey, ListSerializer(ShowerDevice.serializer()))?.let { return it.data }
        }
        val data = client.getDevices(deviceAreaId) ?: return emptyList()
        val list = parse.objects(data["data"] as? JsonArray ?: parse.array(data)).map { d ->
            ShowerDevice(
                uuid = parse.str(d, "uuid", "device_uuid", "id", "deviceUuid"),
                name = parse.str(d, "device_num", "num", "name", "deviceNo").ifBlank { "设备" },
                state = parseState(d),
                remainingText = parse.str(d, "remain_time", "remaining", "reserve_time", "left_time"),
            )
        }
        if (list.isNotEmpty()) {
            store.write(cacheKey, ListSerializer(ShowerDevice.serializer()), list)
        }
        return list
    }

    private fun parseState(d: JsonObject): Int {
        val raw = parse.str(d, "state", "status", "device_state").lowercase()
        return when {
            raw.contains("idle") || raw == "0" || raw.contains("空闲") -> 0
            raw.contains("using") || raw == "1" || raw.contains("使用") -> 1
            raw.contains("reserve") || raw == "2" || raw.contains("预约") -> 2
            raw.contains("fault") || raw == "3" || raw.contains("故障") -> 3
            else -> raw.toIntOrNull() ?: 0
        }
    }

    /** 开始洗浴（写，不重试）。成功后写入活跃会话。 */
    suspend fun startShower(device: ShowerDevice, areaName: String, buildingName: String) {
        val result = client.turnOn(device.uuid) ?: throw AppError.Upstream("开始洗浴无响应，请刷新确认设备状态")
        val orderSn = parse.str(result, "order_sn", "orderSn")
        val session = ShowerSessionState(
            deviceUuid = device.uuid,
            deviceName = device.name,
            areaName = areaName,
            buildingName = buildingName,
            startedAt = System.currentTimeMillis(),
            orderSn = orderSn,
        )
        _activeSession.value = session
        store.write(sessionFileKey, ShowerSessionState.serializer(), session)
    }

    /** 结束洗浴（写，不重试）。 */
    suspend fun endShower() {
        val session = _activeSession.value ?: return
        client.turnOff(session.deviceUuid)
        _activeSession.value = null
        store.remove(sessionFileKey)
    }

    /** 会话恢复：查未完成订单。 */
    suspend fun hasOutstandingOrder(): Boolean = try {
        val data = client.outstandingOrder()
        val v = data?.get("data")?.toString()?.lowercase()
        v?.contains("true") == true
    } catch (_: AppError) {
        false
    }

    suspend fun wallet(): ShowerWallet? {
        val data = client.walletInfo() ?: return null
        val w = (data["data"] as? JsonObject) ?: data
        return ShowerWallet(
            balance = parse.str(w, "balance", "total_balance"),
            availableBalance = parse.str(w, "available_balance", "availableBalance"),
            deposit = parse.str(w, "deposit", "deposit_balance"),
            giftBalance = parse.str(w, "gift_balance", "give_balance"),
        )
    }

    suspend fun orders(mobile: String, uid: String, page: Int = 1): List<ShowerOrder> {
        val data = client.consumptionLog(mobile, uid, page) ?: return emptyList()
        return parse.objects(data["data"] as? JsonArray ?: parse.array(data)).map { o ->
            ShowerOrder(
                orderSn = parse.str(o, "order_sn", "orderSn", "id"),
                deviceName = parse.str(o, "device_num", "device_name", "name").ifBlank { "设备" },
                durationText = parse.str(o, "duration", "use_time", "consume_time"),
                amount = parse.str(o, "money", "amount", "price"),
                time = parse.str(o, "create_time", "time", "created_at"),
            )
        }
    }

    suspend fun myQueueInfo(): JsonObject? = client.myQueueInfo()

    suspend fun reserveDevice(uuid: String) = client.reserve(uuid)

    suspend fun cancelReserve(uuid: String) = client.cancelReserve(uuid)

    suspend fun queueUp(areaId: String) = client.queueUp(areaId)

    suspend fun cancelQueue(areaId: String) = client.cancelQueue(areaId)

    suspend fun clearModuleCache() {
        store.removeByPrefix("shower.areas")
        store.removeByPrefix("shower.devices")
    }
}
