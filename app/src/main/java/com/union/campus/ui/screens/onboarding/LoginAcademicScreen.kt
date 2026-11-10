package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
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

    var studentNo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var captcha by rememberSaveable { mutableStateOf("") }
    var needCaptcha by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var captchaBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var captchaLoading by remember { mutableStateOf(false) }

    fun refreshCaptcha() {
        if (studentNo.isBlank()) return
        captchaLoading = true
        launchUi {
            try {
                val bytes = jw.captchaImage(studentNo.trim())
                captchaBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
        if (studentNo.isBlank()) { error = "请输入学号"; return false }
        if (password.isBlank()) { error = "请输入密码"; return false }
        return true
    }

    fun doLogin() {
        if (!validate()) return
        loading = true
        error = null
        launchUi {
            try {
                // 验证码（如需要）必须随表单提交，否则服务端会一直要求验证码
                jw.login(studentNo.trim(), password, captcha.trim())
                loading = false
                haptics.success()
                onNext()
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
                loading = false
                haptics.error()
                error = "登录失败：${e.message}"
            }
        }
    }

    UnionScreenScaffold(
        title = "登录教务系统",
        onBack = onBack,
        actions = {
            PrismIconButton(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "帮助",
                onClick = { error = "使用学号与教务系统密码；如提示验证码，请按图片输入。" },
            )
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
                    step = 2,
                    total = 4,
                    labels = listOf("欢迎", "教务", "洗浴", "电费"),
                )
            }

            Text(
                "使用学号和教务密码登录",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrismTextField(
                value = studentNo,
                onValueChange = { studentNo = it },
                label = "学号",
                leadingIcon = Icons.Rounded.Person,
                supportingText = "用于课表、成绩、考试",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )
            PrismTextField(
                value = password,
                onValueChange = { password = it },
                label = "密码",
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
                    PrismTextButton(onClick = { refreshCaptcha() }, enabled = !captchaLoading) {
                        Text("换一张")
                    }
                }
            }
            PrismTextField(
                value = captcha,
                onValueChange = { captcha = it },
                label = if (needCaptcha) "验证码" else "验证码（如需要）",
                leadingIcon = Icons.Rounded.VerifiedUser,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = subheadlineStyle())
            }

            PrismButton(
                onClick = { doLogin() },
                loading = loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("登录教务")
            }

            PrismTextButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(skipLabel)
            }

            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text("为什么需要教务账号", style = headlineStyle())
                Text("课表、成绩、考试、评教都来自教务系统。", style = subheadlineStyle())
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