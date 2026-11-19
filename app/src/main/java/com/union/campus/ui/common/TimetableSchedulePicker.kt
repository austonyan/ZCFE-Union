package com.union.campus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.TimetableSchedule
import com.union.campus.domain.TimetableScheduleConfig
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle

/**
 * 教学楼作息选择：
 * J1–J6 和 S1–S6、J7 分别选择第 3、4 节从 10:20 还是 10:40 开始。
 * 两套作息同时运行，这里只是设置楼号与时间的对应关系。
 */
@Composable
fun TimetableSchedulePicker(
    jLate: Boolean,
    onJLateChange: (Boolean) -> Unit,
    fallbackGroup: BuildingGroup,
    onFallbackGroupChange: (BuildingGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = remember(jLate, fallbackGroup) {
        TimetableScheduleConfig(
            j1ToJ6Slot = if (jLate) MorningSlot.Late else MorningSlot.Early,
            fallbackGroup = fallbackGroup,
        )
    }
    val jSlot = config.slotFor(BuildingGroup.J1ToJ6)
    val sSlot = config.slotFor(BuildingGroup.S1ToS6AndJ7)
    val jTimes = remember(jSlot) { TimetableSchedule.sectionTimes(jSlot) }
    val sTimes = remember(sSlot) { TimetableSchedule.sectionTimes(sSlot) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "设置楼号和时间作息怎么对应",
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScheduleRow(
            title = "J1–J6",
            slot = jSlot,
            times = jTimes,
            onSelect = { slot -> onJLateChange(slot == MorningSlot.Late) },
        )
        ScheduleRow(
            title = "S1–S6、J7",
            slot = sSlot,
            times = sTimes,
            onSelect = { slot -> onJLateChange(slot == MorningSlot.Early) },
        )

        Spacer(Modifier.height(2.dp))
        PrismCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        ) {
            Text("课程地点未识别出楼号时", style = subheadlineStyle())
            Spacer(Modifier.height(6.dp))
            PrismSegmented(
                options = listOf("按 J1–J6", "按 S1–S6、J7"),
                selectedIndex = if (fallbackGroup == BuildingGroup.J1ToJ6) 0 else 1,
                onSelect = { index ->
                    onFallbackGroupChange(
                        if (index == 0) BuildingGroup.J1ToJ6 else BuildingGroup.S1ToS6AndJ7,
                    )
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "仅影响未识别楼号课程，识别楼号课程自动匹配作息。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    title: String,
    slot: MorningSlot,
    times: List<com.union.campus.domain.SectionTime>,
    onSelect: (MorningSlot) -> Unit,
) {
    val third = times.firstOrNull { it.section == 3 }
    val fourth = times.firstOrNull { it.section == 4 }
    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = headlineStyle())
                Text(
                    "第 3、4 节时间",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                slot.label,
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        PrismSegmented(
            options = listOf("10:20", "10:40"),
            selectedIndex = if (slot == MorningSlot.Early) 0 else 1,
            onSelect = { index -> onSelect(if (index == 0) MorningSlot.Early else MorningSlot.Late) },
        )
        if (third != null && fourth != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "第 3 节 ${third.start}–${third.end} · 第 4 节 ${fourth.start}–${fourth.end}",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}