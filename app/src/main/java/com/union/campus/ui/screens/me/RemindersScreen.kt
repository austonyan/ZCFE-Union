package com.union.campus.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.ElectricLowState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.SnapSheetDefaults
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.PrismToggleRow
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle

// ---------------- 提醒中心 ----------------

/** 提醒中心：与真实生效范围一致——总开关联动、阈值可调、免打扰时段可改。 */
@Composable
fun RemindersCenterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val messenger = LocalPrismMessenger.current

    val remindEnabled by settings.remindEnabled.collectAsState(initial = true)
    val remindClass by settings.remindClass.collectAsState(initial = true)
    val remindExam by settings.remindExam.collectAsState(initial = true)
    val remindLowElectric by settings.remindLowElectric.collectAsState(initial = true)
    val remindEvaluation by settings.remindEvaluation.collectAsState(initial = true)
    val remindXxt by settings.remindXxtHomework.collectAsState(initial = true)
    val remindXxtNotice by settings.remindXxtNotice.collectAsState(initial = true)
    val remindShowerQueue by settings.remindShowerQueue.collectAsState(initial = true)
    val quietOn by settings.quietEnabled.collectAsState(initial = true)
    val quietStart by settings.quietStart.collectAsState(initial = 23)
    val quietEnd by settings.quietEnd.collectAsState(initial = 7)
    val classLead by settings.remindClassLead.collectAsState(initial = 10)
    val lowThreshold by settings.lowElectricThreshold.collectAsState(initial = ElectricLowState.DefaultThreshold)

    var quietSheet by rememberSaveable { mutableStateOf(false) }
    fun set(block: suspend () -> Unit) = launchUi { block() }

    val leadOptions = remember(classLead) { (listOf(5, 10, 15) + classLead).distinct().sorted() }
    val thresholdOptions = remember(lowThreshold) { (listOf(10, 20, 30) + lowThreshold).distinct().sorted() }

    UnionScreenScaffold(
        title = "提醒中心",
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
            PrismGroupedList {
                PrismToggleRow(
                    title = "总开关",
                    subtitle = if (remindEnabled) "提醒已开启" else "已暂停全部提醒",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    checked = remindEnabled,
                    onCheckedChange = { on ->
                        set { settings.setRemindEnabled(on) }
                        if (on) messenger.success("提醒已开启") else messenger.info("已暂停全部提醒")
                    },
                )
                PrismToggleRow(
                    title = "上课提醒",
                    subtitle = if (remindEnabled && remindClass) "课前 $classLead 分钟" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Schedule) },
                    checked = remindClass,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindClass(on) } },
                )
                PrismToggleRow(
                    title = "考试提醒",
                    subtitle = if (remindEnabled && remindExam) "近期考试加入待办" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Grade) },
                    checked = remindExam,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindExam(on) } },
                )
                PrismToggleRow(
                    title = "低电费提醒",
                    subtitle = if (remindEnabled && remindLowElectric) "低于 $lowThreshold 度，每天 20:00" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Bolt) },
                    checked = remindLowElectric,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindLowElectric(on) } },
                )
                PrismToggleRow(
                    title = "评教截止",
                    subtitle = if (remindEnabled && remindEvaluation) "待评课程加入待办" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.RateReview) },
                    checked = remindEvaluation,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindEvaluation(on) } },
                )
                PrismToggleRow(
                    title = "学习通作业",
                    subtitle = if (remindEnabled && remindXxt) "截止前 24 / 6 / 1 小时各提醒一次" else "已关闭",
                    leading = { IconBadge(Icons.AutoMirrored.Outlined.Assignment) },
                    checked = remindXxt,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindXxtHomework(on) } },
                )
                PrismToggleRow(
                    title = "学习通通知",
                    subtitle = if (remindEnabled && remindXxtNotice) "未读通知逐条提醒" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    checked = remindXxtNotice,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindXxtNotice(on) } },
                )
                PrismToggleRow(
                    title = "洗浴排队",
                    subtitle = if (remindEnabled && remindShowerQueue) "仅在洗浴页打开时到号提醒" else "已关闭",
                    leading = { IconBadge(Icons.Outlined.Shower) },
                    checked = remindShowerQueue,
                    enabled = remindEnabled,
                    onCheckedChange = { on -> set { settings.setRemindShowerQueue(on) } },
                )
                PrismToggleRow(
                    title = "成绩更新",
                    subtitle = "暂不支持系统通知",
                    leading = { IconBadge(Icons.Outlined.Grade) },
                    checked = false,
                    enabled = false,
                    onCheckedChange = { },
                )
            }

            PrismCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (remindEnabled) 1f else 0.5f),
            ) {
                CardHeader("提醒设置")
                Spacer(Modifier.height(10.dp))
                Text("课前提醒时间", style = subheadlineStyle())
                Spacer(Modifier.height(6.dp))
                PrismSegmented(
                    options = leadOptions.map { "课前 $it 分钟" },
                    selectedIndex = leadOptions.indexOf(classLead).coerceAtLeast(0),
                    enabled = remindEnabled && remindClass,
                    onSelect = { index ->
                        set { settings.setRemindClassLead(leadOptions[index]) }
                    },
                )
                Spacer(Modifier.height(14.dp))
                Text("低电费阈值", style = subheadlineStyle())
                Spacer(Modifier.height(6.dp))
                PrismSegmented(
                    options = thresholdOptions.map { "$it 度" },
                    selectedIndex = thresholdOptions.indexOf(lowThreshold).coerceAtLeast(0),
                    enabled = remindEnabled && remindLowElectric,
                    onSelect = { index ->
                        set { settings.setLowElectricThreshold(thresholdOptions[index]) }
                    },
                )
            }

            PrismGroupedList {
                PrismRow(
                    title = "免打扰时段",
                    subtitle = "${formatHour(quietStart)}–${formatHour(quietEnd)} · ${if (quietOn) "已开启" else "已关闭"}",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    trailing = {
                        Text(
                            "修改",
                            style = footnoteStyle(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { quietSheet = true },
                )
                PrismToggleRow(
                    title = "免打扰开关",
                    subtitle = "时段内不发送系统通知",
                    leading = { IconBadge(Icons.Outlined.Notifications) },
                    checked = quietOn,
                    onCheckedChange = { on ->
                        set { settings.setQuietEnabled(on) }
                        if (on) messenger.success("免打扰已开启") else messenger.info("免打扰已关闭")
                    },
                )
            }

            PrismTextButton(
                onClick = {
                    set {
                        settings.setRemindEnabled(true)
                        settings.setRemindClass(true)
                        settings.setRemindClassLead(10)
                        settings.setRemindExam(true)
                        settings.setRemindLowElectric(true)
                        settings.setLowElectricThreshold(ElectricLowState.DefaultThreshold)
                        settings.setRemindEvaluation(true)
                        settings.setRemindXxtHomework(true)
                        settings.setRemindXxtNotice(true)
                        settings.setRemindShowerQueue(true)
                        settings.setQuietEnabled(true)
                        settings.setQuietRange(23, 7)
                    }
                    messenger.success("已恢复默认提醒设置")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复默认提醒设置")
            }

            Text(
                "上课提醒为应用内横幅；考试、评教及低电费加入待办，低电费另发系统通知；学习通作业与低电费走系统通知；洗浴仅在页面打开时到号提醒。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "后台提醒受系统限制，可能延迟；重要事项请打开应用确认。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    QuietHoursSheet(
        visible = quietSheet,
        startHour = quietStart,
        endHour = quietEnd,
        onDismiss = { quietSheet = false },
        onSave = { start, end ->
            quietSheet = false
            set { settings.setQuietRange(start, end) }
            messenger.success("免打扰时段已更新为 ${formatHour(start)}–${formatHour(end)}")
        },
    )
}

private fun formatHour(hour: Int): String = "%02d:00".format(hour)

/** 免打扰时段：双列小时胶囊，保存前本地预览，确定后才写入设置。 */
@Composable
private fun QuietHoursSheet(
    visible: Boolean,
    startHour: Int,
    endHour: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var start by rememberSaveable(visible) { mutableIntStateOf(startHour) }
    var end by rememberSaveable(visible) { mutableIntStateOf(endHour) }
    val startOptions = remember(startHour) { (listOf(21, 22, 23, 0, 1, 2) + startHour).distinct() }
    val endOptions = remember(endHour) { (listOf(5, 6, 7, 8, 9, 10) + endHour).distinct() }

    PrismSnapSheet(visible = visible, onDismiss = onDismiss, startAt = SnapSheetDefaults.Half, paneTitle = "免打扰时段") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("免打扰时段", style = pageTitleStyle())
            Text(
                "时段内停发系统通知，待办仍显示。支持跨日，如 23:00–07:00。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("开始", style = headlineStyle())
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                startOptions.forEach { hour ->
                    PrismChip(
                        label = formatHour(hour),
                        selected = start == hour,
                        onClick = { start = hour },
                    )
                }
            }
            Text("结束", style = headlineStyle())
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                endOptions.forEach { hour ->
                    PrismChip(
                        label = formatHour(hour),
                        selected = end == hour,
                        onClick = { end = hour },
                    )
                }
            }
            Text(
                if (start == end) "起止时间不能相同" else "当前：${formatHour(start)}–${formatHour(end)}",
                style = subheadlineStyle(),
                color = if (start == end) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            PrismButton(
                onClick = { onSave(start, end) },
                enabled = start != end,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存时段")
            }
            PrismTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("取消")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
