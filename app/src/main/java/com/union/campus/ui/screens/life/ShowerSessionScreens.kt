package com.union.campus.ui.screens.life

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.metricStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
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
                        messenger.success("已结束洗浴")
                        haptics.success()
                        onBack()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
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
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Text(session.deviceName, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("${session.buildingName} ${session.areaName}", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            SectionHeader("本次洗浴")
            val minutes = elapsedSec / 60
            val secs = elapsedSec % 60
            val progressTarget = (elapsedSec / (40f * 60f)).coerceIn(0f, 1f)
            val progress by animateFloatAsState(
                targetValue = progressTarget,
                animationSpec = PrismMotion.tween(PrismMotion.Slow),
                label = "session-progress",
            )
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = surfaceColorOf(SurfaceLevel.Card),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                        val ringTrack = MaterialTheme.colorScheme.secondaryContainer
                        val ringActive = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 8.dp.toPx()
                            val inset = stroke / 2f
                            drawArc(
                                color = ringTrack,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = ringActive,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%02d:%02d", minutes, secs),
                                style = metricStyle(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "已使用",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.deviceName, style = headlineStyle())
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${session.buildingName} ${session.areaName}",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "圆环按 40 分钟参考，实际费用以结束后的账单为准。",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            Text("预计费用以结束后的账单为准（按实际使用时长计费）", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader("计费提示")
            PrismGroupedList {
                PrismRow(title = "已使用时长", subtitle = "按实际使用时长计费", leading = { IconBadge(Icons.Rounded.Info) })
                PrismRow(title = "结束提醒", subtitle = "离开前请确认关闭设备", leading = { IconBadge(Icons.Rounded.Info) })
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
