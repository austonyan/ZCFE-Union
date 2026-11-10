package com.union.campus.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.BuildConfig
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.withAlpha

/** 关于：版本、作者、版权与数据来源。 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "关于",
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
                contentPadding = PaddingValues(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "郑财校园",
                            style = pageTitleStyle(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "郑州财经学院校园生活入口",
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.withAlpha(PrismAlpha.Secondary),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "把教务、洗浴、电费、课表与校园生活信息放进一个应用。",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            PrismSection("应用信息") {
                PrismGroupedList {
                    PrismRow(
                        title = "版本",
                        subtitle = "${BuildConfig.VERSION_NAME} · versionCode ${BuildConfig.VERSION_CODE}",
                        leading = { IconBadge(Icons.Outlined.Info) },
                    )
                    PrismRow(
                        title = "作者",
                        subtitle = "Auston · 设计与开发",
                        leading = { IconBadge(Icons.Outlined.Person) },
                    )
                    PrismRow(
                        title = "版权",
                        subtitle = "© 2026 Auston. 保留所有权利。",
                        leading = { IconBadge(Icons.Outlined.Lock) },
                    )
                }
            }

            PrismCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader("数据来源")
                Spacer(Modifier.height(6.dp))
                Text(
                    "课表、成绩、考试与通知来自教务系统；洗浴来自大白U帮；电费来自完美校园。所有账号凭证仅保存在本机加密存储中。",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "本应用为个人开发的校园辅助工具，与郑州财经学院官方无关。功能与数据以各上游系统为准。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Copyright © 2026 Auston. All rights reserved.",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}