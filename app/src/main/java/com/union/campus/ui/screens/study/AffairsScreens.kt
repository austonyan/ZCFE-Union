package com.union.campus.ui.screens.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.JwNotice
import com.union.campus.domain.StudentRecord
import com.union.campus.domain.TextbookItem
import com.union.campus.ui.common.UiState
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.common.dataOrNull
import com.union.campus.ui.common.launchUi
import com.union.campus.ui.common.runListUi
import com.union.campus.ui.prism.IconBadge
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlertDialog
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismLoadingBlock
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.subheadlineStyle

// ---------------- 教材 ----------------

/** 教材：待确认教材、教材对账与确认领取。 */
@Composable
fun TextbooksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var state by remember { mutableStateOf<UiState<List<TextbookItem>>>(UiState.Loading) }
    var confirmTarget by remember { mutableStateOf<TextbookItem?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun load() {
        state = UiState.Loading
        launchUi {
            state = if (!jw.isBound()) UiState.Empty("登录教务后可查看教材信息")
            else runListUi { jw.textbooks() }
        }
    }
    LaunchedEffect(Unit) { load() }

    if (confirmTarget != null || confirmAll) {
        PrismAlertDialog(
            title = "确认领取",
            body = if (confirmAll) "确认领取本学期全部待确认教材？提交后以教务结果为准。"
            else "确认领取《${confirmTarget?.bookName}》？提交后以教务结果为准。",
            confirmText = "确认",
            onConfirm = {
                val target = confirmTarget
                launchUi {
                    try {
                        if (target != null) {
                            jw.confirmTextbook("", target.id)
                        }
                        result = "已提交，结果以教务为准"
                        load()
                    } catch (e: AppError) {
                        result = e.userMessage
                    }
                    confirmTarget = null; confirmAll = false
                }
            },
            onDismiss = { confirmTarget = null; confirmAll = false },
        )
    }
    if (result != null) {
        PrismAlertDialog(
            title = "结果", body = result ?: "", confirmText = "知道了", dismissText = null,
            onConfirm = { result = null }, onDismiss = { result = null },
        )
    }

    UnionScreenScaffold(title = "教材", onBack = onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val items = state.dataOrNull()
            if (items != null) {
                val total = items.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
                PrismGroupedList {
                    items.forEachIndexed { i, t ->
                        PrismRow(
                            title = t.courseName,
                            subtitle = if (t.confirmed) "已确认 · ${t.count} 本 · ¥ ${t.price.ifBlank { "待结算" }}"
                            else "《${t.bookName}》 · ${t.count} 本 · ¥ ${t.price.ifBlank { "待结算" }}",
                            index = i, count = items.size,
                            leading = {
                                IconBadge(
                                    if (t.confirmed) Icons.Rounded.CheckCircle else Icons.Rounded.MenuBook,
                                )
                            },
                            onClick = { if (!t.confirmed) confirmTarget = t },
                        )
                    }
                }
                PrismCard {
                    CardHeader("教材对账")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (total > 0) "本学期合计 ¥ %.2f".format(total) else "本学期合计待结算",
                        style = subheadlineStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val pendingCount = items.count { !it.confirmed }
                if (pendingCount > 0) {
                    PrismButton(
                        onClick = { confirmAll = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("确认领取（$pendingCount 门待确认）")
                    }
                }
            } else {
                StateBlock(state, Icons.Rounded.MenuBook) { load() }
            }
            Text("金额为空时显示待结算", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 教务事务 ----------------

/** 教务事务：学籍、异动、免修、选课、教材、教室预约、问卷、通知的集中入口。 */
@Composable
fun AcademicAffairsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit, // "record" / "select" / "textbooks" / "evaluation" / "notices" / "freeClassrooms"
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "教务事务",
        onBack = onBack,
        actions = {
            IconButton(onClick = { }) { Icon(Icons.Rounded.Search, contentDescription = "搜索") }
        },
        modifier = modifier,
    ) { padding ->
        val entries = listOf(
            "学籍档案" to ("姓名、院系、专业与学籍状态" to Icons.Rounded.Badge),
            "学籍异动" to ("申请、取消与进度（以教务为准）" to Icons.Rounded.SwapHoriz),
            "免修申请" to ("工作流列表与材料" to Icons.Rounded.Assignment),
            "选课" to ("选课轮次、可选课程与提交" to Icons.Rounded.Event),
            "教材确认" to ("待确认教材与教材对账" to Icons.Rounded.MenuBook),
            "教室预约" to ("查询可预约教室与我的预约" to Icons.Rounded.MeetingRoom),
            "问卷与评价" to ("待填问卷、班主任评价" to Icons.Rounded.RateReview),
            "教务通知" to ("新闻、公告、调课/停课" to Icons.Rounded.Campaign),
        )
        val links = listOf("record", "change", "exemption", "select", "textbooks", "freeClassrooms", "evaluation", "notices")
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("井井有条的管理教务入口", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrismGroupedList {
                entries.forEachIndexed { i, (title, pair) ->
                    val (subtitle, icon) = pair
                    PrismRow(
                        title = title, subtitle = subtitle,
                        index = i, count = entries.size,
                        leading = { IconBadge(icon) },
                        onClick = { onOpen(links[i]) },
                    )
                }
            }
            Text("更多功能按学校开放情况显示", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 学籍档案 ----------------

/** 学籍档案：学号、院系、专业、学籍状态与脱敏身份证。 */
@Composable
fun StudentRecordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var state by remember { mutableStateOf<UiState<StudentRecord>>(UiState.Loading) }
    var familyInfo by remember { mutableStateOf<String?>(null) }

    fun load() {
        state = UiState.Loading
        launchUi {
            state = if (!jw.isBound()) UiState.Empty("登录教务后可查看学籍档案")
            else try {
                UiState.Ready(jw.studentRecord())
            } catch (e: AppError) {
                UiState.Failed(e.userMessage)
            } catch (e: Exception) {
                UiState.Failed("学籍信息获取失败：${e.message}")
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    if (familyInfo != null) {
        PrismAlertDialog(
            title = "家庭信息",
            body = familyInfo ?: "",
            confirmText = "知道了", dismissText = null,
            onConfirm = { familyInfo = null }, onDismiss = { familyInfo = null },
        )
    }

    UnionScreenScaffold(title = "学籍档案", onBack = onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is UiState.Ready -> {
                    val r = s.data
                    PrismCard {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.name.ifBlank { "—" }, style = pageTitleStyle())
                            Text("${r.grade} 级 · ${r.major} · ${r.className}", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PrismGroupedList {
                        PrismRow(title = "学号", subtitle = r.studentNo.ifBlank { "—" }, index = 0, count = 5, leading = { IconBadge(Icons.Rounded.Tag) })
                        PrismRow(title = "院系", subtitle = r.college.ifBlank { "—" }, index = 1, count = 5, leading = { IconBadge(Icons.Rounded.AccountBalance) })
                        PrismRow(title = "专业", subtitle = r.major.ifBlank { "—" }, index = 2, count = 5, leading = { IconBadge(Icons.Rounded.School) })
                        PrismRow(title = "学籍状态", subtitle = r.status, index = 3, count = 5, leading = { IconBadge(Icons.Rounded.CheckCircle) })
                        PrismRow(title = "身份证", subtitle = r.idCardMasked.ifBlank { "已脱敏 · 仅本人可见" }, index = 4, count = 5, leading = { IconBadge(Icons.Rounded.Lock) })
                    }
                    PrismButton(
                        filled = false,
                        onClick = { launchUi { familyInfo = "家庭信息来自教务系统 getZgjbxx；当前未开放编辑，仅查看。" } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.FamilyRestroom, null)
                        Spacer(Modifier.width(8.dp))
                        Text("查看家庭信息")
                    }
                }
                else -> StateBlock(state, Icons.Rounded.Badge) { load() }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 教务通知 ----------------

/** 教务通知：新闻、公告、调课与评教截止提醒。 */
@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    onOpenNotice: (JwNotice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    var state by remember { mutableStateOf<UiState<List<JwNotice>>>(UiState.Loading) }

    fun load() {
        state = UiState.Loading
        launchUi {
            state = if (!jw.isBound()) UiState.Empty("登录教务后可查看通知")
            else runListUi { jw.notices() }
        }
    }
    LaunchedEffect(Unit) { load() }

    UnionScreenScaffold(title = "教务通知", onBack = onBack, modifier = modifier) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is UiState.Ready -> {
                    PrismGroupedList {
                        s.data.forEachIndexed { i, n ->
                            PrismRow(
                                title = n.title,
                                subtitle = "${n.date} · ${n.source}",
                                index = i, count = s.data.size,
                                leading = {
                                    IconBadge(
                                        when (n.kind) {
                                            "change" -> Icons.Rounded.SwapHoriz
                                            "news" -> Icons.Rounded.Campaign
                                            else -> Icons.Rounded.Assignment
                                        },
                                    )
                                },
                                onClick = { onOpenNotice(n) },
                            )
                        }
                    }
                    Text("已显示最近 ${s.data.size} 条", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> StateBlock(state, Icons.Rounded.Campaign) { load() }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 通知详情：正文与相关考试入口。 */
@Composable
fun NoticeDetailScreen(
    notice: JwNotice,
    onBack: () -> Unit,
    onOpenExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UnionScreenScaffold(
        title = "通知详情",
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
            Text(notice.title, style = pageTitleStyle())
            Text("${notice.date} · ${notice.source}", style = footnoteStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                notice.body.ifBlank { "正文请以教务系统发布为准。本页展示通知标题与来源，完整正文可在教务网页查看。" },
                style = subheadlineStyle(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (notice.title.contains("考试")) {
                PrismCard {
                    CardHeader("相关考试")
                    Spacer(Modifier.height(6.dp))
                    Text("期末考试安排已发布，点击查看考试安排与倒计时", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PrismButton(onClick = onOpenExams, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Event, null)
                    Spacer(Modifier.width(8.dp))
                    Text("查看考试安排")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------- 通用状态块 ----------------

@Composable
fun <T> StateBlock(
    state: UiState<T>,
    icon: ImageVector,
    onRetry: () -> Unit,
) {
    when (state) {
        is UiState.Loading -> PrismLoadingBlock(label = "正在加载")
        is UiState.Failed -> PrismErrorState(
            message = state.message,
            onRetry = onRetry,
        )
        is UiState.Empty -> NullStatus(icon = icon, title = "暂无数据", subtitle = state.hint)
        is UiState.Ready -> {}
    }
}
