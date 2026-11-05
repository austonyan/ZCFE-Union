package com.union.campus.ui.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
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
import com.union.campus.domain.ShowerDevice
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
    onOpenDevice: (ShowerDevice, ShowerArea?) -> Unit,
    onOpenSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    val settings = ServiceLocator.settings

    var areaName by remember { mutableStateOf("") }
    var areas by remember { mutableStateOf<List<ShowerArea>>(emptyList()) }
    var devices by remember { mutableStateOf<List<ShowerDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var startConfirm by remember { mutableStateOf<ShowerDevice?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var hasActiveSession by remember { mutableStateOf(false) }

    fun load(force: Boolean) {
        loading = true
        error = null
        launchUi {
            try {
                if (!shower.isBound()) {
                    error = "洗浴账号未绑定，请到「我的 → 凭证中心」登录大白U帮"
                } else {
                    val orgArea = settings.lastShowerOrgArea.first()
                    if (orgArea.isNotBlank()) {
                        areas = shower.areasWithState(orgArea, force)
                        val lastArea = settings.lastShowerArea.first()
                        val target = areas.firstOrNull { it.name == lastArea } ?: areas.firstOrNull()
                        target?.let {
                            areaName = it.name
                            devices = shower.devices(it.id, force)
                            // 记住默认区域
                            settings.setLastShower(it.name, settings.lastShowerBuilding.first(), orgArea)
                        }
                    }
                    hasActiveSession = shower.activeSession.value != null
                }
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "洗浴信息获取失败：${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load(true) }

    if (startConfirm != null) {
        val device = startConfirm!!
        PrismAlertDialog(
            title = "开始洗浴",
            body = "设备 ${device.name} · ${areaName}\n按实际使用时长计费，以账单为准。确认开始？",
            confirmText = "开始",
            onConfirm = {
                startConfirm = null
                launchUi {
                    try {
                        val area = areas.firstOrNull { it.name == areaName }
                        shower.startShower(device, areaName, settings.lastShowerBuilding.first())
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

            Text(areaName.ifBlank { "洗浴区域" }, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp).align2())
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { load(true) }) { Text("重试") }
                }
                !shower.isBound() -> NullStatus(Icons.Rounded.Shower, "洗浴未绑定", "到「我的 → 凭证中心」登录大白U帮")
                devices.isEmpty() -> NullStatus(Icons.Rounded.Shower, "暂无设备数据", "该区域未返回设备信息")
                else -> {
                    val idle = devices.count { it.state == 0 }
                    val using = devices.count { it.state == 1 }
                    val reserved = devices.count { it.state == 2 }
                    // 状态片组
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.union.campus.ui.prism.MetaChip("空闲 $idle", color = MaterialTheme.colorScheme.primaryContainer)
                        com.union.campus.ui.prism.MetaChip("使用中 $using")
                        com.union.campus.ui.prism.MetaChip("已预约 $reserved")
                    }
                    Text("设备", style = headlineStyle())
                    // 2 列设备网格
                    devices.chunked(2).forEach { rowDevices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowDevices.forEach { d ->
                                DeviceCard(
                                    device = d,
                                    onClick = {
                                        if (d.state == 0) startConfirm = d
                                        else onOpenDevice(d, areas.firstOrNull { it.name == areaName })
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowDevices.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    // 排队卡
                    val queueArea = areas.firstOrNull { it.name == areaName }
                    if (queueArea != null && queueArea.queueNum > 0) {
                        PrismCard {
                            CardHeader("排队情况")
                            Spacer(Modifier.height(6.dp))
                            Text("前方排队 ${queueArea.queueNum} 人，预计等待 ${queueArea.queueNum * 2} 分钟", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
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
    var startConfirm by remember { mutableStateOf(false) }
    var reserveConfirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    if (startConfirm) {
        PrismAlertDialog(
            title = "开始洗浴",
            body = "设备 ${device.name} · $areaName\n按实际使用时长计费，以账单为准。确认开始？",
            confirmText = "开始",
            onConfirm = {
                startConfirm = false
                launchUi {
                    try {
                        shower.startShower(device, areaName, "")
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
