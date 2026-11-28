package com.union.campus.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismOption
import com.union.campus.ui.prism.PrismOptionSheet
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismToggleRow
import com.union.campus.ui.prism.footnoteStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------- 隐私与安全 ----------------

/** 隐私与安全：生物识别、自动锁定、防截屏、日志脱敏、清缓存与清数据。 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onWipeAll: () -> Unit,
    modifier: Modifier = Modifier,
    requestBiometric: suspend () -> Boolean = { true },
) {
    val settings = ServiceLocator.settings
    val entities = ServiceLocator.entities
    val messenger = LocalPrismMessenger.current
    val scope = rememberCoroutineScope()

    val biometric by settings.biometricUnlock.collectAsState(initial = false)
    val autoLock by settings.autoLock.collectAsState(initial = true)
    val autoLockMinutes by settings.autoLockMinutes.collectAsState(initial = 5)
    val screenshotBlock by settings.screenshotBlock.collectAsState(initial = false)

    var confirmWipe by remember { mutableStateOf(false) }
    var autoLockSheet by remember { mutableStateOf(false) }
    var biometricBusy by remember { mutableStateOf(false) }

    if (confirmWipe) {
        PrismAlertDialog(
            title = "清除全部数据",
            body = "将删除全部凭证、缓存、设置及登录状态，二次确认后执行，不可撤销。",
            confirmText = "清除全部", destructive = true,
            onConfirm = {
                confirmWipe = false
                launchUi {
                    ServiceLocator.jwRepository.logout()
                    ServiceLocator.xxtRepository.logout()
                    ServiceLocator.showerRepository.logout()
                    ServiceLocator.electricRepository.unbindAccount()
                    entities.removeAll()
                    ServiceLocator.credentials.clear()
                    ServiceLocator.settings.clearAll()
                    onWipeAll()
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }
    UnionScreenScaffold(
        title = "隐私与安全",
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
            val showLockDelay = biometric && autoLock
            PrismGroupedList {
                PrismToggleRow(
                    title = "生物识别解锁",
                    subtitle = "指纹或面容验证",
                    leading = { IconBadge(Icons.Outlined.Fingerprint) },
                    checked = biometric,
                    enabled = !biometricBusy,
                    onCheckedChange = { on ->
                        if (biometricBusy) return@PrismToggleRow
                        biometricBusy = true
                        scope.launch {
                            try {
                                if (!on || requestBiometric()) {
                                    settings.setBiometricUnlock(on)
                                } else {
                                    messenger.warning("验证未通过，未开启解锁")
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                messenger.error("解锁设置未保存，请重试")
                            } finally {
                                biometricBusy = false
                            }
                        }
                    },
                )
                PrismToggleRow(
                    title = "自动锁定",
                    subtitle = when {
                        !biometric -> "需先开启生物识别解锁"
                        autoLock -> "离开 $autoLockMinutes 分钟后重新验证"
                        else -> "已关闭"
                    },
                    leading = { IconBadge(Icons.Outlined.LockClock) },
                    checked = autoLock,
                    enabled = biometric,
                    onCheckedChange = { on ->
                        launchUi { settings.setAutoLock(on) }
                    },
                )
                if (showLockDelay) {
                    PrismRow(
                        title = "锁定延迟",
                        leading = { IconBadge(Icons.Outlined.LockClock) },
                        trailing = {
                            Text(
                                "$autoLockMinutes 分钟",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { autoLockSheet = true },
                    )
                }
                PrismToggleRow(
                    title = "防截屏",
                    subtitle = "全应用禁止截屏与录屏",
                    leading = { IconBadge(Icons.Outlined.ScreenshotMonitor) },
                    checked = screenshotBlock,
                    onCheckedChange = { on ->
                        launchUi { settings.setScreenshotBlock(on) }
                    },
                )
                PrismRow(
                    title = "日志脱敏",
                    subtitle = "始终开启",
                    leading = { IconBadge(Icons.Outlined.Shield) },
                    trailing = {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "始终开启",
                            tint = PrismStatus.success(),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }

            PrismGroupedList {
                PrismRow(
                    title = "清除缓存", subtitle = "清理课表、成绩、洗浴与电费缓存，保留凭证与设置",
                    leading = { IconBadge(Icons.Outlined.CleaningServices) },
                    onClick = {
                        launchUi {
                            entities.removeAll()
                            messenger.success("已清除全部缓存")
                        }
                    },
                )
                PrismRow(
                    title = "清除全部数据", subtitle = "含凭证，不可撤销",
                    leading = { IconBadge(Icons.Outlined.DeleteForever) },
                    onClick = { confirmWipe = true },
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    PrismOptionSheet(
        visible = autoLockSheet,
        title = "自动锁定延迟",
        subtitle = "再次进入需验证",
        options = listOf(1, 5, 15, 30).map {
            PrismOption(key = it.toString(), label = "离开 $it 分钟")
        },
        selectedKey = autoLockMinutes.toString(),
        onSelect = { key ->
            val minutes = key.toIntOrNull() ?: return@PrismOptionSheet
            launchUi { settings.setAutoLockMinutes(minutes) }
        },
        onDismiss = { autoLockSheet = false },
    )
}
