package com.union.campus.ui.screens.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricLowState
import com.union.campus.domain.ElectricStats
import com.union.campus.domain.ShowerArea
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.MetaChip
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismEntryCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismMetricCard
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismSpace
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismToggleRow
import com.union.campus.ui.prism.SurfaceLevel
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.withAlpha
import kotlinx.coroutines.flow.first

/** 生活：洗浴、电费与空闲教室的生活总览，含低余额提醒开关。 */
@Composable
fun LifeScreen(
    onOpenElectric: () -> Unit,
    onOpenShower: () -> Unit,
    onOpenFreeClassrooms: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val electric = ServiceLocator.electricRepository
    val shower = ServiceLocator.showerRepository
    val settings = ServiceLocator.settings
    val haptics = rememberPrismHaptics()
    val lowThreshold by settings.lowElectricThreshold.collectAsState(initial = ElectricLowState.DefaultThreshold)

    var balance by remember { mutableStateOf<ElectricBalance?>(null) }
    var area by remember { mutableStateOf<ShowerArea?>(null) }
    var lowElectricOn by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load(force: Boolean) {
        lowElectricOn = settings.remindLowElectric.first()
        runCatching {
            electric.rooms(force).firstOrNull()?.let {
                balance = electric.balance(it.roomVerify, force)
            }
        }
        runCatching {
            val orgArea = settings.lastShowerOrgArea.first()
            val areaId = settings.lastShowerAreaId.first()
            if (orgArea.isNotBlank()) {
                val list = shower.areasWithState(orgArea, forceRefresh = force)
                area = list.firstOrNull { it.id == areaId } ?: list.firstOrNull()
            }
        }
    }

    LaunchedEffect(Unit) { load(false) }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        launchUi {
            try {
                load(true)
                haptics.success()
            } finally {
                refreshing = false
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = PrismSpace.Page, end = PrismSpace.Page, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(PrismSpace.Item),
        ) {
            PrismPageHeader(
                title = "生活",
                subtitle = "电费、洗浴与空闲教室",
                actions = {
                    PrismIconButton(
                        icon = Icons.Outlined.MoreVert,
                        contentDescription = "更多",
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("刷新") }, onClick = { menuOpen = false; refresh() })
                        DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onOpenSettings() })
                    }
                },
            )

            val b = balance
            val a = area
            val low = ElectricLowState.isLow(b, lowThreshold)
            // 正常态用中性卡面 + 主色强调；低余额时整卡保留告警色。
            val electricContainer = if (low) PrismStatus.warningContainer() else surfaceColorOf(SurfaceLevel.Card)
            val electricOn = if (low) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSurface
            val electricAccent = if (low) PrismStatus.warning() else MaterialTheme.colorScheme.primary
            val roomName = b?.fullName?.takeIf { it.isNotBlank() } ?: "电费未绑定"
            val days = b?.let { estimateDays(it) } ?: "—"
            val todayCost = b?.let {
                ElectricStats.cost(ElectricStats.useValue(it.todayUse), ElectricStats.unitPrice(it))
            }

            PrismMetricCard(
                label = "电费",
                value = b?.let { "${it.kwh} 度" } ?: "—",
                subtitle = if (b != null) {
                    buildString {
                        append(roomName)
                        append(" · 预计可用 $days 天")
                        append(" · 今日 ${b.todayUse.ifBlank { "—" }} 度")
                        if (todayCost != null) append(" · 约 ¥${ElectricStats.formatMoney(todayCost)}")
                        if (b.price.isNotBlank()) append(" · 单价 ${b.price} 元/度")
                    }
                } else {
                    "绑定房间后查看余额与用量"
                },
                icon = Icons.Rounded.Bolt,
                trailing = if (low) {
                    { MetaChip("余额偏低", color = electricOn.withAlpha(PrismAlpha.ContainerTint)) }
                } else {
                    null
                },
                onClick = onOpenElectric,
                containerColor = electricContainer,
                contentColor = electricOn,
                accent = electricAccent,
                modifier = Modifier.fillMaxWidth(),
            )

            PrismMetricCard(
                label = "洗浴",
                value = a?.let { "${it.idleNum} / ${it.totalNum}" } ?: "—",
                subtitle = if (a != null) {
                    "${a.name} · 排队 ${a.queueNum} 人"
                } else {
                    "登录大白U帮后查看空闲与排队"
                },
                icon = Icons.Rounded.WaterDrop,
                trailing = if (a != null) {
                    { MetaChip("空闲 ${a.idleNum}/${a.totalNum}", color = MaterialTheme.colorScheme.tertiary.withAlpha(PrismAlpha.ContainerTint)) }
                } else {
                    null
                },
                onClick = onOpenShower,
                containerColor = surfaceColorOf(SurfaceLevel.Card),
                contentColor = MaterialTheme.colorScheme.onSurface,
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            )

            PrismEntryCard(
                title = "空闲教室",
                subtitle = "今天 14:00–16:00 按教学楼查询",
                icon = Icons.Rounded.MeetingRoom,
                onClick = onOpenFreeClassrooms,
                modifier = Modifier.fillMaxWidth(),
            )

            PrismSection(title = "提醒") {
                PrismGroupedList {
                    PrismToggleRow(
                        title = "低余额提醒",
                        subtitle = ElectricLowState.thresholdText(lowThreshold),
                        checked = lowElectricOn,
                        onCheckedChange = {
                            lowElectricOn = it
                            launchUi { settings.setRemindLowElectric(it) }
                        },
                        leading = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                    )
                }
            }
            Spacer(Modifier.height(PrismSpace.Bottom))
        }
    }
}

/** 预计可用天数：余额 ÷ 近 7 日日均（估算）。 */
fun estimateDays(balance: ElectricBalance): String {
    val kwh = balance.kwh.toDoubleOrNull() ?: return "—"
    val uses = balance.weekUseList.takeLast(7).mapNotNull { it.use.toDoubleOrNull() }
    if (uses.isEmpty()) {
        val today = balance.todayUse.toDoubleOrNull() ?: return "—"
        return if (today > 0) "%.0f".format(kwh / today) else "—"
    }
    val avg = uses.average()
    return if (avg > 0) "%.0f".format(kwh / avg) else "—"
}