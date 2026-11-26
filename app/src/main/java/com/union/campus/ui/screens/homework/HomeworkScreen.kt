package com.union.campus.ui.screens.homework

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.XxtHomework
import com.union.campus.domain.XxtNotice
import com.union.campus.domain.XxtHomeworkRules
import com.union.campus.domain.XxtHomeworkSnapshot
import com.union.campus.ui.common.UiState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.formatUpdatedAt
import com.union.campus.ui.common.pageWidth
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismCorner
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismNoticeBanner
import com.union.campus.ui.prism.PrismNoticeTone
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.PrismStatus
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.caption1Style
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.prismShape
import com.union.campus.ui.prism.subheadlineStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 学习通作业提醒：未交清单按截止压力排序，倒计时分级配色沿用语义状态色。
 *
 * 页面只消费仓库缓存/同步结果；登录在独立页面完成，未绑定时这里只做引导。
 */
@Composable
fun HomeworkScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = ServiceLocator.xxtRepository
    val messenger = LocalPrismMessenger.current
    val scope = rememberCoroutineScope()

    var bound by remember { mutableStateOf(repo.isBound()) }
    var state by remember { mutableStateOf<UiState<XxtHomeworkSnapshot>>(UiState.Loading) }
    var syncing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }

    fun load(refresh: Boolean) {
        if (syncing) return
        if (!repo.isBound()) {
            bound = false
            state = UiState.Empty("还没有绑定学习通")
            return
        }
        bound = true
        syncing = true
        progress = if (refresh) "正在同步作业…" else ""
        scope.launch {
            try {
                val snapshot = repo.snapshot(refresh)
                state = if (snapshot.items.isEmpty()) {
                    UiState.Empty("课程里还没有作业")
                } else {
                    UiState.Ready(snapshot, snapshot.syncedAt)
                }
                if (refresh) {
                    val failed = snapshot.errors.size
                    if (failed == 0) messenger.success("作业已同步")
                    else messenger.info("同步完成，${failed} 门课程未拉到")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError.AuthExpired) {
                bound = repo.isBound()
                state = UiState.Failed(e.userMessage, isAuth = true)
            } catch (e: AppError) {
                state = UiState.Failed(e.userMessage)
            } catch (e: Exception) {
                state = UiState.Failed("同步失败，请稍后重试")
            } finally {
                syncing = false
                progress = ""
            }
        }
    }

    // 从登录页返回时重新读绑定状态（首次进入同样由 ON_RESUME 触发）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { load(refresh = false) }

    val snapshot = (state as? UiState.Ready)?.data
    val pending = snapshot?.pending.orEmpty()
    val nearest = pending.filter { it.deadlineAt > 0 }.minByOrNull { it.deadlineAt }

    UnionScreenScaffold(
        title = "学习通作业",
        onBack = onBack,
        subtitle = when {
            !bound -> "未绑定账号"
            pending.isNotEmpty() -> "未交 ${pending.size} 项" +
                (nearest?.let { " · 最近 ${XxtHomeworkRules.countdownText(it.deadlineAt)}" }.orEmpty())
            snapshot != null -> "暂无未交作业"
            else -> null
        },
        actions = {
            if (bound) {
                Box {
                    PrismIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "同步作业",
                        enabled = !syncing,
                        onClick = { load(refresh = true) },
                    )
                    if (syncing) {
                        com.union.campus.ui.prism.PrismLoadingIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            size = 18.dp,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        when {
            !bound -> BindHomeworkCard(
                onLogin = onLogin,
                modifier = Modifier.padding(padding),
            )
            state is UiState.Loading -> PrismSkeletonList(
                rows = 5,
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            )
            state is UiState.Failed -> {
                val failed = state as UiState.Failed
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrismErrorState(
                        message = failed.message,
                        hint = if (failed.isAuth) "登录状态可能已失效，重新登录后即可继续同步。" else "网络或学习通服务异常时会这样。",
                        onRetry = { load(refresh = true) },
                    )
                    if (failed.isAuth) {
                        PrismButton(onClick = onLogin) { Text("重新登录学习通") }
                    }
                }
            }
            else -> HomeworkReadyContent(
                snapshot = snapshot ?: XxtHomeworkSnapshot(),
                showAll = showAll,
                syncing = syncing,
                progress = progress,
                onShowAllChange = { showAll = it },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** 未绑定：一句话说清这个入口做什么、不做什么，再给一个动作。 */
@Composable
private fun BindHomeworkCard(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .pageWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrismCard(
            modifier = Modifier.fillMaxWidth(),
            corner = PrismCorner.Xxl,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentPadding = PaddingValues(20.dp),
        ) {
            Text(
                "未交作业，一眼看完",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "登录学习通后，这里按截止时间排列未交作业；今天页也会出现对应待办，截止前 24 / 6 / 1 小时各提醒一次。",
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            PrismButton(
                onClick = onLogin,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("绑定学习通账号")
            }
        }

        PrismCard {
            CardHeader("这个功能做什么")
            Spacer(Modifier.height(8.dp))
            listOf(
                "读取课程、作业列表与必要的作业详情",
                "按截止时间分档提醒，同一条作业每档只提醒一次",
                "免打扰时段内只发 1 小时档的加急提醒",
                "账号密码与本机会话在设备内加密保存",
            ).forEach { line ->
                Row(
                    Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = PrismStatus.success(),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(line, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "不会做：提交作业、答题、刷课、修改任何学习通数据。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 顶部概览：未交数量 + 最紧急的一条，压力最大时换成 error 语义色。 */
@Composable
private fun HomeworkHero(snapshot: XxtHomeworkSnapshot, syncing: Boolean, progress: String) {
    val pending = snapshot.pending
    val nearest = pending.filter { it.deadlineAt > 0 }.minByOrNull { it.deadlineAt }
    val remain = nearest?.let { XxtHomeworkRules.remainHours(it.deadlineAt) }
    val urgent = remain != null && remain <= 6
    PrismCard(
        modifier = Modifier.fillMaxWidth(),
        corner = PrismCorner.Xxl,
        containerColor = if (urgent) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(20.dp),
    ) {
        val content = if (urgent) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (pending.isEmpty()) "作业都交了" else "未交 ${pending.size} 项",
                    style = MaterialTheme.typography.titleLarge,
                    color = content,
                )
                if (nearest != null) {
                    Text(
                        "${nearest.courseName} · ${nearest.title}",
                        style = subheadlineStyle(),
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (pending.isNotEmpty()) {
                    Text(
                        "截止时间还没公布",
                        style = subheadlineStyle(),
                        color = content,
                    )
                } else {
                    Text(
                        "没有待办作业 · ${formatUpdatedAt(snapshot.syncedAt)}",
                        style = subheadlineStyle(),
                        color = content,
                    )
                }
            }
            if (nearest != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        XxtHomeworkRules.countdownText(nearest.deadlineAt),
                        style = headlineStyle(),
                        color = content,
                    )
                    Text(
                        "截止 ${XxtHomeworkRules.formatDeadline(nearest.deadlineAt)}",
                        style = footnoteStyle(),
                        color = content,
                    )
                }
            }
        }
        if (syncing && progress.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(progress, style = footnoteStyle(), color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeworkEmpty(snapshot: XxtHomeworkSnapshot, showAll: Boolean) {
    PrismCard {
        Text(
            when {
                snapshot.items.isEmpty() -> "课程里还没有作业"
                !showAll -> "全部作业都交了"
                else -> "这里还没有作业"
            },
            style = headlineStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                snapshot.items.isEmpty() -> "同步一次看看，或者确认学习通里是否已加入课程。"
                !showAll -> "切到“全部”可以回看已交作业。"
                else -> "换个筛选条件试试。"
            },
            style = footnoteStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 分组：按截止压力分桶，桶名本身就是信息，不做装饰性编号。 */
internal data class HomeworkGroup(val title: String, val items: List<XxtHomework>)

internal fun homeworkGroups(items: List<XxtHomework>, now: Long = System.currentTimeMillis()): List<HomeworkGroup> {
    val buckets = linkedMapOf(
        "24 小时内" to mutableListOf<XxtHomework>(),
        "3 天内" to mutableListOf<XxtHomework>(),
        "更晚" to mutableListOf<XxtHomework>(),
        "截止未知" to mutableListOf<XxtHomework>(),
        "已过期" to mutableListOf<XxtHomework>(),
        "已交" to mutableListOf<XxtHomework>(),
    )
    items.forEach { item ->
        val remain = XxtHomeworkRules.remainHours(item.deadlineAt, now)
        val key = when {
            item.done -> "已交"
            item.deadlineAt <= 0L -> "截止未知"
            remain != null && remain <= 0 -> "已过期"
            remain != null && remain <= 24 -> "24 小时内"
            remain != null && remain <= 72 -> "3 天内"
            else -> "更晚"
        }
        buckets.getValue(key) += item
    }
    return buckets.mapNotNull { (title, list) ->
        if (list.isEmpty()) null else HomeworkGroup(
            title = "$title · ${list.size}",
            items = list.sortedBy { if (it.deadlineAt == 0L) Long.MAX_VALUE else it.deadlineAt },
        )
    }
}

@Composable
private fun HomeworkRow(item: XxtHomework) {
    // 倒计时在徽标与顶部概览里已经各有一份，行尾只保留“什么时候截止”，
    // 副标题只留课程名，避免 “高等数学 · 截止 11-2…” 被 ellipsis 截断。
    val remain = XxtHomeworkRules.remainHours(item.deadlineAt)
    PrismRow(
        title = item.title,
        subtitle = when {
            item.done && item.status.isNotBlank() -> "${item.courseName} · ${item.status}"
            item.courseName.isBlank() -> "截止时间未公布"
            else -> item.courseName
        },
        leading = { DeadlineBadge(item) },
        trailing = {
            Text(
                text = when {
                    item.done -> "已交"
                    item.deadlineAt <= 0L -> "待公布"
                    else -> XxtHomeworkRules.formatDeadline(item.deadlineAt)
                },
                style = footnoteStyle(),
                color = when {
                    item.done || item.deadlineAt <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                    (remain ?: 999.0) <= 6 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
            )
        },
        subtitleMaxLines = 1,
    )
}

/** 倒计时徽标：形状固定 44dp，颜色只表达"还有多久"，与文字倒计时同一套语义。 */
@Composable
private fun DeadlineBadge(item: XxtHomework) {
    val remain = XxtHomeworkRules.remainHours(item.deadlineAt)
    val container = when {
        item.done -> MaterialTheme.colorScheme.surfaceContainerHighest
        item.deadlineAt <= 0L -> MaterialTheme.colorScheme.surfaceContainerHighest
        remain != null && remain <= 6 -> MaterialTheme.colorScheme.errorContainer
        remain != null && remain <= 24 -> PrismStatus.warningContainer()
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when {
        item.done -> MaterialTheme.colorScheme.onSurfaceVariant
        item.deadlineAt <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        remain != null && remain <= 6 -> MaterialTheme.colorScheme.onErrorContainer
        remain != null && remain <= 24 -> PrismStatus.onWarningContainer()
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val label = when {
        item.done -> "已交"
        item.deadlineAt <= 0L -> "?"
        remain == null -> "?"
        remain <= 0 -> "过期"
        remain < 1 -> "${(remain * 60).toInt()}分"
        remain < 24 -> "${remain.toInt()}h"
        else -> "${(remain / 24).toInt()}天"
    }
    Surface(
        shape = prismShape(12f),
        color = container,
        contentColor = content,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = caption2Style().copy(fontWeight = FontWeight.Bold), color = content, maxLines = 1)
        }
    }
}

/**
 * 内容就绪态：概览卡 + 筛选 + 按截止压力分组。
 * 抽成独立组合函数，设备端测试可以直接渲染，不依赖仓库与网络。
 */
@Composable
internal fun HomeworkReadyContent(
    snapshot: XxtHomeworkSnapshot,
    showAll: Boolean,
    syncing: Boolean,
    progress: String,
    onShowAllChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = if (showAll) snapshot.items else snapshot.pending
    LazyColumn(
        modifier = modifier.fillMaxSize().pageWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HomeworkHero(snapshot, syncing, progress) }
        item {
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrismChip(
                    label = "未交 ${snapshot.pendingCount}",
                    selected = !showAll,
                    onClick = { onShowAllChange(false) },
                )
                PrismChip(
                    label = "全部 ${snapshot.items.size}",
                    selected = showAll,
                    onClick = { onShowAllChange(true) },
                )
            }
        }
        if (snapshot.errors.isNotEmpty()) {
            item {
                PrismNoticeBanner(
                    title = "有 ${snapshot.errors.size} 门课程没同步成功",
                    message = snapshot.errors.take(2).joinToString("；"),
                    tone = PrismNoticeTone.Warning,
                )
            }
        }
        if (visible.isEmpty()) {
            item { HomeworkEmpty(snapshot, showAll) }
        } else {
            homeworkGroups(visible).forEach { group ->
                item(key = group.title) {
                    PrismSection(title = group.title) {
                        PrismGroupedList {
                            group.items.forEach { item -> HomeworkRow(item) }
                        }
                    }
                }
            }
        }
        if (snapshot.notices.isNotEmpty()) {
            item {
                PrismSection(title = "学习通通知 · ${snapshot.unreadNotices.size} 条未读") {
                    PrismGroupedList {
                        snapshot.notices.take(5).forEach { notice -> NoticeRow(notice) }
                    }
                }
            }
        }
        item {
            Text(
                "只读同步：不会提交作业、答题或刷课。截止时间以学习通为准，后台每 2 小时检查一次，系统省电时可能顺延。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                "上次同步 ${formatUpdatedAt(snapshot.syncedAt)} · 课程 ${snapshot.courseCount} 门",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 通知行：未读标出来，正文压成一行副标题；全文仍以学习通为准。 */
@Composable
private fun NoticeRow(notice: XxtNotice) {
    PrismRow(
        title = notice.title,
        subtitle = listOfNotNull(
            notice.sender.takeIf { it.isNotBlank() },
            formatNoticeTime(notice.publishTime).takeIf { it.isNotBlank() },
            notice.content.take(48).takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        leading = { IconBadge(Icons.Rounded.Notifications) },
        trailing = if (notice.read) null else {
            {
                Text(
                    "未读",
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        subtitleMaxLines = 2,
    )
}

/** completeTime 是毫秒时间戳；解析不了就原样留空，不猜时间。 */
private fun formatNoticeTime(raw: String): String {
    val millis = raw.toLongOrNull() ?: return ""
    return java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
