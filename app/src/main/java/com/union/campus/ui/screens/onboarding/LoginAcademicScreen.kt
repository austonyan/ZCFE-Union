package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.ExternalLinks
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.openExternalLink
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 登录教务：学号 + 教务密码 + 验证码（按需）。 */
@Composable
fun LoginAcademicScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    skipLabel: String = "稍后绑定",
) {
    val jw = ServiceLocator.jwRepository
    val haptics = rememberPrismHaptics()
    val messenger = LocalPrismMessenger.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val studentFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val captchaFocus = remember { FocusRequester() }

    var studentNo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var captcha by rememberSaveable { mutableStateOf("") }
    var needCaptcha by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }
    var captchaBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var captchaLoading by remember { mutableStateOf(false) }

    fun refreshCaptcha() {
        if (studentNo.isBlank() || captchaLoading || loading) return
        val account = studentNo.trim()
        captchaLoading = true
        captcha = ""
        captchaBitmap = null
        scope.launch {
            try {
                val bytes = jw.captchaImage(account)
                if (studentNo.trim() == account) {
                    captchaBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                error = e.userMessage
            } catch (e: Exception) {
                error = "验证码加载失败：${e.message}"
            } finally {
                captchaLoading = false
            }
        }
    }

    fun validate(): Boolean {
        if (studentNo.isBlank()) { error = "请输入学号"; studentFocus.requestFocus(); return false }
        if (password.isBlank()) { error = "请输入密码"; passwordFocus.requestFocus(); return false }
        if (needCaptcha && captcha.isBlank()) { error = "请输入验证码"; captchaFocus.requestFocus(); return false }
        return true
    }

    fun doLogin() {
        if (loading || captchaLoading || !validate()) return
        loading = true
        error = null
        focusManager.clearFocus()
        scope.launch {
            try {
                // 验证码（如需要）必须随表单提交，否则服务端会一直要求验证码
                jw.login(studentNo.trim(), password, captcha.trim())
                haptics.success()
                onNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                loading = false
                haptics.error()
                val detail = e.debugDetail?.takeIf { it.isNotBlank() }?.take(140)
                error = if (detail != null) "${e.userMessage}（$detail）" else e.userMessage
                if (e is AppError.Validation && e.reason.contains("验证码")) {
                    needCaptcha = true
                    captcha = ""
                    refreshCaptcha()
                }
            } catch (e: Exception) {
                haptics.error()
                error = "登录失败：${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (needCaptcha && captchaBitmap == null) refreshCaptcha()
    }

    if (helpVisible) {
        PrismAlertDialog(
            title = "提示",
            body = "使用学号与教务密码登录；如需验证码，请按图输入。",
            confirmText = "确定",
            dismissText = null,
            onConfirm = { helpVisible = false },
            onDismiss = { helpVisible = false },
        )
    }

    UnionScreenScaffold(
        title = "登录教务系统",
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
                    step = 2,
                    total = 4,
                    labels = listOf("欢迎", "教务", "洗浴", "电费"),
                )
            }

            OnboardingIntro(
                title = "同步课表",
                description = "使用学号与教务密码登录，查看课表、成绩及考试。",
                icon = Icons.Rounded.Person,
            )
            Text("教务账号", style = MaterialTheme.typography.titleMedium)
            PrismTextField(
                value = studentNo,
                onValueChange = {
                    studentNo = it
                    needCaptcha = false
                    captcha = ""
                    captchaBitmap = null
                    error = null
                },
                label = "学号",
                modifier = Modifier.focusRequester(studentFocus),
                isError = error != null && studentNo.isBlank(),
                leadingIcon = Icons.Rounded.Person,
                enabled = !loading && !captchaLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )
            PrismTextField(
                value = password,
                onValueChange = { password = it },
                label = "密码",
                modifier = Modifier.focusRequester(passwordFocus),
                enabled = !loading,
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
                    imeAction = if (needCaptcha) ImeAction.Next else ImeAction.Done,
                ),
            )
            if (needCaptcha) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val bmp = captchaBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "图形验证码",
                            modifier = Modifier.height(48.dp).width(120.dp),
                        )
                    } else {
                        Text(
                            if (captchaLoading) "验证码加载中…" else "验证码加载失败",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PrismTextButton(onClick = { refreshCaptcha() }, enabled = !captchaLoading && !loading) {
                        Text("刷新验证码")
                    }
                }
                PrismTextField(
                    value = captcha,
                    onValueChange = { captcha = it },
                    label = "验证码",
                    modifier = Modifier.focusRequester(captchaFocus),
                    enabled = !loading && !captchaLoading,
                    isError = error != null && captcha.isBlank(),
                    leadingIcon = Icons.Rounded.VerifiedUser,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }

            error?.let {
                OnboardingError(it)
            }

            PrismButton(
                onClick = { doLogin() },
                loading = loading,
                enabled = !captchaLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("登录教务系统", maxLines = 1)
            }

            Column(Modifier.fillMaxWidth()) {
                PrismTextButton(onClick = onNext, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text(skipLabel, maxLines = 1)
                }
                PrismTextButton(
                    onClick = {
                        openExternalLink(ExternalLinks.RetrievePassword, onFailure = {
                            messenger.error("无法打开找回密码页面")
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("忘记密码？", maxLines = 1)
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
