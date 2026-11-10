package com.union.campus.ui.screens.me

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismLoadingIndicator
import com.union.campus.ui.prism.PrismOption
import com.union.campus.ui.prism.PrismOptionSheet
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismPageHeader
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.SnapSheetDefaults
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismStatusDot
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
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
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
                }
            },
        )
        PrismCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Person)
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

        Text("账号与凭证", style = headlineStyle())
        PrismGroupedList {
            PrismRow(
                title = "教务", subtitle = if (cred.jwStudentNo.isNotBlank()) "已连接 · 学号 ${cred.jwStudentNo.take(4)}****" else "未绑定",
                index = 0, count = 3, leading = { IconBadge(Icons.Rounded.School) },
                trailing = { StatusIcon(cred.jwStudentNo.isNotBlank()) },
                onClick = onOpenCredentials,
            )
            PrismRow(
                title = "洗浴", subtitle = if (cred.showerToken.isNotBlank()) "已连接 · 手机 ${ServiceLocator.showerRepository.maskedMobile()}" else "未绑定",
                index = 1, count = 3, leading = { IconBadge(Icons.Rounded.Shower) },
                trailing = { StatusIcon(cred.showerToken.isNotBlank()) },
                onClick = onOpenCredentials,
            )
            PrismRow(
                title = "电费", subtitle = if (cred.electricAccount.isNotBlank()) "已绑定学号 ${cred.electricAccount.take(4)}****" else "未绑定",
                index = 2, count = 3, leading = { IconBadge(Icons.Rounded.Bolt) },
                trailing = { StatusIcon(cred.electricAccount.isNotBlank()) },
                onClick = onOpenCredentials,
            )
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
                        Icon(Icons.Rounded.Sync, null)
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
            PrismRow(title = "凭证中心", subtitle = "教务、洗浴与电费账号", index = 0, count = 4, leading = { IconBadge(Icons.Rounded.School) }, onClick = onOpenCredentials)
            PrismRow(title = "提醒中心", subtitle = "上课、考试、低电费", index = 1, count = 4, leading = { IconBadge(Icons.Rounded.Notifications) }, onClick = onOpenReminders)
            PrismRow(title = "设置", subtitle = "课表、通知、隐私与数据", index = 2, count = 4, leading = { IconBadge(Icons.Rounded.Settings) }, onClick = onOpenSettings)
            PrismRow(title = "关于", subtitle = "版本、作者与版权", index = 3, count = 4, leading = { IconBadge(Icons.Rounded.Info) }, onClick = onOpenAbout)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            CheckState.Checking -> PrismLoadingIndicator(size = 16.dp)
            CheckState.Ok -> {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = PrismStatus.success(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("正常", style = footnoteStyle(), color = PrismStatus.success())
            }
            CheckState.Fail -> {
                Icon(
                    Icons.Rounded.ErrorOutline,
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
        Icons.Rounded.CheckCircle, null,
        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
            IconButton(onClick = { healthCheck() }) {
                Icon(Icons.Rounded.Sync, contentDescription = "体检")
            }
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
                "三套账号独立登录、独立退出。点击卡片登录；已登录时点击卡片可退出当前账号。",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val jwBound = cred.jwStudentNo.isNotBlank()
            CredentialAccountCard(
                title = "教务",
                subtitle = if (jwBound) {
                    "已连接 · 学号 ${cred.jwStudentNo.take(4)}****"
                } else {
                    "未登录 · 点击卡片登录"
                },
                detail = if (jwBound) {
                    "用于课表、成绩、考试与评教 · 点击卡片退出"
                } else {
                    "使用学号与教务系统密码登录"
                },
                bound = jwBound,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                actionLabel = if (jwBound) "重新登录" else "去登录",
                onClick = {
                    if (jwBound) logoutTarget = AccountTarget.Jw else onLoginAcademic()
                },
                onAction = onLoginAcademic,
            )

            val showerBound = cred.showerToken.isNotBlank()
            CredentialAccountCard(
                title = "洗浴",
                subtitle = if (showerBound) {
                    "已连接 · 手机 ${ServiceLocator.showerRepository.maskedMobile()}"
                } else {
                    "未登录 · 点击卡片登录"
                },
                detail = if (showerBound) {
                    "Token 保存在本机加密存储 · 点击卡片退出"
                } else {
                    "使用大白U帮手机号与密码登录"
                },
                bound = showerBound,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                actionLabel = if (showerBound) "重新登录" else "去登录",
                onClick = {
                    if (showerBound) logoutTarget = AccountTarget.Shower else onLoginShower()
                },
                onAction = onLoginShower,
            )

            val electricBound = cred.electricAccount.isNotBlank()
            CredentialAccountCard(
                title = "电费",
                subtitle = if (electricBound) {
                    "已绑定 · 学号 ${cred.electricAccount.take(4)}**** · 学校编码 ${cred.electricCustomerCode}"
                } else {
                    "未绑定 · 点击卡片登录"
                },
                detail = if (electricBound) {
                    "只查询本人房间 · 点击卡片退出"
                } else {
                    "绑定完美校园账号后查看余额与用量"
                },
                bound = electricBound,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                actionLabel = if (electricBound) "重新登录" else "去登录",
                onClick = {
                    if (electricBound) logoutTarget = AccountTarget.Electric else onLoginElectric()
                },
                onAction = onLoginElectric,
            )

            PrismButton(
                onClick = { healthCheck() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking,
                loading = checking,
            ) {
                Icon(Icons.Rounded.Sync, null)
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
                Icon(Icons.Rounded.Logout, null)
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
    onClick: () -> Unit,
    onAction: () -> Unit,
) {
    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = container,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrismStatusDot(
                color = if (bound) PrismStatus.success() else onContainer.copy(alpha = 0.32f),
                size = 8,
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = headlineStyle(), color = onContainer)
            Spacer(Modifier.weight(1f))
            Text(
                if (bound) "已登录" else "未登录",
                style = footnoteStyle(),
                color = onContainer.copy(alpha = 0.72f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = subheadlineStyle(), color = onContainer)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = footnoteStyle(), color = onContainer.copy(alpha = 0.72f))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
    val lowThreshold by settings.lowElectricThreshold.collectAsState(initial = 20)

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
                ToggleRow(
                    title = "总开关",
                    subtitle = if (remindEnabled) "提醒正常发送" else "已暂停全部提醒",
                    icon = Icons.Rounded.Notifications,
                    checked = remindEnabled,
                    index = 0, count = 7,
                ) { on ->
                    set { settings.setRemindEnabled(on) }
                    if (on) messenger.success("提醒已开启") else messenger.info("已暂停全部提醒")
                }
                ToggleRow(
                    title = "上课提醒",
                    subtitle = if (remindEnabled && remindClass) "课前 $classLead 分钟" else "已关闭",
                    icon = Icons.Rounded.Schedule,
                    checked = remindClass,
                    enabled = remindEnabled,
                    index = 1, count = 7,
                ) { on -> set { settings.setRemindClass(on) } }
                ToggleRow(
                    title = "考试提醒",
                    subtitle = if (remindEnabled && remindExam) "即将到来的考试进入待办" else "已关闭",
                    icon = Icons.Rounded.Grade,
                    checked = remindExam,
                    enabled = remindEnabled,
                    index = 2, count = 7,
                ) { on -> set { settings.setRemindExam(on) } }
                ToggleRow(
                    title = "低电费提醒",
                    subtitle = if (remindEnabled && remindLowElectric) "低于 $lowThreshold 度，每天 20:00" else "已关闭",
                    icon = Icons.Rounded.Bolt,
                    checked = remindLowElectric,
                    enabled = remindEnabled,
                    index = 3, count = 7,
                ) { on -> set { settings.setRemindLowElectric(on) } }
                ToggleRow(
                    title = "评教截止",
                    subtitle = if (remindEnabled && remindEvaluation) "待评课程进入待办" else "已关闭",
                    icon = Icons.Rounded.RateReview,
                    checked = remindEvaluation,
                    enabled = remindEnabled,
                    index = 4, count = 7,
                ) { on -> set { settings.setRemindEvaluation(on) } }
                ToggleRow(
                    title = "洗浴排队",
                    subtitle = if (remindEnabled && remindShowerQueue) "洗浴页打开时叫号提醒" else "已关闭",
                    icon = Icons.Rounded.Shower,
                    checked = remindShowerQueue,
                    enabled = remindEnabled,
                    index = 5, count = 7,
                ) { on -> set { settings.setRemindShowerQueue(on) } }
                ToggleRow(
                    title = "成绩更新",
                    subtitle = "尚未接入系统通知",
                    icon = Icons.Rounded.Grade,
                    checked = false,
                    enabled = false,
                    index = 6, count = 7,
                ) { }
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
                    index = 0, count = 2,
                    leading = { IconBadge(Icons.Rounded.Notifications) },
                    trailing = {
                        Text(
                            "修改",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { quietSheet = true },
                )
                ToggleRow(
                    title = "免打扰开关",
                    subtitle = "时段内不发送系统通知",
                    icon = Icons.Rounded.Notifications,
                    checked = quietOn,
                    index = 1, count = 2,
                ) { on ->
                    set { settings.setQuietEnabled(on) }
                    if (on) messenger.success("免打扰已开启") else messenger.info("免打扰已关闭")
                }
            }

            PrismTextButton(
                onClick = {
                    set {
                        settings.setRemindEnabled(true)
                        settings.setRemindClass(true)
                        settings.setRemindClassLead(10)
                        settings.setRemindExam(true)
                        settings.setRemindLowElectric(true)
                        settings.setLowElectricThreshold(20)
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

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    index: Int,
    count: Int,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val rowAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.45f,
        animationSpec = PrismMotion.tween(PrismMotion.Fast),
        label = "toggle-row-alpha",
    )
    PrismRow(
        title = title, subtitle = subtitle,
        index = index, count = count,
        modifier = Modifier.alpha(rowAlpha),
        leading = { IconBadge(icon) },
        trailing = {
            PrismSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChange,
            )
        },
    )
}

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
            val rowCount = if (showLockDelay) 5 else 4
            val screenshotIndex = if (showLockDelay) 3 else 2
            val logIndex = if (showLockDelay) 4 else 3
            PrismGroupedList {
                ToggleRow(
                    title = "生物识别解锁",
                    subtitle = if (biometric) "Face ID / 指纹已开启" else "Face ID / 指纹",
                    icon = Icons.Rounded.Fingerprint,
                    checked = biometric,
                    enabled = !biometricBusy,
                    index = 0, count = rowCount,
                ) { on ->
                    if (!on) {
                        launchUi { settings.setBiometricUnlock(false) }
                        messenger.info("已关闭生物识别解锁")
                        return@ToggleRow
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
                }
                ToggleRow(
                    title = "自动锁定",
                    subtitle = when {
                        !biometric -> "需先开启生物识别解锁"
                        autoLock -> "离开 $autoLockMinutes 分钟后重新验证"
                        else -> "已关闭"
                    },
                    icon = Icons.Rounded.LockClock,
                    checked = autoLock,
                    enabled = biometric,
                    index = 1, count = rowCount,
                ) { on ->
                    launchUi { settings.setAutoLock(on) }
                    if (on) messenger.success("自动锁定已开启") else messenger.info("自动锁定已关闭")
                }
                if (showLockDelay) {
                    PrismRow(
                        title = "锁定延迟",
                        subtitle = autoLockMinutes.let { "当前 $it 分钟 · 修改后立即生效" },
                        index = 2, count = rowCount,
                        leading = { IconBadge(Icons.Rounded.LockClock) },
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
                ToggleRow(
                    title = "防截屏",
                    subtitle = "全应用禁止截屏与录屏",
                    icon = Icons.Rounded.ScreenshotMonitor,
                    checked = screenshotBlock,
                    index = screenshotIndex, count = rowCount,
                ) { on ->
                    launchUi { settings.setScreenshotBlock(on) }
                    if (on) messenger.success("已开启防截屏") else messenger.info("已关闭防截屏")
                }
                PrismRow(
                    title = "日志脱敏",
                    subtitle = "不记录 Cookie / Token · 始终开启",
                    index = logIndex, count = rowCount,
                    leading = { IconBadge(Icons.Rounded.Shield) },
                    trailing = {
                        Icon(
                            Icons.Rounded.CheckCircle,
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
                    index = 0, count = 2, leading = { IconBadge(Icons.Rounded.CleaningServices) },
                    onClick = {
                        launchUi {
                            entities.removeAll()
                            messenger.success("已清除全部模块缓存")
                        }
                    },
                )
                PrismRow(
                    title = "清除全部数据", subtitle = "含凭证，二次确认",
                    index = 1, count = 2, leading = { IconBadge(Icons.Rounded.DeleteForever) },
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
