package com.union.campus.ui.screens.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WaterDrop
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ShowerDevice
import com.union.campus.domain.ShowerReservation
import kotlinx.coroutines.flow.first
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismNoticeBanner
import com.union.campus.ui.prism.PrismNoticeTone
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle

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
            messenger.warning("预约已超时，已尝试取消，请刷新确认")
        }
    }

    if (startConfirm) {
        PrismAlertDialog(
            title = "启动设备",
            body = "设备 ${device.name} · $areaName\n确认启动并开始计费？",
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
            body = "确认预约 ${device.name}？请在预约倒计时内启动，超时自动取消。",
            confirmText = "预约",
            onConfirm = {
                reserveConfirm = false
                launchUi {
                    try {
                        val r = shower.reserveDevice(device, areaName)
                        reservation = r
                        expiredGuard = null
                        if (r != null) {
                            haptics.success()
                            messenger.success("预约成功，请在倒计时内启动设备")
                        } else {
                            messenger.info("预约已提交，请刷新确认状态与等待时间")
                        }
                    } catch (e: AppError) { result = e.userMessage }
                    catch (e: Exception) { result = "预约失败：${e.message}" }
                }
            },
            onDismiss = { reserveConfirm = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(title = "提示", body = result ?: "", confirmText = "确定", dismissText = null,
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
            val (deviceContainer, deviceOnContainer, deviceIcon) = deviceStatusColors(device.state, reservedHere)
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = deviceContainer,
                corner = 28f,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Icon(deviceIcon, null, tint = deviceOnContainer)
                Spacer(Modifier.height(12.dp))
                Text(device.name, style = pageTitleStyle(), color = deviceOnContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$areaName · " + when {
                        reservedHere -> "已预约 · 等待启动"
                        device.state == 0 -> "空闲 · 可预约"
                        device.state == 1 -> "使用中"
                        device.state == 2 -> "已预约"
                        else -> "故障/维护"
                    } + "\n按实际使用时长计费，以账单为准",
                    style = subheadlineStyle(),
                    color = deviceOnContainer,
                )
            }
            if (activeSession != null) {
                PrismNoticeBanner(
                    title = "洗浴进行中",
                    message = "请先在洗浴页结束当前洗浴。",
                    tone = PrismNoticeTone.Warning,
                    icon = Icons.Rounded.WaterDrop,
                )
            }
            val otherReservation = reservation
            if (!reservedHere && otherReservation != null) {
                PrismNoticeBanner(
                    title = "已在 ${otherReservation.deviceName} 预约",
                    message = "请先启动或取消该预约。",
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
                PrismRow(title = "故障信息", subtitle = if (device.state !in 0..2) "设备异常，请更换设备或联系管理人员" else "暂无故障码", leading = { IconBadge(Icons.Rounded.Info) })
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
                    "请在预约倒计时内返回本页手动启动设备。",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
