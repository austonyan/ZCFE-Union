package com.union.campus.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.union.campus.data.store.CredentialStore
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ElectricLowState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismIconSize
import com.union.campus.ui.prism.PrismLoadingIndicator
import com.union.campus.ui.prism.PrismOption
import com.union.campus.ui.prism.PrismOptionSheet
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.SnapSheetDefaults
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismStatusDot
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.prismTextFaint
import com.union.campus.ui.prism.prismTextMuted
import com.union.campus.ui.prism.PrismToggleRow
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.withAlpha
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// ---------------- 我的 ----------------

/** 凭证快照：每次回到页面（ON_RESUME）重新读取，登录/退出后立即刷新。 */
@Composable
private fun rememberCredentialSnapshot(
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
            val jw = ServiceLocator.jwRepository
            val shower = ServiceLocator.showerRepository
            val electric = ServiceLocator.electricRepository
            // 并行探测三套凭证
            val results = coroutineScope {
                val jwOk = async { runCatching { jw.isBound() && jw.probe() }.getOrDefault(false) }
                val showerOk = async { runCatching { shower.isBound() && shower.probe() }.getOrDefault(false) }
                val electricOk = async { runCatching { electric.isBound() && electric.probe() }.getOrDefault(false) }
                listOf("教务" to jwOk.await(), "洗浴" to showerOk.await(), "电费" to electricOk.await())
            }
            checkStates = results.associate { (name, ok) ->
                name to if (ok) CheckState.Ok else CheckState.Fail
            }
            checking = false
            if (results.all { it.second }) {
                messenger.success("三套凭证均正常")
            } else {
                messenger.warning("有账号异常或未绑定，可到凭证中心重新登录")
            }
        }
    }

    Column(
        modifier
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Person)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (cred.jwStudentNo.isNotBlank()) "同学" else "未登录", style = headlineStyle())
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
        }

        PrismSection("账号与凭证") {
            PrismGroupedList {
                PrismRow(
                    title = "教务", subtitle = if (cred.jwStudentNo.isNotBlank()) "已连接 · 学号 ${cred.jwStudentNo.take(4)}****" else "未绑定",
                    leading = { IconBadge(Icons.Outlined.School) },
                    trailing = { StatusIcon(cred.jwStudentNo.isNotBlank()) },
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = "洗浴", subtitle = if (cred.showerToken.isNotBlank()) "已连接 · 手机 ${ServiceLocator.showerRepository.maskedMobile()}" else "未绑定",
                    leading = { IconBadge(Icons.Outlined.Shower) },
                    trailing = { StatusIcon(cred.showerToken.isNotBlank()) },
                    onClick = onOpenCredentials,
                )
                PrismRow(
                    title = "电费", subtitle = if (cred.electricAccount.isNotBlank()) "已绑定学号 ${cred.electricAccount.take(4)}****" else "未绑定",
                    leading = { IconBadge(Icons.Outlined.Bolt) },
                    trailing = { StatusIcon(cred.electricAccount.isNotBlank()) },
                    onClick = onOpenCredentials,
                )
            }
        }

        PrismCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账号体检", style = headlineStyle())
                    Text(
                        "并行检查教务、洗浴与电费，不阻塞当前页面",
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
                        Text("开始")
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

private enum class CheckState { Idle, Checking, Ok, Fail }

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
            CheckState.Checking -> PrismLoadingIndicator(size = 16.dp)
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
            CheckState.Fail -> {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("异常/未绑定", style = footnoteStyle(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatusIcon(ok: Boolean) {
    Icon(
        Icons.Outlined.CheckCircle, null,
        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Disabled),
    )
}

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
                "三套账号独立登录、独立退出。使用卡片行尾按钮登录、重新登录或退出。",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val jwBound = cred.jwStudentNo.isNotBlank()
            CredentialAccountCard(
                title = "教务",
                subtitle = if (jwBound) {
                    "已连接 · 学号 ${cred.jwStudentNo.take(4)}****"
                } else {
                    "未登录 · 使用行尾按钮登录"
                },
                detail = if (jwBound) {
                    "用于课表、成绩、考试与评教 · 使用行尾按钮重新登录或退出"
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
                    "已连接 · 手机 ${ServiceLocator.showerRepository.maskedMobile()}"
                } else {
                    "未登录 · 使用行尾按钮登录"
                },
                detail = if (showerBound) {
                    "Token 保存在本机加密存储 · 使用行尾按钮重新登录或退出"
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
                    "已绑定 · 学号 ${cred.electricAccount.take(4)}**** · 学校编码 ${cred.electricCustomerCode}"
                } else {
                    "未绑定 · 使用行尾按钮登录"
                },
                detail = if (electricBound) {
                    "只查询本人房间 · 使用行尾按钮重新登录或退出"
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
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrismStatusDot(
                color = if (bound) PrismStatus.success() else onContainer.withAlpha(PrismAlpha.Disabled),
                size = 8,
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = headlineStyle(), color = onContainer)
            Spacer(Modifier.weight(1f))
            Text(
                if (bound) "已登录" else "未登录",
                style = footnoteStyle(),
                color = prismTextMuted(onContainer),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = subheadlineStyle(), color = onContainer)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = footnoteStyle(), color = prismTextMuted(onContainer))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            if (bound) {
                PrismTextButton(onClick = onLogout) {
                    Icon(
                        Icons.Outlined.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(PrismIconSize.Chevron),
                    )
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            }
            PrismTextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

// ---------------- 提醒中心 ----------------

/** 提醒中心：与真实生效范围一致——总开关联动、阈值可调、免打扰时段可改。 */
@Composable
fun RemindersCenterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val messenger = LocalPrismMessenger.current

    val remindEnabled by settings.remindEnabled.collectAsState(initial = true)
    val remindClass by settings.remindClass.collectAsState(initial = true)
    val remindExam by settings.remindExam.collectAsState(initial = true)
    val remindLowElectric by settings.remindLowElectric.collectAsState(initial = true)
    val remindEvaluation by settings.remindEvaluation.collectAsState(initial = true)
    val remindShowerQueue by settings.remindShowerQueue.collectAsState(initial = true)
    val quietOn by settings.quietEnabled.collectAsState(initial = true)
    val quietStart by settings.quietStart.collectAsState(initial = 23)
    val quietEnd by settings.quietEnd.collectAsState(initial = 7)
    val classLead by settings.remindClassLead.collectAsState(initial = 10)
    val lowThreshold by settings.lowElectricThreshold.collectAsState(initial = ElectricLowState.DefaultThreshold)

    var quietSheet by remember { mutableStateOf(false) }
    fun set(block: suspend () -> Unit) = launchUi { block() }

    val leadOptions = remember(classLead) { (listOf(5, 10, 15) + classLead).distinct().sorted() }
    val thresholdOptions = remember(lowThreshold) { (listOf(10, 20, 30) + lowThreshold).distinct().sorted() }

    UnionScreenScaffold(
        title = "提醒中心",
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
            PrismGroupedList {
                PrismToggleRow(
                    title = "总开关",
                    subtitle = if (remindEnabled) "提醒正常发送" else "已暂停全部提醒",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    checked = remindEnabled,
                    onCheckedChange = { on ->
                        set { settings.setRemindEnabled(on) }
                        if (on) messenger.success("提醒已开启") else messenger.info("已暂停全部提醒")
                    },
                )
                PrismToggleRow(
                    title = "上课提醒",
                    subtitle = if (remindEnabled && remindClass) "课前 $classLead 分钟" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Schedule) },
                    checked = remindClass,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindClass(on) } },
                )
                PrismToggleRow(
                    title = "考试提醒",
                    subtitle = if (remindEnabled && remindExam) "即将到来的考试进入待办" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Grade) },
                    checked = remindExam,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindExam(on) } },
                )
                PrismToggleRow(
                    title = "低电费提醒",
                    subtitle = if (remindEnabled && remindLowElectric) "低于 $lowThreshold 度，每天 20:00" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Bolt) },
                    checked = remindLowElectric,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindLowElectric(on) } },
                )
                PrismToggleRow(
                    title = "评教截止",
                    subtitle = if (remindEnabled && remindEvaluation) "待评课程进入待办" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.RateReview) },
                    checked = remindEvaluation,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindEvaluation(on) } },
                )
                PrismToggleRow(
                    title = "洗浴排队",
                    subtitle = if (remindEnabled && remindShowerQueue) "洗浴页打开时叫号提醒" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Shower) },
                    checked = remindShowerQueue,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindShowerQueue(on) } },
                )
                PrismToggleRow(
                    title = "成绩更新",
                    subtitle = "尚未接入系统通知",
                    leading = { IconBadge(Icons.Outlined.Grade) },
                    checked = false,
                    enabled = false,
                    onCheckedChange = { },
                )
            }

            PrismCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (remindEnabled) 1f else 0.5f),
            ) {
                CardHeader("提醒细节")
                Spacer(Modifier.height(10.dp))
                Text("上课提前量", style = subheadlineStyle())
                Spacer(Modifier.height(6.dp))
                PrismSegmented(
                    options = leadOptions.map { "课前 $it 分钟" },
                    selectedIndex = leadOptions.indexOf(classLead).coerceAtLeast(1),
                    enabled = remindEnabled && remindClass,
                    onSelect = { index ->
                        set { settings.setRemindClassLead(leadOptions[index]) }
                    },
                )
                Spacer(Modifier.height(14.dp))
                Text("低电费阈值", style = subheadlineStyle())
                Spacer(Modifier.height(6.dp))
                PrismSegmented(
                    options = thresholdOptions.map { "$it 度" },
                    selectedIndex = thresholdOptions.indexOf(lowThreshold).coerceAtLeast(1),
                    enabled = remindEnabled && remindLowElectric,
                    onSelect = { index ->
                        set { settings.setLowElectricThreshold(thresholdOptions[index]) }
                    },
                )
            }

            PrismGroupedList {
                PrismRow(
                    title = "免打扰时段",
                    subtitle = "${formatHour(quietStart)}–${formatHour(quietEnd)} · ${if (quietOn) "已开启" else "已关闭"}",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    trailing = {
                        Text(
                            "修改",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { quietSheet = true },
                )
                PrismToggleRow(
                    title = "免打扰开关",
                    subtitle = "时段内不发送系统通知",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    checked = quietOn,
                    onCheckedChange = { on ->
                        set { settings.setQuietEnabled(on) }
                        if (on) messenger.success("免打扰已开启") else messenger.info("免打扰已关闭")
                    },
                )
            }

            PrismTextButton(
                onClick = {
                    set {
                        settings.setRemindEnabled(true)
                        settings.setRemindClass(true)
                        settings.setRemindClassLead(10)
                        settings.setRemindExam(true)
                        settings.setRemindLowElectric(true)
                        settings.setLowElectricThreshold(ElectricLowState.DefaultThreshold)
                        settings.setRemindEvaluation(true)
                        settings.setRemindShowerQueue(true)
                        settings.setQuietEnabled(true)
                        settings.setQuietRange(23, 7)
                    }
                    messenger.success("已恢复默认提醒设置")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复默认提醒设置")
            }

            Text(
                "上课为应用内横幅提醒；考试、评教与低电费会进入待办，低电费另发系统通知；洗浴排队在洗浴页打开时叫号提醒。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "后台提醒受系统限制，不保证准时；关键提醒以打开 App 查看为准。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    QuietHoursSheet(
        visible = quietSheet,
        startHour = quietStart,
        endHour = quietEnd,
        onDismiss = { quietSheet = false },
        onSave = { start, end ->
            quietSheet = false
            set { settings.setQuietRange(start, end) }
            messenger.success("免打扰时段已更新为 ${formatHour(start)}–${formatHour(end)}")
        },
    )
}

private fun formatHour(hour: Int): String = "%02d:00".format(hour)

/** 免打扰时段：双列小时胶囊，保存前本地预览，确定后才写入设置。 */
@Composable
private fun QuietHoursSheet(
    visible: Boolean,
    startHour: Int,
    endHour: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var start by remember(visible, startHour) { mutableIntStateOf(startHour) }
    var end by remember(visible, endHour) { mutableIntStateOf(endHour) }
    val startOptions = remember(startHour) { (listOf(21, 22, 23, 0, 1, 2) + startHour).distinct() }
    val endOptions = remember(endHour) { (listOf(5, 6, 7, 8, 9, 10) + endHour).distinct() }

    PrismSnapSheet(visible = visible, onDismiss = onDismiss, startAt = SnapSheetDefaults.Half) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("免打扰时段", style = pageTitleStyle())
            Text(
                "时段内不发送系统通知；应用内待办仍会显示。支持跨零点，例如 23:00–07:00。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("开始", style = headlineStyle())
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                startOptions.forEach { hour ->
                    PrismChip(
                        label = formatHour(hour),
                        selected = start == hour,
                        onClick = { start = hour },
                    )
                }
            }
            Text("结束", style = headlineStyle())
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                endOptions.forEach { hour ->
                    PrismChip(
                        label = formatHour(hour),
                        selected = end == hour,
                        onClick = { end = hour },
                    )
                }
            }
            Text(
                if (start == end) "开始和结束不能相同" else "当前：${formatHour(start)}–${formatHour(end)}",
                style = subheadlineStyle(),
                color = if (start == end) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            PrismButton(
                onClick = { onSave(start, end) },
                enabled = start != end,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存时段")
            }
            PrismTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("取消")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

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
            body = "将删除全部凭证、缓存与设置（含登录状态），二次确认后执行。此操作不可撤销。",
            confirmText = "清除全部", destructive = true,
            onConfirm = {
                confirmWipe = false
                launchUi {
                    ServiceLocator.jwRepository.logout()
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
                    subtitle = if (biometric) "Face ID / 指纹已开启" else "Face ID / 指纹",
                    leading = { IconBadge(Icons.Outlined.Fingerprint) },
                    checked = biometric,
                    enabled = !biometricBusy,
                    onCheckedChange = { on ->
                        if (!on) {
                            launchUi { settings.setBiometricUnlock(false) }
                            messenger.info("已关闭生物识别解锁")
                            return@PrismToggleRow
                        }
                        biometricBusy = true
                        launchUi {
                            val ok = requestBiometric()
                            if (ok) {
                                settings.setBiometricUnlock(true)
                                messenger.success("已开启生物识别解锁")
                            } else {
                                messenger.warning("验证未通过，未开启解锁")
                            }
                            biometricBusy = false
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
                        if (on) messenger.success("自动锁定已开启") else messenger.info("自动锁定已关闭")
                    },
                )
                if (showLockDelay) {
                    PrismRow(
                        title = "锁定延迟",
                        subtitle = autoLockMinutes.let { "当前 $it 分钟 · 修改后立即生效" },
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
                        if (on) messenger.success("已开启防截屏") else messenger.info("已关闭防截屏")
                    },
                )
                PrismRow(
                    title = "日志脱敏",
                    subtitle = "不记录 Cookie / Token · 始终开启",
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
                            messenger.success("已清除全部模块缓存")
                        }
                    },
                )
                PrismRow(
                    title = "清除全部数据", subtitle = "含凭证，二次确认",
                    leading = { IconBadge(Icons.Outlined.DeleteForever) },
                    onClick = { confirmWipe = true },
                )
            }

            Text("只使用本人账号，不提供查他人入口", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }

    PrismOptionSheet(
        visible = autoLockSheet,
        title = "自动锁定延迟",
        subtitle = "离开应用超过该时长后，下次进入需要重新验证生物识别。",
        options = listOf(1, 5, 15, 30).map {
            PrismOption(key = it.toString(), label = "离开 $it 分钟")
        },
        selectedKey = autoLockMinutes.toString(),
        onSelect = { key ->
            val minutes = key.toIntOrNull() ?: return@PrismOptionSheet
            launchUi { settings.setAutoLockMinutes(minutes) }
            messenger.success("自动锁定延迟已设为 $minutes 分钟")
        },
        onDismiss = { autoLockSheet = false },
    )
}
