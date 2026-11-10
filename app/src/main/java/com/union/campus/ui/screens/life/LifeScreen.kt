package com.union.campus.ui.screens.life

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricStats
import com.union.campus.domain.ShowerArea
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.MetaChip
import com.union.campus.ui.prism.PrismAnimatedText
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
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

    UnionScreenScaffold(
        title = "生活",
        modifier = modifier,
        largeTitle = true,
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("刷新") }, onClick = { menuOpen = false; refresh() })
                DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onOpenSettings() })
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val roomName = balance?.fullName?.takeIf { it.isNotBlank() } ?: "电费未绑定"
                Text(roomName, style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

                val low = balance?.kwh?.toDoubleOrNull()?.let { it < 20.0 } == true
                val electricContainer = if (low) PrismStatus.warningContainer() else MaterialTheme.colorScheme.secondaryContainer
                val electricOn = if (low) PrismStatus.onWarningContainer() else MaterialTheme.colorScheme.onSecondaryContainer

                PrismCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenElectric,
                    containerColor = electricContainer,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = electricOn)
                        Spacer(Modifier.width(8.dp))
                        Text("电费", style = headlineStyle(), color = electricOn)
                        Spacer(Modifier.weight(1f))
                        if (low) {
                            MetaChip("余额偏低", color = electricOn.copy(alpha = 0.14f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (balance != null) {
                        val b = balance!!
                        val days = estimateDays(b)
                        val price = ElectricStats.unitPrice(b)
                        val todayCost = ElectricStats.cost(ElectricStats.useValue(b.todayUse), price)
                        Row(verticalAlignment = Alignment.Bottom) {
                            PrismAnimatedText(
                                text = "${b.kwh}",
                                style = headlineStyle().copy(fontSize = 34.sp, lineHeight = 38.sp),
                                color = electricOn,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("度", style = subheadlineStyle(), color = electricOn.copy(alpha = 0.8f))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("预计可用 $days 天 · 今日 ${b.todayUse.ifBlank { "—" }} 度")
                                if (todayCost != null) append(" · 约 ¥${ElectricStats.formatMoney(todayCost)}")
                                if (b.price.isNotBlank()) append("\n单价 ${b.price} 元/度")
                            },
                            style = footnoteStyle(),
                            color = electricOn.copy(alpha = 0.78f),
                        )
                    } else {
                        Text("绑定房间后查看余额与用量", style = subheadlineStyle(), color = electricOn)
                    }
                }

                PrismCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenShower,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("洗浴", style = headlineStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.weight(1f))
                        if (area != null) {
                            MetaChip(
                                "空闲 ${area?.idleNum}/${area?.totalNum}",
                                color = PrismStatus.success().copy(alpha = 0.14f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (area != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            PrismAnimatedText(
                                text = "${area?.idleNum ?: 0} / ${area?.totalNum ?: 0}",
                                style = headlineStyle().copy(fontSize = 28.sp, lineHeight = 32.sp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("空闲", style = footnoteStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${area?.name ?: ""} · 排队 ${area?.queueNum ?: 0} 人",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                        )
                    } else {
                        Text("登录大白U帮后查看空闲与排队", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }

                PrismCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenFreeClassrooms,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MeetingRoom, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("空闲教室", style = headlineStyle())
                            Text("今天 14:00–16:00 按教学楼查询", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                PrismCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    CardHeader("提醒")
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Notifications, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("低余额提醒", style = bodyStyle())
                            Text("低于 20 度时每天 20:00 提醒", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PrismSwitch(
                            checked = lowElectricOn,
                            onCheckedChange = {
                                lowElectricOn = it
                                launchUi { settings.setRemindLowElectric(it) }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
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