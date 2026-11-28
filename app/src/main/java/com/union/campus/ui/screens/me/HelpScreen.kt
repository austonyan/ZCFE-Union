package com.union.campus.ui.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.union.campus.ui.common.ExternalLinks
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.openExternalLink
import com.union.campus.ui.prism.GroupDivider
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.subheadlineStyle

private data class HelpQa(val question: String, val answer: String)

private data class HelpTopic(val title: String, val items: List<HelpQa>)

/**
 * 应用内帮助：把「为什么拉不到课程」「为什么收不到通知」这类高频疑问
 * 放在用户能自己找到的地方，而不是只在出错瞬间给一句提示。
 */
private val HelpTopics = listOf(
    HelpTopic(
        "账号与登录",
        listOf(
            HelpQa(
                "为什么要分别登录四个账号？",
                "教务、学习通、洗浴、电费来自四个不同系统，各绑各的，互不影响。" +
                    "初始化时可以全部跳过；凭证只在本机加密保存，不会上传。",
            ),
            HelpQa(
                "忘记教务密码怎么办？",
                "登录页底部「忘记密码？」会跳转学校统一认证的找回页面，App 不保存密码明文。",
            ),
            HelpQa(
                "提示「登录失效」怎么办？",
                "会话有有效期。到「我的 → 凭证中心」重新登录对应账号即可，其他账号不用重登。",
            ),
        ),
    ),
    HelpTopic(
        "课表与教务",
        listOf(
            HelpQa(
                "课表拉不到课程？",
                "先确认教务已登录；教务系统维护或网络不通时会显示具体原因，恢复后点重试。" +
                    "课表按周显示，左右滑动切周，点「返回本周」回到当前周。",
            ),
            HelpQa(
                "上课时间和我实际的不一样？",
                "在「我的 → 设置 → 课表设置」选择所在教学楼的作息（J1–J6 早/晚）和默认分组，" +
                    "课表会按本周实际楼栋自动套用对应作息。",
            ),
            HelpQa(
                "成绩 / 考试是空的？",
                "教务还没发布时显示空态，不是加载失败。成绩页可按学期和「全部 / 必修」切换，" +
                    "右上角刷新会重新向教务取一次。",
            ),
        ),
    ),
    HelpTopic(
        "学习通作业",
        listOf(
            HelpQa(
                "学习通会改动我的数据吗？",
                "不会。只读同步课程与未交作业和未读通知，不代提交作业、不改动学习通里的任何内容。",
            ),
            HelpQa(
                "作业同步失败？",
                "学习通会话会过期，也可能触发验证码拦截。重新登录学习通后点右上角刷新；" +
                    "部分课程失败时，其余课程仍会正常显示，并说明哪几门没拉到。",
            ),
        ),
    ),
    HelpTopic(
        "洗浴",
        listOf(
            HelpQa(
                "为什么提示不在洗浴服务时间？",
                "浴室开放时间由大白U帮服务端控制，不在时段内无法预约或启动设备，按澡堂开放时间使用即可。",
            ),
            HelpQa(
                "预约了没启动会怎样？",
                "预约有倒计时，超时会自动尝试取消。到号后需要回到洗浴页手动启动设备，" +
                    "启动前会先替你预约，失败会释放预约。",
            ),
        ),
    ),
    HelpTopic(
        "电费",
        listOf(
            HelpQa(
                "绑定电费要填什么？",
                "完美校园账号（本人学号）、学校编码，再依次选园区、楼栋和房间。" +
                    "学号固定为本人，不能查询其他学号。",
            ),
            HelpQa(
                "为什么没有金额，只显示度数？",
                "上游没返回电价时，只展示度数，不做金额估算。点右上角刷新可重新取一次余额与用量。",
            ),
        ),
    ),
    HelpTopic(
        "提醒与通知",
        listOf(
            HelpQa(
                "收不到提醒？",
                "先在「我的 → 提醒中心」允许通知；系统省电策略可能限制后台同步，" +
                    "把本应用设为「不受限制」会更稳。首次同步不会补发历史提醒。",
            ),
            HelpQa(
                "提醒太多太吵？",
                "提醒中心可单独开关上课、考试、低电费、评教、作业与学习通通知，还能设置免打扰时段；" +
                    "误关总开关时提示条上的「撤销」可以立刻恢复。",
            ),
        ),
    ),
    HelpTopic(
        "隐私与数据",
        listOf(
            HelpQa(
                "数据存在哪里？",
                "只在本机：凭证用系统 Keystore 加密，业务数据存在本地缓存。" +
                    "「我的 → 设置 → 隐私与安全」可以开启防截屏、生物识别解锁，或清除缓存 / 全部数据。",
            ),
            HelpQa(
                "这是学校官方应用吗？",
                "不是。这是个人开发的校园工具，数据来自学校各系统；上游接口变动时可能短时不可用。",
            ),
        ),
    ),
)

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messenger = LocalPrismMessenger.current
    // 记住展开的问题，进出详情页不丢；用问题原文当 key，内容改动后也不会串。
    var expanded by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf<Set<String>>(emptySet()) }

    UnionScreenScaffold(
        title = "帮助与常见问题",
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
                Text(
                    "先在这里找答案",
                    style = headlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "常见问题按主题分组，点问题展开答案。仍然解决不了，可以到 GitHub 反馈。",
                    style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            HelpTopics.forEach { topic ->
                PrismSection(topic.title) {
                    PrismCard(modifier = Modifier.fillMaxWidth()) {
                        topic.items.forEachIndexed { index, qa ->
                            if (index > 0) GroupDivider(startPadding = 0.dp)
                            HelpQaRow(
                                qa = qa,
                                expanded = qa.question in expanded,
                                onToggle = {
                                    expanded = if (qa.question in expanded) {
                                        expanded - qa.question
                                    } else {
                                        expanded + qa.question
                                    }
                                },
                            )
                        }
                    }
                }
            }

            PrismSection("反馈") {
                PrismGroupedList {
                    PrismRow(
                        title = "提交问题或建议",
                        subtitle = "在 GitHub Issues 里描述复现步骤",
                        leading = { IconBadge(Icons.Outlined.Chat) },
                        onClick = {
                            openExternalLink(ExternalLinks.Feedback, onFailure = {
                                messenger.error("无法打开反馈页面")
                            })
                        },
                    )
                }
            }

            Text(
                "反馈时附上「我的 → 关于」里的版本号，更容易定位问题。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 一问一答：整行可点，展开后答案接在问题下方；读屏会播报展开/收起状态。 */
@Composable
private fun HelpQaRow(
    qa: HelpQa,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                qa.question,
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                qa.answer,
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}