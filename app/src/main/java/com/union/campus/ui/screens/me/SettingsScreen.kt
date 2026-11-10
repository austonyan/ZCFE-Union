package com.union.campus.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.TimetableScheduleConfig
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.PrismToggleRow
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.subheadlineStyle

/** 设置总页：外观、学习、提醒、隐私与数据的统一入口。 */
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
    val themeMode by settings.themeMode.collectAsState(initial = "system")
    val dynamicColor by settings.dynamicColor.collectAsState(initial = false)
    val themeColorPreset by settings.themeColorPreset.collectAsState(initial = "teal")
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
            PrismSection("学习与课表") {
                PrismGroupedList {
                    PrismRow(
                        title = "课表设置",
                        subtitle = "教学楼作息 · J1–J6 ${jSlot.startLabel} · S1–S6、J7 ${sSlot.startLabel}",
                        leading = { IconBadge(Icons.Outlined.Schedule) },
                        onClick = onOpenTimetableSettings,
                    )
                }
            }

            PrismSection("外观") {
                PrismCard {
                    Text(
                        "主题",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    PrismSegmented(
                        options = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = when (themeMode) {
                            "light" -> 1
                            "dark" -> 2
                            else -> 0
                        },
                        onSelect = { index ->
                            val mode = when (index) {
                                1 -> "light"
                                2 -> "dark"
                                else -> "system"
                            }
                            launchUi { settings.setThemeMode(mode) }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    if (dynamicColor) {
                        Text(
                            "已开启莫奈取色，颜色跟随系统壁纸；关闭后可选择预设主题色。",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "预设主题色",
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        ThemeColorPicker(
                            selectedId = themeColorPreset,
                            onSelect = { id -> launchUi { settings.setThemeColorPreset(id) } },
                        )
                    }
                }
                PrismGroupedList {
                    PrismToggleRow(
                        title = "动态取色（莫奈）",
                        subtitle = "开启后跟随系统壁纸配色；关闭后使用所选预设主题色",
                        leading = { IconBadge(Icons.Outlined.Palette) },
                        checked = dynamicColor,
                        onCheckedChange = { on -> launchUi { settings.setDynamicColor(on) } },
                    )
                }
            }

            PrismSection("通知与提醒") {
                PrismGroupedList {
                    PrismRow(
                        title = "提醒中心",
                        subtitle = reminderSummary,
                        leading = { IconBadge(Icons.Outlined.Notifications) },
                        onClick = onOpenReminders,
                    )
                }
            }

            PrismSection("隐私与安全") {
                PrismGroupedList {
                    PrismRow(
                        title = "隐私与安全",
                        subtitle = "生物识别、自动锁定、防截屏与数据清理",
                        leading = { IconBadge(Icons.Outlined.Lock) },
                        onClick = onOpenPrivacy,
                    )
                }
            }

            PrismSection("关于") {
                PrismCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenAbout,
                ) {
                    CardHeader("郑财校园", icon = Icons.Outlined.Info)
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

/** 预设主题色：种子色取自 FlClash，选中后整页即时换色。 */
private data class ThemePresetOption(val id: String, val label: String, val seed: Color)

private val themePresetOptions = listOf(
    ThemePresetOption("teal", "品牌青", Color(0xFF0B4F52)),
    ThemePresetOption("blue", "海蓝", Color(0xFF007AFF)),
    ThemePresetOption("purple", "品紫", Color(0xFFCB30E0)),
    ThemePresetOption("pink", "玫粉", Color(0xFFFF2D55)),
    ThemePresetOption("red", "赤红", Color(0xFFFF383C)),
    ThemePresetOption("orange", "暖橙", Color(0xFFFF8D28)),
    ThemePresetOption("yellow", "明黄", Color(0xFFFFCC00)),
    ThemePresetOption("green", "草绿", Color(0xFF34C759)),
    ThemePresetOption("gray", "岩灰", Color(0xFF8E8E93)),
)

@Composable
private fun ThemeColorPicker(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val selectedLabel = themePresetOptions.firstOrNull { it.id == selectedId }?.label ?: "品牌青"
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            themePresetOptions.forEach { option ->
                val selected = option.id == selectedId
                Column(
                    Modifier
                        .width(52.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickable { if (!selected) onSelect(option.id) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(option.seed)
                            .then(
                                if (selected) Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                ) else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (option.seed.luminance() > 0.55f) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        option.label,
                        style = caption2Style(),
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "当前：$selectedLabel",
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
