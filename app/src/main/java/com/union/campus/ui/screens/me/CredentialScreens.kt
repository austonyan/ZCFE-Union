package com.union.campus.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.union.campus.core.AccountCheckState
import com.union.campus.core.userFacingMessage
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismActionRow
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismIconSize
import com.union.campus.ui.prism.PrismLoadingIndicator
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle

// ---------------- 凭证中心 ----------------

/**
 * 凭证中心：四个平台各自登录、重新登录或退出，互不影响。
 *
 * 体检入口与我的页共用同一份结论（[AccountHealthController]）：这里不再把四条结果
 * 拼成一句话弹窗，而是逐平台列出结论，失败原因就地展示。
 */
@Composable
fun CredentialsCenterScreen(
    onBack: () -> Unit,
    onLogoutAll: () -> Unit,
    onLoginAcademic: () -> Unit,
    onLoginShower: () -> Unit,
    onLoginElectric: () -> Unit,
    onLoginXxt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messenger = LocalPrismMessenger.current
    val haptics = rememberPrismHaptics()
    val scope = rememberCoroutineScope()
    val health = rememberAccountHealth()
    val (cred, refreshCred) = rememberCredentialSnapshot(ServiceLocator.credentials)
    var confirmLogout by remember { mutableStateOf(false) }
    var logoutTarget by remember { mutableStateOf<AccountPlatform?>(null) }

    if (confirmLogout) {
        PrismAlertDialog(
            title = "全部退出",
            body = "确认退出全部账号？将清除四个平台的凭证与本地缓存，不可撤销。",
            confirmText = "全部退出",
            destructive = true,
            onConfirm = {
                confirmLogout = false
                launchUi {
                    ServiceLocator.jwRepository.logout()
                    ServiceLocator.xxtRepository.logout()
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
            body = "仅退出${target.label}账号，不影响其他账号及本地数据。",
            confirmText = "退出登录",
            destructive = true,
            onConfirm = {
                logoutTarget = null
                launchUi {
                    try {
                        when (target) {
                            AccountPlatform.Jw -> ServiceLocator.jwRepository.logout()
                            AccountPlatform.Xxt -> ServiceLocator.xxtRepository.logout()
                            AccountPlatform.Shower -> ServiceLocator.showerRepository.logout()
                            AccountPlatform.Electric -> ServiceLocator.electricRepository.unbindAccount()
                        }
                        refreshCred()
                        haptics.success()
                        messenger.info("已退出${target.label}账号")
                    } catch (e: Exception) {
                        messenger.error("退出失败：${e.userFacingMessage()}")
                    }
                }
            },
            onDismiss = { logoutTarget = null },
        )
    }

    val bound = mapOf(
        AccountPlatform.Jw to cred.jwStudentNo.isNotBlank(),
        AccountPlatform.Xxt to cred.xxtAccount.isNotBlank(),
        AccountPlatform.Shower to cred.showerToken.isNotBlank(),
        AccountPlatform.Electric to cred.electricAccount.isNotBlank(),
    )

    UnionScreenScaffold(
        title = "凭证中心",
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
            Text(
                "四个平台凭证相互独立，只加密保存在本机。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PlatformCard(
                platform = AccountPlatform.Jw,
                icon = Icons.Outlined.School,
                bound = bound.getValue(AccountPlatform.Jw),
                accountLine = if (bound.getValue(AccountPlatform.Jw)) {
                    "学号 ${cred.jwStudentNo.take(4)}****"
                } else {
                    "未保存教务账号"
                },
                purpose = "用于课表、成绩、考试与评教",
                state = health.stateOf(AccountPlatform.Jw),
                actionLabel = if (bound.getValue(AccountPlatform.Jw)) "重新登录" else "登录",
                onAction = onLoginAcademic,
                onLogout = { logoutTarget = AccountPlatform.Jw },
            )

            PlatformCard(
                platform = AccountPlatform.Xxt,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                bound = bound.getValue(AccountPlatform.Xxt),
                accountLine = if (bound.getValue(AccountPlatform.Xxt)) {
                    "账号 ${ServiceLocator.xxtRepository.maskedAccount()}"
                } else {
                    "未保存学习通账号"
                },
                purpose = "只读同步课程作业与通知，不代提交",
                state = health.stateOf(AccountPlatform.Xxt),
                actionLabel = if (bound.getValue(AccountPlatform.Xxt)) "重新登录" else "登录",
                onAction = onLoginXxt,
                onLogout = { logoutTarget = AccountPlatform.Xxt },
            )

            PlatformCard(
                platform = AccountPlatform.Shower,
                icon = Icons.Outlined.Shower,
                bound = bound.getValue(AccountPlatform.Shower),
                accountLine = if (bound.getValue(AccountPlatform.Shower)) {
                    "手机 ${ServiceLocator.showerRepository.maskedMobile()}"
                } else {
                    "未保存大白U帮账号"
                },
                purpose = "用于洗浴区域、排队与账单",
                state = health.stateOf(AccountPlatform.Shower),
                actionLabel = if (bound.getValue(AccountPlatform.Shower)) "重新登录" else "登录",
                onAction = onLoginShower,
                onLogout = { logoutTarget = AccountPlatform.Shower },
            )

            PlatformCard(
                platform = AccountPlatform.Electric,
                icon = Icons.Outlined.Bolt,
                bound = bound.getValue(AccountPlatform.Electric),
                accountLine = if (bound.getValue(AccountPlatform.Electric)) {
                    "学号 ${cred.electricAccount.take(4)}**** · 学校编码 ${cred.electricCustomerCode}"
                } else {
                    "未绑定完美校园账号"
                },
                purpose = "仅查询本人宿舍余额与用量",
                state = health.stateOf(AccountPlatform.Electric),
                actionLabel = if (bound.getValue(AccountPlatform.Electric)) "重新登录" else "绑定",
                onAction = onLoginElectric,
                onLogout = { logoutTarget = AccountPlatform.Electric },
            )

            PrismSection("账号体检") {
                PrismCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("检查全部账号", style = headlineStyle())
                            Text(
                                if (health.checking) "正在验证四个平台" else "验证凭证是否仍然可用",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (health.checking) {
                            PrismLoadingIndicator(size = 20.dp)
                        } else {
                            PrismTextButton(onClick = {
                                health.check(scope) { results ->
                                    val failures = results.filterNot { it.second.isOk }
                                    val actionable = failures.filter { it.second.state != AccountCheckState.UNBOUND }
                                    when {
                                        results.all { it.second.state == AccountCheckState.UNBOUND } ->
                                            messenger.info("还没有绑定平台账号")
                                        actionable.isNotEmpty() ->
                                            messenger.warning(accountCheckSummary(actionable))
                                        failures.isEmpty() ->
                                            messenger.success("四个平台凭证正常")
                                        else ->
                                            messenger.success("已保存的账号正常")
                                    }
                                }
                            }, enabled = !health.checking) {
                                Icon(Icons.Outlined.Sync, null)
                                Spacer(Modifier.width(6.dp))
                                Text("检查", maxLines = 1)
                            }
                        }
                    }
                    if (health.states.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        AccountPlatform.All.forEach { platform ->
                            CheckResultRow(platform, health.stateOf(platform))
                        }
                    }
                }
            }

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

/**
 * 一个平台的凭证卡：图标 + 名称/脱敏账号 + 状态徽标 + 用途 + 操作。
 * 四个平台统一卡面，用徽标表达状态，不再用四种容器色暗示优先级。
 */
@Composable
private fun PlatformCard(
    platform: AccountPlatform,
    icon: ImageVector,
    bound: Boolean,
    accountLine: String,
    purpose: String,
    state: AccountCheckUi,
    actionLabel: String,
    onAction: () -> Unit,
    onLogout: () -> Unit,
) {
    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        corner = 24f,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(platform.label, style = headlineStyle())
                Text(
                    accountLine,
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            AccountStatusPill(bound = bound, state = state)
        }
        Spacer(Modifier.height(10.dp))
        Text(purpose, style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            PrismButton(onClick = onAction, filled = !bound) {
                Text(actionLabel, maxLines = 1)
            }
        }
    }
}

/** 体检结论行：只在本页展示逐平台结论，正常/失效/不可用各自的图标与语义色。 */
@Composable
private fun CheckResultRow(platform: AccountPlatform, state: AccountCheckUi) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(platform.label, style = bodyStyle(), modifier = Modifier.weight(1f))
        when (state) {
            AccountCheckUi.Idle -> Text("未检查", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccountCheckUi.Checking -> Text("检查中", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccountCheckUi.Ok -> {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = PrismStatus.success(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("正常", style = footnoteStyle(), color = PrismStatus.success())
            }
            AccountCheckUi.Expired -> {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("登录失效，请重新登录", style = footnoteStyle(), color = MaterialTheme.colorScheme.error)
            }
            AccountCheckUi.Unbound -> Text("未绑定", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccountCheckUi.Unavailable -> {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = PrismStatus.warning(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("服务暂不可用", style = footnoteStyle(), color = PrismStatus.warning())
            }
        }
    }
}