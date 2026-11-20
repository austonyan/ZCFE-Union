package com.union.campus.ui.screens.life

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricLowState
import com.union.campus.domain.ElectricRoom
import com.union.campus.domain.ElectricStats
import com.union.campus.domain.ElectricUsage
import com.union.campus.domain.ElectricUsageDay
import com.union.campus.domain.ShowerOrder
import com.union.campus.domain.ShowerWallet
import com.union.campus.ui.common.ExternalLinks
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.common.openExternalLink
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismActionRow
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismNoticeBanner
import com.union.campus.ui.prism.PrismNoticeTone
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- 钱包与账单 ----------------

/** 洗浴钱包与账单：余额、押金、最近 10 条订单与充值入口。 */
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    onOpenAllOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    val messenger = LocalPrismMessenger.current
    var wallet by remember { mutableStateOf<ShowerWallet?>(null) }
    var orders by remember { mutableStateOf<List<ShowerOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var topUpVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            wallet = shower.wallet()
            orders = shower.recentOrders(limit = 10)
            error = null
        } catch (e: AppError) {
            error = e.userMessage
        } catch (e: Exception) {
            error = "钱包加载失败：${e.message}"
        }
        loading = false
    }

    if (topUpVisible) {
        PrismAlertDialog(
            title = "充值",
            body = ExternalLinks.TopUpTip,
            confirmText = "确定", dismissText = null,
            onConfirm = {
                topUpVisible = false
                openExternalLink(ExternalLinks.ShowerTopUp, onFailure = { messenger.error("无法打开充值页面") })
            },
            onDismiss = { topUpVisible = false },
        )
    }

    UnionScreenScaffold(title = "钱包与账单", onBack = onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                loading -> PrismSkeletonList(Modifier.fillMaxWidth(), rows = 4)
                error != null -> PrismErrorState(
                    message = error ?: "",
                    hint = "洗浴账号未绑定或登录失效，请在凭证中心登录",
                )
                else -> {
                    val w = wallet
                    PrismCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    ) {
                        Text("余额 ¥ ${w?.balance ?: "—"}", style = pageTitleStyle(), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "可用 ¥ ${w?.availableBalance ?: "—"} · 押金 ¥ ${w?.deposit ?: "—"}" +
                                (w?.giftBalance?.takeIf { it.isNotBlank() }?.let { " · 赠送 ¥ $it" } ?: ""),
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    PrismButton(
                        onClick = { topUpVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Payment, null)
                        Spacer(Modifier.width(8.dp))
                        Text("充值")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text("充值将跳转至支付宝官方小程序充值页面", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrismSection(
                        title = "最近订单",
                        action = {
                            PrismTextButton(onClick = onOpenAllOrders) {
                                Text("查看全部")
                            }
                        },
                    ) {
                        if (orders.isEmpty()) {
                            NullStatus(Icons.Rounded.ReceiptLong, "暂无消费记录", "洗浴结束后显示账单")
                        } else {
                            PrismGroupedList {
                                orders.forEach { o ->
                                    PrismRow(
                                        title = o.time,
                                        subtitle = "${o.deviceName} · ${o.durationText.ifBlank { "时长 —" }} · ¥ ${o.amount}",
                                        leading = { IconBadge(Icons.Rounded.ReceiptLong) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 全部洗浴订单：分页加载，展示所有消费记录。 */
@Composable
fun ShowerOrdersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    var orders by remember { mutableStateOf<List<ShowerOrder>>(emptyList()) }
    // 分页序号用 rememberSaveable：从详情返回后继续从已加载到的页码往后翻。
    var page by rememberSaveable { mutableIntStateOf(0) }
    var isLast by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadNext() {
        if (loading || isLast) return
        loading = true
        error = null
        launchUi {
            try {
                val result = shower.ordersPage(page)
                val known = orders.map { it.orderSn to it.time }.toSet()
                val fresh = result.items.filter { (it.orderSn to it.time) !in known }
                orders = orders + fresh
                page += 1
                isLast = result.isLast == true || result.items.isEmpty()
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "订单加载失败：${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadNext() }

    UnionScreenScaffold(title = "全部订单", onBack = onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                orders.isEmpty() && loading -> PrismSkeletonList(Modifier.fillMaxWidth(), rows = 6)
                orders.isEmpty() && error != null -> PrismErrorState(
                    message = error ?: "",
                    hint = "洗浴账号未绑定或登录失效，请在凭证中心登录",
                    onRetry = { loadNext() },
                )
                orders.isEmpty() -> NullStatus(Icons.Rounded.ReceiptLong, "暂无消费记录", "洗浴结束后显示账单")
                else -> {
                    Text(
                        "${orders.size} 条消费记录",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrismGroupedList {
                        orders.forEach { o ->
                            PrismRow(
                                title = o.time,
                                subtitle = "${o.deviceName} · ${o.durationText.ifBlank { "时长 —" }} · ¥ ${o.amount}",
                                leading = { IconBadge(Icons.Rounded.ReceiptLong) },
                            )
                        }
                    }
                    if (!isLast) {
                        PrismButton(
                            onClick = { loadNext() },
                            loading = loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("加载更多")
                        }
                    } else {
                        Text(
                            "已显示全部订单",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = subheadlineStyle())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 电费首页 ----------------

/** 电费：余额、预计可用天数、近 7 天用量与充值引导。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricScreen(
    onBack: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenBind: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val electric = ServiceLocator.electricRepository
    val settings = ServiceLocator.settings
    val messenger = LocalPrismMessenger.current
    val lowThreshold by settings.lowElectricThreshold.collectAsState(initial = ElectricLowState.DefaultThreshold)
    var rooms by remember { mutableStateOf<List<ElectricRoom>>(emptyList()) }
    var currentRoom by remember { mutableStateOf<ElectricRoom?>(null) }
    var balance by remember { mutableStateOf<ElectricBalance?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var roomMenu by remember { mutableStateOf(false) }
    var unbindConfirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var topUpVisible by remember { mutableStateOf(false) }

    fun load(force: Boolean) {
        loading = true
        error = null
        launchUi {
            try {
                if (!electric.isBound()) {
                    error = "请先绑定电费房间"
                } else {
                    rooms = electric.rooms(force)
                    val lastRoomVerify = settings.lastElectricRoom.first()
                    currentRoom = rooms.firstOrNull { it.roomVerify == lastRoomVerify } ?: rooms.firstOrNull()
                    currentRoom?.let { balance = electric.balance(it.roomVerify, force) }
                }
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "电费加载失败：${e.message}"
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(false) }

    if (unbindConfirm) {
        PrismAlertDialog(
            title = "解绑房间",
            body = "解绑后将无法查看该房间的余额与用量。确认解绑？",
            confirmText = "解绑", destructive = true,
            onConfirm = {
                unbindConfirm = false
                launchUi {
                    try {
                        currentRoom?.let { electric.unbindRooms(listOf(it.roomVerify)) }
                        messenger.success("已解绑")
                        balance = null; currentRoom = null
                    } catch (e: AppError) { result = e.userMessage }
                }
            },
            onDismiss = { unbindConfirm = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(title = "提示", body = result ?: "", confirmText = "确定", dismissText = null,
            onConfirm = { result = null }, onDismiss = { result = null })
    }
    if (topUpVisible) {
        PrismAlertDialog(
            title = "充值",
            body = ExternalLinks.TopUpTip,
            confirmText = "确定", dismissText = null,
            onConfirm = {
                topUpVisible = false
                openExternalLink(ExternalLinks.ElectricTopUp, onFailure = { messenger.error("无法打开充值页面") })
            },
            onDismiss = { topUpVisible = false },
        )
    }

    UnionScreenScaffold(
        title = "电费",
        onBack = onBack,
        modifier = modifier,
        actions = {
            PrismIconButton(
                icon = Icons.Outlined.MoreVert,
                contentDescription = "更多",
                onClick = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("刷新") }, onClick = { menuOpen = false; load(true) })
                DropdownMenuItem(text = { Text("添加房间") }, onClick = { menuOpen = false; onOpenBind() })
                DropdownMenuItem(text = { Text("解绑房间") }, onClick = { menuOpen = false; unbindConfirm = true })
            }
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
            Box {
                PrismTextButton(
                    onClick = { if (rooms.size > 1) roomMenu = true },
                    enabled = rooms.size > 1,
                ) {
                    Text(
                        currentRoom?.fullName ?: "未选择房间",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (rooms.size > 1) {
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "切换房间")
                    }
                }
                DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                    rooms.forEach { room ->
                        DropdownMenuItem(
                            text = { Text(room.fullName) },
                            onClick = {
                                roomMenu = false
                                currentRoom = room
                                loading = true
                                error = null
                                launchUi {
                                    try {
                                        settings.setLastElectricRoom(room.roomVerify)
                                        balance = electric.balance(room.roomVerify, true)
                                    } catch (e: AppError) {
                                        error = e.userMessage
                                    } catch (e: Exception) {
                                        error = "电量加载失败：${e.message}"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                        )
                    }
                }
            }

            when {
                loading -> PrismSkeletonList(Modifier.fillMaxWidth(), rows = 4)
                error != null && electric.isBound() -> PrismErrorState(
                    message = error ?: "",
                    onRetry = { load(true) },
                )
                !electric.isBound() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NullStatus(Icons.Rounded.Bolt, "电费未绑定", "绑定宿舍后查看余额与用量")
                    PrismButton(onClick = onOpenBind, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Link, null)
                        Spacer(Modifier.width(8.dp))
                        Text("绑定房间")
                    }
                }
                balance == null -> NullStatus(Icons.Rounded.Bolt, "暂无电量数据", "暂无用电信息，请检查绑定状态")
                else -> {
                    val b = balance!!
                    val price = ElectricStats.unitPrice(b)
                    val days = estimateDays(b)
                    val weekDays = b.weekUseList.takeLast(7).map { ElectricUsageDay(it.date, it.use) }
                    val weekTotal = ElectricStats.totalUse(weekDays)
                    val todayUseValue = ElectricStats.useValue(b.todayUse)
                    val todayCost = ElectricStats.cost(todayUseValue, price)?.takeIf { b.todayUse.isNotBlank() }
                    val weekCost = ElectricStats.totalCost(weekDays, price)?.takeIf { weekDays.isNotEmpty() }
                    val projectedCost = ElectricStats.projectedMonthlyCost(weekDays, price)?.takeIf { weekDays.isNotEmpty() }
                    val remainingValue = ElectricStats.remainingValue(b)
                    val isLow = ElectricLowState.isLow(b, lowThreshold)
                    var metric by rememberSaveable { mutableStateOf("use") }
                    val effectiveMetric = if (price == null) "use" else metric

                    LifeServiceCard(
                        title = "剩余电量",
                        location = b.fullName.ifBlank { currentRoom?.fullName ?: "当前房间" },
                        value = "${b.kwh} 度",
                        detail = buildString {
                            append("预计可用 $days 天 · 今日 ${b.todayUse.ifBlank { "—" }} 度")
                            if (price != null && remainingValue != null) {
                                append("\n单价 ¥${ElectricStats.formatMoney(price)}/度 · 剩余估值 ¥${ElectricStats.formatMoney(remainingValue)}")
                            } else {
                                append("\n电价未返回，暂不计算费用")
                            }
                        },
                        icon = Icons.Rounded.Bolt,
                        container = if (isLow) PrismStatus.warningContainer() else MaterialTheme.colorScheme.surfaceContainerLow,
                        onContainer = if (isLow) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSurface,
                        status = if (isLow) "余额低于 $lowThreshold 度" else null,
                        statusIcon = Icons.Rounded.WarningAmber,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PrismActionRow {
                        PrismButton(onClick = { topUpVisible = true }) {
                            Icon(Icons.Rounded.Payment, null)
                            Spacer(Modifier.width(8.dp))
                            Text("充值", maxLines = 1, softWrap = false)
                        }
                        PrismTextButton(onClick = onOpenUsage) {
                            Text("用量明细", maxLines = 1, softWrap = false)
                        }
                    }

                    if (isLow) {
                        PrismNoticeBanner(
                            title = "余额低于 $lowThreshold 度，请及时充值",
                            tone = PrismNoticeTone.Warning,
                            icon = Icons.Rounded.WarningAmber,
                            actionLabel = "充值",
                            onAction = { topUpVisible = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (price != null) {
                        PrismCard(modifier = Modifier.fillMaxWidth()) {
                            CardHeader("费用计算", icon = Icons.Rounded.Payment)
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CostCell("今日", todayCost, Modifier.weight(1f))
                                CostCell("近 7 天", weekCost, Modifier.weight(1f))
                                CostCell("预计 30 天", projectedCost, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "按 ¥${ElectricStats.formatMoney(price)}/度计算，以完美校园账单为准",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    PrismSection(title = "用量趋势") {
                        PrismCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (effectiveMetric == "cost") "近 7 天费用" else "近 7 天用量",
                                style = headlineStyle(),
                            )
                            if (price != null) {
                                Spacer(Modifier.height(8.dp))
                                PrismSegmented(
                                    options = listOf("用量", "费用"),
                                    selectedIndex = if (effectiveMetric == "cost") 1 else 0,
                                    onSelect = { index -> metric = if (index == 0) "use" else "cost" },
                                    modifier = Modifier.width(168.dp),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            if (weekDays.isEmpty()) {
                                Text("暂无每日用量数据", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                ElectricBarChart(
                                    bars = weekDays.map { day ->
                                        val use = ElectricStats.useValue(day.use)
                                        ElectricBar(
                                            label = day.date.takeLast(5),
                                            value = if (effectiveMetric == "cost") ElectricStats.cost(use, price) ?: 0.0 else use,
                                        )
                                    },
                                    valueSuffix = if (effectiveMetric == "cost") "元" else "度",
                                    valueFormat = if (effectiveMetric == "cost") {
                                        { ElectricStats.formatMoney(it) }
                                    } else {
                                        { ElectricStats.formatKwh(it) }
                                    },
                                    barColor = if (effectiveMetric == "cost") MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary,
                                    highlightIndex = weekDays.lastIndex,
                                )
                            }
                            if (weekTotal > 0 && price != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "近 7 天合计 ${ElectricStats.formatKwh(weekTotal)} 度 · 约 ¥${ElectricStats.formatMoney(weekCost ?: 0.0)}",
                                    style = footnoteStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    val months = b.monthUseList.sortedBy { it.yearMonth }.takeLast(6)
                    if (months.isNotEmpty()) {
                        PrismSection(title = "近 6 个月") {
                            PrismCard(modifier = Modifier.fillMaxWidth()) {
                                ElectricBarChart(
                                    bars = months.map { month ->
                                        val use = ElectricStats.useValue(month.use)
                                        ElectricBar(
                                            label = month.yearMonth.substringAfter('-').ifBlank { month.yearMonth } + "月",
                                            value = if (effectiveMetric == "cost") ElectricStats.cost(use, price) ?: 0.0 else use,
                                        )
                                    },
                                    valueSuffix = if (effectiveMetric == "cost") "元" else "度",
                                    valueFormat = if (effectiveMetric == "cost") {
                                        { ElectricStats.formatMoney(it) }
                                    } else {
                                        { ElectricStats.formatKwh(it) }
                                    },
                                    barColor = if (effectiveMetric == "cost") MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary,
                                    highlightIndex = months.lastIndex,
                                )
                            }
                        }
                    }

                    PrismGroupedList {
                        PrismRow(
                            title = "用量明细",
                            subtitle = "近 30 天每日用量与费用估算",
                            leading = { IconBadge(Icons.Rounded.BarChart) },
                            onClick = onOpenUsage,
                        )
                    }
                    if (b.lineStatus.isNotBlank()) {
                        PrismCard {
                            CardHeader("线路状态")
                            Spacer(Modifier.height(6.dp))
                            Text(b.lineStatus, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 用量明细 ----------------

@Composable
private fun CostCell(
    label: String,
    value: Double?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (value != null) "¥${ElectricStats.formatMoney(value)}" else "—",
            style = headlineStyle(),
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PrismChip(
        label = label,
        selected = selected,
        onClick = onClick,
    )
}

/** 用量明细：本月总量、日均、预计可用天数与近 N 天逐日明细。 */
@Composable
fun UsageDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val electric = ServiceLocator.electricRepository
    val settings = ServiceLocator.settings
    var range by rememberSaveable { mutableIntStateOf(7) } // 7 / 30 / 自定义
    var metric by rememberSaveable { mutableStateOf("use") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var usage by remember { mutableStateOf<ElectricUsage?>(null) }
    var price by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun load() {
        loading = true
        error = null
        showAll = false
        launchUi {
            try {
                val roomVerify = settings.lastElectricRoom.first()
                val end = fmt.format(Date())
                val start = fmt.format(Date(System.currentTimeMillis() - range.toLong() * 24 * 3600_000))
                usage = electric.usageRange(roomVerify, start, end)
                price = try {
                    ElectricStats.unitPrice(electric.balance(roomVerify))
                } catch (_: Exception) {
                    null
                }
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "用量获取失败：${e.message}"
            }
            loading = false
        }
    }
    LaunchedEffect(range) { load() }

    UnionScreenScaffold(title = "用量明细", onBack = onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(7, 30, 84).forEach { r ->
                    PrismChip(
                        label = "近 $r 天",
                        selected = range == r,
                        onClick = { range = r },
                    )
                }
            }
            when {
                loading -> PrismSkeletonList(Modifier.fillMaxWidth(), rows = 4)
                error != null -> PrismErrorState(
                    message = error ?: "",
                    onRetry = { load() },
                )
                usage == null || usage!!.days.isEmpty() -> NullStatus(Icons.Rounded.BarChart, "暂无用量数据", "绑定房间后查看每日用量")
                else -> {
                    val u = usage!!
                    val effectiveMetric = if (price == null) "use" else metric
                    val totalUse = ElectricStats.totalUse(u.days)
                    val avg = ElectricStats.dailyAverage(u.days)
                    val totalCost = ElectricStats.totalCost(u.days, price)
                    val avgCost = ElectricStats.averageCost(u.days, price)
                    val maxDay = u.days.maxByOrNull { ElectricStats.useValue(it.use) }

                    PrismCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    ) {
                        Text(
                            "区间用电 ${ElectricStats.formatKwh(totalUse)} 度",
                            style = headlineStyle(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("日均 ${ElectricStats.formatKwh(avg)} 度 · 共 ${u.days.size} 天")
                                if (totalCost != null && avgCost != null) {
                                    append("\n估算 ¥${ElectricStats.formatMoney(totalCost)} · 日均 ¥${ElectricStats.formatMoney(avgCost)}")
                                } else {
                                    append("\n电价未返回，暂不计算费用")
                                }
                            },
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (maxDay != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "最高 ${maxDay.date} · ${ElectricStats.formatKwh(ElectricStats.useValue(maxDay.use))} 度",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    PrismCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (effectiveMetric == "cost") "每日费用" else "每日用量",
                                style = headlineStyle(),
                            )
                            Spacer(Modifier.weight(1f))
                            if (price != null) {
                                MetricChip("用量", metric == "use") { metric = "use" }
                                Spacer(Modifier.width(6.dp))
                                MetricChip("费用", metric == "cost") { metric = "cost" }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        ElectricBarChart(
                            bars = u.days.map { day ->
                                val use = ElectricStats.useValue(day.use)
                                ElectricBar(
                                    label = day.date.takeLast(5),
                                    value = if (effectiveMetric == "cost") ElectricStats.cost(use, price) ?: 0.0 else use,
                                )
                            },
                            valueSuffix = if (effectiveMetric == "cost") "元" else "度",
                            valueFormat = if (effectiveMetric == "cost") {
                                { ElectricStats.formatMoney(it) }
                            } else {
                                { ElectricStats.formatKwh(it) }
                            },
                            barColor = if (effectiveMetric == "cost") MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                            highlightIndex = u.days.lastIndex,
                        )
                    }

                    PrismSection(
                        title = "每日明细",
                        action = {
                            if (u.days.size > 7) {
                                PrismTextButton(onClick = { showAll = !showAll }) {
                                    Text(if (showAll) "收起" else "展开全部")
                                }
                            }
                        },
                    ) {
                        val sorted = u.days.sortedByDescending { it.date }
                        val visible = if (showAll) sorted else sorted.take(7)
                        PrismGroupedList {
                            visible.forEach { d ->
                                val dayCost = ElectricStats.cost(ElectricStats.useValue(d.use), price)
                                val subtitle = buildString {
                                    append("${d.use} 度")
                                    if (dayCost != null) append(" · ¥${ElectricStats.formatMoney(dayCost)}")
                                    if (d.balance.isNotBlank()) append(" · 余额 ${d.balance} 度")
                                }
                                PrismRow(
                                    title = d.date,
                                    subtitle = subtitle,
                                    leading = { IconBadge(Icons.Rounded.BarChart) },
                                )
                            }
                        }
                        if (!showAll && sorted.size > 7) {
                            Text(
                                "默认显示近 7 天，选择「展开全部」查看完整明细",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        if (price != null) {
                            "按 ¥${ElectricStats.formatMoney(price!!)}/度计算，以完美校园账单为准。"
                        } else {
                            "暂无电价，仅显示用量。"
                        },
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
