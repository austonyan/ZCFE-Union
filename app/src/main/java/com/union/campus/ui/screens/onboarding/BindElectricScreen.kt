package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.BindWizardNode
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.footnoteStyle

/** 绑定电费：学号带入 → 园区 → 楼栋 → 楼层 → 房间（写操作，上游不校验身份，只查本人房间）。 */
@Composable
fun BindElectricScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
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

    var zone by remember { mutableStateOf<BindWizardNode?>(null) }
    var build by remember { mutableStateOf<BindWizardNode?>(null) }
    var floor by remember { mutableStateOf<BindWizardNode?>(null) }
    var room by remember { mutableStateOf<BindWizardNode?>(null) }

    LaunchedEffect(Unit) {
        if (account.isBlank()) {
            // 带入完整学号；maskedStudentNo() 只用于展示（前 4 位 + ****）
            account = jw.studentNo()
        }
    }

    // 学号有效后自动加载园区列表（打字过程中 LaunchedEffect 会被取消，天然防抖）
    LaunchedEffect(account) {
        if (account.trim().length >= 6 && zones.isEmpty()) {
            runCatching { zones = electric.zones() }
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

    fun doBind() {
        val z = zone ?: run { error = "请选择园区"; return }
        val b = build ?: run { error = "请选择楼栋"; return }
        val f = floor ?: run { error = "请选择楼层"; return }
        val r = room ?: run { error = "请选择房间"; return }
        if (account.isBlank()) { error = "请输入学号"; return }
        loading = true
        error = null
        launchUi {
            try {
                electric.bindAccount(account.trim())
                val bound = electric.bindRoom(z.id, b.id, f.id, r.id)
                ServiceLocator.settings.setLastElectricRoom(bound.roomVerify)
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
            LinearProgressIndicator(
                progress = { 0.75f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            Text("选择你所在的宿舍房间", style = subheadlineStyle())

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("学号") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                supportingText = { Text(if (jw.isBound()) "已从教务带入 ${jw.maskedStudentNo()}" else "未登录教务时需手动输入") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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

            Button(
                onClick = { doBind() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Link, null)
                    Spacer(Modifier.width(8.dp))
                    Text("绑定房间")
                }
            }
            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text("稍后绑定")
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("电费只查本人房间", style = headlineStyle())
                    Text("使用绑定功能会将目标房间绑定至您的完美校园账户", style = subheadlineStyle())
                }
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
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = icon?.let { { Icon(it, null) } },
            supportingText = loadHint?.let { { Text(it) } },
            trailingIcon = {
                IconButton(onClick = { if (enabled && options.isNotEmpty()) expanded = !expanded }) {
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = "展开")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded && options.isNotEmpty() && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(index); expanded = false },
                )
            }
        }
    }
}
