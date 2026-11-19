package com.union.campus.ui.screens.me

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
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismActionRow
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismIconSize
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// ---------------- 凭证中心 ----------------

/** 凭证中心：三套账号可独立登录、重新登录或退出，互不影响。 */
@Composable
fun CredentialsCenterScreen(
    onBack: () -> Unit,
    onLogoutAll: () -> Unit,
    onLoginAcademic: () -> Unit,
    onLoginShower: () -> Unit,
    onLoginElectric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messenger = LocalPrismMessenger.current
    val haptics = rememberPrismHaptics()
    val (cred, refreshCred) = rememberCredentialSnapshot(ServiceLocator.credentials)
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var logoutTarget by remember { mutableStateOf<AccountTarget?>(null) }

    fun healthCheck() {
        if (checking) return
        checking = true
        launchUi {
            val jw = ServiceLocator.jwRepository
            val shower = ServiceLocator.showerRepository
            val electric = ServiceLocator.electricRepository
            val results = coroutineScope {
                val jwOk = async { runCatching { jw.isBound() && jw.probe() }.getOrDefault(false) }
                val showerOk = async { runCatching { shower.isBound() && shower.probe() }.getOrDefault(false) }
                val electricOk = async { runCatching { electric.isBound() && electric.probe() }.getOrDefault(false) }
                listOf("教务" to jwOk.await(), "洗浴" to showerOk.await(), "电费" to electricOk.await())
            }
            checkResult = results.joinToString("；") { (name, ok) -> "$name：${if (ok) "正常" else "异常/未绑定"}" }
            checking = false
        }
    }

    if (checkResult != null) {
        PrismAlertDialog(
            title = "体检结果",
            body = checkResult ?: "",
            confirmText = "知道了",
            dismissText = null,
            onConfirm = { checkResult = null },
            onDismiss = { checkResult = null },
        )
    }
    if (confirmLogout) {
        PrismAlertDialog(
            title = "全部退出",
            body = "将清除三套账号凭证与本地缓存数据，此操作不可撤销。确认退出？",
            confirmText = "全部退出",
            destructive = true,
            onConfirm = {
                confirmLogout = false
                launchUi {
                    ServiceLocator.jwRepository.logout()
                    ServiceLocator.showerRepository.logout()
                    ServiceLocator.electricRepository.unbindAccount()
                    ServiceLocator.entities.removeAll()
                    onLogoutAll()
                }
            },
            onDismiss = { confirmLogout = false },
        )
    }
    logoutTarget?.let { target ->
        PrismAlertDialog(
            title = "退出${target.label}登录？",
            body = "只会退出当前${target.label}账号，其他账号和本地数据不受影响。下次使用时再登录即可。",
            confirmText = "退出登录",
            destructive = true,
            onConfirm = {
                logoutTarget = null
                launchUi {
                    try {
                        when (target) {
                            AccountTarget.Jw -> ServiceLocator.jwRepository.logout()
                            AccountTarget.Shower -> ServiceLocator.showerRepository.logout()
                            AccountTarget.Electric -> ServiceLocator.electricRepository.unbindAccount()
                        }
                        refreshCred()
                        haptics.success()
                        messenger.info("已退出${target.label}账号")
                    } catch (e: Exception) {
                        messenger.error("退出失败：${e.message ?: "请稍后重试"}")
                    }
                }
            },
            onDismiss = { logoutTarget = null },
        )
    }

    UnionScreenScaffold(
        title = "凭证中心",
        onBack = onBack,
        modifier = modifier,
        actions = {
            PrismIconButton(
                icon = Icons.Outlined.Sync,
                contentDescription = "体检",
                onClick = { healthCheck() },
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
            Text(
                "三个平台，独立管理。已保存的凭证是否有效，可通过体检向服务器验证。",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val jwBound = cred.jwStudentNo.isNotBlank()
            CredentialAccountCard(
                title = "教务",
                subtitle = if (jwBound) {
                    "学号 ${cred.jwStudentNo.take(4)}****"
                } else {
                    "尚未保存教务账号"
                },
                detail = if (jwBound) {
                    "用于课表、成绩、考试与评教"
                } else {
                    "使用学号与教务系统密码登录"
                },
                bound = jwBound,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                actionLabel = if (jwBound) "重新登录" else "去登录",
                onAction = onLoginAcademic,
                onLogout = { logoutTarget = AccountTarget.Jw },
            )

            val showerBound = cred.showerToken.isNotBlank()
            CredentialAccountCard(
                title = "洗浴",
                subtitle = if (showerBound) {
                    "手机 ${ServiceLocator.showerRepository.maskedMobile()}"
                } else {
                    "尚未保存大白U帮账号"
                },
                detail = if (showerBound) {
                    "登录凭证保存在本机加密存储"
                } else {
                    "使用大白U帮手机号与密码登录"
                },
                bound = showerBound,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                actionLabel = if (showerBound) "重新登录" else "去登录",
                onAction = onLoginShower,
                onLogout = { logoutTarget = AccountTarget.Shower },
            )

            val electricBound = cred.electricAccount.isNotBlank()
            CredentialAccountCard(
                title = "电费",
                subtitle = if (electricBound) {
                    "学号 ${cred.electricAccount.take(4)}**** · 学校编码 ${cred.electricCustomerCode}"
                } else {
                    "尚未绑定完美校园账号"
                },
                detail = if (electricBound) {
                    "只查询本人房间的余额与用量"
                } else {
                    "绑定完美校园账号后查看余额与用量"
                },
                bound = electricBound,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                actionLabel = if (electricBound) "重新登录" else "去登录",
                onAction = onLoginElectric,
                onLogout = { logoutTarget = AccountTarget.Electric },
            )

            PrismButton(
                onClick = { healthCheck() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking,
                loading = checking,
            ) {
                Icon(Icons.Outlined.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text("一键体检")
            }
            Text(
                "「重新登录」只刷新当前账号，不会清除其他账号；「全部退出」才会清除三套凭证与本地缓存。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrismTextButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("全部退出")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private enum class AccountTarget(val label: String) {
    Jw("教务"),
    Shower("洗浴"),
    Electric("电费"),
}

@Composable
private fun CredentialAccountCard(
    title: String,
    subtitle: String,
    detail: String,
    bound: Boolean,
    container: Color,
    onContainer: Color,
    actionLabel: String,
    onAction: () -> Unit,
    onLogout: () -> Unit,
) {
    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = container,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        corner = 24f,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = headlineStyle(), color = onContainer)
            Spacer(Modifier.weight(1f))
            Text(
                if (bound) "凭证已保存" else "待绑定",
                style = footnoteStyle(),
                color = onContainer,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = subheadlineStyle(), color = onContainer)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = footnoteStyle(), color = onContainer)
        Spacer(Modifier.height(12.dp))
        PrismActionRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (bound) {
                PrismTextButton(onClick = onLogout) {
                    Icon(
                        Icons.Outlined.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(PrismIconSize.Chevron),
                    )
                    Text("退出", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                }
            }
            PrismButton(
                onClick = onAction,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(actionLabel, maxLines = 1)
            }
        }
    }
}
