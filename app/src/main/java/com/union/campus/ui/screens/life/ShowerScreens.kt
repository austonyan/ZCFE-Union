package com.union.campus.ui.screens.life

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.data.remote.shower.formatShowerCountdown
import com.union.campus.data.repo.isShowerQueueTurn
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerBuilding
import com.union.campus.domain.ShowerDevice
import com.union.campus.domain.ShowerOrg
import com.union.campus.domain.ShowerQueue
import com.union.campus.domain.ShowerReservation
import com.union.campus.domain.ShowerSessionState
import kotlinx.coroutines.flow.first
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCheckMark
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismNoticeBanner
import com.union.campus.ui.prism.PrismNoticeTone
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismStatusDot
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.metricStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.withAlpha
import com.union.campus.ui.prism.SurfaceLevel

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
    var orgId by remember { mutableStateOf("") }
    var orgAreaId by remember { mutableStateOf("") }
    var buildingId by remember { mutableStateOf("") }
    var areaId by remember { mutableStateOf("") }
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
                    val savedOrg = settings.lastShowerOrgId.first()
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
                val target = list.firstOrNull { it.id == saved } ?: list.first()
                orgId = target.id
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                if (e is AppError.AuthExpired || e is AppError.Network) {
                    error = e.userMessage
                    loading = false
                } else {
                    val savedOrg = settings.lastShowerOrgId.first()
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
            val target = list.firstOrNull { it.id == saved } ?: list.firstOrNull()
            if (target == null) {
                if (saved.isNotBlank() && saved != "0") {
                    orgAreaId = saved
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
                val fallback = settings.lastShowerOrgArea.first()
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
            var target = list.firstOrNull { it.id == saved }
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
            val target = list.firstOrNull { it.id == savedId }
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
                PrismCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer, onClick = onOpenSession, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
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

            PrismCard(onClick = { locationSheet = true }) {
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

private fun buildingLabel(name: String): String {
    val n = name.trim()
    return when {
        n.isBlank() -> "未命名楼栋"
        n.contains("楼") -> n
        n.endsWith("#") -> "${n.dropLast(1)}号楼"
        n.endsWith("号") -> "${n}楼"
        n.all { it.isDigit() } -> "${n}号楼"
        else -> n
    }
}

private fun areaLabel(area: ShowerArea): String {
    val floor = when {
        area.floor.isBlank() -> ""
        area.floor.contains("层") -> area.floor
        else -> "${area.floor}层"
    }
    val gender = when (area.genderLimit.lowercase()) {
        "male", "1", "男" -> "男"
        "female", "2", "女" -> "女"
        else -> ""
    }
    val prefix = listOf(floor, gender).filter { it.isNotBlank() }.joinToString(" ")
    return if (prefix.isBlank()) area.name else "$prefix ${area.name}"
}

@Composable
private fun ShowerLocationSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    orgs: List<ShowerOrg>,
    orgAreas: List<ShowerOrg>,
    buildings: List<ShowerBuilding>,
    areas: List<ShowerArea>,
    selectedOrgId: String,
    selectedOrgAreaId: String,
    selectedBuildingId: String,
    selectedAreaId: String,
    onSelectOrg: (String) -> Unit,
    onSelectOrgArea: (String) -> Unit,
    onSelectBuilding: (String) -> Unit,
    onSelectArea: (String) -> Unit,
) {
    val haptics = rememberPrismHaptics()
    PrismSnapSheet(visible = visible, onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("洗浴位置", style = pageTitleStyle())
                Text(
                    "选择学校与校区后，再选楼栋和区域",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (orgs.isNotEmpty()) {
                item { SectionHeader("学校") }
                item {
                    PrismGroupedList {
                        orgs.forEach { org ->
                            LocationOption(
                                title = org.name,
                                selected = org.id == selectedOrgId,
                                onClick = { haptics.select(); onSelectOrg(org.id) },
                            )
                        }
                    }
                }
            }
            if (orgAreas.isNotEmpty()) {
                item { SectionHeader("校区") }
                item {
                    PrismGroupedList {
                        orgAreas.forEach { item ->
                            LocationOption(
                                title = item.name,
                                selected = item.id == selectedOrgAreaId,
                                onClick = { haptics.select(); onSelectOrgArea(item.id) },
                            )
                        }
                    }
                }
            }
            if (buildings.isNotEmpty()) {
                item { SectionHeader("楼栋") }
                item {
                    PrismGroupedList {
                        buildings.forEach { building ->
                            LocationOption(
                                title = buildingLabel(building.name),
                                selected = building.id == selectedBuildingId,
                                onClick = { haptics.select(); onSelectBuilding(building.id) },
                            )
                        }
                    }
                }
            }
            if (areas.isNotEmpty()) {
                item { SectionHeader("区域") }
                item {
                    PrismGroupedList {
                        areas.forEach { area ->
                            LocationOption(
                                title = areaLabel(area),
                                subtitle = "空闲 ${area.idleNum}/${area.totalNum} · 排队 ${area.queueNum} 人",
                                selected = area.id == selectedAreaId,
                                onClick = { haptics.select(); onSelectArea(area.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationOption(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    PrismRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = if (selected) {
            {
                PrismCheckMark(checked = true)
            }
        } else {
            null
        },
    )
}

@Composable
private fun SelectorRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Box {
            PrismTextButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) {
                Text(value.ifBlank { "请选择" }, style = subheadlineStyle())
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "选择$label")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            open = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}
@Composable
private fun DeviceCard(
    device: ShowerDevice,
    reservedByMe: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPrismHaptics()
    val (targetBg, targetOnBg, statusText) = when {
        reservedByMe -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "已预约 · 等待启动",
        )
        device.state == 0 -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "空闲 · 可预约")
        device.state == 1 -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, "使用中 · ${device.remainingText.ifBlank { "计时中" }}")
        device.state == 2 -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "已预约" + if (device.remainingText.isNotBlank()) " · ${device.remainingText}" else "")
        else -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "故障/维护")
    }
    val bg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = PrismMotion.tween(PrismMotion.Standard),
        label = "device-bg",
    )
    val onBg by animateColorAsState(
        targetValue = targetOnBg,
        animationSpec = PrismMotion.tween(PrismMotion.Standard),
        label = "device-content",
    )
    val dotColor = when {
        reservedByMe -> MaterialTheme.colorScheme.tertiary
        device.state == 0 -> PrismStatus.success()
        device.state == 1 -> PrismStatus.warning()
        device.state == 2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    PrismCard(
        onClick = {
            haptics.tap()
            onClick()
        },
        containerColor = bg,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrismStatusDot(dotColor, size = 8)
            Spacer(Modifier.width(6.dp))
            Text(
                device.name,
                style = headlineStyle(),
                color = onBg,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(statusText, style = footnoteStyle(), color = onBg, textAlign = TextAlign.Start)
    }
}

/** 预约剩余秒数：服务端 remainTime 作为起点，本地只负责每秒递减。 */
@Composable
private fun rememberReservationRemaining(expiresAt: Long): Int {
    var now by remember(expiresAt) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= expiresAt) break
            kotlinx.coroutines.delay(1000)
        }
    }
    return ((expiresAt - now) / 1000).toInt().coerceAtLeast(0)
}

/** 预约卡片：显示服务端下发的剩余时间，超时自动取消。 */
@Composable
private fun ReservationCard(
    reservation: ShowerReservation,
    busy: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onExpired: () -> Unit,
) {
    val remaining = rememberReservationRemaining(reservation.expiresAt)
    LaunchedEffect(remaining) {
        if (remaining <= 0) onExpired()
    }
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val ringColor by animateColorAsState(
        targetValue = if (remaining <= 60) PrismStatus.warning() else MaterialTheme.colorScheme.tertiary,
        animationSpec = PrismMotion.tween(PrismMotion.Standard),
        label = "reservation-ring",
    )
    val total = reservation.maxWaitSeconds.coerceAtLeast(1)
    val fraction = (remaining.toFloat() / total).coerceIn(0f, 1f)
    PrismCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2f
                    drawArc(
                        color = onContainer.withAlpha(PrismAlpha.Track),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = "%d:%02d".format(remaining / 60, remaining % 60),
                    style = caption2Style(),
                    color = onContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("已预约 ${reservation.deviceName} · 等待启动", style = headlineStyle(), color = onContainer)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (remaining > 0) {
                        "请在 ${formatShowerCountdown(remaining)} 内启动设备，超时自动取消 · ${reservation.areaName.ifBlank { "当前区域" }}"
                    } else {
                        "预约已过期，正在自动取消，请重新预约"
                    },
                    style = footnoteStyle(),
                    color = onContainer.withAlpha(PrismAlpha.Secondary),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "预约不会自动开启设备，需在倒计时内手动启动。",
                    style = footnoteStyle(),
                    color = onContainer.withAlpha(PrismAlpha.Secondary),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrismButton(onClick = onStart, enabled = remaining > 0 && !busy, loading = busy) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("启动设备")
            }
            PrismTextButton(onClick = onCancel, enabled = !busy) {
                Text("取消预约")
            }
        }
    }
}

private fun Modifier.align2(): Modifier = this.then(Modifier)

// ---------------- 设备详情 ----------------

/** 设备详情：设备状态、最近使用、预约与预约等待内启动。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: ShowerDevice,
    areaName: String,
    onBack: () -> Unit,
    onOpenSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    val settings = ServiceLocator.settings
    val messenger = LocalPrismMessenger.current
    val haptics = rememberPrismHaptics()
    var startConfirm by remember { mutableStateOf(false) }
    var reserveConfirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var buildingName by remember { mutableStateOf("") }
    var reservation by remember { mutableStateOf<ShowerReservation?>(null) }
    var reservationBusy by remember { mutableStateOf(false) }
    var expiredGuard by remember { mutableStateOf<String?>(null) }
    val activeSession by shower.activeSession.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        buildingName = settings.lastShowerBuilding.first()
        reservation = runCatching { shower.refreshReservation() }.getOrNull()
    }

    // 预约倒计时以服务端 remainTime 为准，定时回读，避免本地时钟/后台停留造成偏差
    LaunchedEffect(device.uuid) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            reservation = runCatching { shower.refreshReservation() }.getOrNull()
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

    fun reservationExpired() {
        val r = reservation ?: return
        if (expiredGuard == r.deviceUuid) return
        expiredGuard = r.deviceUuid
        launchUi {
            runCatching { shower.cancelReserve(r.deviceUuid) }
            reservation = runCatching { shower.refreshReservation() }.getOrNull()
            messenger.warning("预约已超时并自动取消，请重新预约")
        }
    }

    if (startConfirm) {
        PrismAlertDialog(
            title = "启动设备",
            body = "设备 ${device.name} · $areaName\n预约已生效，确认启动设备并开始计费？",
            confirmText = "启动设备",
            onConfirm = {
                startConfirm = false
                launchUi {
                    try {
                        shower.startShower(device, areaName, buildingName)
                        reservation = null
                        expiredGuard = null
                        haptics.success()
                        onOpenSession()
                    } catch (e: AppError) {
                        result = e.userMessage
                        reservation = runCatching { shower.refreshReservation() }.getOrNull()
                    } catch (e: Exception) {
                        result = "启动失败：${e.message}，请刷新确认状态"
                    }
                }
            },
            onDismiss = { startConfirm = false },
        )
    }
    if (reserveConfirm) {
        PrismAlertDialog(
            title = "预约洗浴位置",
            body = "预约 ${device.name}？预约成功后进入等待倒计时，请在倒计时内启动设备；超时会自动取消。",
            confirmText = "预约",
            onConfirm = {
                reserveConfirm = false
                launchUi {
                    try {
                        val r = shower.reserveDevice(device, areaName)
                        reservation = r
                        expiredGuard = null
                        haptics.success()
                        messenger.success(
                            if (r != null) {
                                "预约成功，请在倒计时内启动设备"
                            } else {
                                "预约已提交，请刷新后查看等待时间"
                            },
                        )
                    } catch (e: AppError) { result = e.userMessage }
                    catch (e: Exception) { result = "预约失败：${e.message}" }
                }
            },
            onDismiss = { reserveConfirm = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(title = "提示", body = result ?: "", confirmText = "知道了", dismissText = null,
            onConfirm = { result = null }, onDismiss = { result = null })
    }

    val reservedHere = reservation?.deviceUuid == device.uuid

    UnionScreenScaffold(
        title = "设备详情",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text(device.name, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$areaName · " + when (device.state) {
                        0 -> "空闲 · 可预约"
                        1 -> "使用中"
                        2 -> "已预约"
                        else -> "故障/维护"
                    } + "\n按实际使用时长计费，以账单为准",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (activeSession != null) {
                PrismNoticeBanner(
                    title = "已有正在进行的洗浴",
                    message = "请先在洗浴页结束当前洗浴再预约。",
                    tone = PrismNoticeTone.Warning,
                    icon = Icons.Rounded.WaterDrop,
                )
            }
            val otherReservation = reservation
            if (!reservedHere && otherReservation != null) {
                PrismNoticeBanner(
                    title = "已在 ${otherReservation.deviceName} 预约",
                    message = "请先启动或取消该预约，再预约当前设备。",
                    tone = PrismNoticeTone.Info,
                    icon = Icons.Rounded.Schedule,
                )
            }
            if (reservedHere && activeSession == null) {
                ReservationCard(
                    reservation = reservation!!,
                    busy = reservationBusy,
                    onStart = { startConfirm = true },
                    onCancel = ::cancelReservation,
                    onExpired = ::reservationExpired,
                )
            }
            SectionHeader("设备状态")
            PrismGroupedList {
                PrismRow(title = "当前状态", subtitle = when {
                    reservedHere -> "已预约 · 请在倒计时内启动"
                    device.state == 0 -> "空闲 · 可预约"
                    device.state == 1 -> "使用中"
                    device.state == 2 -> "已预约"
                    else -> "故障"
                }, leading = { IconBadge(Icons.Rounded.WaterDrop) })
                PrismRow(title = "最近使用", subtitle = device.remainingText.ifBlank { "—" }, leading = { IconBadge(Icons.Rounded.History) })
                PrismRow(title = "故障码", subtitle = "无", leading = { IconBadge(Icons.Rounded.Info) })
            }
            when {
                reservedHere -> PrismButton(
                    onClick = { startConfirm = true },
                    enabled = activeSession == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (activeSession == null) "启动设备" else "洗浴进行中")
                }
                device.state == 0 -> PrismButton(
                    onClick = { reserveConfirm = true },
                    enabled = activeSession == null && otherReservation == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            activeSession != null -> "洗浴进行中"
                            otherReservation != null -> "请先处理其他预约"
                            else -> "预约此设备"
                        },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                else -> PrismButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设备不可用")
                }
            }
            if (!reservedHere && device.state == 0) {
                Text(
                    "预约后不会自动开启设备，请在倒计时内回到此页启动。",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 洗浴会话 ----------------

/** 洗浴中：计时、预计费用、计费提示、结束洗浴与账单入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowerSessionScreen(
    session: ShowerSessionState,
    onBack: () -> Unit,
    onOpenWallet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    val settings = ServiceLocator.settings
    val messenger = LocalPrismMessenger.current
    val haptics = rememberPrismHaptics()
    val keepScreenOn by settings.keepScreenOn.collectAsState(initial = true)
    val view = LocalView.current
    var elapsedSec by remember { mutableStateOf(((System.currentTimeMillis() - session.startedAt) / 1000).toInt()) }
    var endConfirm by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSec = ((System.currentTimeMillis() - session.startedAt) / 1000).toInt()
        }
    }

    if (endConfirm) {
        PrismAlertDialog(
            title = "结束洗浴",
            body = "确认关闭设备并结束本次洗浴？离开前请确认已关闭设备。",
            confirmText = "结束洗浴",
            destructive = true,
            onConfirm = {
                endConfirm = false
                ending = true
                launchUi {
                    try {
                        shower.endShower()
                        ending = false
                        messenger.success("已结束洗浴")
                        haptics.success()
                        onBack()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: AppError) {
                        ending = false
                        result = "结束请求失败：${e.userMessage}。可能已结束，请刷新订单确认；请勿重复点击。"
                    } catch (e: Exception) {
                        ending = false
                        result = "结束请求失败：${e.message}。可能已结束，请到账单页确认；请勿重复点击。"
                    }
                }
            },
            onDismiss = { endConfirm = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(title = "结束结果", body = result ?: "", confirmText = "知道了", dismissText = null,
            onConfirm = { result = null }, onDismiss = { result = null })
    }

    UnionScreenScaffold(
        title = "洗浴中",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text(session.deviceName, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("${session.buildingName} ${session.areaName}", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            SectionHeader("本次洗浴")
            val minutes = elapsedSec / 60
            val secs = elapsedSec % 60
            val progressTarget = (elapsedSec / (40f * 60f)).coerceIn(0f, 1f)
            val progress by animateFloatAsState(
                targetValue = progressTarget,
                animationSpec = PrismMotion.tween(PrismMotion.Slow),
                label = "session-progress",
            )
            PrismCard(containerColor = surfaceColorOf(SurfaceLevel.Card)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                        val ringTrack = MaterialTheme.colorScheme.secondaryContainer
                        val ringActive = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 8.dp.toPx()
                            val inset = stroke / 2f
                            drawArc(
                                color = ringTrack,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = ringActive,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%02d:%02d", minutes, secs),
                                style = metricStyle(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "已使用",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.deviceName, style = headlineStyle())
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${session.buildingName} ${session.areaName}",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "圆环按 40 分钟参考，实际费用以结束后的账单为准。",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("屏幕常亮", style = bodyStyle())
                        Text("洗浴期间不自动锁屏", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrismSwitch(
                        checked = keepScreenOn,
                        onCheckedChange = {
                            launchUi { settings.setKeepScreenOn(it) }
                        },
                    )
                }
            }
            Text("预计费用以结束后的账单为准（按实际使用时长计费）", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader("计费提示")
            PrismGroupedList {
                PrismRow(title = "已使用时长", subtitle = "按实际使用时长计费", leading = { IconBadge(Icons.Rounded.Info) })
                PrismRow(title = "结束提醒", subtitle = "离开前请确认关闭设备", leading = { IconBadge(Icons.Rounded.Info) })
            }

            PrismButton(
                onClick = { endConfirm = true },
                enabled = !ending,
                loading = ending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("结束洗浴")
            }
            PrismTextButton(onClick = onOpenWallet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AccountBalanceWallet, null)
                Spacer(Modifier.width(8.dp))
                Text("查看账单")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
