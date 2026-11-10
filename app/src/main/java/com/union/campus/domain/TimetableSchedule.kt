package com.union.campus.domain

import java.util.Locale

/**
 * 教学楼分组：
 * - J1–J6 一套作息；
 * - S1–S6、J7 一套作息。
 * 两套作息同时运行，只有上午第 3、4 节时间不同。
 */
enum class BuildingGroup(
    val label: String,
    val classCodes: String,
) {
    J1ToJ6("J1–J6", "J1–J6"),
    S1ToS6AndJ7("S1–S6、J7", "S1–S6、J7"),
}

/** 第 3、4 节的两种时间槽；每学期两套作息可能互换。 */
enum class MorningSlot(
    val label: String,
    val startLabel: String,
    val thirdStart: String,
    val thirdEnd: String,
    val fourthStart: String,
    val fourthEnd: String,
) {
    Early(
        label = "10:20 开始",
        startLabel = "10:20",
        thirdStart = "10:20",
        thirdEnd = "11:05",
        fourthStart = "11:15",
        fourthEnd = "12:00",
    ),
    Late(
        label = "10:40 开始",
        startLabel = "10:40",
        thirdStart = "10:40",
        thirdEnd = "11:25",
        fourthStart = "11:35",
        fourthEnd = "12:20",
    );

    fun other(): MorningSlot = if (this == Early) Late else Early
}

/**
 * 本学期的“楼号 → 作息”关联。
 *
 * @param j1ToJ6Slot J1–J6 使用的时间槽；S1–S6、J7 自动使用另一个时间槽。
 * @param fallbackGroup 课程地点里没有识别出楼号时使用的默认组。
 */
data class TimetableScheduleConfig(
    val j1ToJ6Slot: MorningSlot = MorningSlot.Late,
    val fallbackGroup: BuildingGroup = BuildingGroup.J1ToJ6,
) {
    fun slotFor(group: BuildingGroup): MorningSlot =
        if (group == BuildingGroup.J1ToJ6) j1ToJ6Slot else j1ToJ6Slot.other()

    companion object {
        fun fromStored(jLate: Boolean, fallbackValue: Int): TimetableScheduleConfig =
            TimetableScheduleConfig(
                j1ToJ6Slot = if (jLate) MorningSlot.Late else MorningSlot.Early,
                fallbackGroup = if (fallbackValue == 2) BuildingGroup.S1ToS6AndJ7
                else BuildingGroup.J1ToJ6,
            )
    }
}

/** 某门课实际使用的时间、楼号组和时间槽。 */
data class CourseScheduleInfo(
    val group: BuildingGroup,
    val slot: MorningSlot,
    val start: String,
    val end: String,
    val detectedFromLocation: Boolean,
)

/**
 * 课表节次时间。
 *
 * 第 1、2 节与第 5–10 节两套作息相同；第 3、4 节由 [MorningSlot] 决定。
 * 显示时按课程地点里的楼号（J1–J6 / S1–S6、J7）自动匹配，识别不出时用默认组。
 */
object TimetableSchedule {

    const val MAX_SECTION = 10

    /** 第 4 节之后是午休。 */
    const val LUNCH_AFTER_SECTION = 4

    /** 第 8 节之后是晚休。 */
    const val DINNER_AFTER_SECTION = 8

    /** 第 9、10 节是晚间课程。 */
    const val EVENING_START_SECTION = 9

    private val fixedTimes = listOf(
        SectionTime(1, "08:20", "09:05"),
        SectionTime(2, "09:15", "10:00"),
        SectionTime(3, "10:20", "11:05"),
        SectionTime(4, "11:15", "12:00"),
        SectionTime(5, "14:30", "15:15"),
        SectionTime(6, "15:25", "16:10"),
        SectionTime(7, "16:30", "17:15"),
        SectionTime(8, "17:25", "18:10"),
        SectionTime(9, "18:30", "19:15"),
        SectionTime(10, "19:25", "20:10"),
    )

    private val buildingRegex = Regex("""(?:^|[^A-Z0-9])([JS])\s*-?\s*(\d{1,2})""")

    /** 按时间槽返回完整的 1–10 节时间。 */
    fun sectionTimes(slot: MorningSlot): List<SectionTime> = fixedTimes.map { time ->
        when (time.section) {
            3 -> time.copy(start = slot.thirdStart, end = slot.thirdEnd)
            4 -> time.copy(start = slot.fourthStart, end = slot.fourthEnd)
            else -> time
        }
    }

    /** 按楼号组返回完整的 1–10 节时间。 */
    fun sectionTimes(
        group: BuildingGroup,
        config: TimetableScheduleConfig,
    ): List<SectionTime> = sectionTimes(config.slotFor(group))

    /** 某门课的实际作息：优先从地点识别楼号，识别不出时用默认组。 */
    fun courseSchedule(
        cell: CourseCell,
        config: TimetableScheduleConfig,
    ): CourseScheduleInfo? {
        val detected = buildingGroupOf(cell.location)
        val group = detected ?: config.fallbackGroup
        val slot = config.slotFor(group)
        val times = sectionTimes(slot).associateBy { it.section }
        val start = times[cell.startSection]?.start ?: return null
        val end = times[cell.endSection]?.end
            ?: times[cell.startSection]?.end
            ?: return null
        return CourseScheduleInfo(
            group = group,
            slot = slot,
            start = start,
            end = end,
            detectedFromLocation = detected != null,
        )
    }

    /** 从课程地点中识别楼号：J1–J6 → J 组，J7 / S1–S6 → S 组。 */
    fun buildingGroupOf(location: String): BuildingGroup? {
        if (location.isBlank()) return null
        val match = buildingRegex.find(location.uppercase(Locale.ROOT)) ?: return null
        val number = match.groupValues[2].toIntOrNull() ?: return null
        return when (match.groupValues[1]) {
            "J" -> when (number) {
                in 1..6 -> BuildingGroup.J1ToJ6
                7 -> BuildingGroup.S1ToS6AndJ7
                else -> null
            }
            "S" -> if (number in 1..6) BuildingGroup.S1ToS6AndJ7 else null
            else -> null
        }
    }

    /** 今天“正在上/下一节”的课使用的时间槽；没有课时用默认组。 */
    fun slotForCurrentOrNext(
        cells: List<CourseCell>,
        config: TimetableScheduleConfig,
        now: Long,
    ): MorningSlot {
        val scheduled = cells.mapNotNull { cell ->
            val info = courseSchedule(cell, config) ?: return@mapNotNull null
            val start = SemesterCalc.timeToMillisToday(info.start, now) ?: return@mapNotNull null
            val end = SemesterCalc.timeToMillisToday(info.end, now) ?: return@mapNotNull null
            Triple(cell, info, start to end)
        }
        val current = scheduled.firstOrNull { (_, _, range) -> now in range.first..range.second }
        val next = scheduled
            .filter { (_, _, range) -> range.first > now }
            .minByOrNull { (_, _, range) -> range.first }
        return (current?.second ?: next?.second)?.slot
            ?: config.slotFor(config.fallbackGroup)
    }

    /** 第 3、4 节时间（用于设置页摘要）。 */
    fun morningTimes(
        group: BuildingGroup,
        config: TimetableScheduleConfig,
    ): Pair<SectionTime, SectionTime> {
        val times = sectionTimes(group, config)
        return times.first { it.section == 3 } to times.first { it.section == 4 }
    }

    /** 午休：第 4 节结束 → 第 5 节开始。 */
    fun lunchRange(slot: MorningSlot): Pair<String, String> {
        val times = sectionTimes(slot)
        return times.first { it.section == LUNCH_AFTER_SECTION }.end to
            times.first { it.section == LUNCH_AFTER_SECTION + 1 }.start
    }

    /** 晚休：第 8 节结束 → 第 9 节开始（两套作息相同）。 */
    fun dinnerRange(): Pair<String, String> {
        val times = sectionTimes(MorningSlot.Early)
        return times.first { it.section == DINNER_AFTER_SECTION }.end to
            times.first { it.section == DINNER_AFTER_SECTION + 1 }.start
    }
}