package com.union.campus.ui.screens.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StopCircle
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ShowerSessionState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel

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
            body = "确认关闭设备并结束洗浴？离开前请确认设备已关闭。",
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
                        result = "结束失败：${e.userMessage}。洗浴可能已结束，请刷新订单确认，勿重复操作。"
                    } catch (e: Exception) {
                        ending = false
                        result = "结束失败：${e.message}。洗浴可能已结束，请查看账单确认，勿重复操作。"
                    }
                }
            },
            onDismiss = { endConfirm = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(title = "结束结果", body = result ?: "", confirmText = "确定", dismissText = null,
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
            LifeServiceCard(
                title = session.deviceName,
                location = "${session.buildingName} ${session.areaName}",
                value = "使用中",
                detail = "离开前请结束洗浴并确认设备已关闭。",
                icon = Icons.Rounded.Schedule,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            SectionHeader("本次洗浴")
            val minutes = elapsedSec / 60
            val secs = elapsedSec % 60
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = surfaceColorOf(SurfaceLevel.Card),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%02d:%02d", minutes, secs),
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "已使用",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            SectionHeader("计费提示")
            PrismGroupedList {
                PrismRow(title = "已使用时长", subtitle = "按实际时长计费，以最终账单为准", leading = { IconBadge(Icons.Rounded.Info) })
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
