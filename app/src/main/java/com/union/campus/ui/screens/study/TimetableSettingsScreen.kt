package com.union.campus.ui.screens.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.TimetableSchedule
import com.union.campus.domain.TimetableScheduleConfig
import com.union.campus.ui.common.TimetableSchedulePicker
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.caption1Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle

/** 课表设置：教学楼作息与完整节次时间预览。 */
@Composable
fun TimetableSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val jLate by settings.timetableJLate.collectAsState(initial = true)
    val fallbackValue by settings.timetableFallbackGroup.collectAsState(initial = 1)
    val fallbackGroup = if (fallbackValue == 2) BuildingGroup.S1ToS6AndJ7 else BuildingGroup.J1ToJ6
    val config = TimetableScheduleConfig(
        j1ToJ6Slot = if (jLate) MorningSlot.Late else MorningSlot.Early,
        fallbackGroup = fallbackGroup,
    )
    val sSlot = config.slotFor(BuildingGroup.S1ToS6AndJ7)
    val jSlot = config.slotFor(BuildingGroup.J1ToJ6)
    val sEnd4 = TimetableSchedule.sectionTimes(sSlot).first { it.section == 4 }.end
    val jEnd4 = TimetableSchedule.sectionTimes(jSlot).first { it.section == 4 }.end
    val commonTimes = TimetableSchedule.sectionTimes(MorningSlot.Early)
        .filter { it.section !in 3..4 }
    val (dinnerStart, dinnerEnd) = TimetableSchedule.dinnerRange()

    UnionScreenScaffold(
        title = "课表设置",
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
            SectionHeader("教学楼作息")
            Text(
                "学校同时运行两套作息：S1–S6、J7 一套，J1–J6 一套。两套都按正常课表运行，只有第 3、4 节时间不同。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimetableSchedulePicker(
                jLate = jLate,
                onJLateChange = { late -> launchUi { settings.setTimetableJLate(late) } },
                fallbackGroup = fallbackGroup,
                onFallbackGroupChange = { group ->
                    launchUi {
                        settings.setTimetableFallbackGroup(
                            if (group == BuildingGroup.J1ToJ6) 1 else 2,
                        )
                    }
                },
            )

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("其余节次（两套相同）", style = headlineStyle())
            }
            Text(
                "两套作息同时运行；第 3、4 节按课程地点里的楼号自动匹配。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                commonTimes.forEach { time ->
                    if (time.section == TimetableSchedule.LUNCH_AFTER_SECTION + 1) {
                        BreakLine("第 3、4 节见上方 · 午休 $sEnd4 / $jEnd4 起")
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${time.section}",
                                style = caption1Style(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("第 ${time.section} 节", style = subheadlineStyle(), maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${time.start} – ${time.end}",
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (time.section == TimetableSchedule.DINNER_AFTER_SECTION) {
                        BreakLine("晚休 $dinnerStart–$dinnerEnd")
                    }
                }
            }

            Text(
                "课表最多展示到第 10 节；第 9、10 节无课时会自动隐藏，避免留白。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BreakLine(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(1.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .height(1.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}