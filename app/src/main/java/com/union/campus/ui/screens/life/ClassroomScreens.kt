package com.union.campus.ui.screens.life

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.FreeClassroom
import com.union.campus.domain.SectionTime
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TeachingBuilding
import com.union.campus.domain.TimetableData
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.CardHeader
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismAlpha
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismCheckMark
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSection
import com.union.campus.ui.prism.PrismShape
import com.union.campus.ui.prism.PrismSkeleton
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.prism.PrismSpace
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.SectionHeader
import com.union.campus.ui.prism.SurfaceLevel
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.caption1Style
import com.union.campus.ui.prism.caption2Style
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle
import com.union.campus.ui.prism.prismShape
import com.union.campus.ui.prism.rememberPrismHaptics
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.withAlpha
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 空闲教室：日期/节次/楼栋筛选、按节次方块与跨楼栋查询。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeClassroomsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jw = ServiceLocator.jwRepository
    val entities = ServiceLocator.entities
    val settings = ServiceLocator.settings
    val messenger = LocalPrismMessenger.current

    val today = remember { LocalDate.now() }
    val todayIso = remember(today) { today.toString() }

    // 三个筛选条件都可单独使用；空值即“不限”。用 rememberSaveable 保证从详情返回后不重置。
    var dateIso by rememberSaveable { mutableStateOf(todayIso) }
    var sectionFilters by rememberSaveable(stateSaver = SectionFilterSaver) {
        mutableStateOf(emptyList<Int>())
    }
    var buildingId by rememberSaveable { mutableStateOf("") }

    var buildings by remember { mutableStateOf<List<TeachingBuilding>>(emptyList()) }
    var buildingsLoading by remember { mutableStateOf(true) }
    var buildingsError by remember { mutableStateOf<String?>(null) }
    var buildingReloadTick by remember { mutableIntStateOf(0) }

    // 查询结果按 (楼栋, 日期) 缓存在页面内，切回来不重复请求；状态 Map 可被 Compose 观察。
    val results = remember { mutableStateMapOf<Pair<String, String>, List<FreeClassroom>>() }
    val failures = remember { mutableStateMapOf<Pair<String, String>, String>() }
    var loading by remember { mutableStateOf(false) }
    var roomReloadTick by remember { mutableIntStateOf(0) }

    var mySections by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var sectionTimes by remember { mutableStateOf<List<SectionTime>>(emptyList()) }
    var showAllFreeDetails by rememberSaveable { mutableStateOf(false) }

    var sectionSheetOpen by remember { mutableStateOf(false) }
    var buildingSheetOpen by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    val selectedDate = remember(dateIso) {
        runCatching { LocalDate.parse(dateIso) }.getOrDefault(today)
    }
    val dayOffset = (selectedDate.toEpochDay() - today.toEpochDay()).toInt()

    // 进程重建后保存的日期可能已经超出「今天起 30 天」的范围。
    LaunchedEffect(Unit) {
        if (dayOffset < 0 || dayOffset > DateRangeDays) dateIso = todayIso
    }

    LaunchedEffect(buildingReloadTick) {
        buildingsLoading = true
        buildingsError = null
        buildings = if (jw.isBound()) {
            try {
                jw.teachingBuildings()
            } catch (e: AppError) {
                buildingsError = e.userMessage
                emptyList()
            } catch (e: Exception) {
                buildingsError = "教学楼列表获取失败：${e.message}"
                emptyList()
            }
        } else {
            emptyList()
        }
        if (jw.isBound() && buildings.isEmpty() && buildingsError == null) {
            buildingsError = "未获取到教学楼列表，请稍后重试"
        }
        buildingsLoading = false
    }

    // 楼栋列表刷新后，旧的选择可能已不存在，回落到“全部楼栋”。
    LaunchedEffect(buildings) {
        if (buildings.isNotEmpty() &&
            buildingId.isNotBlank() &&
            buildings.none { it.dm == buildingId }
        ) {
            buildingId = ""
        }
    }

    LaunchedEffect(Unit) {
        if (jw.isBound()) {
            sectionTimes = runCatching { jw.sectionTimes() }.getOrDefault(emptyList())
        }
    }

    // 我的冲突：只读本地当周课表缓存；拿不到就不算冲突。
    LaunchedEffect(dateIso) {
        showAllFreeDetails = false
        mySections = emptySet()
        if (!jw.isBound()) return@LaunchedEffect
        val semesterDm = settings.currentSemesterDm.first().ifBlank { SemesterCalc.guessSemesterDm() }
        val firstWeekStart = settings.firstWeekStart.first()
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return@LaunchedEffect
        val dateMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val week = SemesterCalc.currentWeek(semesterDm, firstWeekStart, dateMs)
        val timetable = entities.read("jw.timetable.$semesterDm.w$week", TimetableData.serializer())?.data
            ?: return@LaunchedEffect
        val day = date.dayOfWeek.value
        mySections = timetable.days.firstOrNull { it.day == day }?.cells
            ?.flatMap { cell -> cell.startSection..cell.endSection }
            ?.toSet()
            .orEmpty()
    }

    // 全部楼栋时按 6 并发分批查询，单栋失败不阻塞整体。
    LaunchedEffect(dateIso, buildingId, buildings, roomReloadTick) {
        if (buildings.isEmpty()) return@LaunchedEffect
        val targets = if (buildingId.isBlank()) buildings else buildings.filter { it.dm == buildingId }
        val pending = targets.filter { (it.dm to dateIso) !in results && (it.dm to dateIso) !in failures }
        if (pending.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val semaphore = Semaphore(QueryConcurrency)
        coroutineScope {
            pending.forEach { building ->
                launch {
                    semaphore.withPermit {
                        try {
                            results[building.dm to dateIso] = jw.freeClassrooms(dateIso, building.dm)
                            failures.remove(building.dm to dateIso)
                        } catch (e: AppError) {
                            failures[building.dm to dateIso] = e.userMessage
                        } catch (e: Exception) {
                            failures[building.dm to dateIso] = "教室查询失败：${e.message}"
                        }
                    }
                }
            }
        }
        loading = false
    }

    val retryFailed: () -> Unit = {
        failures.keys.toList().filter { it.second == dateIso }.forEach { failures.remove(it) }
        roomReloadTick++
    }

    val selectableDates = remember(today) {
        val start = today.toUtcMillis()
        val end = today.plusDays(DateRangeDays.toLong()).toUtcMillis()
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in start..end

            override fun isSelectableYear(year: Int): Boolean =
                year in today.year..today.plusDays(DateRangeDays.toLong()).year
        }
    }

    UnionScreenScaffold(
        title = "空闲教室",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 筛选栏：日期 / 节次 / 楼栋，一行可横滑。
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DateFilterControl(
                    label = dateChipLabel(selectedDate, today),
                    canGoBack = dayOffset > 0,
                    canGoForward = dayOffset < DateRangeDays,
                    onBack = { dateIso = selectedDate.minusDays(1).toString() },
                    onForward = { dateIso = selectedDate.plusDays(1).toString() },
                    onClick = { datePickerOpen = true },
                )
                FilterPill(
                    label = sectionFilters.takeIf { it.isNotEmpty() }
                        ?.let { sectionLabel(it) }
                        ?: "节次",
                    selected = sectionFilters.isNotEmpty(),
                    onClick = { sectionSheetOpen = true },
                    onClear = if (sectionFilters.isNotEmpty()) {
                        { sectionFilters = emptyList() }
                    } else {
                        null
                    },
                )
                FilterPill(
                    label = buildings.firstOrNull { it.dm == buildingId }?.name ?: "全部楼栋",
                    selected = buildingId.isNotBlank(),
                    onClick = { buildingSheetOpen = true },
                    onClear = if (buildingId.isNotBlank()) {
                        { buildingId = "" }
                    } else {
                        null
                    },
                )
                if (dayOffset > 0) {
                    PrismTextButton(onClick = { dateIso = todayIso }) { Text("回到今天") }
                }
            }

            val hasFilter = sectionFilters.isNotEmpty() ||
                buildingId.isNotBlank() ||
                dateIso != todayIso

            AnimatedContent(
                targetState = dateIso,
                transitionSpec = {
                    fadeIn(PrismMotion.tween(210, delayMillis = 90)) togetherWith
                        fadeOut(PrismMotion.tween(90))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "classroom-date-content",
            ) { shownDate ->
                val shownDateObj = remember(shownDate) {
                    runCatching { LocalDate.parse(shownDate) }.getOrDefault(today)
                }
                val shownDayLabel = "周" + WeekdayLabels[shownDateObj.dayOfWeek.value - 1]
                val shownGroups = buildings
                    .mapNotNull { building ->
                        results[building.dm to shownDate]?.let { building to it }
                    }
                    .filter { buildingId.isBlank() || it.first.dm == buildingId }
                val shownRooms = shownGroups.flatMap { it.second }
                val shownFailures = failures.filterKeys { (dm, date) ->
                    date == shownDate && (buildingId.isBlank() || dm == buildingId)
                }
                val targetBuildings = if (buildingId.isBlank()) {
                    buildings
                } else {
                    buildings.filter { it.dm == buildingId }
                }
                val awaiting = targetBuildings.any { building ->
                    (building.dm to shownDate) !in results && (building.dm to shownDate) !in failures
                }
                val finishedCount = targetBuildings.count { building ->
                    (building.dm to shownDate) in results || (building.dm to shownDate) in failures
                }
                val matchingGroups = shownGroups.map { (building, rooms) ->
                    building to rooms.filter { room ->
                        sectionFilters.isEmpty() || sectionFilters.all { section ->
                            room.sections.getOrNull(section - 1) == true
                        }
                    }
                }.filter { it.second.isNotEmpty() }
                val matchingRooms = matchingGroups.flatMap { it.second }
                val filterLabel = sectionLabel(sectionFilters)
                val singleFailure = if (buildingId.isNotBlank()) {
                    shownFailures[buildingId to shownDate]
                } else {
                    null
                }
                val showAllFree = sectionFilters.isEmpty() &&
                    !showAllFreeDetails &&
                    !awaiting &&
                    matchingRooms.isNotEmpty() &&
                    matchingRooms.all { room ->
                        room.sections.isNotEmpty() && room.sections.all { it == true }
                    }

                Column(Modifier.fillMaxSize()) {
                    if (buildingId.isBlank() && buildings.isNotEmpty() && awaiting && loading) {
                        Text(
                            "已查 $finishedCount/${targetBuildings.size} 栋",
                            style = caption2Style(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                    if (hasFilter && matchingRooms.isNotEmpty()) {
                        Text(
                            "${matchingRooms.size} 间教室符合 · ${matchingGroups.size} 栋楼",
                            style = caption2Style(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                    if (sectionFilters.isEmpty() && matchingRooms.isNotEmpty()) {
                        val hasFree = matchingRooms.any { room -> room.sections.any { it == true } }
                        val hasOccupied = matchingRooms.any { room -> room.sections.any { it == false } }
                        val hasMine = mySections.isNotEmpty() && matchingRooms.any { room ->
                            mySections.any { section -> room.sections.getOrNull(section - 1) == true }
                        }
                        if (hasFree || hasOccupied || hasMine) {
                            ClassroomLegend(
                                showFree = hasFree,
                                showOccupied = hasOccupied,
                                showMine = hasMine,
                            )
                        }
                    }

                    when {
                        !jw.isBound() -> NullStatus(
                            icon = Icons.Rounded.MeetingRoom,
                            title = "登录教务后查询空闲教室",
                            subtitle = "在「我的 → 凭证中心」绑定教务账号",
                        )

                        buildingsError != null -> PrismErrorState(
                            message = buildingsError ?: "",
                            hint = "教学楼列表来自教务系统",
                            onRetry = { buildingReloadTick++ },
                        )

                        buildingsLoading || (awaiting && shownRooms.isEmpty()) -> ClassroomSkeleton()

                        singleFailure != null && shownRooms.isEmpty() -> PrismErrorState(
                            message = singleFailure,
                            onRetry = retryFailed,
                        )

                        shownRooms.isEmpty() && shownFailures.isNotEmpty() -> PrismErrorState(
                            message = shownFailures.values.first(),
                            hint = "部分楼栋暂时不可达，可稍后重试",
                            onRetry = retryFailed,
                        )

                        matchingRooms.isEmpty() -> if (shownRooms.isNotEmpty()) {
                            FilteredEmptyState(
                                onClear = {
                                    sectionFilters = emptyList()
                                    buildingId = ""
                                },
                            )
                        } else {
                            NoClassroomsState(
                                allBuildings = buildingId.isBlank(),
                                failureCount = shownFailures.size,
                                onRetry = retryFailed,
                            )
                        }

                        showAllFree -> AllFreeCard(
                            roomCount = matchingRooms.size,
                            allBuildings = buildingId.isBlank(),
                            onShowDetails = { showAllFreeDetails = true },
                        )

                        else -> {
                            // 有节次筛选时列表不画方块，表头也就没有对齐对象了。
                            if (sectionFilters.isEmpty()) SectionNumbersHeader()
                            ClassroomList(
                                groups = matchingGroups,
                                singleBuilding = buildingId.isNotBlank(),
                                filtered = sectionFilters.isNotEmpty(),
                                filterLabel = filterLabel,
                                mySections = mySections,
                                failureCount = shownFailures.size,
                                onRetryFailures = retryFailed,
                                onCellClick = { room, section ->
                                    val time = sectionTimes.firstOrNull { it.section == section }
                                    val parts = buildList {
                                        add("${shownDayLabel}第 $section 节")
                                        if (time != null && time.start.isNotBlank() && time.end.isNotBlank()) {
                                            add("${time.start}–${time.end}")
                                        }
                                        seatText(room).takeIf { it.isNotBlank() }?.let { add(it) }
                                    }
                                    messenger.info(parts.joinToString(" · "))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    SectionFilterSheet(
        visible = sectionSheetOpen,
        selected = sectionFilters,
        onApply = { sectionFilters = it },
        onDismiss = { sectionSheetOpen = false },
    )

    BuildingFilterSheet(
        visible = buildingSheetOpen,
        buildings = buildings,
        selectedId = buildingId,
        onSelect = { buildingId = it },
        onDismiss = { buildingSheetOpen = false },
    )

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toUtcMillis(),
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                PrismTextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            dateIso = utcMillisToLocalDate(millis).toString()
                        }
                        datePickerOpen = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                PrismTextButton(onClick = { datePickerOpen = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

// ---------------- 筛选栏 ----------------

private const val DateRangeDays = 30
private const val QueryConcurrency = 6

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private val SectionPresets = listOf(
    "上午" to (1..4).toSet(),
    "下午" to (5..8).toSet(),
    "晚上" to (9..10).toSet(),
    "全天" to (1..10).toSet(),
)

/** 节次筛选跨页面返回保存。 */
private val SectionFilterSaver = listSaver<List<Int>, Int>(
    save = { it },
    restore = { it },
)

private fun sectionLabel(sections: List<Int>): String =
    if (sections.isEmpty()) "" else "第 ${sections.sorted().joinToString("·")} 节"

private fun dateChipLabel(date: LocalDate, today: LocalDate): String {
    val weekday = "周" + WeekdayLabels[date.dayOfWeek.value - 1]
    val monthDay = "%02d/%02d".format(date.monthValue, date.dayOfMonth)
    return if (date == today) "今天 · $weekday $monthDay" else "$weekday $monthDay"
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun DateFilterControl(
    label: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = remember { PrismShape(Float.MAX_VALUE) }
    Surface(
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = shape,
        color = surfaceColorOf(SurfaceLevel.Inset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateArrow(Icons.Rounded.ChevronLeft, "前一天", canGoBack, onBack)
            Text(
                label,
                style = caption1Style(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(shape)
                    .clickable(role = Role.Button, onClickLabel = "选择日期", onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
            DateArrow(Icons.Rounded.ChevronRight, "后一天", canGoForward, onForward)
        }
    }
}

@Composable
private fun DateArrow(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberPrismHaptics()
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Disabled)
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 实心/描边筛选胶囊；选中态可带 ✕ 直接清空。 */
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    fillWidth: Boolean = false,
) {
    val haptics = rememberPrismHaptics()
    val shape = remember { PrismShape(Float.MAX_VALUE) }
    val container = if (selected) MaterialTheme.colorScheme.primary else surfaceColorOf(SurfaceLevel.Inset)
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = shape,
        color = container,
    ) {
        Row(
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = caption1Style(),
                color = content,
                maxLines = 1,
                textAlign = if (fillWidth) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .clip(shape)
                    .clickable(role = Role.Button) {
                        haptics.select()
                        onClick()
                    }
                    .padding(
                        start = 14.dp,
                        end = if (onClear != null) 6.dp else 14.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
            )
            if (onClear != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "清除筛选") {
                            haptics.tap()
                            onClear()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清除筛选",
                        tint = content,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ---------------- 筛选面板 ----------------

@Composable
private fun SectionFilterSheet(
    visible: Boolean,
    selected: List<Int>,
    onApply: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    PrismSnapSheet(visible = visible, onDismiss = onDismiss) {
        var draft by remember { mutableStateOf(selected.toSet()) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("选择节次", style = pageTitleStyle())
            Text(
                "选多个节次时，这些节必须同时空闲。",
                style = footnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionPresets.forEach { (label, sections) ->
                    FilterPill(
                        label = label,
                        selected = draft == sections,
                        onClick = { draft = if (draft == sections) emptySet() else sections },
                    )
                }
            }
            listOf(1..5, 6..10).forEach { rowSections ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowSections.forEach { section ->
                        Box(Modifier.weight(1f)) {
                            FilterPill(
                                label = section.toString(),
                                selected = section in draft,
                                onClick = {
                                    draft = if (section in draft) draft - section else draft + section
                                },
                                fillWidth = true,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PrismTextButton(onClick = { draft = emptySet() }) { Text("清空") }
                Spacer(Modifier.weight(1f))
                PrismButton(
                    onClick = {
                        onApply(draft.sorted())
                        onDismiss()
                    },
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
private fun BuildingFilterSheet(
    visible: Boolean,
    buildings: List<TeachingBuilding>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PrismSnapSheet(visible = visible, onDismiss = onDismiss) {
        val current = buildings.firstOrNull { it.dm == selectedId }
        val rest = buildings.filter { it.dm != selectedId }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("选择教学楼", style = pageTitleStyle())
            PrismGroupedList {
                PrismRow(
                    title = "全部楼栋",
                    subtitle = "跨楼栋查询，结果按楼栋分组",
                    onClick = {
                        onSelect("")
                        onDismiss()
                    },
                    trailing = if (selectedId.isBlank()) {
                        { PrismCheckMark(checked = true) }
                    } else {
                        null
                    },
                )
            }
            if (current != null) {
                PrismGroupedList {
                    PrismRow(
                        title = current.name,
                        subtitle = current.campus.takeIf { it.isNotBlank() },
                        onClick = {
                            onSelect(current.dm)
                            onDismiss()
                        },
                        trailing = { PrismCheckMark(checked = true) },
                    )
                }
            }
            rest.groupBy { it.campus.ifBlank { "其他校区" } }.forEach { (campus, list) ->
                SectionHeader(campus)
                PrismGroupedList {
                    list.forEach { building ->
                        PrismRow(
                            title = building.name,
                            onClick = {
                                onSelect(building.dm)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 列表与状态 ----------------

private const val ClassroomColumns = 10

/**
 * 节次方块配色与尺寸集中在这里：
 * 空闲 = 白底 + 描边、有课 = onSurface 28% 灰、我的冲突 = 主色、未知 = 描边空心。
 */
private object ClassroomCellStyle {
    val Size: Dp = 9.dp
    val Gap: Dp = 2.dp
    val Corner: Dp = 2.dp
    val BorderWidth: Dp = 1.dp
    const val OccupiedAlpha = 0.28f
}

private enum class ClassroomCellState { Free, Occupied, Mine, Unknown }

@Composable
private fun ClassroomLegend(
    showFree: Boolean,
    showOccupied: Boolean,
    showMine: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showFree) LegendItem(ClassroomCellState.Free, "空闲")
        if (showOccupied) LegendItem(ClassroomCellState.Occupied, "有课")
        if (showMine) LegendItem(ClassroomCellState.Mine, "我的冲突")
    }
}

@Composable
private fun LegendItem(state: ClassroomCellState, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ClassroomCell(state)
        Text(label, style = caption2Style(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 节次表头：1…10 与下方方块同宽、居中。 */
@Composable
private fun SectionNumbersHeader(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(ClassroomCellStyle.Gap)) {
            for (section in 1..ClassroomColumns) {
                Box(
                    Modifier.width(ClassroomCellStyle.Size),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        section.toString(),
                        style = caption2Style(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassroomList(
    groups: List<Pair<TeachingBuilding, List<FreeClassroom>>>,
    singleBuilding: Boolean,
    filtered: Boolean,
    filterLabel: String,
    mySections: Set<Int>,
    failureCount: Int,
    onRetryFailures: () -> Unit,
    onCellClick: (FreeClassroom, Int) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PrismSpace.Bottom),
    ) {
        groups.forEach { (building, rooms) ->
            if (!singleBuilding) {
                item(key = "building-${building.dm}") {
                    SectionHeader(building.name, Modifier.padding(horizontal = 16.dp))
                }
            }
            rooms.groupBy { it.type.ifBlank { "其他教室" } }.forEach { (type, list) ->
                item(key = "type-${building.dm}-$type") {
                    if (singleBuilding) {
                        SectionHeader(type, Modifier.padding(horizontal = 16.dp))
                    } else {
                        TypeHeader(type)
                    }
                }
                items(list.sortedBy { it.name }, key = { "${building.dm}-${it.name}" }) { room ->
                    ClassroomRow(
                        room = room,
                        filtered = filtered,
                        filterLabel = filterLabel,
                        mySections = mySections,
                        onCellClick = { section -> onCellClick(room, section) },
                    )
                }
            }
        }
        if (failureCount > 0) {
            item(key = "failures") {
                FailureRow(count = failureCount, onRetry = onRetryFailures)
            }
        }
    }
}

@Composable
private fun TypeHeader(title: String) {
    Text(
        title,
        style = subheadlineStyle().copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ClassroomRow(
    room: FreeClassroom,
    filtered: Boolean,
    filterLabel: String,
    mySections: Set<Int>,
    onCellClick: (Int) -> Unit,
) {
    if (filtered) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(prismShape(2f))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = bodyStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        seatText(room).takeIf { it.isNotBlank() },
                        "${filterLabel}可用",
                    ).joinToString(" · "),
                    style = caption2Style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = bodyStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    seatText(room),
                    style = caption2Style(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            SectionCells(
                sections = room.sections,
                mySections = mySections,
                onCellClick = onCellClick,
            )
        }
    }
}

private fun seatText(room: FreeClassroom): String =
    room.capacity.trim().takeIf { it.isNotBlank() }?.let { "$it 座" } ?: ""

/** 10 个节次方块；整条可点，按横坐标定位节次（9dp 单块不适合做触控目标）。 */
@Composable
private fun SectionCells(
    sections: List<Boolean?>,
    mySections: Set<Int>,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberPrismHaptics()
    val density = LocalDensity.current
    val stepPx = with(density) { (ClassroomCellStyle.Size + ClassroomCellStyle.Gap).toPx() }
    val currentClick by rememberUpdatedState(onCellClick)
    var pressedSection by remember { mutableIntStateOf(0) }
    val cellsWidth = ClassroomCellStyle.Size * ClassroomColumns +
        ClassroomCellStyle.Gap * (ClassroomColumns - 1)
    Box(
        modifier = modifier
            .width(cellsWidth)
            .height(40.dp)
            .pointerInput(ClassroomColumns) {
                detectTapGestures(
                    onPress = { offset ->
                        val section = (offset.x / stepPx).toInt() + 1
                        if (section in 1..ClassroomColumns) {
                            pressedSection = section
                            val released = tryAwaitRelease()
                            pressedSection = 0
                            if (released) {
                                haptics.tap()
                                currentClick(section)
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ClassroomCellStyle.Gap)) {
            for (section in 1..ClassroomColumns) {
                val free = sections.getOrNull(section - 1)
                val state = when {
                    free == null -> ClassroomCellState.Unknown
                    free && section in mySections -> ClassroomCellState.Mine
                    free -> ClassroomCellState.Free
                    else -> ClassroomCellState.Occupied
                }
                val scale by animateFloatAsState(
                    targetValue = if (pressedSection == section) 0.98f else 1f,
                    animationSpec = PrismMotion.springSnappy(),
                    label = "classroom-cell-press",
                )
                ClassroomCell(state, Modifier.scale(scale))
            }
        }
    }
}

@Composable
private fun ClassroomCell(state: ClassroomCellState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ClassroomCellStyle.Corner)
    val base = Modifier
        .size(ClassroomCellStyle.Size)
        .then(modifier)
    val outline = MaterialTheme.colorScheme.outlineVariant
    when (state) {
        ClassroomCellState.Free -> Box(
            base
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(ClassroomCellStyle.BorderWidth, outline, shape),
        )

        ClassroomCellState.Occupied -> Box(
            base
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.withAlpha(ClassroomCellStyle.OccupiedAlpha)),
        )

        ClassroomCellState.Mine -> Box(
            base
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        )

        ClassroomCellState.Unknown -> Box(
            base.drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(ClassroomCellStyle.Corner.toPx()),
                    style = Stroke(
                        width = ClassroomCellStyle.BorderWidth.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(2.dp.toPx(), 1.5.dp.toPx()),
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
private fun FailureRow(count: Int, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count 栋未查询成功",
            style = caption2Style(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        PrismTextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun FilteredEmptyState(onClear: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NullStatus(
            icon = Icons.Rounded.MeetingRoom,
            title = "没有符合条件的教室",
            subtitle = "试试第 5–6 节或换栋楼",
        )
        PrismButton(filled = false, onClick = onClear) { Text("清空筛选") }
    }
}

@Composable
private fun NoClassroomsState(
    allBuildings: Boolean,
    failureCount: Int,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NullStatus(
            icon = Icons.Rounded.CalendarMonth,
            title = if (allBuildings) "今天这些楼没有排教室" else "今天这栋楼没有排教室",
            subtitle = "换一天或换栋楼看看",
        )
        if (failureCount > 0) FailureRow(count = failureCount, onRetry = onRetry)
    }
}

@Composable
private fun AllFreeCard(
    roomCount: Int,
    allBuildings: Boolean,
    onShowDetails: () -> Unit,
) {
    PrismCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        CardHeader("全天空闲")
        Spacer(Modifier.height(6.dp))
        Text(
            "${if (allBuildings) "已查询的" else "本楼"} $roomCount 间教室今天全天空闲",
            style = subheadlineStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PrismTextButton(onClick = onShowDetails) { Text("查看详情") }
    }
}

@Composable
private fun ClassroomSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        SectionNumbersHeader()
        repeat(3) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PrismSkeleton(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .height(14.dp),
                        corner = 6f,
                    )
                    PrismSkeleton(
                        Modifier
                            .fillMaxWidth(0.28f)
                            .height(11.dp),
                        corner = 4f,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(ClassroomCellStyle.Gap)) {
                    repeat(ClassroomColumns) {
                        PrismSkeleton(
                            Modifier.size(ClassroomCellStyle.Size),
                            corner = 2f,
                        )
                    }
                }
            }
        }
    }
}

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
                Spacer(Modifier.height(4.dp))
                Text("容量与设备信息以教务数据为准", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            PrismSection(title = "今日空闲时段") {
                // 时段以教务 getJsxx 返回为准；上游未返回明细时不编造数据
                NullStatus(
                    Icons.Rounded.CalendarMonth,
                    "暂无分时段数据",
                    "空闲时段以上游返回为准；可在空闲教室页按日期/教学楼重新查询",
                )
            }
            PrismCard {
                CardHeader("说明")
                Spacer(Modifier.height(6.dp))
                Text("教室容量、设备与分时段空闲情况均以教务系统为准。", style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}