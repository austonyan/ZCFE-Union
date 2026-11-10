package com.union.campus.ui.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.di.ServiceLocator
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismProgressRail
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSwitch
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.flow.first

/** 绑定完成：三套账号就绪状态 + 开启提醒 + 进入应用。 */
@Composable
fun BindDoneScreen(
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = ServiceLocator.settings
    val credentials = ServiceLocator.credentials
    var remindOn by remember { mutableStateOf(true) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        remindOn = settings.remindEnabled.first()
        shown = true
    }

    val checkScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
        label = "bind-done-check",
    )

    val cred = credentials.load()
    val jwBound = cred.jwStudentNo.isNotBlank()
    val showerBound = cred.showerToken.isNotBlank()
    val electricBound = cred.electricAccount.isNotBlank()

    UnionScreenScaffold(
        title = "绑定完成",
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrismProgressRail(
                step = 4,
                total = 4,
                labels = listOf("欢迎", "教务", "洗浴", "电费"),
            )
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                        alpha = checkScale
                    },
            )
            Text("设置完成", style = headlineStyle().copy(fontSize = 32.sp, lineHeight = 38.sp))
            Text("您的所有账号数据已就绪", style = subheadlineStyle())

            PrismGroupedList {
                StatusRow(
                    title = "教务系统",
                    subtitle = if (jwBound) "已连接 · 学号 ${ServiceLocator.jwRepository.maskedStudentNo()}" else "未绑定，可稍后在凭证中心添加",
                    ok = jwBound, icon = Icons.Rounded.School, index = 0, count = 3,
                )
                StatusRow(
                    title = "大白U帮",
                    subtitle = if (showerBound) "已连接 · 手机 ${ServiceLocator.showerRepository.maskedMobile()}" else "未绑定，可稍后添加",
                    ok = showerBound, icon = Icons.Rounded.Shower, index = 1, count = 3,
                )
                StatusRow(
                    title = "完美校园",
                    subtitle = if (electricBound) "已绑定电费 · ${cred.electricAccount}" else "未绑定，可稍后添加",
                    ok = electricBound, icon = Icons.Rounded.Bolt, index = 2, count = 3,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Notifications, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("开启提醒", style = bodyStyle())
                    Text("上课、考试、低电费", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PrismSwitch(checked = remindOn, onCheckedChange = {
                    remindOn = it
                    launchUi { settings.setRemindEnabled(it) }
                })
            }

            PrismButton(
                onClick = {
                    launchUi {
                        settings.setOnboardingDone()
                        settings.setBoundDone(true)
                        onEnter()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("进入应用")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, null)
            }

            Text("稍后可在设置中调整", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    subtitle: String,
    ok: Boolean,
    icon: ImageVector,
    index: Int,
    count: Int,
) {
    PrismRow(
        title = title,
        subtitle = subtitle,
        index = index,
        count = count,
        leading = { IconBadge(icon) },
        trailing = {
            Icon(
                Icons.Rounded.CheckCircle, null,
                tint = if (ok) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        },
    )
}
