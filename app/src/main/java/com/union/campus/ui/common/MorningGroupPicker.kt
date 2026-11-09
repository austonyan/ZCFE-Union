package com.union.campus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.domain.MorningGroup
import com.union.campus.domain.TimetableSchedule
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel

/** 上午作息分组选择：设置页与引导页共用。 */
@Composable
fun MorningGroupPicker(
    selected: MorningGroup,
    onSelect: (MorningGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MorningGroup.entries.forEach { group ->
            MorningGroupOption(
                group = group,
                selected = group == selected,
                onClick = { onSelect(group) },
            )
        }
    }
}

@Composable
private fun MorningGroupOption(
    group: MorningGroup,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else surfaceColorOf(SurfaceLevel.Card)
    val titleColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    val (third, fourth) = TimetableSchedule.morningTimes(group)

    PrismCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = container,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${group.label} · ${group.classCodes}",
                    style = headlineStyle(),
                    color = titleColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "第 3 节 ${third.start}–${third.end} · 第 4 节 ${fourth.start}–${fourth.end}",
                    style = footnoteStyle(),
                    color = subtitleColor,
                )
            }
            if (selected) {
                Spacer(Modifier.width(2.dp))
                Text("当前", style = footnoteStyle(), color = subtitleColor)
            }
        }
    }
}