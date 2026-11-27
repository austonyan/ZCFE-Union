package com.union.campus.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
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
import com.union.campus.ui.prism.LocalPrismDark
import com.union.campus.ui.theme.DarkColors
import com.union.campus.ui.theme.LightColors
import com.union.campus.ui.theme.PresetPalettes

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
                    if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Text(
                            "跟随壁纸取色",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            if (dynamicColor) "系统不支持动态取色，已使用预设主题色" else "预设主题色",
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
                        subtitle = "Android 12 及以上可用",
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
                        subtitle = "解锁、防截屏与数据清理",
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "版本与版权",
                        style = footnoteStyle(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 使用应用实际色板预览，不把种子色误当作最终主题色。 */
private data class ThemePresetOption(val id: String, val label: String)

private val themePresetOptions = listOf(
    ThemePresetOption("teal", "品牌青"),
    ThemePresetOption("blue", "海蓝"),
    ThemePresetOption("purple", "品紫"),
    ThemePresetOption("pink", "玫粉"),
    ThemePresetOption("red", "赤红"),
    ThemePresetOption("orange", "暖橙"),
    ThemePresetOption("yellow", "明黄"),
    ThemePresetOption("green", "草绿"),
    ThemePresetOption("gray", "岩灰"),
)

@Composable
private fun ThemeColorPicker(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val dark = LocalPrismDark.current
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        themePresetOptions.chunked(3).forEach { options ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    val selected = option.id == selectedId
                    val scheme = PresetPalettes.scheme(option.id, dark) ?: if (dark) DarkColors else LightColors
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surfaceContainer)
                            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) onSelect(option.id) })
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = scheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Box(Modifier.size(12.dp).clip(CircleShape).background(scheme.secondary))
                            Box(Modifier.size(12.dp).clip(CircleShape).background(scheme.tertiary))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            option.label,
                            style = caption2Style(),
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
