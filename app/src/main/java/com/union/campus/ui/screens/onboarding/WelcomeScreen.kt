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
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.pageTitleStyle

/** 欢迎页：说明 Union 把教务、洗浴、电费合成一个入口。 */
@Composable
fun WelcomeScreen(
    onStartBinding: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .pageWidth()
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

        OnboardingIntro(
            title = "校园服务统一入口",
            description = "绑定本人账号，或先逛逛。",
            icon = Icons.Rounded.School,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Icons.Rounded.School to "教务", Icons.Rounded.Shower to "洗浴", Icons.Rounded.Bolt to "电费").forEach { (icon, label) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Column(
                        Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }

        PrismButton(
            onClick = onStartBinding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始绑定", maxLines = 1)
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
            Text("暂不绑定")
        }
    }
}
