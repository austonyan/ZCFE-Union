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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.union.campus.core.AccountCheckState
import com.union.campus.data.store.CredentialStore
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.footnoteStyle
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

/**
 * 我的 = 账号。四个平台一行一个，行尾徽标承担「是否绑定」与「体检结论」；
 * 体检是这一组的行内动作，结果直接写回徽标，不再另起一张「账号检查」卡。
 * 提醒中心与设置是唯一的应用入口；帮助、关于、隐私统一放在设置里。
 */
@Composable
fun ProfileScreen(
    onOpenCredentials: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messenger = LocalPrismMessenger.current
    val scope = rememberCoroutineScope()
    val health = rememberAccountHealth()

    val (cred, _) = rememberCredentialSnapshot(ServiceLocator.credentials)
    val bound = mapOf(
        AccountPlatform.Jw to cred.jwStudentNo.isNotBlank(),
        AccountPlatform.Xxt to cred.xxtAccount.isNotBlank(),
        AccountPlatform.Shower to cred.showerToken.isNotBlank(),
        AccountPlatform.Electric to cred.electricAccount.isNotBlank(),
    )

    fun runCheck() {
        health.check(scope) { results ->
            val failures = results.filterNot { it.second.isOk }
            val actionable = failures.filter { it.second.state != AccountCheckState.UNBOUND }
            when {
                results.all { it.second.state == AccountCheckState.UNBOUND } ->
                    messenger.info("还没有绑定平台账号，去凭证中心登录")
                actionable.isNotEmpty() ->
                    messenger.warning(accountCheckSummary(actionable))
                failures.isEmpty() ->
                    messenger.success("四个平台凭证正常")
                else ->
                    messenger.success("已保存的账号正常")
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
                    Text("欢迎", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(
                            "郑州财经学院",
                            cred.jwStudentNo.takeIf { it.isNotBlank() }?.let { "学号 ${it.take(4)}****" },
                        ).joinToString(" · "),
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "已保存 ${bound.count { it.value }} / ${AccountPlatform.All.size} 个平台账号 · 独立管理",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrismSection("平台账号") {
            PrismGroupedList {
                PrismRow(
                    title = AccountPlatform.Jw.label,
                    subtitle = if (bound.getValue(AccountPlatform.Jw)) {
                        "学号 ${cred.jwStudentNo.take(4)}**** · 课表、成绩与考试"
                    } else {
                        "登录后查看课表、成绩与考试"
                    },
                    leading = { IconBadge(Icons.Outlined.School) },
                    trailing = {
                        AccountStatusPill(
                            bound = bound.getValue(AccountPlatform.Jw),
                            state = health.stateOf(AccountPlatform.Jw),
                        )
                    },
                    subtitleMaxLines = Int.MAX_VALUE,
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = AccountPlatform.Xxt.label,
                    subtitle = if (bound.getValue(AccountPlatform.Xxt)) {
                        "${ServiceLocator.xxtRepository.maskedAccount()} · 只读同步作业与通知"
                    } else {
                        "登录后同步课程作业与通知"
                    },
                    leading = { IconBadge(Icons.AutoMirrored.Outlined.MenuBook) },
                    trailing = {
                        AccountStatusPill(
                            bound = bound.getValue(AccountPlatform.Xxt),
                            state = health.stateOf(AccountPlatform.Xxt),
                        )
                    },
                    subtitleMaxLines = Int.MAX_VALUE,
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = AccountPlatform.Shower.label,
                    subtitle = if (bound.getValue(AccountPlatform.Shower)) {
                        "手机 ${ServiceLocator.showerRepository.maskedMobile()} · 洗浴与账单"
                    } else {
                        "登录大白U帮，查看洗浴与账单"
                    },
                    leading = { IconBadge(Icons.Outlined.Shower) },
                    trailing = {
                        AccountStatusPill(
                            bound = bound.getValue(AccountPlatform.Shower),
                            state = health.stateOf(AccountPlatform.Shower),
                        )
                    },
                    subtitleMaxLines = Int.MAX_VALUE,
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = AccountPlatform.Electric.label,
                    subtitle = if (bound.getValue(AccountPlatform.Electric)) {
                        "学号 ${cred.electricAccount.take(4)}**** · 余额与用量"
                    } else {
                        "绑定本人宿舍，查看余额与用量"
                    },
                    leading = { IconBadge(Icons.Outlined.Bolt) },
                    trailing = {
                        AccountStatusPill(
                            bound = bound.getValue(AccountPlatform.Electric),
                            state = health.stateOf(AccountPlatform.Electric),
                        )
                    },
                    subtitleMaxLines = Int.MAX_VALUE,
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = if (health.checking) "正在检查四个平台" else "检查全部账号",
                    subtitle = if (health.checking) {
                        "正在验证凭证，结果会写回上面四行"
                    } else {
                        "验证凭证是否仍然可用"
                    },
                    leading = { IconBadge(Icons.Outlined.Sync) },
                    onClick = if (health.checking) null else ({ runCheck() }),
                )
            }
        }

        PrismSection("应用") {
            PrismGroupedList {
                PrismRow(
                    title = "提醒中心",
                    subtitle = "上课、考试、作业与低电费",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    onClick = onOpenReminders,
                )
                PrismRow(
                    title = "设置",
                    subtitle = "外观、课表、通知、隐私与帮助",
                    leading = { IconBadge(Icons.Outlined.Settings) },
                    onClick = onOpenSettings,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
