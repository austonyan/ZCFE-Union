package com.union.campus.ui.screens.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCorner
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismTextField
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.PrismToggleRow
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 登录学习通：手机号/学号 + 密码。只读说明放在表单上方，
 * 让用户先知道这个入口不会提交作业，再决定是否填密码。
 */
@Composable
fun HomeworkLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    onSkip: (() -> Unit)? = null,
) {
    val repo = ServiceLocator.xxtRepository
    val messenger = LocalPrismMessenger.current
    val haptics = rememberPrismHaptics()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberPassword by rememberSaveable { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    fun submit() {
        if (loading) return
        val name = account.trim()
        if (name.isBlank()) { error = "请填写学习通手机号或学号"; return }
        if (password.isBlank()) { error = "请填写学习通密码"; return }
        loading = true
        error = null
        scope.launch {
            try {
                repo.login(name, password, rememberPassword)
                haptics.success()
                messenger.success("学习通已连接")
                onLoggedIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                haptics.error()
                error = e.debugDetail?.takeIf { it.isNotBlank() && it != e.userMessage }
                    ?.let { "${e.userMessage}（$it）" } ?: e.userMessage
            } catch (e: Exception) {
                haptics.error()
                error = "登录失败：${e.message ?: "未知错误"}"
            } finally {
                loading = false
            }
        }
    }

    UnionScreenScaffold(
        title = "登录学习通",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
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
                    total = 5,
                    labels = listOf("欢迎", "教务", "学习通", "洗浴", "电费"),
                )
            }
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                corner = PrismCorner.Xxl,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "把作业截止时间搬到手机里",
                    style = headlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "登录后自动整理未交作业，按截止时间排序并分档提醒。",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        Icons.Outlined.Lock,
                        container = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "只读 · 账号密码仅在本机加密保存",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            PrismTextField(
                value = account,
                onValueChange = { account = it; error = null },
                label = "手机号或学号",
                leadingIcon = Icons.Outlined.Person,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                enabled = !loading,
            )
            PrismTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "学习通密码",
                leadingIcon = Icons.Outlined.Lock,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                enabled = !loading,
            )

            PrismToggleRow(
                title = "记住密码",
                subtitle = if (rememberPassword) "会话过期后可自动续期" else "只保留账号，过期后需手动登录",
                checked = rememberPassword,
                enabled = !loading,
                onCheckedChange = { rememberPassword = it },
            )

            error?.let {
                Text(
                    it,
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.error,
      
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PrismButton(
                onClick = { submit() },
                loading = loading,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "正在登录…" else "登录学习通")
            }

            onSkip?.let { skip ->
                PrismTextButton(onClick = skip, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text("稍后绑定", maxLines = 1)
                }
            }

            Text(
                "需要图形验证码时，请先在浏览器登录一次学习通再重试。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
