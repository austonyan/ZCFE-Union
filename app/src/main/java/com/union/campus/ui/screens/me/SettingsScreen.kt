package com.union.campus.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.MorningGroup
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle

/** 设置总页：学习、提醒、隐私与数据的统一入口。 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTimetableSettings: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val morningGroupValue by settings.timetableMorningGroup
        .collectAsState(initial = MorningGroup.GroupOne.storedValue)
    val morningGroup = MorningGroup.fromStored(morningGroupValue)

    UnionScreenScaffold(
        title = "设置",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("学习与课表", style = headlineStyle())
            PrismGroupedList {
                PrismRow(
                    title = "课表设置",
                    subtitle = "上午作息 · ${morningGroup.label}（${morningGroup.classCodes}）· 节次时间",
                    index = 0, count = 1,
                    leading = { IconBadge(Icons.Rounded.Schedule) },
                    onClick = onOpenTimetableSettings,
                )
            }

            Text("通知与提醒", style = headlineStyle())
            PrismGroupedList {
                PrismRow(
                    title = "提醒中心",
                    subtitle = "上课、考试、低电费与免打扰",
                    index = 0, count = 1,
                    leading = { IconBadge(Icons.Rounded.Notifications) },
                    onClick = onOpenReminders,
                )
            }

            Text("隐私与安全", style = headlineStyle())
            PrismGroupedList {
                PrismRow(
                    title = "隐私与安全",
                    subtitle = "生物识别、自动锁定、防截屏与数据清理",
                    index = 0, count = 1,
                    leading = { IconBadge(Icons.Rounded.Lock) },
                    onClick = onOpenPrivacy,
                )
            }

            Text("关于", style = headlineStyle())
            PrismCard {
                CardHeader("郑财校园", icon = Icons.Rounded.Info)
                Spacer(Modifier.height(6.dp))
                Text(
                    "教务、洗浴与电费合并入口；登录状态仅保存在本机，只使用本人账号。",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "设置项会立即生效，重启应用后保留。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}