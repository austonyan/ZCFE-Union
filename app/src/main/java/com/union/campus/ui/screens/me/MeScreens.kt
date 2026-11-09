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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.union.campus.core.AppError
import com.union.campus.data.store.CredentialStore
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismStatusDot
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

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

    UnionScreenScaffold(
        title = "我的",
        modifier = modifier,
        largeTitle = true,
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "设置") }
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
            com.union.campus.ui.prism.PrismCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
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
            Spacer(Modifier.height(12.dp))
        }
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
            CheckState.Checking -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
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
            ) {
                Icon(Icons.Rounded.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text(if (checking) "正在体检…" else "一键体检")
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

/** 提醒中心：上课、考试、低电费、评教、洗浴排队与成绩更新开关 + 免打扰。 */
@Composable
fun RemindersCenterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings

    var remindEnabled by remember { mutableStateOf(true) }
    var remindClass by remember { mutableStateOf(true) }
    var remindExam by remember { mutableStateOf(true) }
    var remindLowElectric by remember { mutableStateOf(true) }
    var remindEvaluation by remember { mutableStateOf(true) }
    var remindShowerQueue by remember { mutableStateOf(false) }
    var remindGrade by remember { mutableStateOf(true) }
    var quietOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        remindEnabled = settings.remindEnabled.first()
        remindClass = settings.remindClass.first()
        remindExam = settings.remindExam.first()
        remindLowElectric = settings.remindLowElectric.first()
        remindEvaluation = settings.remindEvaluation.first()
        remindShowerQueue = settings.remindShowerQueue.first()
        remindGrade = settings.remindGradeUpdate.first()
        quietOn = settings.quietEnabled.first()
    }

    fun set(block: suspend () -> Unit) = launchUi { block() }

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
            Text("免打扰 23:00–07:00", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)

            PrismGroupedList {
                ToggleRow("总开关", "关闭后暂停全部提醒", Icons.Rounded.Notifications, remindEnabled, index = 0, count = 7) {
                    remindEnabled = it; set { settings.setRemindEnabled(it) }
                }
                ToggleRow("上课提醒", "课前 10 分钟", Icons.Rounded.Schedule, remindClass, index = 1, count = 7) {
                    remindClass = it; set { settings.setRemindClass(it) }
                }
                ToggleRow("考试提醒", "7 天 / 1 天 / 2 小时", Icons.Rounded.Grade, remindExam, index = 2, count = 7) {
                    remindExam = it; set { settings.setRemindExam(it) }
                }
                ToggleRow("低电费提醒", "低于 20 度，每天 20:00", Icons.Rounded.Bolt, remindLowElectric, index = 3, count = 7) {
                    remindLowElectric = it; set { settings.setRemindLowElectric(it) }
                }
                ToggleRow("评教截止", "提前 3 天 / 1 天", Icons.Rounded.RateReview, remindEvaluation, index = 4, count = 7) {
                    remindEvaluation = it; set { settings.setRemindEvaluation(it) }
                }
                ToggleRow("洗浴排队", "仅 App 打开时提醒", Icons.Rounded.Shower, remindShowerQueue, index = 5, count = 7) {
                    remindShowerQueue = it; set { settings.setRemindShowerQueue(it) }
                }
                ToggleRow("成绩更新", "打开 App 时比对", Icons.Rounded.Grade, remindGrade, index = 6, count = 7) {
                    remindGrade = it; set { settings.setRemindGradeUpdate(it) }
                }
            }

            PrismGroupedList {
                ToggleRow("免打扰", "23:00–07:00 静音（考试提醒除外）", Icons.Rounded.Notifications, quietOn, index = 0, count = 1) {
                    quietOn = it; set { settings.setQuietEnabled(it) }
                }
            }

            Text("后台提醒受系统限制，不保证准时；关键提醒以打开 App 查看为准", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    PrismRow(
        title = title, subtitle = subtitle,
        index = index, count = count,
        leading = { IconBadge(icon) },
        trailing = { PrismSwitch(checked = checked, onCheckedChange = onChange) },
    )
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

    var biometric by remember { mutableStateOf(false) }
    var autoLock by remember { mutableStateOf(true) }
    var screenshotBlock by remember { mutableStateOf(false) }
    var logMask by remember { mutableStateOf(true) }
    var confirmWipe by remember { mutableStateOf(false) }
    var clearNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        biometric = settings.biometricUnlock.first()
        autoLock = settings.autoLock.first()
        screenshotBlock = settings.screenshotBlock.first()
        logMask = settings.logMask.first()
    }

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
                    onWipeAll()
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }
    if (clearNote != null) {
        PrismAlertDialog(title = "清除缓存", body = clearNote ?: "", confirmText = "知道了", dismissText = null,
            onConfirm = { clearNote = null }, onDismiss = { clearNote = null })
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
            PrismGroupedList {
                ToggleRow("生物识别解锁", "Face ID / 指纹", Icons.Rounded.Fingerprint, biometric, index = 0, count = 4) {
                    if (it) {
                        // 开启前先验证一次生物识别
                        launchUi {
                            val ok = requestBiometric()
                            if (ok) {
                                biometric = true
                                settings.setBiometricUnlock(true)
                            } else {
                                clearNote = "生物识别验证未通过，未开启解锁"
                            }
                        }
                    } else {
                        biometric = false
                        launchUi { settings.setBiometricUnlock(false) }
                    }
                }
                ToggleRow("自动锁定", "离开 5 分钟后", Icons.Rounded.LockClock, autoLock, index = 1, count = 4) {
                    autoLock = it; launchUi { settings.setAutoLock(it) }
                }
                ToggleRow("敏感页防截屏", "凭证、学籍页面", Icons.Rounded.ScreenshotMonitor, screenshotBlock, index = 2, count = 4) {
                    screenshotBlock = it; launchUi { settings.setScreenshotBlock(it) }
                }
                ToggleRow("日志脱敏", "不记录 Cookie / Token", Icons.Rounded.Shield, logMask, index = 3, count = 4) {
                    logMask = it; launchUi { settings.setLogMask(it) }
                }
            }

            PrismGroupedList {
                PrismRow(
                    title = "清除缓存", subtitle = "按模块清理（课表/成绩/洗浴/电费）",
                    index = 0, count = 2, leading = { IconBadge(Icons.Rounded.CleaningServices) },
                    onClick = {
                        launchUi {
                            entities.removeAll()
                            clearNote = "已清除全部模块缓存（凭证与设置保留）"
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
}
