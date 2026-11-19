package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle

/** 登录洗浴：大白U帮手机号 + 密码，支持验证码登录。 */
@Composable
fun LoginShowerScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    skipLabel: String = "稍后绑定",
) {
    val shower = ServiceLocator.showerRepository
    val haptics = rememberPrismHaptics()
    val mobileFocus = remember { FocusRequester() }
    val secretFocus = remember { FocusRequester() }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var useSms by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var smsSent by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    fun doLogin() {
        when {
            mobile.isBlank() -> { error = "请输入手机号"; mobileFocus.requestFocus(); return }
            !useSms && password.isBlank() -> { error = "请输入密码"; secretFocus.requestFocus(); return }
            useSms && smsCode.isBlank() -> { error = "请输入验证码"; secretFocus.requestFocus(); return }
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
                haptics.success()
                onNext()
            } catch (e: AppError) {
                loading = false
                haptics.error()
                error = e.userMessage
            } catch (e: Exception) {
                loading = false
                haptics.error()
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
            PrismIconButton(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "帮助",
                onClick = { info = "洗浴账号是大白U帮 App 的手机号与密码；仅用于本人查询与开关设备。" },
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
                    step = 3,
                    total = 4,
                    labels = listOf("欢迎", "教务", "洗浴", "电费"),
                )
            }
            OnboardingIntro(
                title = "洗浴，轻松安排",
                description = "连接大白U帮账号，查看空闲、排队与账单。",
                icon = Icons.Rounded.Phone,
            )
            Text(if (useSms) "短信验证" else "账号密码", style = MaterialTheme.typography.titleMedium)
            PrismTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = "手机号",
                modifier = Modifier.focusRequester(mobileFocus),
                isError = error != null && mobile.isBlank(),
                leadingIcon = Icons.Rounded.Phone,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
            )
            if (!useSms) {
                PrismTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    modifier = Modifier.focusRequester(secretFocus),
                    isError = error != null && password.isBlank(),
                    leadingIcon = Icons.Rounded.Lock,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        PrismIconButton(
                            icon = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            onClick = { passwordVisible = !passwordVisible },
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            } else {
                PrismTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it },
                    label = "短信验证码",
                    modifier = Modifier.focusRequester(secretFocus),
                    isError = error != null && smsCode.isBlank(),
                    leadingIcon = Icons.Rounded.Sms,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = if (smsSent) "验证码已发送" else null,
                )
            }
            error?.let { OnboardingError(it) }

            PrismButton(
                onClick = { doLogin() },
                loading = loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("登录洗浴", maxLines = 1)
            }

            PrismTextButton(
                onClick = {
                    if (!useSms) {
                        if (mobile.isBlank()) { error = "请先输入手机号"; mobileFocus.requestFocus(); return@PrismTextButton }
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
                Text(if (useSms) "改用密码登录" else "发送验证码并切换登录", maxLines = 1)
            }

            PrismTextButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(skipLabel, maxLines = 1)
            }

            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text("为什么需要洗浴账号", style = headlineStyle())
                Text("查看澡堂空闲、排队、开关设备与账单。", style = subheadlineStyle())
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
