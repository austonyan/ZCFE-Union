package com.union.campus.ui.screens.life

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.core.AppError
import com.union.campus.core.userFacingMessage
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.FreeClassroom
import com.union.campus.domain.SectionTime
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.TeachingBuilding
import com.union.campus.domain.TimetableData
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismErrorState
import com.union.campus.ui.prism.rememberPrismMotion
import com.union.campus.ui.prism.PrismTextButton
import com.union.campus.ui.prism.caption2Style
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.ZoneId

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
    val motion = rememberPrismMotion()

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
                buildingsError = "教学楼加载失败：${e.userFacingMessage()}"
                emptyList()
            }
        } else {
            emptyList()
        }
        if (jw.isBound() && buildings.isEmpty() && buildingsError == null) {
            buildingsError = "暂无教学楼列表，请稍后重试"
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
                            failures[building.dm to dateIso] = "教室查询失败：${e.userFacingMessage()}"
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
            // 日期单独一行；节次与楼栋成组，给长名称和清除按钮留出空间。
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateFilterControl(
                    weekday = "周" + WeekdayLabels[selectedDate.dayOfWeek.value - 1],
                    dateText = "%02d/%02d".format(selectedDate.monthValue, selectedDate.dayOfMonth),
                    isToday = dayOffset == 0,
                    modifier = Modifier.fillMaxWidth(),
                    canGoBack = dayOffset > 0,
                    canGoForward = dayOffset < DateRangeDays,
                    onBack = { dateIso = selectedDate.minusDays(1).toString() },
                    onForward = { dateIso = selectedDate.plusDays(1).toString() },
                    onClick = { datePickerOpen = true },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(
                        label = sectionFilters.takeIf { it.isNotEmpty() }
                            ?.let { sectionLabel(it) }
                            ?: "节次",
                        selected = sectionFilters.isNotEmpty(),
                        onClick = { sectionSheetOpen = true },
                        modifier = Modifier.weight(1f),
                        fillWidth = true,
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
                        modifier = Modifier.weight(1f),
                        fillWidth = true,
                    )
                }
            }

            AnimatedContent(
                targetState = dateIso,
                transitionSpec = {
                    fadeIn(motion.tween(210, delayMillis = 90)) togetherWith
                        fadeOut(motion.tween(90))
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
                    if (matchingRooms.isNotEmpty()) {
                        Text(
                            "${matchingRooms.size} 间教室 · ${matchingGroups.size} 栋楼" + if (awaiting) "（查询中）" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                            hint = "部分楼栋查询失败，请稍后重试",
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

private const val DateRangeDays = 30
private const val QueryConcurrency = 6

/** 节次筛选跨页面返回保存。 */
private val SectionFilterSaver = listSaver<List<Int>, Int>(
    save = { it },
    restore = { it },
)

private fun sectionLabel(sections: List<Int>): String =
    if (sections.isEmpty()) "" else "第 ${sections.sorted().joinToString("·")} 节"
