package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.BindWizardNode
import com.union.campus.domain.ElectricRoom
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismDropdownField
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.footnoteStyle

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

    var account by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

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

    LaunchedEffect(Unit) {
        if (account.isBlank()) {
            // 优先用教务学号；纯电费用户则用已保存的电费账号
            account = jw.studentNo().ifBlank { ServiceLocator.credentials.load().electricAccount }
        }
    }

    // 学号有效后：先查该账号已绑定房间，再加载园区列表（打字过程中 LaunchedEffect 会被取消，天然防抖）
    LaunchedEffect(account) {
        val a = account.trim()
        if (a.length < 6) {
            boundRooms = emptyList()
            boundLoading = false
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
            error = "已绑定房间查询失败：${e.message}"
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
                error = "园区加载失败：${e.message}"
            }
        }
    }

    LaunchedEffect(zone) {
        val z = zone ?: return@LaunchedEffect
        runCatching {
            builds = electric.builds(z.id)
            floor = null; room = null; floors = emptyList(); rooms = emptyList()
        }.onFailure { error = "楼栋加载失败，请重试" }
    }
    LaunchedEffect(build) {
        val b = build ?: return@LaunchedEffect
        runCatching {
            floors = electric.floors(b.id)
            floor = null; room = null; rooms = emptyList()
        }.onFailure { error = "楼层加载失败，请重试" }
    }
    LaunchedEffect(floor) {
        val f = floor ?: return@LaunchedEffect
        runCatching {
            rooms = electric.roomNums(f.id)
            room = null
        }.onFailure { error = "房间加载失败，请重试" }
    }

    fun chooseBoundRoom(room: ElectricRoom) {
        if (account.isBlank()) { error = "请输入学号"; return }
        loading = true
        error = null
        launchUi {
            try {
                electric.bindAccount(account.trim())
                ServiceLocator.settings.setLastElectricRoom(room.roomVerify)
                loading = false
                onNext()
            } catch (e: Exception) {
                loading = false
                error = "选择房间失败：${e.message}"
            }
        }
    }

    fun doBind() {
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
            error = "该房间已经绑定，直接点上方「使用」即可"
            return
        }
        loading = true
        error = null
        launchUi {
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
                loading = false
                onNext()
            } catch (e: AppError) {
                loading = false
                error = e.userMessage
            } catch (e: Exception) {
                loading = false
                error = "绑定失败：${e.message}"
            }
        }
    }

    UnionScreenScaffold(
        title = "绑定完美校园",
        onBack = onBack,
        actions = {
            IconButton(onClick = { error = "电费只查本人房间：绑定会把目标房间挂到你的账号下。" }) {
                Icon(Icons.Rounded.HelpOutline, contentDescription = "帮助")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showProgress) {
                PrismProgressRail(
                    step = 4,
                    total = 4,
                    labels = listOf("欢迎", "教务", "洗浴", "电费"),
                )
            }
            Text("选择你所在的宿舍房间", style = subheadlineStyle())

            PrismTextField(
                value = account,
                onValueChange = { account = it },
                label = "学号",
                leadingIcon = Icons.Rounded.Person,
                supportingText = if (jw.isBound()) "已从教务带入 ${jw.maskedStudentNo()}" else "未登录教务时需手动输入",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (boundLoading) {
                Text(
                    "正在查询已绑定房间…",
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
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                )
                            }
                            PrismTextButton(
                                onClick = { chooseBoundRoom(bound) },
                                enabled = !loading,
                            ) {
                                Text("使用")
                            }
                        }
                    }
                    Text(
                        "点「使用」切换到该房间；需要新增房间时在下方选择并绑定",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                Text("绑定新房间", style = headlineStyle())
            }
            DropdownField(
                label = "园区", icon = Icons.Rounded.LocationOn,
                options = zones.map { it.name }, selected = zone?.name,
                enabled = zones.isNotEmpty(),
                onSelected = { idx -> zone = zones[idx] },
                loadHint = if (zones.isEmpty()) "输入学号后自动加载" else null,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "楼栋", icon = Icons.Rounded.Apartment,
                options = builds.map { it.name }, selected = build?.name,
                enabled = builds.isNotEmpty(),
                onSelected = { idx -> build = builds[idx] },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "楼层", icon = Icons.Rounded.Apartment,
                options = floors.map { it.name }, selected = floor?.name,
                enabled = floors.isNotEmpty(),
                onSelected = { idx -> floor = floors[idx] },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "房间", icon = Icons.Rounded.MeetingRoom,
                options = rooms.map { it.name }, selected = room?.name,
                enabled = rooms.isNotEmpty(),
                onSelected = { idx -> room = rooms[idx] },
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = subheadlineStyle()) }

            PrismButton(
                onClick = { doBind() },
                loading = loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("绑定房间")
            }
            PrismTextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(skipLabel)
            }

            PrismCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("电费只查本人房间", style = headlineStyle())
                Text("使用绑定功能会将目标房间绑定至您的完美校园账户", style = subheadlineStyle())
            }
            Text("登录状态仅保存在本机", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
