package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.remote.electric.ElectricClient
import com.union.campus.data.store.CacheTtl
import com.union.campus.data.store.CredentialStore
import com.union.campus.data.store.EntityStore
import com.union.campus.data.store.SettingsStore
import com.union.campus.domain.BindWizardNode
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricDayUse
import com.union.campus.domain.ElectricMonthUse
import com.union.campus.domain.ElectricRoom
import com.union.campus.domain.ElectricUsage
import com.union.campus.domain.ElectricUsageDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 电费仓库：绑定房间（四级向导）、余额、用量、充值引导。
 */
class ElectricRepository constructor(
    private val client: ElectricClient,
    private val store: EntityStore,
    private val credentials: CredentialStore,
    private val settings: SettingsStore,
) {
    private val parse = Parse

    private val _sessionState = MutableStateFlow(SessionState.NOT_BOUND)
    val sessionState: StateFlow<SessionState> = _sessionState

    enum class SessionState { NOT_BOUND, VALID }

    fun restore() {
        val cred = credentials.load()
        if (cred.electricAccount.isNotBlank()) {
            client.account = cred.electricAccount
            client.customerCode = cred.electricCustomerCode
            client.command = cred.electricCommand
            _sessionState.value = SessionState.VALID
        } else {
            _sessionState.value = SessionState.NOT_BOUND
        }
    }

    fun isBound(): Boolean = credentials.load().electricAccount.isNotBlank()

    /** 绑定电费：学号固定当前用户（安全约束，power.md §4.3.2）。 */
    suspend fun bindAccount(account: String) {
        if (account.isBlank()) throw AppError.Validation("学号不能为空")
        val cred = credentials.load().copy(electricAccount = account)
        credentials.save(cred)
        client.account = account
        _sessionState.value = SessionState.VALID
    }

    suspend fun unbindAccount() {
        val cred = credentials.load().copy(electricAccount = "")
        credentials.save(cred)
        client.account = ""
        _sessionState.value = SessionState.NOT_BOUND
        store.removeByPrefix("electric.")
        settings.setLastElectricRoom("")
    }

    suspend fun probe(): Boolean = try {
        rooms()
        true
    } catch (_: AppError) {
        false
    }

    // ---------------- 房间 ----------------

    suspend fun rooms(forceRefresh: Boolean = false): List<ElectricRoom> {
        val cacheKey = "electric.rooms"
        if (!forceRefresh) {
            store.read(cacheKey, ListSerializer(ElectricRoom.serializer()))?.let { return it.data }
        }
        val body = client.getBindRooms()
        val existFlag = parse.str(body, "existflag")
        if (existFlag != "2") return emptyList()
        val list = parse.objects(body["roomlist"] as? kotlinx.serialization.json.JsonArray ?: parse.array(body)).map { r ->
            val verify = parse.str(r, "roomverify", "roomVerify")
            ElectricRoom(
                roomVerify = verify,
                fullName = parse.str(r, "roomfullname", "room_fullname", "name").ifBlank { verify },
                zoneId = parse.str(r, "zoneid", "zone_id"),
                buildingId = parse.str(r, "buildid", "build_id"),
                floorId = parse.str(r, "floorid", "floor_id"),
                roomNum = parse.str(r, "roomnum", "room_num"),
            )
        }
        if (list.isNotEmpty()) {
            store.write(cacheKey, ListSerializer(ElectricRoom.serializer()), list)
        }
        return list
    }

    // ---------------- 余额 ----------------

    suspend fun balance(roomVerify: String, forceRefresh: Boolean = false): ElectricBalance? {
        val cacheKey = "electric.balance.$roomVerify"
        if (!forceRefresh) {
            store.read(cacheKey, ElectricBalance.serializer())?.let { return it.data }
        }
        val body = client.getIndexPage(roomVerify)
        val mods = parse.objects(body["modlist"] as? kotlinx.serialization.json.JsonArray ?: parse.array(body))
            .filter { parse.str(it, "businesstype") == "0" }
        val mod = mods.firstOrNull() ?: return null
        val weekList = parse.objects(mod["weekuselist"] as? kotlinx.serialization.json.JsonArray).map {
            ElectricDayUse(
                date = parse.str(it, "date"),
                use = parse.str(it, "use"),
                weekday = parse.int(it, "weekday"),
            )
        }
        val monthList = parse.objects(mod["monthuselist"] as? kotlinx.serialization.json.JsonArray).map {
            ElectricMonthUse(
                yearMonth = parse.str(it, "yearmonth").replace('.', '-'),
                use = parse.str(it, "use"),
            )
        }
        val data = ElectricBalance(
            roomVerify = roomVerify,
            fullName = parse.str(mod, "roomfullname", "room_full_name", "name").ifBlank { roomVerify },
            kwh = parse.str(mod, "odd", "balance"),
            price = parse.str(mod, "price"),
            todayUse = parse.str(mod, "todayuse"),
            weekUseList = weekList,
            monthUseList = monthList,
            lineStatus = parse.objects(mod["linestatus"] as? kotlinx.serialization.json.JsonArray)
                .map { parse.str(it, "desc", "status", "text") }
                .filter { it.isNotBlank() }
                .joinToString("；"),
            deviceName = parse.str(mod, "devicename", "device_name"),
            fetchedAt = System.currentTimeMillis(),
        )
        store.write(cacheKey, ElectricBalance.serializer(), data)
        return data
    }

    // ---------------- 用量 ----------------

    suspend fun usageRange(roomVerify: String, startDate: String, endDate: String): ElectricUsage {
        val body = client.getUsageRange(roomVerify, startDate, endDate)
        val days = parse.objects(body["dayuselist"] as? kotlinx.serialization.json.JsonArray).map {
            ElectricUsageDay(date = parse.str(it, "date"), use = parse.str(it, "use"))
        }.sortedBy { it.date }
        return ElectricUsage(
            roomVerify = roomVerify,
            total = parse.str(body, "sumuse", "sum"),
            days = days,
            kind = "range",
        )
    }

    suspend fun usageMonth(roomVerify: String, yearMonth: String): ElectricUsage {
        val body = client.getUsageMonth(roomVerify, yearMonth)
        val days = parse.objects(body["dayuselist"] as? kotlinx.serialization.json.JsonArray).map {
            ElectricUsageDay(date = parse.str(it, "date"), use = parse.str(it, "use"))
        }.sortedBy { it.date }
        return ElectricUsage(
            roomVerify = roomVerify,
            total = parse.str(body, "monthuse", "sum"),
            days = days,
            kind = "month",
        )
    }

    /** 每日余额曲线（近 N 天）。 */
    suspend fun dailyBalance(roomVerify: String, days: Int = 30): List<ElectricUsageDay> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val end = fmt.format(Date())
        val start = fmt.format(Date(System.currentTimeMillis() - days.toLong() * 24 * 3600_000))
        val body = client.getStroomDay(roomVerify, start, end)
        val arr = body["datalist"] as? kotlinx.serialization.json.JsonArray ?: parse.array(body)
        return parse.objects(arr).map {
            ElectricUsageDay(
                date = parse.str(it, "statdate", "date"),
                use = parse.str(it, "dayuse", "use"),
                balance = parse.str(it, "odd", "balance"),
            )
        }.sortedBy { it.date }
    }

    // ---------------- 绑定向导 ----------------

    suspend fun zones(): List<BindWizardNode> {
        val body = client.getZoneList()
        return parse.objects(parse.array(body)).map {
            BindWizardNode(parse.str(it, "id", "zoneid"), parse.str(it, "name", "zone_name").ifBlank { "园区" })
        }
    }

    suspend fun builds(zoneId: String): List<BindWizardNode> {
        val body = client.getBuildList(zoneId)
        return parse.objects(parse.array(body)).map {
            BindWizardNode(parse.str(it, "id", "buildid"), parse.str(it, "name", "build_name").ifBlank { "楼栋" })
        }
    }

    suspend fun floors(buildId: String): List<BindWizardNode> {
        val body = client.getFloorList(buildId)
        return parse.objects(parse.array(body)).map {
            BindWizardNode(parse.str(it, "id", "floorid"), parse.str(it, "name", "floor_name").ifBlank { "楼层" })
        }
    }

    suspend fun roomNums(floorId: String): List<BindWizardNode> {
        val body = client.getRoomList(floorId)
        return parse.objects(parse.array(body)).map {
            BindWizardNode(
                parse.str(it, "id", "roomnum"),
                parse.str(it, "name", "roomnum_name", "roomnum").ifBlank { "房间" },
            )
        }
    }

    /** 绑定房间（写，不重试）。 */
    suspend fun bindRoom(zoneId: String, buildId: String, floorId: String, roomNum: String): ElectricRoom {
        val body = client.bindRoom(zoneId, buildId, floorId, roomNum)
        store.remove("electric.rooms")
        return ElectricRoom(
            roomVerify = parse.str(body, "roomverify"),
            fullName = parse.str(body, "roomfullname").ifBlank { parse.str(body, "roomverify") },
            zoneId = zoneId,
            buildingId = buildId,
            floorId = floorId,
            roomNum = roomNum,
        )
    }

    /** 解绑房间（写，二次确认后调用）。 */
    suspend fun unbindRooms(roomVerifies: List<String>) {
        client.unbindRooms(roomVerifies)
        store.remove("electric.rooms")
        val last = settings.lastElectricRoom.let { "" }
        settings.setLastElectricRoom(last)
    }

    suspend fun clearModuleCache() {
        store.removeByPrefix("electric.")
    }
}
