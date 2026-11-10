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
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.TimetableScheduleConfig
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
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val jLate by settings.timetableJLate.collectAsState(initial = true)
    val fallbackValue by settings.timetableFallbackGroup.collectAsState(initial = 1)
    val config = TimetableScheduleConfig(
        j1ToJ6Slot = if (jLate) MorningSlot.Late else MorningSlot.Early,
        fallbackGroup = if (fallbackValue == 2) BuildingGroup.S1ToS6AndJ7 else BuildingGroup.J1ToJ6,
    )
    val jSlot = config.slotFor(BuildingGroup.J1ToJ6)
    val sSlot = config.slotFor(BuildingGroup.S1ToS6AndJ7)
    val remindEnabled by settings.remindEnabled.collectAsState(initial = true)
    val quietOn by settings.quietEnabled.collectAsState(initial = true)
    val quietStart by settings.quietStart.collectAsState(initial = 23)
    val quietEnd by settings.quietEnd.collectAsState(initial = 7)
    val reminderSummary = when {
        !remindEnabled -> "已暂停全部提醒"
        quietOn -> "提醒已开启 · 免打扰 %02d:00–%02d:00".format(quietStart, quietEnd)
        else -> "提醒已开启 · 免打扰关闭"
    }

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
                    subtitle = "教学楼作息 · J1–J6 ${jSlot.startLabel} · S1–S6、J7 ${sSlot.startLabel}",
                    index = 0, count = 1,
                    leading = { IconBadge(Icons.Rounded.Schedule) },
                    onClick = onOpenTimetableSettings,
                )
            }

            Text("通知与提醒", style = headlineStyle())
            PrismGroupedList {
                PrismRow(
                    title = "提醒中心",
                    subtitle = reminderSummary,
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
            PrismCard(onClick = onOpenAbout) {
                CardHeader("郑财校园", icon = Icons.Rounded.Info)
                Spacer(Modifier.height(6.dp))
                Text(
                    "教务、洗浴与电费合并入口；登录状态仅保存在本机，只使用本人账号。",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "查看版本、作者与版权",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.primary,
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