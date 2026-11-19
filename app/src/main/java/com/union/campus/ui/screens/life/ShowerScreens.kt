package com.union.campus.ui.screens.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.data.repo.isShowerQueueTurn
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerBuilding
import com.union.campus.domain.ShowerDevice
import com.union.campus.domain.ShowerOrg
import com.union.campus.domain.ShowerQueue
import com.union.campus.domain.ShowerReservation
import kotlinx.coroutines.flow.first
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismNoticeBanner
import com.union.campus.ui.prism.PrismNoticeTone
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle

// ---------------- 洗浴首页 ----------------

/** 洗浴：位置/设备、预约等待、排队叫号与钱包入口；启动必须经过预约。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowerScreen(
    onBack: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenBind: () -> Unit = {},
    onOpenDevice: (ShowerDevice, ShowerArea?) -> Unit,
    onOpenSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    val settings = ServiceLocator.settings
    val messenger = LocalPrismMessenger.current
    val haptics = rememberPrismHaptics()

    var orgs by remember { mutableStateOf<List<ShowerOrg>>(emptyList()) }
    var orgAreas by remember { mutableStateOf<List<ShowerOrg>>(emptyList()) }
    var buildings by remember { mutableStateOf<List<ShowerBuilding>>(emptyList()) }
    var areas by remember { mutableStateOf<List<ShowerArea>>(emptyList()) }
    var devices by remember { mutableStateOf<List<ShowerDevice>>(emptyList()) }
    // 位置选择链保存 id：从设备详情返回后继续停在同一学校/校区/楼栋/区域。
    var orgId by rememberSaveable { mutableStateOf("") }
    var orgAreaId by rememberSaveable { mutableStateOf("") }
    var buildingId by rememberSaveable { mutableStateOf("") }
    var areaId by rememberSaveable { mutableStateOf("") }
    var queueInfo by remember { mutableStateOf<ShowerQueue?>(null) }
    var queueCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deviceError by remember { mutableStateOf<String?>(null) }
    var startConfirm by remember { mutableStateOf<ShowerDevice?>(null) }
    var reserveTarget by remember { mutableStateOf<ShowerDevice?>(null) }
    var reserveBusy by remember { mutableStateOf(false) }
    var expiredGuard by remember { mutableStateOf<String?>(null) }
    var queueNotifiedFor by remember { mutableStateOf<String?>(null) }
    var queueConfirm by remember { mutableStateOf(false) }
    var cancelQueueConfirm by remember { mutableStateOf(false) }
    var queueBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    // StateFlow 直接在组合中 .value 会触发 lint 的 StateFlowValueCalledInComposition；
// collectAsState() 会订阅并在值变化时重组。
    val activeSession by shower.activeSession.collectAsState()
    val hasActiveSession = activeSession != null
    var reservation by remember { mutableStateOf<ShowerReservation?>(null) }
    var reservationBusy by remember { mutableStateOf(false) }
    var locationSheet by remember { mutableStateOf(false) }
    val remindShowerQueue by settings.remindShowerQueue.collectAsState(initial = true)

    fun loadOrgs() {
        loading = true
        error = null
        launchUi {
            try {
                val list = shower.orgs()
                orgs = list
                if (list.isEmpty()) {
                    // 列表为空或请求失败时，也优先沿用页面里已有的学校，避免返回后被清空。
                    val savedOrg = orgId.ifBlank { settings.lastShowerOrgId.first() }
                    if (savedOrg.isNotBlank() && savedOrg != "0") {
                        orgId = savedOrg
                        error = null
                    } else {
                        error = "未找到洗浴学校，请到「我的 → 凭证中心」重新登录大白U帮"
                        loading = false
                    }
                    return@launchUi
                }
                val saved = settings.lastShowerOrgId.first()
                // 返回本页时优先用保存的学校；失效了再回落到上次记录/第一项。
                val target = list.firstOrNull { it.id == orgId }
                    ?: list.firstOrNull { it.id == saved }
                    ?: list.first()
                orgId = target.id
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                if (e is AppError.AuthExpired || e is AppError.Network) {
                    error = e.userMessage
                    loading = false
                } else {
                    // 列表为空或请求失败时，也优先沿用页面里已有的学校，避免返回后被清空。
                    val savedOrg = orgId.ifBlank { settings.lastShowerOrgId.first() }
                    if (savedOrg.isNotBlank() && savedOrg != "0") {
                        orgId = savedOrg
                        error = null
                    } else {
                        error = "洗浴信息获取失败：${e.debugDetail ?: e.userMessage}"
                        loading = false
                    }
                }
            } catch (e: Exception) {
                error = "洗浴信息获取失败：${e.message}"
                loading = false
            }
        }
    }

    fun reload() {
        orgId = ""
        orgAreaId = ""
        buildingId = ""
        areaId = ""
        areas = emptyList()
        devices = emptyList()
        queueInfo = null
        queueCounts = emptyMap()
        loadOrgs()
    }

    LaunchedEffect(Unit) {
        if (!shower.isBound()) {
            loading = false
        } else {
            loadOrgs()
            reservation = runCatching { shower.refreshReservation() }.getOrNull()
        }
    }

    // 学校 -> 校区
    LaunchedEffect(orgId) {
        if (orgId.isBlank()) return@LaunchedEffect
        try {
            loading = true
            error = null
            val list = shower.orgAreas(orgId)
            orgAreas = list
            val saved = settings.lastShowerOrgArea.first()
            // 返回本页时保留已选校区；已不存在再按上次记录/第一项回落。
            val target = list.firstOrNull { it.id == orgAreaId }
                ?: list.firstOrNull { it.id == saved }
                ?: list.firstOrNull()
            if (target == null) {
                val fallback = orgAreaId.ifBlank { saved }
                if (fallback.isNotBlank() && fallback != "0") {
                    orgAreaId = fallback
                    error = null
                } else {
                    error = "该学校暂无洗浴校区"
                    loading = false
                }
            } else {
                orgAreaId = target.id
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AppError) {
            if (e is AppError.AuthExpired || e is AppError.Network) {
                error = e.userMessage
                loading = false
            } else {
                // 校区列表接口异常时，直接用登录返回/上次保存的 orgArea 继续加载
                val fallback = orgAreaId.ifBlank { settings.lastShowerOrgArea.first() }
                if (fallback.isNotBlank() && fallback != "0") {
                    orgAreas = emptyList()
                    orgAreaId = fallback
                    error = null
                } else {
                    error = "校区加载失败：${e.debugDetail ?: e.userMessage}"
                    loading = false
                }
            }
        } catch (e: Exception) {
            error = "校区加载失败：${e.message}"
            loading = false
        }
    }

    // 校区 -> 楼栋
    LaunchedEffect(orgAreaId) {
        if (orgAreaId.isBlank()) return@LaunchedEffect
        try {
            loading = true
            error = null
            val list = shower.buildings(orgAreaId)
            buildings = list
            val saved = settings.lastShowerBuildingId.first()
            val savedAreaName = settings.lastShowerArea.first()
            // 返回本页时优先保留已选楼栋，再按上次记录/区域名反查/第一栋回落。
            var target = list.firstOrNull { it.id == buildingId }
                ?: list.firstOrNull { it.id == saved }
            // 旧版本只存了区域名称：用区域反查它所在的楼栋，避免一律落在 1 号楼
            if (target == null && savedAreaName.isNotBlank()) {
                val match = runCatching { shower.areasWithState(orgAreaId) }
                    .getOrNull()
                    ?.firstOrNull { it.name == savedAreaName }
                target = list.firstOrNull { it.id == match?.buildingId }
            }
            target = target ?: list.firstOrNull()
            if (target == null) {
                // 楼栋接口不可用/为空时不阻塞区域加载，直接显示该校区全部区域
                buildings = emptyList()
                buildingId = ""
                error = null
            } else {
                buildingId = target.id
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AppError) {
            if (e is AppError.AuthExpired || e is AppError.Network) {
                error = e.userMessage
                loading = false
            } else {
                buildings = emptyList()
                buildingId = ""
                error = null
            }
        } catch (e: Exception) {
            buildings = emptyList()
            buildingId = ""
            error = null
        }
    }

    // 楼栋 -> 区域
    LaunchedEffect(orgAreaId, buildingId) {
        if (orgAreaId.isBlank()) return@LaunchedEffect
        try {
            loading = true
            error = null
            val list = shower.areasForBuilding(orgAreaId, buildingId)
            areas = list
            val savedId = settings.lastShowerAreaId.first()
            val savedName = settings.lastShowerArea.first()
            // 返回本页时优先保留已选区域；已不存在再按上次记录/名称/第一项回落。
            val target = list.firstOrNull { it.id == areaId }
                ?: list.firstOrNull { it.id == savedId }
                ?: list.firstOrNull { it.name == savedName }
                ?: list.firstOrNull()
            if (target == null) {
                error = "该校区暂无洗浴区域"
                loading = false
            } else {
                areaId = target.id
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AppError) {
            error = e.userMessage
            loading = false
        } catch (e: Exception) {
            error = "区域加载失败：${e.message}"
            loading = false
        }
    }

    // 区域 -> 设备 + 排队
    LaunchedEffect(areaId, orgAreaId) {
        if (areaId.isBlank() || orgAreaId.isBlank()) return@LaunchedEffect
        try {
            loading = true
            error = null
            deviceError = null
            devices = runCatching { shower.devices(areaId) }.getOrElse { ex ->
                if (ex is kotlinx.coroutines.CancellationException) throw ex
                if (ex is AppError.AuthExpired || ex is AppError.Network) throw ex
                // 设备接口异常（常见 code=139）不要让整页变成“数据源异常”
                deviceError = "该区域设备暂时无法加载，可切换其他区域或稍后重试"
                emptyList()
            }
            queueInfo = runCatching { shower.myQueue() }.getOrNull()
            queueCounts = runCatching { shower.queueCounts(orgAreaId) }.getOrDefault(emptyMap())
            val b = buildings.firstOrNull { it.id == buildingId }
            val a = areas.firstOrNull { it.id == areaId }
            if (a != null) {
                settings.setShowerSelection(
                    orgId = orgId,
                    orgAreaId = orgAreaId,
                    buildingId = buildingId,
                    buildingName = b?.name.orEmpty(),
                    areaId = a.id,
                    areaName = a.name,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AppError) {
            error = e.userMessage
        } catch (e: Exception) {
            error = "设备加载失败：${e.message}"
        } finally {
            loading = false
        }
    }

    // 排队进度与设备状态轮询
    LaunchedEffect(areaId, orgAreaId, buildingId) {
        if (areaId.isBlank() || orgAreaId.isBlank() || !shower.isBound()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(15_000)
            runCatching { queueInfo = shower.myQueue() }
            runCatching { queueCounts = shower.queueCounts(orgAreaId) }
            runCatching { devices = shower.devices(areaId, forceRefresh = true) }
            reservation = runCatching { shower.refreshReservation() }.getOrNull()
            runCatching { shower.areasForBuilding(orgAreaId, buildingId, forceRefresh = true) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { areas = it }
        }
    }

    val selectedOrg = orgs.firstOrNull { it.id == orgId }
    val selectedOrgArea = orgAreas.firstOrNull { it.id == orgAreaId }
    val selectedBuilding = buildings.firstOrNull { it.id == buildingId }
    val selectedArea = areas.firstOrNull { it.id == areaId }
    val myQueueAtArea = queueInfo?.takeIf {
        it.areaId == areaId ||
            (it.areaId.isBlank() && (it.areaName.isBlank() || it.areaName == selectedArea?.name))
    }
    val queueNumAtArea = queueCounts[areaId] ?: queueCounts[""] ?: selectedArea?.queueNum ?: 0
    val idleCount = if (devices.isNotEmpty()) devices.count { it.state == 0 } else selectedArea?.idleNum ?: 0
    val queueTurn = isShowerQueueTurn(myQueueAtArea, idleCount)
    val queueTurnDevice = if (queueTurn) devices.firstOrNull { it.state == 0 } else null
    val queueElsewhere = queueInfo != null && queueInfo!!.areaId.isNotBlank() && queueInfo!!.areaId != areaId
    val canReserveNow = !hasActiveSession && reservation == null && !queueElsewhere &&
        (myQueueAtArea == null || queueTurn)
    val queueCancelAreaId = queueInfo?.areaId?.takeIf { it.isNotBlank() } ?: areaId

    // 叫号提醒：仅在洗浴页打开时轮询到“已轮到”时提示一次
    LaunchedEffect(queueTurn, remindShowerQueue, reservation?.deviceUuid, myQueueAtArea?.areaId, myQueueAtArea?.position) {
        if (queueTurn && remindShowerQueue && reservation == null) {
            val key = "${myQueueAtArea?.areaId ?: areaId}:${myQueueAtArea?.position ?: 0}"
            if (queueNotifiedFor != key) {
                queueNotifiedFor = key
                haptics.success()
                messenger.success("轮到你了，请在洗浴页预约空闲设备")
            }
        }
    }

    LaunchedEffect(myQueueAtArea?.areaId, myQueueAtArea?.position) {
        if (myQueueAtArea == null) queueNotifiedFor = null
    }

    // 服务端/手动预约成功后退出排队，避免同时占着队列和设备
    LaunchedEffect(reservation?.deviceUuid, queueInfo?.areaId) {
        if (reservation == null) return@LaunchedEffect
        val q = queueInfo ?: return@LaunchedEffect
        if (q.areaId.isNotBlank() || areaId.isNotBlank()) {
            runCatching { shower.cancelQueue(q.areaId.takeIf { it.isNotBlank() } ?: areaId) }
            queueInfo = null
        }
    }

    fun chooseOrg(id: String) {
        if (id == orgId) return
        orgId = id
        orgAreaId = ""
        buildingId = ""
        areaId = ""
        orgAreas = emptyList()
        buildings = emptyList()
        areas = emptyList()
        devices = emptyList()
        queueInfo = null
        queueCounts = emptyMap()
    }

    fun chooseOrgArea(id: String) {
        if (id == orgAreaId) return
        orgAreaId = id
        buildingId = ""
        areaId = ""
        buildings = emptyList()
        areas = emptyList()
        devices = emptyList()
        queueInfo = null
    }

    fun chooseBuilding(id: String) {
        if (id == buildingId) return
        buildingId = id
        areaId = ""
        areas = emptyList()
        devices = emptyList()
        queueInfo = null
    }

    fun chooseArea(id: String) {
        if (id == areaId) return
        areaId = id
        devices = emptyList()
        queueInfo = null
        launchUi {
            val b = buildings.firstOrNull { it.id == buildingId }
            val a = areas.firstOrNull { it.id == id }
            settings.setShowerSelection(
                orgId = orgId,
                orgAreaId = orgAreaId,
                buildingId = buildingId,
                buildingName = b?.name.orEmpty(),
                areaId = id,
                areaName = a?.name.orEmpty(),
            )
        }
    }

    fun joinQueue() {
        if (hasActiveSession) {
            result = "当前正在洗浴，请先结束当前洗浴"
            return
        }
        if (queueInfo != null) {
            result = "已在排队中，请勿重复加入"
            return
        }
        if (queueBusy || areaId.isBlank()) return
        queueBusy = true
        launchUi {
            try {
                shower.queueUp(areaId)
                queueInfo = runCatching { shower.myQueue() }.getOrNull()
                queueCounts = runCatching { shower.queueCounts(orgAreaId) }.getOrDefault(queueCounts)
                messenger.success("已加入排队，轮到后可在本页预约空闲设备")
                haptics.success()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                result = e.userMessage
            } catch (e: Exception) {
                result = "加入排队失败：${e.message}"
            } finally {
                queueBusy = false
            }
        }
    }

    fun cancelQueue() {
        if (queueBusy || queueCancelAreaId.isBlank()) return
        queueBusy = true
        launchUi {
            try {
                shower.cancelQueue(queueCancelAreaId)
                queueInfo = runCatching { shower.myQueue() }.getOrNull()
                queueCounts = runCatching { shower.queueCounts(orgAreaId) }.getOrDefault(queueCounts)
                messenger.info("已取消排队")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                result = e.userMessage
            } catch (e: Exception) {
                result = "取消排队失败：${e.message}"
            } finally {
                queueBusy = false
            }
        }
    }

    /** 预约当前区域的一个空闲设备；成功后进入等待倒计时，不再直接启动。 */
    fun reserve(device: ShowerDevice) {
        if (hasActiveSession) {
            result = "当前正在洗浴，请先结束当前洗浴"
            return
        }
        if (myQueueAtArea != null && !queueTurn) {
            result = "还在排队中，轮到后请在本页预约空闲设备"
            return
        }
        if (queueElsewhere) {
            result = "请先取消其他区域的排队，再预约当前区域"
            return
        }
        if (reserveBusy || reservation != null) return
        reserveBusy = true
        val areaText = selectedArea?.name ?: "当前区域"
        launchUi {
            try {
                val r = shower.reserveDevice(device, areaText)
                reservation = r
                expiredGuard = null
                devices = runCatching { shower.devices(areaId, forceRefresh = true) }
                    .getOrDefault(devices)
                haptics.success()
                if (r != null) {
                    messenger.success("预约成功，请在倒计时内启动设备")
                } else {
                    messenger.success("预约已提交，请稍候刷新预约状态")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                result = e.userMessage
            } catch (e: Exception) {
                result = "预约失败：${e.message}"
            } finally {
                reserveBusy = false
            }
        }
    }

    /** 从预约卡片启动：先向服务端核对预约，再进入启动确认。 */
    fun startReserved() {
        if (reservationBusy) return
        reservationBusy = true
        launchUi {
            try {
                val latest = shower.refreshReservation()
                if (latest == null) {
                    reservation = null
                    result = "预约已超时或已取消，请重新预约"
                    return@launchUi
                }
                reservation = latest
                val fresh = runCatching { shower.devices(areaId, forceRefresh = true) }
                    .getOrDefault(emptyList())
                if (fresh.isNotEmpty()) devices = fresh
                val device = fresh.firstOrNull { it.uuid == latest.deviceUuid }
                if (device != null) {
                    startConfirm = device
                } else {
                    result = "预约在 ${latest.areaName.ifBlank { "其他区域" }}，请先切换到该区域再启动"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                result = e.userMessage
            } catch (e: Exception) {
                result = "预约状态刷新失败：${e.message}"
            } finally {
                reservationBusy = false
            }
        }
    }

    fun cancelReservation() {
        val r = reservation ?: return
        if (reservationBusy) return
        reservationBusy = true
        launchUi {
            try {
                shower.cancelReserve(r.deviceUuid)
                reservation = null
                expiredGuard = null
                devices = runCatching { shower.devices(areaId, forceRefresh = true) }
                    .getOrDefault(devices)
                messenger.info("已取消预约")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                result = e.userMessage
            } catch (e: Exception) {
                result = "取消预约失败：${e.message}"
            } finally {
                reservationBusy = false
            }
        }
    }

    /** 本地倒计时归零：主动释放预约并以服务端状态为准，避免占住设备。 */
    fun reservationExpired() {
        val r = reservation ?: return
        if (expiredGuard == r.deviceUuid) return
        expiredGuard = r.deviceUuid
        launchUi {
            runCatching { shower.cancelReserve(r.deviceUuid) }
            reservation = runCatching { shower.refreshReservation() }.getOrNull()
            devices = runCatching { shower.devices(areaId, forceRefresh = true) }
                .getOrDefault(devices)
            messenger.warning("预约已超时并自动取消，请重新预约")
        }
    }

    if (reserveTarget != null) {
        val device = reserveTarget!!
        val areaText = selectedArea?.name ?: "当前区域"
        val buildingText = selectedBuilding?.let { buildingLabel(it.name) } ?: "当前楼栋"
        PrismAlertDialog(
            title = "预约洗浴位置",
            body = "预约设备 ${device.name} · $buildingText $areaText\n预约成功后进入等待倒计时，请在倒计时内启动设备；超时会自动取消。",
            confirmText = "预约",
            onConfirm = {
                reserveTarget = null
                reserve(device)
            },
            onDismiss = { reserveTarget = null },
        )
    }
    if (startConfirm != null) {
        val device = startConfirm!!
        val areaText = selectedArea?.name ?: "当前区域"
        val buildingText = selectedBuilding?.let { buildingLabel(it.name) } ?: "当前楼栋"
        PrismAlertDialog(
            title = "启动设备",
            body = "设备 ${device.name} · $buildingText $areaText\n预约已生效，确认启动设备并开始计费？",
            confirmText = "启动设备",
            onConfirm = {
                startConfirm = null
                launchUi {
                    try {
                        shower.startShower(device, areaText, buildingText)
                        if (queueInfo != null && queueCancelAreaId.isNotBlank()) {
                            runCatching { shower.cancelQueue(queueCancelAreaId) }
                        }
                        reservation = null
                        expiredGuard = null
                        queueInfo = null
                        result = null
                        haptics.success()
                        onOpenSession()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: AppError) {
                        result = e.userMessage
                        reservation = runCatching { shower.refreshReservation() }.getOrNull()
                    } catch (e: Exception) {
                        result = "启动失败：${e.message}，请刷新设备状态"
                    }
                }
            },
            onDismiss = { startConfirm = null },
        )
    }
    if (queueConfirm) {
        PrismAlertDialog(
            title = "加入排队",
            body = "当前区域暂无空闲设备。加入排队后请保持洗浴页打开，轮到后可在本页预约空闲设备并在倒计时内启动。确认加入？",
            confirmText = "加入排队",
            onConfirm = { queueConfirm = false; joinQueue() },
            onDismiss = { queueConfirm = false },
        )
    }
    if (cancelQueueConfirm) {
        PrismAlertDialog(
            title = "取消排队",
            body = "确认取消当前排队？取消后需要重新排队。",
            confirmText = "取消排队",
            destructive = true,
            onConfirm = { cancelQueueConfirm = false; cancelQueue() },
            onDismiss = { cancelQueueConfirm = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(title = "提示", body = result ?: "", confirmText = "知道了", dismissText = null,
            onConfirm = { result = null }, onDismiss = { result = null })
    }

    UnionScreenScaffold(
        title = "洗浴",
        onBack = onBack,
        modifier = modifier,
        actions = {
            PrismIconButton(
                icon = Icons.Outlined.AccountBalanceWallet,
                contentDescription = "钱包",
                onClick = onOpenWallet,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasActiveSession) {
                PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onOpenSession,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shower, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("洗浴进行中 · 点此回到计时页", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            if (!hasActiveSession) {
                reservation?.let { r ->
                    ReservationCard(
                        reservation = r,
                        busy = reservationBusy,
                        onStart = ::startReserved,
                        onCancel = ::cancelReservation,
                        onExpired = ::reservationExpired,
                    )
                }
            }

            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { locationSheet = true },
            ) {
                CardHeader("洗浴位置")
                Spacer(Modifier.height(6.dp))
                val path = listOfNotNull(
                    selectedOrg?.name,
                    selectedOrgArea?.name,
                    selectedBuilding?.let { buildingLabel(it.name) },
                    selectedArea?.let { areaLabel(it) },
                ).joinToString(" · ")
                Text(
                    path.ifBlank { "选择学校、校区、楼栋与区域" },
                    style = bodyStyle(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (selectedArea != null) "空闲 ${selectedArea.idleNum}/${selectedArea.totalNum} · 点此更换" else "点此选择位置",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (buildings.isEmpty() && areas.isNotEmpty()) {
                Text(
                    "楼栋列表暂不可用，已显示该校区的全部洗浴区域",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 已在其他区域排队时给出提示，避免重复排队
            if (!loading && error == null && reservation == null && queueInfo != null && queueInfo!!.areaId.isNotBlank() && queueInfo!!.areaId != areaId) {
                val q = queueInfo!!
                PrismNoticeBanner(
                    title = "已在 ${q.areaName.ifBlank { "其他区域" }} 排队" +
                        if (q.position > 0) " · 第 ${q.position} 位" else "",
                    tone = PrismNoticeTone.Warning,
                    icon = Icons.Rounded.Schedule,
                    actionLabel = "取消排队",
                    onAction = { if (!queueBusy) cancelQueueConfirm = true },
                )
            }

            // 无空闲设备时提供排队入口；已排队时显示进度
            if (!loading && error == null && reservation == null && selectedArea != null && shower.isBound() &&
                !queueElsewhere && (idleCount == 0 || myQueueAtArea != null)
            ) {
                PrismCard {
                    CardHeader("排队")
                    Spacer(Modifier.height(6.dp))
                    if (myQueueAtArea != null) {
                        val pos = myQueueAtArea.position
                        Text(
                            if (queueTurn) "轮到你了" else if (pos > 0) "排队中 · 第 $pos 位" else "排队中",
                            style = headlineStyle(),
                        )
                        val wait = myQueueAtArea.waitMinutes.takeIf { it > 0 }
                            ?: (pos * 2).takeIf { it > 0 }
                            ?: 0
                        Text(
                            when {
                                queueTurn -> "区域已有空闲设备，预约后进入等待，请在倒计时内启动设备"
                                wait > 0 -> "预计等待约 $wait 分钟，轮到时会通知"
                                else -> "轮到时会通知"
                            },
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (queueTurn) {
                            Spacer(Modifier.height(8.dp))
                            PrismButton(
                                onClick = {
                                    val device = queueTurnDevice
                                    if (device != null) {
                                        reserveTarget = device
                                    } else {
                                        result = "空闲设备还在加载，请稍后重试"
                                    }
                                },
                                enabled = queueTurnDevice != null && !queueBusy && !reserveBusy && !hasActiveSession,
                                loading = reserveBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (queueTurnDevice != null) "预约空闲设备" else "正在加载空闲设备",
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        PrismTextButton(onClick = { cancelQueueConfirm = true }, enabled = !queueBusy) {
                            Text("取消排队")
                        }
                    } else {
                        Text("当前区域没有空闲设备", style = headlineStyle())
                        Text(
                            if (queueNumAtArea > 0) "前方排队 $queueNumAtArea 人，预计等待 ${queueNumAtArea * 2} 分钟"
                            else "当前无人排队，加入后轮到你会通知",
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val otherQueue = queueInfo != null && queueInfo!!.areaId.isNotBlank() && queueInfo!!.areaId != areaId
                        PrismButton(
                            onClick = { queueConfirm = true },
                            enabled = !queueBusy && !otherQueue && !hasActiveSession,
                            loading = queueBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    otherQueue -> "请先取消其他区域排队"
                                    else -> "加入排队"
                                },
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Text(
                selectedArea?.let { "${it.name} · 空闲 ${it.idleNum}/${it.totalNum}" } ?: "洗浴区域",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                loading -> PrismSkeletonList(Modifier.fillMaxWidth(), rows = 4, showHero = false)
                error != null -> PrismErrorState(
                    message = error ?: "",
                    onRetry = { reload() },
                )
                !shower.isBound() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NullStatus(Icons.Rounded.Shower, "洗浴未绑定", "登录大白U帮后查看设备空闲与排队")
                    PrismButton(onClick = onOpenBind, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Shower, null)
                        Spacer(Modifier.width(8.dp))
                        Text("去登录洗浴")
                    }
                }
                devices.isEmpty() -> NullStatus(
                    Icons.Rounded.Shower,
                    if (deviceError != null) "设备暂时无法加载" else "暂无设备数据",
                    deviceError ?: "该区域未返回设备信息；如无空位可在上方加入排队",
                )
                else -> {
                    val using = devices.count { it.state == 1 }
                    val reserved = devices.count { it.state == 2 }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.union.campus.ui.prism.MetaChip("空闲 $idleCount", color = MaterialTheme.colorScheme.primaryContainer)
                        com.union.campus.ui.prism.MetaChip("使用中 $using")
                        com.union.campus.ui.prism.MetaChip("已预约 $reserved")
                        if (queueNumAtArea > 0) {
                            com.union.campus.ui.prism.MetaChip("排队 $queueNumAtArea")
                        }
                    }
                    SectionHeader("设备")
                    Text(
                        "点击空闲设备先预约，预约成功后在倒计时内启动；超时会自动取消。",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (canReserveNow && idleCount > 0 && myQueueAtArea == null) {
                        val firstIdle = devices.firstOrNull { it.state == 0 }
                        Spacer(Modifier.height(8.dp))
                        PrismButton(
                            onClick = {
                                if (firstIdle != null) {
                                    reserveTarget = firstIdle
                                } else {
                                    result = "空闲设备还在加载，请稍后重试"
                                }
                            },
                            enabled = firstIdle != null && !reserveBusy && canReserveNow,
                            loading = reserveBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Schedule, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (firstIdle != null) "预约空闲位置" else "正在加载空闲设备",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    devices.chunked(2).forEach { rowDevices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowDevices.forEach { d ->
                                DeviceCard(
                                    device = d,
                                    reservedByMe = reservation?.deviceUuid == d.uuid,
                                    onClick = {
                                        if (hasActiveSession) {
                                            onOpenSession()
                                        } else {
                                            val r = reservation
                                            when {
                                                r != null && r.deviceUuid == d.uuid -> startConfirm = d
                                                r != null -> result = "请先启动或取消当前预约（${r.deviceName}）"
                                                d.state == 0 -> if (canReserveNow) {
                                                    reserveTarget = d
                                                } else if (queueElsewhere) {
                                                    result = "请先取消其他区域的排队"
                                                } else if (myQueueAtArea != null && !queueTurn) {
                                                    result = "还在排队中，轮到后再预约设备"
                                                } else {
                                                    result = "请先完成当前预约或洗浴"
                                                }
                                                else -> onOpenDevice(d, selectedArea)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowDevices.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    ShowerLocationSheet(
        visible = locationSheet,
        onDismiss = { locationSheet = false },
        orgs = orgs,
        orgAreas = orgAreas,
        buildings = buildings,
        areas = areas,
        selectedOrgId = orgId,
        selectedOrgAreaId = orgAreaId,
        selectedBuildingId = buildingId,
        selectedAreaId = areaId,
        onSelectOrg = ::chooseOrg,
        onSelectOrgArea = ::chooseOrgArea,
        onSelectBuilding = ::chooseBuilding,
        onSelectArea = { id ->
            chooseArea(id)
            locationSheet = false
        },
    )
}
