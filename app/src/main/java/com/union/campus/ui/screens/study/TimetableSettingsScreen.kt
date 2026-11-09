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
import androidx.compose.ui.unit.sp
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.MorningGroup
import com.union.campus.domain.TimetableSchedule
import com.union.campus.ui.common.MorningGroupPicker
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle

/** 课表设置：上午作息分组与完整节次时间预览。 */
@Composable
fun TimetableSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val savedGroup by settings.timetableMorningGroup.collectAsState(initial = MorningGroup.GroupOne.storedValue)
    val selected = MorningGroup.fromStored(savedGroup)
    val times = TimetableSchedule.sectionTimes(selected)
    val (lunchStart, lunchEnd) = TimetableSchedule.lunchRange(selected)
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
            Text("上午作息分组", style = headlineStyle())
            Text(
                "两组仅上午第 3、4 节时间不同，其余节次一致。分组时间每学期可能调整，以教务通知为准，可随时在此切换。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MorningGroupPicker(
                selected = selected,
                onSelect = { group ->
                    launchUi { settings.setTimetableMorningGroup(group.storedValue) }
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
                Text("${selected.label}节次时间", style = headlineStyle())
            }
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                times.forEach { time ->
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
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("第 ${time.section} 节", style = subheadlineStyle())
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${time.start} – ${time.end}",
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (time.section == TimetableSchedule.LUNCH_AFTER_SECTION) {
                        BreakLine("午休 $lunchStart–$lunchEnd")
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