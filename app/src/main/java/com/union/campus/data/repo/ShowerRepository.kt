package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.remote.shower.ShowerClient
import com.union.campus.data.remote.shower.formatShowerCountdown
import com.union.campus.data.remote.shower.showerAlreadyStoppedMessage
import com.union.campus.data.remote.shower.showerNeedsReserveMessage
import com.union.campus.data.store.CacheTtl
import com.union.campus.data.store.CredentialStore
import com.union.campus.data.store.EntityStore
import com.union.campus.data.store.SettingsStore
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerBuilding
import com.union.campus.domain.ShowerDevice
import com.union.campus.domain.ShowerOrg
import com.union.campus.domain.ShowerOrder
import com.union.campus.domain.ShowerQueue
import com.union.campus.domain.ShowerReservation
import com.union.campus.domain.ShowerSessionState
import com.union.campus.domain.ShowerWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

    /** 当前预约；倒计时来源是服务端 remainTime，不在本地写死时长。 */
    private val _reservation = MutableStateFlow<ShowerReservation?>(null)
    val reservation: StateFlow<ShowerReservation?> = _reservation

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
        // LoginEntity：token/id/mobile/service_host/default_org_area_id...
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
        rememberDefaultArea(data)
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
        rememberDefaultArea(data)
        _sessionState.value = SessionState.VALID
    }

    /** 登录响应的默认校区/区域/楼栋：洗浴首页靠它决定首次加载的位置。 */
    private suspend fun rememberDefaultArea(data: JsonObject) {
        val orgId = parse.str(data, "default_org_id", "defaultOrgId", "org_id", "orgId")
        val orgAreaId = parse.str(
            data, "default_org_area_id", "defaultOrgAreaId", "org_area_id", "orgAreaId",
        )
        val buildingId = parse.str(
            data, "default_building_id", "defaultBuildingId", "building_id", "buildingId",
        )
        val areaId = parse.str(
            data, "default_device_area_id", "defaultDeviceAreaId", "device_area_id", "deviceAreaId",
        )
        val areaName = parse.str(data, "default_device_area_name", "defaultDeviceAreaName")
        if (orgAreaId.isBlank() || orgAreaId == "0" || orgAreaId.equals("null", true)) return
        settings.setShowerSelection(orgId, orgAreaId, buildingId, "", areaId, areaName)
    }

    suspend fun logout() {
        client.clear()
        val cred = credentials.load().copy(showerMobile = "", showerToken = "", showerServiceHost = "", showerUid = "")
        credentials.save(cred)
        settings.setShowerSelection("", "", "", "", "", "")
        _sessionState.value = SessionState.NOT_BOUND
        _activeSession.value = null
        _reservation.value = null
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
        return parse.objects(parse.array(data, "data", "list", "orgs")).mapNotNull {
            val id = parse.str(it, "org_id", "orgId", "id")
            val name = parse.str(it, "org_name", "orgName", "name").ifBlank { "学校" }
            if (id.isBlank()) null else ShowerOrg(id, name)
        }.distinctBy { it.id }
    }

    /** 校区区域（orgArea）：登录响应没带 default_org_area_id 时用来兜底。 */
    suspend fun orgAreas(orgId: String): List<ShowerOrg> {
        val data = client.getOrgAreas(orgId) ?: return emptyList()
        return parse.objects(parse.array(data)).mapNotNull {
            val id = parse.str(it, "org_area_id", "orgAreaId", "id")
            val name = parse.str(it, "org_area_name", "orgAreaName", "name").ifBlank { "校区" }
            if (id.isBlank()) null else ShowerOrg(id, name)
        }.distinctBy { it.id }
    }

    /**
     * 洗浴首页需要的 orgAreaId。优先用登录响应保存的默认值；
     * 旧版本登录没保存时，自动取第一个校区/区域并落盘。
     */
    suspend fun ensureDefaultOrgArea(): String {
        settings.lastShowerOrgArea.first()
            .takeIf { it.isNotBlank() && it != "0" && !it.equals("null", true) }
            ?.let { return it }
        val org = orgs().firstOrNull() ?: return ""
        val area = orgAreas(org.id).firstOrNull() ?: return ""
        if (area.id.isNotBlank() && area.id != "0") settings.setLastShower("", "", area.id)
        return area.id
    }

    suspend fun buildings(orgAreaId: String): List<ShowerBuilding> {
        val data = client.getBuildings(orgAreaId) ?: return emptyList()
        return parse.objects(parse.array(data, "data", "list")).mapNotNull {
            val id = parse.str(it, "building_id", "buildingId", "id")
            val name = parse.str(it, "building_name", "buildingName", "building_no", "buildingNo", "name")
            if (id.isBlank()) null else ShowerBuilding(id, name.ifBlank { id }, orgAreaId)
        }.distinctBy { it.id }
    }

    /** 楼栋下的设备区域（无设备统计）。 */
    suspend fun deviceAreas(orgAreaId: String, buildingId: String): List<ShowerArea> {
        val data = client.getDeviceAreas(orgAreaId, buildingId) ?: return emptyList()
        return parse.objects(parse.array(data)).mapNotNull { parseArea(it) }.distinctBy { it.id }
    }

    /** 区域 + 空闲统计（缓存 30s）。 */
    suspend fun areasWithState(orgAreaId: String, forceRefresh: Boolean = false): List<ShowerArea> {
        val cacheKey = "shower.areas.$orgAreaId"
        if (!forceRefresh) {
            store.read(cacheKey, ListSerializer(ShowerArea.serializer()))?.let {
                if (fresh(it.updatedAt, CacheTtl.SHOWER_STATE)) return it.data
            }
        }
        val data = client.getAreasWithState(orgAreaId) ?: return emptyList()
        val list = parse.objects(parse.array(data)).mapNotNull { parseArea(it) }
        if (list.isNotEmpty()) {
            store.write(cacheKey, ListSerializer(ShowerArea.serializer()), list)
        }
        return list
    }

    /**
     * 指定楼栋下的区域，并把实时空闲统计合并进来。
     * getDeviceArea 只给区域定义，selectDeviceAreasWithDeviceState 给 idleNum/totalNum。
     */
    suspend fun areasForBuilding(
        orgAreaId: String,
        buildingId: String,
        forceRefresh: Boolean = false,
    ): List<ShowerArea> {
        // v0.2.3 已验证 selectDeviceAreasWithDeviceState 返回的区域 ID 可直接用于设备查询。
        // getDeviceArea 仅作为兜底，且绝不能在主路径上抛错。
        val statesResult = runCatching { areasWithState(orgAreaId, forceRefresh) }
        val states = statesResult.getOrDefault(emptyList())
        if (states.isNotEmpty()) {
            return chooseBuildingAreas(states, emptyList(), buildingId)
        }

        val defs = runCatching { deviceAreas(orgAreaId, buildingId) }.getOrDefault(emptyList())
        if (defs.isNotEmpty()) return defs

        val error = statesResult.exceptionOrNull()
        if (error is AppError.AuthExpired || error is AppError.Network) throw error
        if (error is AppError.Validation && error.userMessage.contains("校区")) throw error
        return emptyList()
    }

    /** 解析区域：id/name/空闲数/楼栋/楼层/性别限制。 */
    private fun parseArea(a: JsonObject): ShowerArea? {
        val id = parse.str(a, "device_area_id", "deviceAreaId", "area_id", "areaId", "id")
        val name = parse.str(a, "device_area_name", "deviceAreaName", "area_name", "areaName", "name")
        if (id.isBlank() && name.isBlank()) return null
        return ShowerArea(
            id = id.ifBlank { name },
            name = name.ifBlank { "区域" },
            idleNum = parse.int(a, "idle_num", "idleNum", "free_num", "freeNum"),
            totalNum = parse.int(a, "total_num", "totalNum", "device_num", "deviceNum"),
            queueNum = parse.int(a, "queue_num", "queueNum", "wait_num", "waitNum"),
            serviceTime = parse.str(a, "service_time", "serviceTime", "open_time", "openTime"),
            maintenance = parse.str(a, "maintenance", "repair", "stop_desc", "maintenance_tip"),
            buildingId = parse.str(a, "building_id", "buildingId"),
            floor = parse.str(a, "floor", "floor_no", "floorNo"),
            genderLimit = parse.str(a, "gender_limit", "genderLimit"),
        )
    }

    private fun fresh(updatedAt: Long, ttl: Long) = System.currentTimeMillis() - updatedAt < ttl

    suspend fun devices(deviceAreaId: String, forceRefresh: Boolean = false): List<ShowerDevice> {
        val cacheKey = "shower.devices.$deviceAreaId"
        if (!forceRefresh) {
            store.read(cacheKey, ListSerializer(ShowerDevice.serializer()))?.let {
                if (fresh(it.updatedAt, CacheTtl.SHOWER_STATE)) return it.data
            }
        }
        val data = client.getDevices(deviceAreaId) ?: return emptyList()
        val list = parse.objects(parse.array(data)).map(::parseShowerDevice)
        if (list.isNotEmpty()) {
            store.write(cacheKey, ListSerializer(ShowerDevice.serializer()), list)
        }
        return list
    }

    /**
     * 启动洗浴（写，不重试）。
     * 必须先通过 [reserveDevice] 预约当前设备，并在服务端下发/兜底的等待时间内启动；
     * 不再提供“直接 turnOn”或“预约后立即 turnOn”的路径。
     */
    suspend fun startShower(device: ShowerDevice, areaName: String, buildingName: String) {
        if (_activeSession.value != null) {
            throw AppError.Validation("已有正在进行的洗浴，请先结束当前洗浴")
        }
        val reservation = _reservation.value
        if (reservation == null || reservation.deviceUuid != device.uuid) {
            throw AppError.Validation("请先预约该设备，等预约成功后再启动洗浴")
        }
        if (!canStartReservedShower(reservation, device.uuid)) {
            _reservation.value = null
            throw AppError.Validation("预约已超时并自动取消，请重新预约")
        }
        val result = try {
            client.turnOn(device.uuid)
        } catch (e: AppError.Validation) {
            // 预约已过期/被释放时，上游会回到“尚未预约任何设备”
            if (showerNeedsReserveMessage(e.userMessage)) {
                _reservation.value = null
                throw AppError.Validation("预约已失效，请重新预约后再启动")
            }
            throw e
        } catch (e: AppError.Upstream) {
            // 上游仅给错误码/技术文案时，给出可执行提示，避免“数据源返回异常”
            throw AppError.Validation("暂时无法启动洗浴，请确认设备状态后重试")
        } ?: throw AppError.Validation("设备没有返回启动结果，请刷新设备状态后重试")
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
        _reservation.value = null
    }

    /** 结束洗浴（写，不重试）。设备已结束/已关闭时按幂等成功处理。 */
    suspend fun endShower() {
        val session = _activeSession.value ?: return
        try {
            client.turnOff(session.deviceUuid)
        } catch (e: AppError.Validation) {
            // code 100170「您没有正在使用的设备」：订单已结束，清掉本地会话即可
            if (!showerAlreadyStoppedMessage(e.userMessage)) throw e
        }
        _activeSession.value = null
        store.remove(sessionFileKey)
    }

    /** 会话恢复：查未完成订单。 */
    suspend fun hasOutstandingOrder(): Boolean = try {
        val data = client.outstandingOrder()
        data?.toString()?.lowercase()?.contains("true") == true
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

    /** 消费明细：原生接口返回 { consumptionList[], ifLast, totalAmount }。 */
    suspend fun orders(page: Int = 1): List<ShowerOrder> {
        val data = client.consumptionLog(page) ?: return emptyList()
        return parse.objects(parse.array(data)).map { o ->
            ShowerOrder(
                orderSn = parse.str(o, "order_sn", "orderSn", "id"),
                deviceName = parse.str(o, "device_name", "device_number", "device_num", "name")
                    .ifBlank { "洗浴设备" },
                durationText = formatDuration(parse.str(o, "total_used_time", "duration", "use_time")),
                amount = parse.str(o, "cash_amount", "order_amount", "amount", "money"),
                time = formatEpoch(parse.str(o, "start_time", "create_time", "time", "created_at")),
            )
        }
    }

    private fun formatDuration(raw: String): String {
        val sec = raw.toLongOrNull() ?: return raw
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h}小时${m}分"
            m > 0 -> "${m}分${s}秒"
            else -> "${s}秒"
        }
    }

    private fun formatEpoch(raw: String): String {
        val value = raw.toLongOrNull() ?: return raw
        if (value <= 0) return ""
        val ms = if (value > 10_000_000_000L) value else value * 1000
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(ms))
    }

    suspend fun myQueueInfo(): JsonElement? = client.myQueueInfo()

    /** 我校区各区域排队人数：areaId -> count；无法对应区域时 key 为空串。 */
    suspend fun queueCounts(orgAreaId: String): Map<String, Int> {
        if (orgAreaId.isBlank()) return emptyMap()
        return parseQueueCounts(client.getQueueCounts(orgAreaId))
    }

    /** 我的排队状态；未排队返回 null。 */
    suspend fun myQueue(): ShowerQueue? = parseMyQueue(client.myQueueInfo())

    /**
     * 预约设备。预约成功后立即读取服务端状态：
     * `userState=reserved` 时 `remainTime` 是实时剩余秒数，用它作为倒计时。
     * 读取失败时退回预约响应里的 `maxWaitTime`；仍缺失才使用兜底窗口，
     * 之后的轮询会用服务端 remainTime 覆盖。
     */
    suspend fun reserveDevice(device: ShowerDevice, areaName: String): ShowerReservation? {
        if (_activeSession.value != null) {
            throw AppError.Validation("已有正在进行的洗浴，请先结束当前洗浴")
        }
        val existing = _reservation.value
        if (existing != null &&
            existing.deviceUuid != device.uuid &&
            existing.expiresAt > System.currentTimeMillis()
        ) {
            throw AppError.Validation("已有其他设备的预约，请先启动或取消当前预约")
        }
        val result = client.reserve(device.uuid)
        val serverReservation = try {
            refreshReservation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val merged = serverReservation ?: reservationFromReserve(device, areaName, result)
        val reservation = if (merged.areaName.isBlank()) merged.copy(areaName = areaName) else merged
        _reservation.value = reservation
        return reservation
    }

    /** 读取当前预约（服务端为准）；未预约或已超时返回 null。 */
    suspend fun refreshReservation(): ShowerReservation? {
        val state = client.stateByDeviceType()
        val reservation = parseShowerReservation(state)
        _reservation.value = reservation
        return reservation
    }

    /** 预约接口只给 maxWaitTime；拿不到 getStateByDeviceType 时用它兜底。 */
    private fun reservationFromReserve(
        device: ShowerDevice,
        areaName: String,
        result: JsonObject?,
    ): ShowerReservation {
        // 响应没有等待时间时仍要进入“预约等待”流程，用服务端常见窗口兜底；
        // 之后每次 refreshReservation 都会用服务端 remainTime 覆盖。
        val wait = result?.let { parse.int(it, "maxWaitTime", "max_wait_time") }
            ?.takeIf { it > 0 }
            ?: DEFAULT_RESERVATION_WAIT_SECONDS
        return ShowerReservation(
            deviceUuid = device.uuid,
            deviceName = device.name,
            areaName = areaName,
            maxWaitSeconds = wait,
            expiresAt = System.currentTimeMillis() + wait * 1000L,
        )
    }

    suspend fun cancelReserve(uuid: String) {
        client.cancelReserve(uuid)
        if (_reservation.value?.deviceUuid == uuid) _reservation.value = null
    }

    suspend fun queueUp(areaId: String) = client.queueUp(areaId)

    suspend fun cancelQueue(areaId: String) = client.cancelQueue(areaId)

    suspend fun clearModuleCache() {
        store.removeByPrefix("shower.areas")
        store.removeByPrefix("shower.devices")
    }
}

/**
 * 解析设备项。
 * `uuid` 取每台唯一的 `device_key`（形如 `<controller_key>_faucet_01`），
 * `controller_key` 是整层共用，不能用来区分浴位。
 */
internal fun parseShowerDevice(d: JsonObject): ShowerDevice {
    val state = showerDeviceState(d)
    val reserveTime = Parse.str(d, "reserve_time").toLongOrNull() ?: 0L
    val reserveWindow = Parse.int(d, "reserve_expires", "max_wait_time", "maxWaitTime")
    val remainingText = if (state == 2 && reserveTime > 0 && reserveWindow > 0) {
        // 已预约：用服务端 reserve_time + reserve_expires 显示剩余，而不是原始 epoch
        val left = reserveTime + reserveWindow - System.currentTimeMillis() / 1000
        formatShowerCountdown(left.toInt())
    } else {
        Parse.str(d, "remain_time", "remaining", "left_time", "max_running_time")
    }
    return ShowerDevice(
        uuid = Parse.str(d, "device_key", "controller_key", "uuid", "device_uuid", "id", "deviceUuid"),
        name = Parse.str(d, "device_name", "device_number", "device_num", "num", "name", "deviceNo")
            .ifBlank { "设备" },
        state = state,
        remainingText = remainingText,
        needReserve = Parse.str(d, "need_reserve", "needReserve").lowercase() in setOf("1", "true", "yes"),
    )
}

/** 设备状态：0 空闲 / 1 使用中 / 2 已预约 / 3 故障。 */
internal fun showerDeviceState(d: JsonObject): Int {
    val raw = Parse.str(d, "running_status", "state", "status", "device_state", "device_status").lowercase()
    return when {
        raw.contains("idle") || raw.contains("ready") || raw.contains("free") ||
            raw == "0" || raw.contains("空闲") -> 0
        raw.contains("using") || raw.contains("running") || raw.contains("busy") ||
            raw == "1" || raw.contains("使用") -> 1
        raw.contains("reserve") || raw == "2" || raw.contains("预约") -> 2
        raw.contains("fault") || raw.contains("error") || raw.contains("offline") ||
            raw == "3" || raw.contains("故障") -> 3
        else -> raw.toIntOrNull() ?: 0
    }
}

/**
 * 解析 `getStateByDeviceType` 的当前预约。
 * - `userState` 必须是 `reserved`；
 * - `remainTime` 是服务端实时剩余秒数（实测 300 → 295 → 290 → 280 → 270）；
 * - 缺失时用 `deviceInfo.reserve_time + reserve_expires`（或 `max_wait_time`）换算。
 */
internal fun parseShowerReservation(state: JsonObject?): ShowerReservation? {
    if (state == null) return null
    if (!Parse.str(state, "userState").equals("reserved", ignoreCase = true)) return null
    val info = state["deviceInfo"] as? JsonObject ?: return null
    val uuid = Parse.str(info, "device_key", "uuid")
    if (uuid.isBlank()) return null
    val remain = Parse.int(state, "remainTime")
    val window = Parse.int(info, "reserve_expires", "max_wait_time", "maxWaitTime")
    val reserveTime = Parse.str(info, "reserve_time", "reserveTime").toLongOrNull() ?: 0L
    val now = System.currentTimeMillis()
    val expiresAt = when {
        remain > 0 -> now + remain * 1000L
        reserveTime > 0 && window > 0 -> {
            val end = (reserveTime + window) * 1000L
            if (end <= now) return null
            end
        }
        else -> return null
    }
    return ShowerReservation(
        deviceUuid = uuid,
        deviceName = Parse.str(info, "device_name", "device_number", "device_num").ifBlank { "设备" },
        areaName = Parse.str(info, "deviceAreaName", "device_area_name", "area_name"),
        maxWaitSeconds = if (window > 0) window else remain,
        expiresAt = expiresAt,
    )
}

/**
 * 选择某楼栋下的区域：
 * 1) 有 getDeviceArea 定义时，用定义 ID 过滤实时区域；
 * 2) 定义缺失/ID 不匹配时，退回 buildingId；
 * 3) 区域没有 buildingId 字段时，保留全部（兼容旧校区）。
 */
internal fun chooseBuildingAreas(
    states: List<ShowerArea>,
    defs: List<ShowerArea>,
    buildingId: String,
): List<ShowerArea> {
    if (states.isEmpty()) return defs
    val byBuilding = states.filter { it.buildingId == buildingId }
    return if (byBuilding.isNotEmpty()) byBuilding else states
}

/**
 * 解析 `selectQueueCountByOrgAreaId`。
 * 可能是 `[{device_area_id, queue_num}]`、`{areaId: count}` 或直接数字。
 */
internal fun parseQueueCounts(element: JsonElement?): Map<String, Int> {
    if (element == null) return emptyMap()
    val out = mutableMapOf<String, Int>()
    fun put(areaId: String, count: Int) {
        if (areaId.isNotBlank()) out[areaId] = count
    }
    when (element) {
        is JsonArray -> element.forEach { item ->
            when (item) {
                is JsonObject -> {
                    val id = Parse.str(item, "device_area_id", "deviceAreaId", "area_id", "areaId", "id")
                    val count = Parse.int(item, "queue_num", "queueNum", "count", "num", "wait_num", "waitNum", "total")
                    if (id.isNotBlank()) put(id, count)
                    else if (count > 0) out[""] = (out[""] ?: 0) + count
                }
                is JsonPrimitive -> item.content.toIntOrNull()?.let { out[""] = (out[""] ?: 0) + it }
                else -> Unit
            }
        }
        is JsonObject -> {
            val id = Parse.str(element, "device_area_id", "deviceAreaId", "area_id", "areaId", "id")
            if (id.isNotBlank()) {
                put(id, Parse.int(element, "queue_num", "queueNum", "count", "num", "wait_num", "waitNum", "total"))
            } else {
                element.forEach { (k, v) ->
                    (v as? JsonPrimitive)?.content?.toIntOrNull()?.let { out[k] = it }
                }
            }
        }
        is JsonPrimitive -> element.content.toIntOrNull()?.let { out[""] = it }
        else -> Unit
    }
    return out
}

/**
 * 解析 `getMyQueueInfo`；空闲时上游返回空字符串，此时返回 null。
 */
internal fun parseMyQueue(element: JsonElement?): ShowerQueue? {
    val obj = when (element) {
        is JsonObject -> {
            when (val inner = element["data"]) {
                is JsonObject -> inner
                is JsonPrimitive -> if (inner.content.isNotBlank()) {
                    return parseMyQueue(inner)
                } else {
                    return null
                }
                else -> element
            }
        }
        is JsonPrimitive -> {
            val raw = element.content
            if (raw.isBlank()) return null
            val parsed = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(raw)
            }.getOrNull() ?: return null
            return parseMyQueue(parsed)
        }
        else -> return null
    }
    if (obj.isEmpty()) return null
    val areaId = Parse.str(obj, "device_area_id", "deviceAreaId", "area_id", "areaId")
    val areaName = Parse.str(obj, "device_area_name", "deviceAreaName", "area_name", "areaName")
    val position = Parse.int(
        obj, "position", "queue_position", "queuePosition", "queue_index", "queueIndex",
        "index", "sort", "queue_num", "queueNum",
    )
    val queueNum = Parse.int(obj, "queue_num", "queueNum", "count", "num", "wait_num", "waitNum", "total")
    val wait = Parse.int(
        obj, "wait_time", "waitTime", "estimated_time", "estimatedTime",
        "queue_time", "queueTime", "remain_time", "remainTime",
    )
    if (areaId.isBlank() && areaName.isBlank() && position <= 0 && queueNum <= 0) return null
    return ShowerQueue(
        areaId = areaId,
        areaName = areaName,
        position = position,
        queueNum = queueNum,
        waitMinutes = wait,
    )
}

/** 预约响应缺少等待窗口时的展示兜底；服务端 remainTime 会随后覆盖。 */
private const val DEFAULT_RESERVATION_WAIT_SECONDS = 300

/**
 * 排队是否已轮到：区域里已有空闲设备，且自己排在最前（position<=1）。
 * position=0 表示服务端未给出具体名次或已叫号，此时同样允许预约。
 */
internal fun isShowerQueueTurn(queue: ShowerQueue?, idleCount: Int): Boolean =
    queue != null && idleCount > 0 && queue.position <= 1

/** 启动前必须满足：预约属于当前设备，且尚未超过服务端下发的等待时间。 */
internal fun canStartReservedShower(
    reservation: ShowerReservation?,
    deviceUuid: String,
    now: Long = System.currentTimeMillis(),
): Boolean = reservation != null &&
    reservation.deviceUuid == deviceUuid &&
    reservation.expiresAt > now
