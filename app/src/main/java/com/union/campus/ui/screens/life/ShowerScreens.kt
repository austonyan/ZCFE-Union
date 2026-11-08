package com.union.campus.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerBuilding
import com.union.campus.domain.ShowerDevice
import com.union.campus.domain.ShowerOrg
import com.union.campus.domain.ShowerQueue
import com.union.campus.domain.ShowerSessionState
import kotlinx.coroutines.flow.first
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel

// ---------------- 洗浴首页 ----------------

/** 洗浴：楼层、设备状态、空闲数、开始洗浴与钱包入口。 */
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
    var queueConfirm by remember { mutableStateOf(false) }
    var cancelQueueConfirm by remember { mutableStateOf(false) }
    var queueBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var hasActiveSession by remember { mutableStateOf(false) }

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
                if (ex is AppError.AuthExpired || ex is AppError.Network) throw ex
                // 设备接口异常（常见 code=139）不要让整页变成“数据源异常”
                deviceError = "该区域设备暂时无法加载，可切换其他区域或稍后重试"
                emptyList()
            }
            queueInfo = runCatching { shower.myQueue() }.getOrNull()
            queueCounts = runCatching { shower.queueCounts(orgAreaId) }.getOrDefault(emptyMap())
            hasActiveSession = shower.activeSession.value != null
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
    val queueCancelAreaId = queueInfo?.areaId?.takeIf { it.isNotBlank() } ?: areaId

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
        if (queueBusy || areaId.isBlank()) return
        queueBusy = true
        launchUi {
            try {
                shower.queueUp(areaId)
                queueInfo = runCatching { shower.myQueue() }.getOrNull()
                queueCounts = runCatching { shower.queueCounts(orgAreaId) }.getOrDefault(queueCounts)
                result = "已加入排队，轮到时会通知"
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
                result = "已取消排队"
            } catch (e: AppError) {
                result = e.userMessage
            } catch (e: Exception) {
                result = "取消排队失败：${e.message}"
            } finally {
                queueBusy = false
            }
        }
    }

    if (startConfirm != null) {
        val device = startConfirm!!
        val areaText = selectedArea?.name ?: "当前区域"
        val buildingText = selectedBuilding?.let { buildingLabel(it.name) } ?: "当前楼栋"
        PrismAlertDialog(
            title = "开始洗浴",
            body = "设备 ${device.name} · $buildingText $areaText\n按实际使用时长计费，以账单为准。确认开始？",
            confirmText = "开始",
            onConfirm = {
                startConfirm = null
                launchUi {
                    try {
                        shower.startShower(device, areaText, buildingText)
                        result = null
                        onOpenSession()
                    } catch (e: AppError) {
                        result = e.userMessage
                    } catch (e: Exception) {
                        result = "开始失败：${e.message}，请刷新设备状态"
                    }
                }
            },
            onDismiss = { startConfirm = null },
        )
    }
    if (queueConfirm) {
        PrismAlertDialog(
            title = "加入排队",
            body = "当前区域暂无空闲设备，加入排队后请留意叫号，轮到时会通知。确认加入？",
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

    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("洗浴", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = onOpenWallet) { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = "钱包") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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

            PrismCard {
                CardHeader("洗浴位置")
                Spacer(Modifier.height(4.dp))
                SelectorRow(
                    label = "学校",
                    value = selectedOrg?.name ?: "请选择",
                    options = orgs.map { it.id to it.name },
                    enabled = orgs.isNotEmpty(),
                    onSelect = ::chooseOrg,
                )
                SelectorRow(
                    label = "校区",
                    value = selectedOrgArea?.name ?: "请选择",
                    options = orgAreas.map { it.id to it.name },
                    enabled = orgAreas.isNotEmpty(),
                    onSelect = ::chooseOrgArea,
                )
                SelectorRow(
                    label = "楼栋",
                    value = selectedBuilding?.let { buildingLabel(it.name) } ?: "请选择",
                    options = buildings.map { it.id to buildingLabel(it.name) },
                    enabled = buildings.isNotEmpty(),
                    onSelect = ::chooseBuilding,
                )
                SelectorRow(
                    label = "区域",
                    value = selectedArea?.let { areaLabel(it) } ?: "请选择",
                    options = areas.map { it.id to areaLabel(it) },
                    enabled = areas.isNotEmpty(),
                    onSelect = ::chooseArea,
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
            if (!loading && error == null && queueInfo != null && queueInfo!!.areaId.isNotBlank() && queueInfo!!.areaId != areaId) {
                val q = queueInfo!!
                PrismCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "已在 ${q.areaName.ifBlank { "其他区域" }} 排队" +
                            if (q.position > 0) " · 第 ${q.position} 位" else "",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = { cancelQueueConfirm = true }, enabled = !queueBusy) {
                        Text("取消排队")
                    }
                }
            }

            // 无空闲设备时提供排队入口；已排队时显示进度
            if (!loading && error == null && selectedArea != null && shower.isBound() &&
                (idleCount == 0 || myQueueAtArea != null)
            ) {
                PrismCard {
                    CardHeader("排队")
                    Spacer(Modifier.height(6.dp))
                    if (myQueueAtArea != null) {
                        val pos = myQueueAtArea.position
                        Text(if (pos > 0) "排队中 · 第 $pos 位" else "排队中", style = headlineStyle())
                        val wait = myQueueAtArea.waitMinutes.takeIf { it > 0 }
                            ?: (pos * 2).takeIf { it > 0 }
                            ?: 0
                        Text(
                            if (wait > 0) "预计等待约 $wait 分钟，轮到时会通知" else "轮到时会通知",
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { cancelQueueConfirm = true }, enabled = !queueBusy) {
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
                        Button(
                            onClick = { queueConfirm = true },
                            enabled = !queueBusy && !otherQueue,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    otherQueue -> "请先取消其他区域排队"
                                    queueBusy -> "处理中…"
                                    else -> "加入排队"
                                },
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
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp).align2())
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reload() }) { Text("重试") }
                }
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
                    Text("设备", style = headlineStyle())
                    devices.chunked(2).forEach { rowDevices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowDevices.forEach { d ->
                                DeviceCard(
                                    device = d,
                                    onClick = {
                                        if (d.state == 0) startConfirm = d
                                        else onOpenDevice(d, selectedArea)
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
            TextButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) {
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
private fun DeviceCard(device: ShowerDevice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, onBg, statusText) = when (device.state) {
        0 -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "空闲 · 可开始")
        1 -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, "使用中 · ${device.remainingText.ifBlank { "计时中" }}")
        2 -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "已预约 · ${device.remainingText}")
        else -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "故障/维护")
    }
    PrismCard(
        onClick = onClick,
        containerColor = bg,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Text(device.name, style = headlineStyle(), color = onBg)
        Spacer(Modifier.height(4.dp))
        Text(statusText, style = footnoteStyle(), color = onBg, textAlign = TextAlign.Start)
    }
}

private fun Modifier.align2(): Modifier = this.then(Modifier)

// ---------------- 设备详情 ----------------

/** 设备详情：设备状态、最近使用、开始洗浴与预约。 */
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
    var startConfirm by remember { mutableStateOf(false) }
    var reserveConfirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var buildingName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        buildingName = settings.lastShowerBuilding.first()
    }

    if (startConfirm) {
        PrismAlertDialog(
            title = "开始洗浴",
            body = "设备 ${device.name} · $areaName\n按实际使用时长计费，以账单为准。确认开始？",
            confirmText = "开始",
            onConfirm = {
                startConfirm = false
                launchUi {
                    try {
                        shower.startShower(device, areaName, buildingName)
                        onOpenSession()
                    } catch (e: AppError) { result = e.userMessage }
                    catch (e: Exception) { result = "开始失败：${e.message}，请刷新确认状态" }
                }
            },
            onDismiss = { startConfirm = false },
        )
    }
    if (reserveConfirm) {
        PrismAlertDialog(
            title = "预约设备",
            body = "预约 ${device.name}？预约需二次确认，过期自动释放。",
            confirmText = "预约",
            onConfirm = {
                reserveConfirm = false
                launchUi {
                    try {
                        shower.reserveDevice(device.uuid)
                        result = "预约成功，请在倒计时内开始"
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

    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("设备详情", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text(device.name, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$areaName · " + when (device.state) {
                        0 -> "空闲"
                        1 -> "使用中"
                        2 -> "已预约"
                        else -> "故障/维护"
                    } + "\n按实际使用时长计费，以账单为准",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text("设备状态", style = headlineStyle())
            PrismGroupedList {
                PrismRow(title = "当前状态", subtitle = when (device.state) { 0 -> "空闲 · 可立即开始"; 1 -> "使用中"; 2 -> "已预约"; else -> "故障" }, index = 0, count = 3, leading = { IconBadge(Icons.Rounded.WaterDrop) })
                PrismRow(title = "最近使用", subtitle = device.remainingText.ifBlank { "—" }, index = 1, count = 3, leading = { IconBadge(Icons.Rounded.History) })
                PrismRow(title = "故障码", subtitle = "无", index = 2, count = 3, leading = { IconBadge(Icons.Rounded.Info) })
            }
            Button(
                onClick = { startConfirm = true },
                enabled = device.state == 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (device.state == 0) "开始洗浴" else "设备不可用")
            }
            TextButton(
                onClick = { reserveConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text("预约设备")
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
    var elapsedSec by remember { mutableStateOf(((System.currentTimeMillis() - session.startedAt) / 1000).toInt()) }
    var endConfirm by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

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
                        onBack()
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

    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("洗浴中", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text(session.deviceName, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("${session.buildingName} ${session.areaName}", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("本次洗浴", style = headlineStyle())
            val minutes = elapsedSec / 60
            val secs = elapsedSec % 60
            // 时长进度（以 40 分钟为参考显示波浪进度）
            val progress = (elapsedSec / (40f * 60f)).coerceIn(0f, 1f)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            Text(
                String.format("%02d:%02d", minutes, secs),
                style = com.union.campus.ui.prism.displayStyle(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text("预计费用以结束后的账单为准（按实际使用时长计费）", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("计费提示", style = headlineStyle())
            PrismGroupedList {
                PrismRow(title = "已使用时长", subtitle = "按实际使用时长计费", index = 0, count = 2, leading = { IconBadge(Icons.Rounded.Info) })
                PrismRow(title = "结束提醒", subtitle = "离开前请确认关闭设备", index = 1, count = 2, leading = { IconBadge(Icons.Rounded.Info) })
            }

            Button(
                onClick = { endConfirm = true },
                enabled = !ending,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(if (ending) "正在结束…" else "结束洗浴")
            }
            TextButton(onClick = onOpenWallet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AccountBalanceWallet, null)
                Spacer(Modifier.width(8.dp))
                Text("查看账单")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
