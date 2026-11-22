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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.union.campus.core.AccountCheck
import com.union.campus.core.AccountCheckState
import com.union.campus.data.store.CredentialStore
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismLoadingIndicator
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.prismTextFaint
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle

// ---------------- 我的 ----------------

/** 凭证快照：每次回到页面（ON_RESUME）重新读取，登录/退出后立即刷新。 */
@Composable
internal fun rememberCredentialSnapshot(
    store: CredentialStore,
): Pair<CredentialStore.Credentials, () -> Unit> {
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val snapshot = remember(revision) { store.load() }
    return snapshot to { revision++ }
}

/** 我的：个人资料、三套凭证状态、一键体检、提醒与隐私入口。 */
@Composable
fun ProfileScreen(
    onOpenCredentials: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messenger = LocalPrismMessenger.current
    var checking by remember { mutableStateOf(false) }
    var checkStates by remember { mutableStateOf<Map<String, CheckState>>(emptyMap()) }

    val (cred, _) = rememberCredentialSnapshot(ServiceLocator.credentials)

    fun healthCheck() {
        if (checking) return
        checking = true
        checkStates = listOf("教务", "洗浴", "电费").associateWith { CheckState.Checking }
        launchUi {
            // 并行探测三套凭证；结论区分未绑定、登录失效与网络不可用
            val results = checkAllAccounts()
            checkStates = results.associate { (name, check) -> name to check.toCheckState() }
            checking = false
            val failed = results.filterNot { it.second.isOk }
            if (failed.isEmpty()) {
                messenger.success("全部凭证正常")
            } else {
                messenger.warning(failed.joinToString("；") { (name, check) -> "$name：${check.displayText}" })
            }
        }
    }

    Column(
        modifier
            .pageWidth()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrismPageHeader(
            title = "我的",
            actions = {
                PrismIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                )
            },
        )
        PrismCard(
            modifier = Modifier.fillMaxWidth(),
            corner = 28f,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(24.dp, 24.dp, 8.dp, 24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Outlined.Person, null, Modifier.padding(16.dp).size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (cred.jwStudentNo.isNotBlank()) "欢迎" else "欢迎", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(
                            "郑州财经学院".takeIf { true },
                            cred.jwStudentNo.takeIf { it.isNotBlank() }?.let { "学号 ${it.take(4)}****" },
                        ).joinToString(" · "),
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "已保存 ${listOf(cred.jwStudentNo.isNotBlank(), cred.showerToken.isNotBlank(), cred.electricAccount.isNotBlank()).count { it }} / 3 个账号 · 独立管理",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrismSection("账号与凭证") {
            PrismGroupedList {
                PrismRow(
                    title = "教务", subtitle = if (cred.jwStudentNo.isNotBlank()) "学号 ${cred.jwStudentNo.take(4)}****" else "登录后查看课表、成绩与考试",
                    leading = { IconBadge(Icons.Outlined.School) },
                    trailing = { AccountStatusBadge(cred.jwStudentNo.isNotBlank()) },
                    subtitleMaxLines = Int.MAX_VALUE,
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = "洗浴", subtitle = if (cred.showerToken.isNotBlank()) "手机 ${ServiceLocator.showerRepository.maskedMobile()}" else "登录大白U帮，查看洗浴与账单",
                    leading = { IconBadge(Icons.Outlined.Shower) },
                    trailing = { AccountStatusBadge(cred.showerToken.isNotBlank()) },
                    subtitleMaxLines = Int.MAX_VALUE,
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = "电费", subtitle = if (cred.electricAccount.isNotBlank()) "学号 ${cred.electricAccount.take(4)}****" else "绑定本人宿舍，查看余额与用量",
                    leading = { IconBadge(Icons.Outlined.Bolt) },
                    trailing = { AccountStatusBadge(cred.electricAccount.isNotBlank()) },
                    subtitleMaxLines = Int.MAX_VALUE,
                    onClick = onOpenCredentials,
                )
            }
        }

        PrismCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账号检查", style = headlineStyle())
                    Text(
                        if (checking) "正在验证三个平台账号" else "凭证有效性以服务器检查结果为准",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checking) {
                    PrismLoadingIndicator(size = 20.dp)
                } else {
                    PrismTextButton(onClick = { healthCheck() }) {
                        Icon(Icons.Outlined.Sync, null)
                        Spacer(Modifier.width(6.dp))
                        Text("检查", maxLines = 1)
                    }
                }
            }
            if (checkStates.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                CheckStateRow("教务", checkStates["教务"] ?: CheckState.Idle)
                CheckStateRow("洗浴", checkStates["洗浴"] ?: CheckState.Idle)
                CheckStateRow("电费", checkStates["电费"] ?: CheckState.Idle)
            }
        }

        PrismGroupedList {
            PrismRow(title = "凭证中心", subtitle = "教务、洗浴与电费账号", leading = { IconBadge(Icons.Outlined.School) }, onClick = onOpenCredentials)
            PrismRow(title = "提醒中心", subtitle = "上课、考试、低电费", leading = { IconBadge(Icons.Outlined.Notifications) }, onClick = onOpenReminders)
            PrismRow(title = "设置", subtitle = "课表、通知、隐私与数据", leading = { IconBadge(Icons.Outlined.Settings) }, onClick = onOpenSettings)
            PrismRow(title = "关于", subtitle = "版本、作者与版权", leading = { IconBadge(Icons.Outlined.Info) }, onClick = onOpenAbout)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private enum class CheckState { Idle, Checking, Ok, Unbound, Expired, Unavailable }

private fun AccountCheck.toCheckState(): CheckState = when (state) {
    AccountCheckState.OK -> CheckState.Ok
    AccountCheckState.UNBOUND -> CheckState.Unbound
    AccountCheckState.EXPIRED -> CheckState.Expired
    AccountCheckState.UNAVAILABLE -> CheckState.Unavailable
}

@Composable
private fun CheckStateRow(name: String, state: CheckState) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = bodyStyle(), modifier = Modifier.weight(1f))
        when (state) {
            CheckState.Idle -> Text(
                "未检查",
                style = footnoteStyle(),
                color = prismTextFaint(),
            )
            CheckState.Checking -> Text("检查中", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CheckState.Ok -> {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = PrismStatus.success(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("正常", style = footnoteStyle(), color = PrismStatus.success())
            }
            CheckState.Unbound -> Text(
                "未绑定",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CheckState.Expired -> {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("登录失效", style = footnoteStyle(), color = MaterialTheme.colorScheme.error)
            }
            CheckState.Unavailable -> {
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

@Composable
private fun AccountStatusBadge(ok: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(if (ok) "已保存" else "未绑定", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
