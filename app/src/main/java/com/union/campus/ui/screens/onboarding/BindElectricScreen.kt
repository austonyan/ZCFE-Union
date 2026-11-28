package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.core.userFacingMessage
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.BindWizardNode
import com.union.campus.domain.ElectricRoom
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismDropdownField
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.footnoteStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 绑定电费：学号带入 → 园区 → 楼栋 → 楼层 → 房间（写操作，上游不校验身份，只查本人房间）。 */
@Composable
fun BindElectricScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    skipLabel: String = "稍后绑定",
) {
    val electric = ServiceLocator.electricRepository
    val jw = ServiceLocator.jwRepository
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var account by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }
    var confirmBind by remember { mutableStateOf(false) }

    var zones by remember { mutableStateOf<List<BindWizardNode>>(emptyList()) }
    var builds by remember { mutableStateOf<List<BindWizardNode>>(emptyList()) }
    var floors by remember { mutableStateOf<List<BindWizardNode>>(emptyList()) }
    var rooms by remember { mutableStateOf<List<BindWizardNode>>(emptyList()) }

    var boundRooms by remember { mutableStateOf<List<ElectricRoom>>(emptyList()) }
    var boundLoading by remember { mutableStateOf(false) }

    var zone by remember { mutableStateOf<BindWizardNode?>(null) }
    var build by remember { mutableStateOf<BindWizardNode?>(null) }
    var floor by remember { mutableStateOf<BindWizardNode?>(null) }
    var room by remember { mutableStateOf<BindWizardNode?>(null) }
    var zoneRequest by remember { mutableIntStateOf(0) }
    var buildRequest by remember { mutableIntStateOf(0) }
    var floorRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (account.isBlank()) {
            // 优先用教务学号；纯电费用户则用已保存的电费账号
            account = jw.studentNo().ifBlank { ServiceLocator.credentials.load().electricAccount }
        }
    }

    // 学号有效后：先查该账号已绑定房间，再加载园区列表（打字过程中 LaunchedEffect 会被取消，天然防抖）
    LaunchedEffect(account) {
        val a = account.trim()
        boundRooms = emptyList()
        boundLoading = a.length >= 6
        if (a.length < 6) {
            return@LaunchedEffect
        }
        // 打字防抖：每次输入都会取消上一个协程，避免逐位请求网关
        kotlinx.coroutines.delay(400)

        boundLoading = true
        try {
            boundRooms = electric.boundRooms(a)
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AppError) {
            boundRooms = emptyList()
            error = "已绑定房间查询失败：${e.userMessage}"
        } catch (e: Exception) {
            boundRooms = emptyList()
            error = "已绑定房间查询失败：${e.userFacingMessage()}"
        } finally {
            boundLoading = false
        }

        if (zones.isEmpty()) {
            try {
                zones = electric.zones()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AppError) {
                error = "园区加载失败：${e.userMessage}"
            } catch (e: Exception) {
                error = "园区加载失败：${e.userFacingMessage()}"
            }
        }
    }

    LaunchedEffect(zone, zoneRequest) {
        val z = zone ?: return@LaunchedEffect
        try {
            builds = electric.builds(z.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "楼栋加载失败，请重新选择园区"
        }
    }
    LaunchedEffect(build, buildRequest) {
        val b = build ?: return@LaunchedEffect
        try {
            floors = electric.floors(b.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "楼层加载失败，请重新选择楼栋"
        }
    }
    LaunchedEffect(floor, floorRequest) {
        val f = floor ?: return@LaunchedEffect
        try {
            rooms = electric.roomNums(f.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "房间加载失败，请重新选择楼层"
        }
    }

    fun chooseBoundRoom(room: ElectricRoom) {
        if (loading || boundLoading) return
        if (account.isBlank()) { error = "请输入学号"; return }
        loading = true
        error = null
        focusManager.clearFocus()
        scope.launch {
            try {
                electric.bindAccount(account.trim())
                ServiceLocator.settings.setLastElectricRoom(room.roomVerify)
                onNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "选择房间失败：${e.userFacingMessage()}"
            } finally {
                loading = false
            }
        }
    }

    fun doBind() {
        if (loading || boundLoading) return
        val z = zone ?: run { error = "请选择园区"; return }
        val b = build ?: run { error = "请选择楼栋"; return }
        val f = floor ?: run { error = "请选择楼层"; return }
        val r = room ?: run { error = "请选择房间"; return }
        if (account.isBlank()) { error = "请输入学号"; return }
        // 已在绑定列表中：不重复写上游，直接引导用户选择
        val duplicate = boundRooms.any { bound ->
            bound.fullName.contains(r.name) || (r.id.isNotBlank() && bound.roomVerify.contains(r.id))
        }
        if (duplicate) {
            error = "房间已绑定，请选择「使用」"
            return
        }
        loading = true
        error = null
        focusManager.clearFocus()
        scope.launch {
            try {
                electric.bindAccount(account.trim())
                val bound = electric.bindRoom(z.id, b.id, f.id, r.id)
                val verify = bound.roomVerify.ifBlank {
                    electric.rooms(forceRefresh = true)
                        .firstOrNull { it.fullName.contains(r.name) || (r.id.isNotBlank() && it.roomVerify.contains(r.id)) }
                        ?.roomVerify
                        .orEmpty()
                }
                ServiceLocator.settings.setLastElectricRoom(verify)
                onNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "绑定失败：${e.userFacingMessage()}"
            } finally {
                loading = false
            }
        }
    }

    if (helpVisible) {
        PrismAlertDialog(
            title = "提示",
            body = "仅支持本人宿舍，将绑定至当前账号。",
            confirmText = "确定",
            dismissText = null,
            onConfirm = { helpVisible = false },
            onDismiss = { helpVisible = false },
        )
    }

    if (confirmBind) {
        PrismAlertDialog(
            title = "绑定本人房间？",
            body = "确认将 ${zone?.name.orEmpty()} / ${build?.name.orEmpty()} / ${floor?.name.orEmpty()} / ${room?.name.orEmpty()} 绑定至当前完美校园账号？仅限本人宿舍。",
            confirmText = "确认绑定",
            onConfirm = { confirmBind = false; doBind() },
            onDismiss = { confirmBind = false },
        )
    }

    UnionScreenScaffold(
        title = "绑定完美校园",
        onBack = onBack,
        actions = {
            PrismIconButton(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "帮助",
                onClick = { helpVisible = true },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showProgress) {
                PrismProgressRail(
                    step = 5,
                    total = 5,
                    labels = listOf("欢迎", "教务", "学习通", "洗浴", "电费"),
                )
            }
            OnboardingIntro(
                title = "宿舍用电",
                description = "确认账号后选择本人宿舍；已绑定房间无需重复绑定。",
                icon = Icons.Rounded.MeetingRoom,
            )
            Text("完美校园账号", style = MaterialTheme.typography.titleMedium)

            PrismTextField(
                value = account,
                onValueChange = {
                    account = it
                    boundRooms = emptyList()
                    boundLoading = it.trim().length >= 6
                    error = null
                },
                label = "学号",
                enabled = !loading,
                leadingIcon = Icons.Rounded.Person,
                supportingText = if (jw.isBound() && account.trim() == jw.studentNo()) "已从教务导入 ${jw.maskedStudentNo()}" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            if (boundLoading) {
                Text(
                    "已绑定房间查询中…",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (boundRooms.isNotEmpty()) {
                PrismCard(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                ) {
                    Text(
                        "已绑定房间（${boundRooms.size}）",
                        style = headlineStyle(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    boundRooms.forEach { bound ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bound.fullName,
                                    style = subheadlineStyle(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    bound.roomVerify,
                                    style = footnoteStyle(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            PrismTextButton(
                                onClick = { chooseBoundRoom(bound) },
                                enabled = !loading,
                            ) {
                                Text("使用", color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Text("选择本人房间", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            DropdownField(
                label = "园区", icon = Icons.Rounded.LocationOn,
                options = zones.map { it.name }, selected = zone?.name,
                enabled = !loading && zones.isNotEmpty(),
                onSelected = { idx ->
                    zone = zones[idx]
                    zoneRequest++
                    build = null; floor = null; room = null
                    builds = emptyList(); floors = emptyList(); rooms = emptyList()
                    error = null
                    focusManager.clearFocus()
                },
                loadHint = if (zones.isEmpty()) "输入学号后自动加载" else null,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "楼栋", icon = Icons.Rounded.Apartment,
                options = builds.map { it.name }, selected = build?.name,
                enabled = !loading && builds.isNotEmpty(),
                onSelected = { idx ->
                    build = builds[idx]
                    buildRequest++
                    floor = null; room = null
                    floors = emptyList(); rooms = emptyList()
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "楼层", icon = Icons.Rounded.Apartment,
                options = floors.map { it.name }, selected = floor?.name,
                enabled = !loading && floors.isNotEmpty(),
                onSelected = { idx ->
                    floor = floors[idx]
                    floorRequest++
                    room = null; rooms = emptyList()
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "房间", icon = Icons.Rounded.MeetingRoom,
                options = rooms.map { it.name }, selected = room?.name,
                enabled = !loading && rooms.isNotEmpty(),
                onSelected = { idx -> room = rooms[idx] },
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { OnboardingError(it) }

            PrismButton(
                onClick = {
                    if (account.isBlank() || zone == null || build == null || floor == null || room == null) {
                        doBind() // 仅复用已有校验；缺少字段时不会发起写操作。
                    } else {
                        confirmBind = true
                    }
                },
                loading = loading,
                enabled = !boundLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("绑定房间", maxLines = 1)
            }
            PrismTextButton(onClick = onNext, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(skipLabel, maxLines = 1)
            }

            PrismCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("仅绑定本人宿舍", style = headlineStyle())
                Text("绑定会写入完美校园账号，请核对学号与房间。", style = subheadlineStyle())
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 通用下拉选择：outline 输入框 + arrow_drop_down + DropdownMenu。 */
@Composable
fun DropdownField(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    options: List<String>,
    selected: String?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loadHint: String? = null,
) {
    PrismDropdownField(
        label = label,
        options = options,
        selected = selected,
        onSelected = onSelected,
        modifier = modifier,
        leadingIcon = icon,
        enabled = enabled,
        supportingText = loadHint,
    )
}
