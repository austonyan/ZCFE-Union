package com.union.campus.ui.screens.me

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.union.campus.core.AccountCheck
import com.union.campus.core.AccountCheckState
import com.union.campus.ui.prism.PrismLoadingIndicator
import com.union.campus.ui.prism.PrismStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 账号体系里的四个平台。顺序即列表顺序：教务与学习通属学习，洗浴与电费属生活。
 * 标签是唯一主键：我的页、凭证中心、体检结果都用同一套说法，避免一处叫「教务」一处叫「教务系统」。
 */
internal enum class AccountPlatform(val label: String) {
    Jw("教务"),
    Xxt("学习通"),
    Shower("洗浴"),
    Electric("电费"),
    ;

    companion object {
        val All: List<AccountPlatform> = entries.toList()
    }
}

/** 体检结论的界面状态；Idle 表示还没检查过，只看「已保存 / 未绑定」。 */
internal enum class AccountCheckUi { Idle, Checking, Ok, Unbound, Expired, Unavailable }

internal fun AccountCheck.toUiState(): AccountCheckUi = when (state) {
    AccountCheckState.OK -> AccountCheckUi.Ok
    AccountCheckState.UNBOUND -> AccountCheckUi.Unbound
    AccountCheckState.EXPIRED -> AccountCheckUi.Expired
    AccountCheckState.UNAVAILABLE -> AccountCheckUi.Unavailable
}

/** 状态徽标文案：绑定状态与体检结论共用一个位置，同一时刻只表达一件事。 */
internal fun accountStatusLabel(bound: Boolean, state: AccountCheckUi): String = when (state) {
    AccountCheckUi.Idle -> if (bound) "已保存" else "未绑定"
    AccountCheckUi.Checking -> "检查中"
    AccountCheckUi.Ok -> "正常"
    AccountCheckUi.Unbound -> "未绑定"
    AccountCheckUi.Expired -> "登录失效"
    AccountCheckUi.Unavailable -> "暂不可用"
}

/** 体检提示文案：默认只列失败项，全部正常时给一句结论。 */
internal fun accountCheckSummary(
    results: List<Pair<AccountPlatform, AccountCheck>>,
    onlyFailures: Boolean = true,
): String {
    val failed = results.filterNot { it.second.isOk }
    if (failed.isEmpty()) return "四个平台凭证正常"
    val lines = (if (onlyFailures) failed else results).map { (platform, check) ->
        "${platform.label}：${check.displayText}"
    }
    return lines.joinToString("；")
}

/**
 * 四个平台的行内体检控制器。
 *
 * 结果写回 `states`，调用方直接把徽标绑到对应行上：不再弹一个把四条结论拼成一句的对话框，
 * 也不再为「我的」和「凭证中心」各写一份体检状态机。
 */
@Stable
internal class AccountHealthController {
    var checking by mutableStateOf(false)
        private set

    var states by mutableStateOf<Map<AccountPlatform, AccountCheckUi>>(emptyMap())
        private set

    /** 是否至少完成过一次体检；用于区分「未检查」与「检查后结果为空」。 */
    var checked by mutableStateOf(false)
        private set

    fun stateOf(platform: AccountPlatform): AccountCheckUi =
        states[platform] ?: AccountCheckUi.Idle

    fun check(
        scope: CoroutineScope,
        onResult: (List<Pair<AccountPlatform, AccountCheck>>) -> Unit = {},
    ) {
        if (checking) return
        checking = true
        states = AccountPlatform.All.associateWith { AccountCheckUi.Checking }
        scope.launch {
            val results = checkAllAccounts()
            states = results.associate { (platform, check) -> platform to check.toUiState() }
            checking = false
            checked = true
            onResult(results)
        }
    }
}

@Composable
internal fun rememberAccountHealth(): AccountHealthController = remember { AccountHealthController() }

/**
 * 账号状态徽标：先表达「有没有绑定」，体检后表达「还能不能用」。
 * 颜色只取语义色（成功 / 警告 / 错误），不新造一套色板。
 */
@Composable
internal fun AccountStatusPill(
    bound: Boolean,
    state: AccountCheckUi,
    modifier: Modifier = Modifier,
) {
    val container: Color
    val content: Color
    when (state) {
        AccountCheckUi.Ok -> {
            container = PrismStatus.successContainer()
            content = PrismStatus.onSuccessContainer()
        }
        AccountCheckUi.Expired -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
        AccountCheckUi.Unavailable -> {
            container = PrismStatus.warningContainer()
            content = PrismStatus.onWarningContainer()
        }
        else -> {
            if (bound) {
                container = MaterialTheme.colorScheme.secondaryContainer
                content = MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                container = MaterialTheme.colorScheme.surfaceContainerHigh
                content = MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == AccountCheckUi.Checking) {
                PrismLoadingIndicator(size = 12.dp, strokeWidth = 1.5.dp, color = content)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                accountStatusLabel(bound, state),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}