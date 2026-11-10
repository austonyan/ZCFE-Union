package com.union.campus.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Explore
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
import com.union.campus.ui.common.TimetableSchedulePicker
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle

/** 欢迎页：说明 Union 把教务、洗浴、电费合成一个入口。 */
@Composable
fun WelcomeScreen(
    onStartBinding: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val jLate by settings.timetableJLate.collectAsState(initial = true)
    val fallbackGroupValue by settings.timetableFallbackGroup.collectAsState(initial = 1)
    val fallbackGroup = if (fallbackGroupValue == 2) BuildingGroup.S1ToS6AndJ7 else BuildingGroup.J1ToJ6

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "郑财校园",
            style = pageTitleStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )

        PrismProgressRail(
            step = 1,
            total = 4,
            labels = listOf("欢迎", "教务", "洗浴", "电费"),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "把三套账号，收进一个入口",
            style = headlineStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "课表、成绩、洗浴空闲与电费余额，打开应用就能看到。三个平台的账号会在接下来的步骤里依次绑定。",
            style = subheadlineStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PrismCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Text(
                text = "三个平台，合三为一",
                style = headlineStyle(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "绑定教务、大白U帮与完美校园后，课表、洗浴与电费会汇总到这里。",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Text(
            text = "选择教学楼作息",
            style = headlineStyle(),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "学校同时运行两套作息：S1–S6、J7 一套，J1–J6 一套。按你主要上课的楼号选择；之后也可在「课表设置」中修改。",
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

        PrismButton(
            onClick = onStartBinding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始绑定")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }

        PrismTextButton(
            onClick = onExplore,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.Rounded.Explore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("先逛逛")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "登录状态仅保存在本机",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "第 1 步 / 共 4 步",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}