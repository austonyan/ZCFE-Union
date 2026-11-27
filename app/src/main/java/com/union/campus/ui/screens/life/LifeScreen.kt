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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.PrismEntryCard
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismSpace
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.SurfaceLevel
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.surfaceColorOf
import kotlinx.coroutines.flow.first

/** 生活：洗浴、电费与空闲教室的生活总览，含低余额提醒开关。 */
@Composable
fun LifeScreen(
    onOpenElectric: () -> Unit,
    onOpenShower: () -> Unit,
    onOpenFreeClassrooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val electric = ServiceLocator.electricRepository
    val shower = ServiceLocator.showerRepository
    val settings = ServiceLocator.settings
    val haptics = rememberPrismHaptics()
    val lowThreshold by settings.lowElectricThreshold.collectAsState(initial = ElectricLowState.DefaultThreshold)

    var balance by remember { mutableStateOf<ElectricBalance?>(null) }
    var area by remember { mutableStateOf<ShowerArea?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load(force: Boolean) {
        if (electric.isBound()) runCatching {
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
                .pageWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = PrismSpace.Page, end = PrismSpace.Page, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(PrismSpace.Item),
        ) {
            PrismPageHeader(
                title = "生活",
                actions = {
                    PrismIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = if (refreshing) "正在刷新" else "刷新",
                        enabled = !refreshing,
                        onClick = { refresh() },
                    )
                },
            )

            val b = balance
            val a = area
            val low = ElectricLowState.isLow(b, lowThreshold)
            // 正常态用中性卡面 + 主色强调；低余额时整卡保留告警色。
            val electricContainer = if (low) PrismStatus.warningContainer() else surfaceColorOf(SurfaceLevel.Card)
            val electricOn = if (low) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSurface
            val roomName = b?.fullName?.takeIf { it.isNotBlank() } ?: "电费信息待获取"
            val days = b?.let { estimateDays(it) } ?: "—"
            val todayCost = b?.let {
                ElectricStats.cost(ElectricStats.useValue(it.todayUse), ElectricStats.unitPrice(it))
            }

            LifeServiceCard(
                title = "电费",
                location = roomName,
                value = b?.let { "${it.kwh} 度" } ?: "—",
                detail = if (b != null) {
                    buildString {
                        append("预计可用 $days 天")
                        append(" · 今日 ${b.todayUse.ifBlank { "—" }} 度")
                        if (todayCost != null) append(" · 约 ¥${ElectricStats.formatMoney(todayCost)}")
                        if (b.price.isNotBlank()) append(" · 单价 ${b.price} 元/度")
                    }
                } else {
                    "查看绑定状态"
                },
                icon = Icons.Rounded.Bolt,
                status = if (low) "余额偏低 · 请及时充值" else null,
                statusIcon = Icons.Rounded.WarningAmber,
                onClick = onOpenElectric,
                container = electricContainer,
                onContainer = electricOn,
                modifier = Modifier.fillMaxWidth(),
            )

            val queueing = a != null && a.queueNum > 0
            LifeServiceCard(
                title = "洗浴",
                location = a?.name ?: "洗浴信息待获取",
                value = a?.let { "${it.idleNum} 个空闲" } ?: "—",
                detail = if (a != null) {
                    "共 ${a.totalNum} 个位置 · 排队 ${a.queueNum} 人"
                } else {
                    "查看账号与区域"
                },
                icon = Icons.Rounded.WaterDrop,
                status = if (queueing) "有人排队 · 查看实时状态" else null,
                statusIcon = Icons.Rounded.Schedule,
                onClick = onOpenShower,
                container = if (queueing) MaterialTheme.colorScheme.tertiaryContainer else surfaceColorOf(SurfaceLevel.Card),
                onContainer = if (queueing) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

            PrismEntryCard(
                title = "空闲教室",
                subtitle = "查找可用教室",
                icon = Icons.Rounded.MeetingRoom,
                onClick = onOpenFreeClassrooms,
                modifier = Modifier.fillMaxWidth(),
            )

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
