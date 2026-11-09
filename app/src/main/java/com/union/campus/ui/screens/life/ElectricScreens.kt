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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.union.campus.UnionApplicationHolder
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricRoom
import com.union.campus.domain.ElectricStats
import com.union.campus.domain.ElectricUsage
import com.union.campus.domain.ElectricUsageDay
import com.union.campus.domain.ShowerOrder
import com.union.campus.domain.ShowerWallet
import com.union.campus.ui.common.UiState
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
import com.union.campus.ui.prism.MetaChip
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- 钱包与账单 ----------------

/** 洗浴钱包与账单：余额、押金、最近订单与充值入口。 */
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    var wallet by remember { mutableStateOf<ShowerWallet?>(null) }
    var orders by remember { mutableStateOf<List<ShowerOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var topUpNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            wallet = shower.wallet()
            orders = shower.orders()
            error = null
        } catch (e: AppError) {
            error = e.userMessage
        } catch (e: Exception) {
            error = "钱包信息获取失败：${e.message}"
        }
        loading = false
    }

    if (topUpNote != null) {
        PrismAlertDialog(
            title = "充值",
            body = topUpNote ?: "",
            confirmText = "知道了", dismissText = null,
            onConfirm = { topUpNote = null }, onDismiss = { topUpNote = null },
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
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp))
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("洗浴账号未绑定或会话过期时可到凭证中心处理", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val w = wallet
                    PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                        Text("余额 ¥ ${w?.balance ?: "—"}", style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "可用 ¥ ${w?.availableBalance ?: "—"} · 押金 ¥ ${w?.deposit ?: "—"}" +
                                (w?.giftBalance?.takeIf { it.isNotBlank() }?.let { " · 赠送 ¥ $it" } ?: ""),
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text("最近订单", style = headlineStyle())
                    if (orders.isEmpty()) {
                        NullStatus(Icons.Rounded.ReceiptLong, "暂无消费记录", "洗浴结束后账单会显示在这里")
                    } else {
                        PrismGroupedList {
                            orders.forEachIndexed { i, o ->
                                PrismRow(
                                    title = o.time,
                                    subtitle = "${o.deviceName} · ${o.durationText.ifBlank { "时长 —" }} · ¥ ${o.amount}",
                                    index = i, count = orders.size,
                                    leading = { IconBadge(Icons.Rounded.ReceiptLong) },
                                )
                            }
                        }
                    }
                    PrismButton(
                        onClick = {
                            topUpNote = "充值将跳转至大白U帮官方小程序（支付宝）完成，本应用不托管支付密码。"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Payment, null)
                        Spacer(Modifier.width(8.dp))
                        Text("去充值")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text("充值将跳转至大白U帮官方小程序（支付宝）", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onOpenTopUp: () -> Unit,
    onOpenBind: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val electric = ServiceLocator.electricRepository
    val settings = ServiceLocator.settings
    var rooms by remember { mutableStateOf<List<ElectricRoom>>(emptyList()) }
    var currentRoom by remember { mutableStateOf<ElectricRoom?>(null) }
    var balance by remember { mutableStateOf<ElectricBalance?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var roomMenu by remember { mutableStateOf(false) }
    var unbindConfirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun load(force: Boolean) {
        loading = true
        error = null
        launchUi {
            try {
                if (!electric.isBound()) {
                    error = "电费未绑定房间，请到绑定页完成绑定"
                } else {
                    rooms = electric.rooms(force)
                    val lastRoomVerify = settings.lastElectricRoom.first()
                    currentRoom = rooms.firstOrNull { it.roomVerify == lastRoomVerify } ?: rooms.firstOrNull()
                    currentRoom?.let { balance = electric.balance(it.roomVerify, force) }
                }
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "电费信息获取失败：${e.message}"
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(false) }

    if (unbindConfirm) {
        PrismAlertDialog(
            title = "解绑房间",
            body = "解绑后将无法查看该房间的余额与用量（写操作，以完美校园为准）。确认解绑？",
            confirmText = "解绑", destructive = true,
            onConfirm = {
                unbindConfirm = false
                launchUi {
                    try {
                        currentRoom?.let { electric.unbindRooms(listOf(it.roomVerify)) }
                        result = "解绑完成"
                        balance = null; currentRoom = null
                    } catch (e: AppError) { result = e.userMessage }
                }
            },
            onDismiss = { unbindConfirm = false },
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
                title = { Text("电费", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "更多") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("刷新") }, onClick = { menuOpen = false; load(true) })
                        DropdownMenuItem(text = { Text("添加房间") }, onClick = { menuOpen = false; onOpenBind() })
                        DropdownMenuItem(text = { Text("解绑房间") }, onClick = { menuOpen = false; unbindConfirm = true })
                    }
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
            Box {
                TextButton(
                    onClick = { if (rooms.size > 1) roomMenu = true },
                    enabled = rooms.size > 1,
                ) {
                    Text(
                        currentRoom?.fullName ?: "11#宿舍 · 未指定",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        error = "电量信息获取失败：${e.message}"
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
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp))
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { load(true) }) { Text("重试") }
                }
                !electric.isBound() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NullStatus(Icons.Rounded.Bolt, "电费未绑定", "绑定宿舍房间后可查看余额与用量")
                    PrismButton(onClick = onOpenBind, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Link, null)
                        Spacer(Modifier.width(8.dp))
                        Text("去绑定房间")
                    }
                }
                balance == null -> NullStatus(Icons.Rounded.Bolt, "暂无电量数据", "该房间未返回用电信息（可能未绑定）")
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
                    var metric by rememberSaveable { mutableStateOf("use") }
                    val effectiveMetric = if (price == null) "use" else metric

                    PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                        Text("剩余 ${b.kwh} 度", style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("预计可用 $days 天 · 今日 ${b.todayUse.ifBlank { "—" }} 度")
                                if (price != null && remainingValue != null) {
                                    append("\n单价 ¥${ElectricStats.formatMoney(price)}/度 · 剩余估值 ¥${ElectricStats.formatMoney(remainingValue)}")
                                } else {
                                    append("\n电价未返回 · 暂不估算费用")
                                }
                            },
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    if (price != null) {
                        PrismCard(modifier = Modifier.fillMaxWidth()) {
                            CardHeader("费用估算", icon = Icons.Rounded.Payment)
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
                                "按 ¥${ElectricStats.formatMoney(price)}/度估算，不含阶梯电价与附加费用，以完美校园账单为准。",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text("用量趋势", style = headlineStyle())
                    PrismCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (effectiveMetric == "cost") "近 7 天费用" else "近 7 天用量",
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

                    val months = b.monthUseList.sortedBy { it.yearMonth }.takeLast(6)
                    if (months.isNotEmpty()) {
                        Text("近 6 个月", style = headlineStyle())
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

                    PrismRow(
                        title = "查看用量明细",
                        subtitle = "近 30 天每日用量与费用估算",
                        index = 0, count = 1,
                        leading = { IconBadge(Icons.Rounded.BarChart) },
                        onClick = onOpenUsage,
                    )
                    if (b.lineStatus.isNotBlank()) {
                        PrismCard {
                            CardHeader("线路状态")
                            Spacer(Modifier.height(6.dp))
                            Text(b.lineStatus, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PrismButton(onClick = onOpenTopUp, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Payment, null)
                        Spacer(Modifier.width(8.dp))
                        Text("去充值")
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
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
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text("近 $r 天") },
                    )
                }
            }
            when {
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.padding(32.dp))
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error ?: "", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { load() }) { Text("重试") }
                }
                usage == null || usage!!.days.isEmpty() -> NullStatus(Icons.Rounded.BarChart, "暂无用量数据", "绑定房间后可查看每日用量")
                else -> {
                    val u = usage!!
                    val effectiveMetric = if (price == null) "use" else metric
                    val totalUse = ElectricStats.totalUse(u.days)
                    val avg = ElectricStats.dailyAverage(u.days)
                    val totalCost = ElectricStats.totalCost(u.days, price)
                    val avgCost = ElectricStats.averageCost(u.days, price)
                    val maxDay = u.days.maxByOrNull { ElectricStats.useValue(it.use) }

                    PrismCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                        Text(
                            "区间用电 ${ElectricStats.formatKwh(totalUse)} 度",
                            style = headlineStyle(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("日均 ${ElectricStats.formatKwh(avg)} 度 · 共 ${u.days.size} 天")
                                if (totalCost != null && avgCost != null) {
                                    append("\n估算 ¥${ElectricStats.formatMoney(totalCost)} · 日均 ¥${ElectricStats.formatMoney(avgCost)}")
                                } else {
                                    append("\n电价未返回 · 暂不估算费用")
                                }
                            },
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (maxDay != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "最高 ${maxDay.date} · ${ElectricStats.formatKwh(ElectricStats.useValue(maxDay.use))} 度",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每日明细", style = headlineStyle())
                        Spacer(Modifier.weight(1f))
                        if (u.days.size > 7) {
                            TextButton(onClick = { showAll = !showAll }) {
                                Text(if (showAll) "收起" else "展开全部")
                            }
                        }
                    }
                    val sorted = u.days.sortedByDescending { it.date }
                    val visible = if (showAll) sorted else sorted.take(7)
                    PrismGroupedList {
                        visible.forEachIndexed { i, d ->
                            val dayCost = ElectricStats.cost(ElectricStats.useValue(d.use), price)
                            val subtitle = buildString {
                                append("${d.use} 度")
                                if (dayCost != null) append(" · ¥${ElectricStats.formatMoney(dayCost)}")
                                if (d.balance.isNotBlank()) append(" · 余额 ${d.balance} 度")
                            }
                            PrismRow(
                                title = d.date,
                                subtitle = subtitle,
                                index = i, count = visible.size,
                                leading = { IconBadge(Icons.Rounded.BarChart) },
                            )
                        }
                    }
                    if (!showAll && sorted.size > 7) {
                        Text(
                            "默认显示最近 7 天，点「展开全部」查看全部明细",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (price != null) {
                            "费用按 ¥${ElectricStats.formatMoney(price!!)}/度估算，不含阶梯电价与附加费用，以完美校园账单为准。"
                        } else {
                            "电费接口未返回单价，当前仅展示用量。"
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

// ---------------- 电费充值 ----------------

/** 电费充值：选择金额、支付方式、生成支付链接与到账说明。 */
@Composable
fun ElectricTopUpScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val electric = ServiceLocator.electricRepository
    val settings = ServiceLocator.settings
    var amountIndex by rememberSaveable { mutableIntStateOf(1) } // 20/50/100/自定义
    var customAmount by rememberSaveable { mutableStateOf("") }
    var linkNote by remember { mutableStateOf<String?>(null) }
    var balanceText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val roomVerify = settings.lastElectricRoom.first()
            electric.balance(roomVerify)?.let { balanceText = "${it.kwh} 度" }
        } catch (_: Exception) { }
    }

    if (linkNote != null) {
        PrismAlertDialog(
            title = "支付链接已生成",
            body = linkNote ?: "",
            confirmText = "复制并返回",
            onConfirm = {
                UnionApplicationHolder.clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("union_pay", linkNote ?: ""),
                )
                linkNote = null
                onDone()
            },
            onDismiss = { linkNote = null },
        )
    }

    UnionScreenScaffold(title = "电费充值", onBack = onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismCard {
                Text("宿舍房间 · 当前余额 $balanceText", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("选择充值金额", style = headlineStyle())
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("20 元", "50 元", "100 元", "自定义").forEachIndexed { i, label ->
                    FilterChip(
                        selected = amountIndex == i,
                        onClick = { amountIndex = i },
                        label = { Text(label) },
                    )
                }
            }
            if (amountIndex == 3) {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("自定义金额（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrismCard {
                CardHeader("支付方式")
                Spacer(Modifier.height(6.dp))
                Text("完美校园 / 支付宝", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrismButton(
                onClick = {
                    val amount = when (amountIndex) {
                        0 -> "20"; 1 -> "50"; 2 -> "100"
                        else -> customAmount
                    }
                    if (amount.isBlank() || amount.toDoubleOrNull() ?: 0.0 <= 0.0) {
                        linkNote = "请输入有效金额"
                        return@PrismButton
                    }
                    // MVP：只生成提示链接，不托管支付（power.md §4.3.2）
                    linkNote = "请打开完美校园 App → 智能水电 → 选择房间 → 充值 $amount 元。\n\n（App 内支付链路需额外验证，当前版本提供跳转指引，到账以完美校园为准）"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("生成支付链接")
            }
            Text("到账可能有延迟，以完美校园为准", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrismCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text("链路说明", style = headlineStyle(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("App 内支付链路需额外验证；MVP 先做跳转/复制链接。", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
