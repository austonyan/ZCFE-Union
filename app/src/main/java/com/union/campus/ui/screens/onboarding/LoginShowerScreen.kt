package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.footnoteStyle

/** 登录洗浴：大白U帮手机号 + 密码，支持验证码登录。 */
@Composable
fun LoginShowerScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shower = ServiceLocator.showerRepository
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var useSms by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var smsSent by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }

    fun doLogin() {
        when {
            mobile.isBlank() -> { error = "请输入手机号"; return }
            !useSms && password.isBlank() -> { error = "请输入密码"; return }
            useSms && smsCode.isBlank() -> { error = "请输入验证码"; return }
        }
        loading = true
        error = null
        launchUi {
            try {
                if (useSms) {
                    shower.clientFastLogin(mobile.trim(), smsCode.trim())
                } else {
                    shower.login(mobile.trim(), password)
                }
                loading = false
                onNext()
            } catch (e: AppError) {
                loading = false
                error = e.userMessage
            } catch (e: Exception) {
                loading = false
                error = "登录失败：${e.message}"
            }
        }
    }

    if (info != null) {
        PrismAlertDialog(
            title = "提示",
            body = info ?: "",
            confirmText = "知道了",
            dismissText = null,
            onConfirm = { info = null },
            onDismiss = { info = null },
        )
    }

    UnionScreenScaffold(
        title = "登录大白U帮",
        onBack = onBack,
        actions = {
            IconButton(onClick = { info = "洗浴账号是大白U帮 App 的手机号与密码；仅用于本人查询与开关设备。" }) {
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
                progress = { 0.5f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            Text(
                "使用大白U帮的手机号和密码",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("手机号") },
                leadingIcon = { Icon(Icons.Rounded.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!useSms) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it },
                    label = { Text("短信验证码") },
                    leadingIcon = { Icon(Icons.Rounded.Sms, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = subheadlineStyle()) }

            Button(
                onClick = { doLogin() },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("登录洗浴")
                }
            }

            TextButton(
                onClick = {
                    if (!useSms) {
                        if (mobile.isBlank()) { error = "请先输入手机号"; return@TextButton }
                        launchUi {
                            try {
                                shower.clientSendSms(mobile.trim())
                                smsSent = true
                                useSms = true
                                error = null
                            } catch (e: AppError) {
                                error = e.userMessage
                            }
                        }
                    } else {
                        useSms = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Sms, null)
                Spacer(Modifier.width(8.dp))
                Text(if (useSms) "改用密码登录" else "改用验证码登录")
            }

            TextButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text("稍后绑定")
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("为什么需要洗浴账号", style = headlineStyle())
                    Text("查看澡堂空闲、排队、开关设备与账单。", style = subheadlineStyle())
                }
            }

            Text(
                "登录状态仅保存在本机",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
