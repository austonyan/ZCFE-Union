package com.union.campus.ui.screens.onboarding

import android.os.SystemClock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val mobileFocus = remember { FocusRequester() }
    val secretFocus = remember { FocusRequester() }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var useSms by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sendingSms by remember { mutableStateOf(false) }
    var smsSentMobile by rememberSaveable { mutableStateOf("") }
    var smsSentAt by rememberSaveable { mutableLongStateOf(0L) }
    var resendSeconds by remember { mutableIntStateOf(0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    val busy = loading || sendingSms
    val smsSentToCurrent = smsSentMobile.isNotBlank() && smsSentMobile == mobile.trim()

    LaunchedEffect(smsSentAt) {
        while (smsSentAt > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - smsSentAt
            resendSeconds = if (elapsed in 0L until 60_000L) {
                ((60_000L - elapsed + 999L) / 1_000L).toInt()
            } else 0
            if (resendSeconds == 0) break
            delay(1_000L)
        }
    }

    fun sendSms() {
        if (loading || sendingSms) return
        val phone = mobile.trim()
        if (phone.isBlank()) { error = "请输入手机号"; mobileFocus.requestFocus(); return }
        if (phone == smsSentMobile && SystemClock.elapsedRealtime() - smsSentAt in 0L until 60_000L) {
            useSms = true
            error = null
            return
        }
        sendingSms = true
        error = null
        focusManager.clearFocus()
        scope.launch {
            try {
                shower.clientSendSms(phone)
                smsSentMobile = phone
                smsSentAt = SystemClock.elapsedRealtime()
                smsCode = ""
                useSms = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "验证码发送失败：${e.message ?: "请稍后重试"}"
            } finally {
                sendingSms = false
            }
        }
    }

    fun doLogin() {
        if (loading || sendingSms) return
        when {
            mobile.isBlank() -> { error = "请输入手机号"; mobileFocus.requestFocus(); return }
            !useSms && password.isBlank() -> { error = "请输入密码"; secretFocus.requestFocus(); return }
            useSms && smsCode.isBlank() -> { error = "请输入验证码"; secretFocus.requestFocus(); return }
        }
        loading = true
        error = null
        focusManager.clearFocus()
        scope.launch {
            try {
                if (useSms) {
                    shower.clientFastLogin(mobile.trim(), smsCode.trim())
                } else {
                    shower.login(mobile.trim(), password)
                }
                haptics.success()
                onNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                haptics.error()
                error = e.userMessage
            } catch (e: Exception) {
                haptics.error()
                error = "登录失败：${e.message}"
            } finally {
                loading = false
            }
        }
    }

    UnionScreenScaffold(
        title = "登录大白U帮",
        onBack = onBack,
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
                    step = 4,
                    total = 5,
                    labels = listOf("欢迎", "教务", "学习通", "洗浴", "电费"),
                )
            }
            OnboardingIntro(
                title = "洗浴服务",
                description = "登录大白U帮，查看空闲设备、排队及账单。",
                icon = Icons.Rounded.Phone,
            )
            Text(if (useSms) "短信验证" else "账号密码", style = MaterialTheme.typography.titleMedium)
            PrismTextField(
                value = mobile,
                onValueChange = { mobile = it; smsCode = ""; error = null },
                label = "手机号",
                modifier = Modifier.focusRequester(mobileFocus),
                enabled = !busy,
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
                    enabled = !busy,
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
                    enabled = !busy,
                    isError = error != null && smsCode.isBlank(),
                    leadingIcon = Icons.Rounded.Sms,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = if (smsSentToCurrent) "验证码已发送" else "请先获取当前手机号的验证码",
                )
            }
            error?.let { OnboardingError(it) }

            PrismButton(
                onClick = { doLogin() },
                loading = loading,
                enabled = !sendingSms,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("登录大白U帮", maxLines = 1)
            }

            PrismTextButton(
                onClick = {
                    if (!useSms) {
                        sendSms()
                    } else {
                        focusManager.clearFocus()
                        useSms = false
                        error = null
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Sms, null)
                Spacer(Modifier.width(8.dp))
                Text(if (sendingSms) "正在发送验证码…" else if (useSms) "密码登录" else "短信登录（发送验证码）")
            }

            if (useSms) {
                val coolingDown = smsSentToCurrent && resendSeconds > 0
                PrismTextButton(
                    onClick = { sendSms() },
                    enabled = !busy && !coolingDown,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (coolingDown) "${resendSeconds} 秒后重新发送" else "发送验证码")
                }
            }

            PrismTextButton(
                onClick = onNext,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(skipLabel, maxLines = 1)
            }

            Text(
                "登录状态仅存本机；仅限本人账号查询及设备启停。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
