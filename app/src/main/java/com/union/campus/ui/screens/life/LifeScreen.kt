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
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.union.campus.data.repo.ElectricRepository
import com.union.campus.data.repo.ShowerRepository
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricStats
import com.union.campus.domain.ShowerArea
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel
import kotlinx.coroutines.flow.first

/** 生活：洗浴、电费与空闲教室的生活总览，含低余额提醒开关。 */
@OptIn(ExperimentalMaterial3Api::class)
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

    var balance by remember { mutableStateOf<ElectricBalance?>(null) }
    var area by remember { mutableStateOf<ShowerArea?>(null) }
    var lowElectricOn by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lowElectricOn = settings.remindLowElectric.first()
        runCatching {
            electric.rooms().firstOrNull()?.let { balance = electric.balance(it.roomVerify) }
        }
        runCatching {
            val orgArea = settings.lastShowerOrgArea.first()
            val areaId = settings.lastShowerAreaId.first()
            if (orgArea.isNotBlank()) {
                val list = shower.areasWithState(orgArea)
                area = list.firstOrNull { it.id == areaId } ?: list.firstOrNull()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = { Text("生活", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onOpenSettings() })
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
            // 位置信息
            val roomName = balance?.fullName?.takeIf { it.isNotBlank() } ?: "电费未绑定"
            Text(roomName, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 电费卡
            PrismCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenElectric, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("电费", style = headlineStyle(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.padding(4.dp))
                if (balance != null) {
                    val b = balance!!
                    val days = estimateDays(b)
                    val price = ElectricStats.unitPrice(b)
                    val todayCost = ElectricStats.cost(ElectricStats.useValue(b.todayUse), price)
                    Text(
                        buildString {
                            append("剩余 ${b.kwh} 度 · 预计 $days 天\n今日 ${b.todayUse.ifBlank { "—" }} 度")
                            if (todayCost != null) append(" · 约 ¥${ElectricStats.formatMoney(todayCost)}")
                            append("\n单价 ${b.price.ifBlank { "—" }} 元/度")
                        },
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Text("绑定房间后查看余额与用量", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            // 洗浴卡
            PrismCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenShower, containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text("洗浴", style = headlineStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.padding(4.dp))
                if (area != null) {
                    Text(
                        "${area?.name ?: ""} · 空闲 ${area?.idleNum}/${area?.totalNum}\n排队 ${area?.queueNum ?: 0} 人 · ${area?.serviceTime?.ifBlank { "服务时间以现场为准" }}",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    Text("登录大白U帮后查看空闲与排队", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }

            // 空闲教室卡
            PrismCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenFreeClassrooms, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MeetingRoom, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("空闲教室", style = headlineStyle())
                        Text("今天 14:00–16:00 按教学楼查询", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 低余额提醒开关
            PrismCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
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
                    Switch(checked = lowElectricOn, onCheckedChange = {
                        lowElectricOn = it
                        launchUi { settings.setRemindLowElectric(it) }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
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
