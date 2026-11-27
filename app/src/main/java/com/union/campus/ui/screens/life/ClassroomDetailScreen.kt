package com.union.campus.ui.screens.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.pageTitleStyle

/** 教室详情：容量、设备与今日空闲时段。 */
@Composable
fun ClassroomDetailScreen(
    room: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "教室详情",
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
            PrismCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Text(room, style = pageTitleStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            PrismSection(title = "今日空闲时段") {
                // 时段以教务 getJsxx 返回为准；上游未返回明细时不编造数据
                NullStatus(
                    Icons.Rounded.CalendarMonth,
                    "暂无分时段数据",
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
